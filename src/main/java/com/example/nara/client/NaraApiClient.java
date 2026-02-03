package com.example.nara.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 나라장터 API HTTP 호출 전담.
 * 역할: URL 조립, 요청 전송, 응답 본문 문자열 반환만 담당.
 * 비즈니스 로직·파싱은 Service 계층에서 처리.
 */
public final class NaraApiClient {

    /**
     * GET 요청으로 API를 호출하고 응답 본문(JSON 문자열)을 반환.
     *
     * @param connTimeoutMs   연결 타임아웃(ms)
     * @param requestTimeoutMs 요청 타임아웃(ms)
     * @return JSON 문자열
     */
    public String get(
            String baseUrl,
            String serviceKey,
            String numOfRows,
            String pageNo,
            String bidNtceBgnDt,
            String bidNtceEndDt,
            String type,
            int connTimeoutMs,
            int requestTimeoutMs
    ) throws IOException, InterruptedException {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connTimeoutMs))
                .build();

        String apiUrl = baseUrl
                + "?numOfRows=" + numOfRows
                + "&pageNo=" + pageNo
                + "&bidNtceBgnDt=" + bidNtceBgnDt
                + "&bidNtceEndDt=" + bidNtceEndDt
                + "&ServiceKey=" + serviceKey
                + "&type=" + type;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " / body = " + response.body());
        }

        return response.body();
    }

    private NaraApiClient() {}

    private static final NaraApiClient INSTANCE = new NaraApiClient();

    public static NaraApiClient getInstance() {
        return INSTANCE;
    }
}
