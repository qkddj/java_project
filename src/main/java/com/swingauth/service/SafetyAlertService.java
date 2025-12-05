package com.swingauth.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.swingauth.db.Mongo;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 행안부 긴급재난문자 + 경찰청 실종경보 등을 가져오되,
 * API 호출 결과를 MongoDB alerts 컬렉션에 캐시해 두고
 * 일정 시간(1시간) 이내에는 DB 데이터만 사용하는 서비스.
 *
 * ★ 실제 endpoint URL/파라미터/JSON 구조는
 *   각 오픈API 문서를 보고 맞춰야 한다. (여기는 골격 예시)
 */
public class SafetyAlertService {

    // === 캐시 TTL (ms) : 1시간 ===
    private static final long CACHE_MILLIS = 60 * 60 * 1000L;

    // TODO: 행안부 재난문자 serviceKey 발급 후 교체
    private static final String MOIS_SERVICE_KEY = "행안부_서비스키";

    // 경찰청 안전Dream 실종경보 authKey (발급받은 값)
    private static final String POLICE_AMBER_KEY = "726ce91740b845ad";

    // 안전Dream 문서 기준 esntlId (고유아이디) – 키 목록 화면의 "발급 ID"
    private static final String POLICE_ESNTL_ID = "10000898";

    // TODO: 실제 긴급재난문자 API URL (나중에 교체)
    private static final String MOIS_ALERT_URL =
            "https://www.safetydata.go.kr/openapi/긴급재난문자_엔드포인트";

    // 안전Dream 실종경보(amberList) URL – 문서 기준 POST
    private static final String POLICE_AMBER_URL =
            "https://www.safe182.go.kr/api/lcm/amberList.do";

    // Amber Alert를 최근 며칠까지 가져올지 (임시 30일)
    private static final int AMBER_DAYS_RANGE = 30;

    // 전국 기준 조회 시 사용하는 regionKey
    private static final String GLOBAL_REGION_KEY = "__ALL__";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final MongoCollection<Document> alertsColl = Mongo.alerts();

    /** 공통 포맷 Alert DTO */
    public static class Alert {
        public String type;      // "재난문자" / "실종경보" 등
        public String region;    // "서울특별시 송파구 잠실동" 등
        public String message;   // 리스트에서 보일 한 줄 요약
        public String timeText;  // 표시용 시간 문자열
        public OffsetDateTime timestamp; // 정렬용 (null 가능)

        // ===== Amber(실종경보) 상세용 필드 =====
        public String name;          // 성명
        public String ageNow;        // 현재 나이
        public String sex;           // 성별 코드/설명
        public String feature;       // 옷차림/신체특징(etcSpfeatr)
        public String occrDate;      // 발생일자(YYYYMMDD)
        public String detailAddress; // 발생장소
        public String rawSource;     // 원본 JSON 문자열 (디버깅용, 선택)

        @Override
        public String toString() {
            String t = (timeText != null && !timeText.isBlank()) ? " (" + timeText + ")" : "";
            return "[" + type + "][" + region + "] " + message + t;
        }
    }

    /* =========================================================
       MainFrame에서 호출하는 공개 메서드들
       ========================================================= */

    /**
     * (이전 버전과의 호환용)
     * 지역 기준(영문 region/city)을 사용해 캐시+API로 알림 조회.
     * 지금은 쓰지 않지만 남겨둔다.
     */
    public List<Alert> fetchAllAlertsForRegion(String regionEn, String cityEn, int maxCount)
            throws IOException, InterruptedException {
        return getAlertsForRegion(regionEn, cityEn, maxCount);
    }

