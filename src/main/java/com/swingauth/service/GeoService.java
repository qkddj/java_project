package com.swingauth.service;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// 간단한 IP → 위치 조회
// - ip-api.com은 무료/무인증(제한 있음). 상용 서비스는 키 필요.
// - 데스크톱에서 호출 시, 클라이언트 공인 IP 기준으로 지역 추정(정확도는 네트워크/ISP에 따라 달라짐).
public class GeoService {
  private final HttpClient http = HttpClient.newHttpClient();

  public static class GeoInfo {
    public String ip;       // ipify로 조회한 IP (옵션)
    public Double lat;
    public Double lon;
    public String country;
    public String region;   // regionName
    public String city;     // city
    public String neighborhood; // 간단 파생 (region + " " + city)
  }

  public GeoInfo fetch() {
    GeoInfo g = new GeoInfo();
    try {
      // 1) 공인 IP
      String ip = httpGetText("https://api.ipify.org");
      g.ip = (ip != null && !ip.isBlank()) ? ip.trim() : null;
    } catch (Exception ignored) {}

    try {
      // 2) 위치 (호출 IP 기준, 별도 IP 지정하려면 .../json/{ip})
      String json = httpGetText("http://ip-api.com/json");
      if (json != null) {
        JSONObject o = new JSONObject(json);
        if ("success".equalsIgnoreCase(o.optString("status"))) {
          g.lat = o.optDouble("lat");
          g.lon = o.optDouble("lon");
          g.country = o.optString("country", null);
          g.region = o.optString("regionName", null);
          g.city = o.optString("city", null);
        }
      }
    } catch (Exception ignored) {}

    // 파생 필드
    if (g.region != null && g.city != null) {
      g.neighborhood = g.region + " " + g.city;
    } else if (g.city != null) {
      g.neighborhood = g.city;
    } else if (g.region != null) {
      g.neighborhood = g.region;
    } else if (g.country != null) {
      g.neighborhood = g.country;
    } else {
      g.neighborhood = "unknown";
    }

    return g;
  }

  private String httpGetText(String url) throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "swing-mongo-auth/1.0")
        .GET()
        .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() >= 200 && res.statusCode() < 300) {
      return res.body();
    }
    return null;
  }
}
