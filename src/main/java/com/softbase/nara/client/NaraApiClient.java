package com.softbase.nara.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

/*
나라장터 API HTTP 호출 전담.
역할: URL 조립, 요청 전송, 응답 본문 문자열 반환만 담당.
비즈니스 로직·파싱은 Service 계층에서 처리.
 */
public final class NaraApiClient {

    private static final Logger log = Logger.getLogger(NaraApiClient.class.getName());

    /*
    GET 요청으로 API를 호출하고 응답 본문(JSON 문자열)을 반환.
    @param connTimeoutMs     연결 타임아웃(ms)
    @param requestTimeoutMs  요청 타임아웃(ms)
    @return JSON 문자열
     */
    public String get(
            String baseUrl,             // 나라장터 API 기본 End Point URL
            String serviceKey,          // 일반 인증키
            String numOfRows,           // 한 페이지 결과 수
            String pageNo,              // 페이지 번호
            String bidNtceBgnDt,        // 입찰공고시작일시
            String bidNtceEndDt,        // 입찰공고종료일시
            String type,                // 응답 타입 (json, xml)
            int connTimeoutMs,          // 연결 타임아웃(ms)
            int requestTimeoutMs        // 요청 타임아웃(ms)
    ) throws IOException, InterruptedException {

        // HTTP 클라이언트 생성 (연결 타임아웃 설정)
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connTimeoutMs))
                .build();

        // API URL 쿼리스트링 조합 (기본 End Point URL + 파라미터)
        String apiUrl = baseUrl
                + "?numOfRows=" + numOfRows
                + "&pageNo=" + pageNo
                + "&bidNtceBgnDt=" + bidNtceBgnDt
                + "&bidNtceEndDt=" + bidNtceEndDt
                + "&ServiceKey=" + serviceKey
                + "&type=" + type;

        // HTTP 요청 생성 (URI, 타임아웃, 헤더)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Accept", "application/json")
                .GET()
                .build();

        long startMs = System.currentTimeMillis();
        // API 호출 및 응답 처리
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long elapsedMs = System.currentTimeMillis() - startMs;

        log.info(String.format("[시간측정] HTTP get | pageNo=%s | %d ms", pageNo, elapsedMs));

        // 응답 상태 코드 확인 (200 OK 아니면 예외 발생)
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