    /**
     * ✅ 전국 기준, 가장 최신 알림 maxCount건을 가져온다.
     * - MongoDB에 regionKey="__ALL__" 로 캐시
     * - 캐시가 1시간 이내면 DB만 조회
     */
    public List<Alert> fetchLatestAlerts(int maxCount)
            throws IOException, InterruptedException {

        String regionKeyKo = GLOBAL_REGION_KEY;
        long now = System.currentTimeMillis();

        // 1) 전국 기준 최신 fetchedAt 확인
        Document newest = alertsColl.find(Filters.eq("regionKey", regionKeyKo))
                .sort(Sorts.descending("fetchedAt"))
                .limit(1)
                .first();

        boolean needRefresh = true;
        if (newest != null) {
            Date fetchedAt = newest.getDate("fetchedAt");
            if (fetchedAt != null && now - fetchedAt.getTime() < CACHE_MILLIS) {
                needRefresh = false;
            }
        }

        // 2) 캐시가 오래되었으면, 실제 API로 전국 기준 최신 데이터 가져와서 캐시
        if (needRefresh) {
            List<Alert> fresh = fetchLatestAlertsFromApi(maxCount);

            alertsColl.deleteMany(Filters.eq("regionKey", regionKeyKo));

            Date fetchedAt = new Date();
            List<Document> docs = new ArrayList<>();
            for (Alert a : fresh) {
                docs.add(toDoc(a, regionKeyKo, fetchedAt));
            }
            if (!docs.isEmpty()) {
                alertsColl.insertMany(docs);
            }
        }

        // 3) 캐시에서 createdAt 내림차순으로 읽기
        List<Alert> result = new ArrayList<>();
        try (MongoCursor<Document> cur = alertsColl.find(Filters.eq("regionKey", regionKeyKo))
                .sort(Sorts.descending("createdAt"))
                .limit(maxCount)
                .iterator()) {
            while (cur.hasNext()) {
                result.add(fromDoc(cur.next()));
            }
        }
        return result;
    }

    /**
     * [기존 버전] 내 지역 기준 안전알림 조회 (캐시 사용)
     * 지금은 쓰지 않지만, 필요하면 재사용 가능.
     */
    public List<Alert> getAlertsForRegion(String regionEn, String cityEn, int maxCount)
            throws IOException, InterruptedException {

        String regionKeyKo = mapToKoreanRegionKeyword(regionEn, cityEn);

        long now = System.currentTimeMillis();

        // 1) 해당 regionKey의 최신 fetchedAt 을 확인
        Document newest = alertsColl.find(Filters.eq("regionKey", regionKeyKo))
                .sort(Sorts.descending("fetchedAt"))
                .limit(1)
                .first();

        boolean needRefresh = true;
        if (newest != null) {
            Date fetchedAt = newest.getDate("fetchedAt");
            if (fetchedAt != null && now - fetchedAt.getTime() < CACHE_MILLIS) {
                // 캐시가 아직 유효(1시간 이내)
                needRefresh = false;
            }
        }

        // 2) 캐시가 오래됐으면 API 한 번만 호출해서 갱신
        if (needRefresh) {
            List<Alert> fresh = fetchAllAlertsForRegionFromApi(regionEn, cityEn, maxCount);

            // 해당 지역 이전 캐시 삭제
            alertsColl.deleteMany(Filters.eq("regionKey", regionKeyKo));

            Date fetchedAt = new Date();
            List<Document> docs = new ArrayList<>();
            for (Alert a : fresh) {
                docs.add(toDoc(a, regionKeyKo, fetchedAt));
            }
            if (!docs.isEmpty()) {
                alertsColl.insertMany(docs);
            }
        }

        // 3) 최종적으로 DB에서 읽어서 반환 (항상 DB에서 읽음)
        List<Alert> result = new ArrayList<>();
        try (MongoCursor<Document> cur = alertsColl.find(Filters.eq("regionKey", regionKeyKo))
                .sort(Sorts.descending("createdAt"))
                .limit(maxCount)
                .iterator()) {
            while (cur.hasNext()) {
                result.add(fromDoc(cur.next()));
            }
        }
        return result;
    }

    /* ==================== DB 도큐먼트 <-> Alert 변환 ==================== */

