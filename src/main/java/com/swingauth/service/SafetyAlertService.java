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
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 행안부 긴급재난문자를 가져오고,
 * MongoDB alerts 컬렉션에 캐시해 두는 서비스.
 *
 * - 캐시 TTL: 1시간
 * - 전국 기준 최신 N개(예: 30개)만 가져와 정렬해서 반환
 * - 필요하면 지역 기준 조회 메서드(getAlertsForRegion)도 사용 가능
 */
public class SafetyAlertService {

    // === 캐시 TTL (ms) : 1시간 ===
    private static final long CACHE_MILLIS = 60 * 60 * 1000L;

    // ★ 행안부 재난문자 serviceKey (네가 발급받은 값으로 교체)
    private static final String MOIS_SERVICE_KEY = "N8TKWZ468EC6747W";

    // 행안부 긴급재난문자 API URL
    private static final String MOIS_ALERT_URL =
            "https://www.safetydata.go.kr/V2/api/DSSP-IF-00247";

    // 전국 기준 조회 시 사용하는 regionKey
    private static final String GLOBAL_REGION_KEY = "__ALL__";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final MongoCollection<Document> alertsColl = Mongo.alerts();

    /** 공통 포맷 Alert DTO */
    public static class Alert {
        public String type;      // "재난문자"
        public String region;    // "서울특별시 송파구 잠실동" 등
        public String message;   // 리스트에서 보일 한 줄 요약
        public String timeText;  // 표시용 시간 문자열 (CRT_DT)
        public OffsetDateTime timestamp; // 정렬용 (null 가능)

        // 선택: 재난문자 추가 필드들
        public String stepName;      // 긴급단계명(EMRG_STEP_NM)
        public String disasterType;  // 재해구분명(DST_SE_NM)
        public String sn;            // 일련번호(SN)
        public String regYmd;        // 등록일자(REG_YMD)
        public String mdfcnYmd;      // 수정일자(MDFCN_YMD)

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
     * ✅ 전국 기준, 가장 최신 알림 maxCount건을 가져온다.
     * - MongoDB에 regionKey="__ALL__" 로 캐시
     * - 캐시가 1시간 이내면 DB만 조회
     * - 재난문자만 사용 (안전Dream, 실종경보 X)
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
     * (옵션) 내 지역 기준 안전알림 조회 (캐시 사용)
     * - regionEn / cityEn 은 영문 (예: "seoul", "chungcheongnam-do")
     * - 내부에서 한글 키워드로 매핑해서 사용
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
            List<Alert> fresh = fetchAllAlertsForRegionFromApi(regionKeyKo, maxCount);

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

        d.append("stepName", a.stepName);
        d.append("disasterType", a.disasterType);
        d.append("sn", a.sn);
        d.append("regYmd", a.regYmd);
        d.append("mdfcnYmd", a.mdfcnYmd);

