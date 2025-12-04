package com.swingauth.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ngrok 터널 URL을 감지하는 유틸리티
 */
public class NgrokUtil {
    private static final String NGROK_API_URL = "http://localhost:4040/api/tunnels";
    
    /**
     * ngrok이 실행 중인지 확인하고 HTTPS URL을 가져옴
     * @return HTTPS URL (ngrok이 실행 중이 아니면 null)
     */
    public static String getNgrokHttpsUrl() {
        try {
            URL url = new URL(NGROK_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000); // 2초 타임아웃
            connection.setReadTimeout(2000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                JSONArray tunnels = json.getJSONArray("tunnels");
                
                // HTTPS 터널 찾기
                for (int i = 0; i < tunnels.length(); i++) {
                    JSONObject tunnel = tunnels.getJSONObject(i);
                    String protocol = tunnel.getString("proto");
                    if ("https".equals(protocol)) {
                        String publicUrl = tunnel.getString("public_url");
                        System.out.println("[NgrokUtil] ngrok HTTPS URL 발견: " + publicUrl);
                        return publicUrl;
                    }
                }
                
                // HTTPS가 없으면 HTTP 터널 사용
                if (tunnels.length() > 0) {
                    JSONObject tunnel = tunnels.getJSONObject(0);
                    String publicUrl = tunnel.getString("public_url");
                    System.out.println("[NgrokUtil] ngrok HTTP URL 발견: " + publicUrl);
                    return publicUrl;
                }
            }
        } catch (Exception e) {
            // ngrok이 실행 중이 아니거나 API에 접근할 수 없음
            // 조용히 실패 (로그 출력 안 함)
        }
        return null;
    }
    
    /**
     * ngrok이 실행 중인지 확인
     * @return ngrok이 실행 중이면 true
     */
    public static boolean isNgrokRunning() {
        return getNgrokHttpsUrl() != null;
    }
}