    private Document toDoc(Alert a, String regionKeyKo, Date fetchedAt) {
        Document d = new Document();
        d.append("regionKey", regionKeyKo);
        d.append("type", a.type);
        d.append("region", a.region);
        d.append("message", a.message);
        d.append("timeText", a.timeText);
        d.append("fetchedAt", fetchedAt);

        // Amber 상세 필드 (없으면 null 저장)
        d.append("name", a.name);
        d.append("ageNow", a.ageNow);
        d.append("sex", a.sex);
        d.append("feature", a.feature);
        d.append("occrDate", a.occrDate);
        d.append("detailAddress", a.detailAddress);
        d.append("rawSource", a.rawSource);

        // createdAt: 실제 알림 시간(없으면 fetchedAt)
        Date createdAt;
        if (a.timestamp != null) {
            createdAt = Date.from(a.timestamp.toInstant());
        } else if (a.occrDate != null && !a.occrDate.isBlank()) {
            try {
                LocalDate dDate = LocalDate.parse(a.occrDate, DateTimeFormatter.BASIC_ISO_DATE);
                createdAt = Date.from(dDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
            } catch (Exception e) {
                createdAt = fetchedAt;
            }
        } else {
            createdAt = fetchedAt;
        }
        d.append("createdAt", createdAt);
        return d;
    }

    private Alert fromDoc(Document d) {
        Alert a = new Alert();
        a.type = d.getString("type");
        a.region = d.getString("region");
        a.message = d.getString("message");
        a.timeText = d.getString("timeText");

        a.name = d.getString("name");
        a.ageNow = d.getString("ageNow");
        a.sex = d.getString("sex");
        a.feature = d.getString("feature");
        a.occrDate = d.getString("occrDate");
        a.detailAddress = d.getString("detailAddress");
        a.rawSource = d.getString("rawSource");

        // timestamp는 굳이 다시 쓰지 않아도 되서 null 처리 (정렬은 createdAt 기반)
        a.timestamp = null;
        return a;
    }

    /* ==================== 지역 영문 → 한글 키워드 매핑 ==================== */

    private String mapToKoreanRegionKeyword(String regionEn, String cityEn) {
        String key = (regionEn != null && !regionEn.isBlank())
                ? regionEn.trim().toLowerCase()
                : (cityEn != null ? cityEn.trim().toLowerCase() : "");

        Map<String, String> map = new HashMap<>();
        map.put("seoul", "서울");
        map.put("busan", "부산");
        map.put("incheon", "인천");
        map.put("daegu", "대구");
        map.put("daejeon", "대전");
        map.put("gwangju", "광주");
        map.put("ulsan", "울산");
        map.put("sejong", "세종");
        map.put("gyeonggi-do", "경기");
        map.put("gangwon-do", "강원");
        map.put("chungcheongbuk-do", "충북");
        map.put("chungcheongnam-do", "충남");
        map.put("jeollabuk-do", "전북");
        map.put("jeollanam-do", "전남");
        map.put("gyeongsangbuk-do", "경북");
        map.put("gyeongsangnam-do", "경남");
        map.put("jeju-do", "제주");

        String val = map.get(key);
        if (val != null) return val;

        String cityKey = (cityEn != null ? cityEn.trim().toLowerCase() : "");
        val = map.get(cityKey);
        if (val != null) return val;

        return (regionEn != null && !regionEn.isBlank())
                ? regionEn
                : (cityEn != null ? cityEn : "");
    }

    /* ============ 실제 API를 날리는 부분 ============ */

    /**
     * (기존) 지역 기준 API 호출. 지금은 안 쓰지만 남겨둔다.
     */
    private List<Alert> fetchAllAlertsForRegionFromApi(String regionEn, String cityEn, int maxCount)
            throws IOException, InterruptedException {

        String regionKeywordKo = mapToKoreanRegionKeyword(regionEn, cityEn);

        List<Alert> all = new ArrayList<>();

        // TODO: 나중에 행안부 재난문자 붙이면 여기에서 호출
        // all.addAll(fetchMoisDisasterAlerts(regionKeywordKo, 20));

        int remain = Math.max(0, maxCount - all.size());
        if (remain > 0) {
            all.addAll(fetchMissingAlertsLast30Days(regionKeywordKo, remain));
        }

        all.sort((a, b) -> {
            if (a.timestamp == null && b.timestamp == null) return 0;
            if (a.timestamp == null) return 1;
            if (b.timestamp == null) return -1;
            return b.timestamp.compareTo(a.timestamp);
        });

        if (all.size() > maxCount) {
            return new ArrayList<>(all.subList(0, maxCount));
        }
        return all;
    }

    /**
     * ✅ 전국 기준 최신 알림을 API에서 직접 가져오는 버전.
     * - 지역 필터 없음 (전체 지역)
     * - Amber 30일 범위 내에서 최대 maxCount건까지 수집
     */
    private List<Alert> fetchLatestAlertsFromApi(int maxCount)
            throws IOException, InterruptedException {

        List<Alert> all = new ArrayList<>();

        // TODO: 나중에 행안부 재난문자 붙이면 여기에서 all.addAll(...)

        all.addAll(fetchMissingAlertsLast30Days("", maxCount));

        all.sort((a, b) -> {
            if (a.timestamp == null && b.timestamp == null) return 0;
            if (a.timestamp == null) return 1;
            if (b.timestamp == null) return -1;
            return b.timestamp.compareTo(a.timestamp);
        });

        if (all.size() > maxCount) {
            return new ArrayList<>(all.subList(0, maxCount));
        }
        return all;
    }

    /**
     * 행안부 재난문자 – 아직 실제 스펙을 안 맞춰서 더미 구현.
     * 나중에 MOIS_SERVICE_KEY / MOIS_ALERT_URL 세팅 후 구현하면 됨.
     */
    private List<Alert> fetchMoisDisasterAlerts(String regionKeywordKo, int rows)
            throws IOException, InterruptedException {

        // TODO: 실제 행안부 재난문자 API 스펙에 맞춰 구현
        return Collections.emptyList();
    }

    /**
     * 경찰청 안전Dream 실종경보(amberList) API를
     * 최근 30일 범위에서 돌면서 최대 maxCount 개까지 가져온다.
     * - regionKeywordKo가 비어있지 않으면 해당 키워드가 포함된 지역/특징만 남김
     * - 이름+발생일자+주소 조합으로 중복 제거
     */
    private List<Alert> fetchMissingAlertsLast30Days(String regionKeywordKo, int maxCount)
            throws IOException, InterruptedException {

        List<Alert> result = new ArrayList<>();
        String lowerRegionKeyword = regionKeywordKo.toLowerCase();

        // 이미 추가된 알림을 기억하기 위한 Set
        Set<String> seenKeys = new HashSet<>();

        LocalDate today = LocalDate.now();

        for (int offset = 0; offset < AMBER_DAYS_RANGE && result.size() < maxCount; offset++) {

            String date = today.minusDays(offset)
                    .format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD

            String form = "esntlId=" + URLEncoder.encode(POLICE_ESNTL_ID, StandardCharsets.UTF_8)
                    + "&authKey=" + URLEncoder.encode(POLICE_AMBER_KEY, StandardCharsets.UTF_8)
                    + "&rowSize=" + maxCount
                    + "&page=1"
                    + "&occrde=" + date;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(POLICE_AMBER_URL))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                continue;
            }

            String body = response.body();
            JSONObject root = new JSONObject(body);

            String resultCode = root.optString("result", "");
            if (!"00".equals(resultCode)) {
                continue;
            }

            JSONArray list = root.optJSONArray("list");
            if (list == null) continue;

            for (int i = 0; i < list.length() && result.size() < maxCount; i++) {
                JSONObject item = list.getJSONObject(i);

                String addr = item.optString("occrAdres", "");     // 발생장소
                String name = item.optString("nm", "");            // 성명
                String ageNow = item.optString("ageNow", "");      // 현재 나이
                String sex = item.optString("sexdstnDscd", "");    // 성별 코드
                String feature = item.optString("etcSpfeatr", ""); // 옷차림/특징
                String occrde = item.optString("occrde", date);    // 발생일자

                // ===== 중복 체크용 키 =====
                String key = name + "|" + occrde + "|" + addr;
                if (!seenKeys.add(key)) {
                    // 이미 같은 (이름, 날짜, 장소) 조합이 있다면 스킱
                    continue;
                }

                String joined = (addr + " " + feature).toLowerCase();
                if (!lowerRegionKeyword.isBlank()
                        && !joined.contains(lowerRegionKeyword)) {
                    continue;
                }

                Alert a = new Alert();
                a.type = "실종경보";
                a.region = addr.isBlank() ? "전국" : addr;
                a.name = name;
                a.ageNow = ageNow;
                a.sex = sex;
                a.feature = feature;
                a.occrDate = occrde;
                a.detailAddress = addr;
                a.timeText = occrde;

                // 리스트에서 보이는 한 줄 요약
                a.message = String.format("%s(%s세) 실종 – %s", name, ageNow, feature);

                // timestamp (발생일 00시 기준)
                try {
                    LocalDate dDate = LocalDate.parse(occrde, DateTimeFormatter.BASIC_ISO_DATE);
                    a.timestamp = dDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
                } catch (Exception e) {
                    a.timestamp = null;
                }

                a.rawSource = item.toString();

                result.add(a);
            }
        }

        return result;
    }

    private OffsetDateTime parseIsoTimeOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