        // createdAt: 실제 알림 시간(없으면 fetchedAt)
        Date createdAt;
        if (a.timestamp != null) {
            createdAt = Date.from(a.timestamp.toInstant());
        } else if (a.timeText != null && !a.timeText.isBlank()) {
            // CRT_DT 기반으로 한 번 더 시도
            OffsetDateTime odt = parseMoisTimeOrNull(a.timeText);
            if (odt != null) {
                createdAt = Date.from(odt.toInstant());
            } else {
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

        a.stepName = d.getString("stepName");
        a.disasterType = d.getString("disasterType");
        a.sn = d.getString("sn");
        a.regYmd = d.getString("regYmd");
        a.mdfcnYmd = d.getString("mdfcnYmd");

        a.timestamp = null;  // 정렬은 createdAt 기준이라 다시 세팅 안 해도 됨
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
     * 지역 기준 API 호출 (재난문자만).
     */
    private List<Alert> fetchAllAlertsForRegionFromApi(String regionKeywordKo, int maxCount)
            throws IOException, InterruptedException {

        List<Alert> all = new ArrayList<>();

        // 행안부 재난문자 (지역 키워드 기준)
        all.addAll(fetchMoisDisasterAlerts(regionKeywordKo, maxCount));

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
     */
    private List<Alert> fetchLatestAlertsFromApi(int maxCount)
            throws IOException, InterruptedException {

        List<Alert> all = fetchMoisDisasterAlerts("", maxCount);

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
     * ✅ 행안부 재난문자 API 호출 구현.
     * regionKeywordKo 가 비어있지 않으면 rgnNm 파라미터와
     * 메시지/지역 문자열에 대한 추가 필터 둘 다 적용.
     */
    private List<Alert> fetchMoisDisasterAlerts(String regionKeywordKo, int rows)
            throws IOException, InterruptedException {

        List<Alert> result = new ArrayList<>();

        StringBuilder url = new StringBuilder(MOIS_ALERT_URL)
                .append("?serviceKey=")
                .append(URLEncoder.encode(MOIS_SERVICE_KEY, StandardCharsets.UTF_8))
                .append("&pageNo=1")
                .append("&numOfRows=").append(rows)
                .append("&returnType=json");

        // 지역명이 있으면 rgnNm 파라미터로 같이 넘겨줌 (예: "서울", "충남" 등)
        if (regionKeywordKo != null && !regionKeywordKo.isBlank()) {
            url.append("&rgnNm=")
               .append(URLEncoder.encode(regionKeywordKo, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("MOIS API HTTP 오류: " + response.statusCode() +
                    " body=" + response.body());
        }

        String body = response.body();

        JSONArray items = extractMoisItemsArray(body);
        if (items == null) {
            System.err.println("[MOIS] JSON 파싱 오류: items 배열을 찾을 수 없음");
            return result;
        }

        String lowerRegionKeyword = regionKeywordKo == null ? "" : regionKeywordKo.toLowerCase();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);

            String msg       = item.optString("MSG_CN", "").trim();        // 메시지내용
            String areaName  = item.optString("RCPTN_RGN_NM", "").trim();  // 수신지역명
            String crtDt     = item.optString("CRT_DT", "").trim();        // 생성일시
            String stepNm    = item.optString("EMRG_STEP_NM", "").trim();  // 긴급단계명
            String dstSeNm   = item.optString("DST_SE_NM", "").trim();     // 재해구분명
            String sn        = item.optString("SN", "").trim();            // 일련번호
            String regYmd    = item.optString("REG_YMD", "").trim();       // 등록일자
            String mdfcnYmd  = item.optString("MDFCN_YMD", "").trim();     // 수정일자

            if (msg.isEmpty() && areaName.isEmpty()) {
                continue;
            }

            String joined = (areaName + " " + msg).toLowerCase();
            if (!lowerRegionKeyword.isBlank()
                    && !joined.contains(lowerRegionKeyword)) {
                continue;
            }

            Alert a = new Alert();
            a.type = "재난문자";
            a.region = areaName.isBlank() ? "전국" : areaName;

            // 리스트에서 보이는 한 줄 요약
            String prefix = stepNm.isEmpty() ? "" : ("[" + stepNm + "] ");
            a.message = prefix + msg;

            a.timeText = crtDt;
            a.timestamp = parseMoisTimeOrNull(crtDt);

            a.stepName = stepNm;
            a.disasterType = dstSeNm;
            a.sn = sn;
            a.regYmd = regYmd;
            a.mdfcnYmd = mdfcnYmd;

            result.add(a);
        }

        return result;
    }

    /* ==================== 시간/JSON 파싱 유틸 ==================== */

    /**
     * 행안부 CRT_DT 포맷은 문서에 명시가 없어서
     * 몇 가지 가능한 포맷을 순서대로 시도.
     */
    private OffsetDateTime parseMoisTimeOrNull(String s) {
        if (s == null || s.isBlank()) return null;

        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyyMMddHHmmss",
                "yyyyMMddHHmm",
                "yyyyMMdd"
        };

        for (String p : patterns) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern(p);
                if (p.length() >= 12) { // 날짜+시간
                    LocalDateTime ldt = LocalDateTime.parse(s, f);
                    return ldt.atZone(KST).toOffsetDateTime();
                } else { // 날짜만
                    LocalDate ld = LocalDate.parse(s, f);
                    return ld.atStartOfDay(KST).toOffsetDateTime();
                }
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /**
     * 행안부 JSON 응답에서 실제 데이터 배열을 찾아서 반환.
     * - body가 JSONArray 인 케이스까지 모두 처리
     */
    private JSONArray extractMoisItemsArray(String body) {
        try {
            String trimmed = body.trim();

            // 응답이 바로 배열인 경우
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed);
            }

            JSONObject root = new JSONObject(trimmed);

            // 1) body가 곧 배열인 경우
            if (root.has("body")) {
                Object b = root.get("body");
                if (b instanceof JSONArray) {
                    return (JSONArray) b;
                }
                if (b instanceof JSONObject) {
                    JSONObject bj = (JSONObject) b;
                    if (bj.has("items")) return bj.getJSONArray("items");
                    if (bj.has("data"))  return bj.getJSONArray("data");
                }
            }

            // 2) data / items가 상위에 있는 경우
            if (root.has("data")) {
                Object d = root.get("data");
                if (d instanceof JSONArray) return (JSONArray) d;
            }
            if (root.has("items")) {
                Object d = root.get("items");
                if (d instanceof JSONArray) return (JSONArray) d;
            }

        } catch (Exception e) {
            System.err.println("[MOIS] JSON 파싱 오류: " + e.getMessage());
        }
        return null;
    }
}
