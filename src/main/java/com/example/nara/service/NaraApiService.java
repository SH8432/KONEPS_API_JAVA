package com.example.nara.service;

import com.example.nara.client.NaraApiClient;
import com.example.nara.config.NaraApiConfig;
import com.example.nara.dto.BidItemColumn;
import com.example.nara.dto.GridResult;
import com.example.nara.parser.NaraResponseParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 나라장터 API 비즈니스 로직: 클라이언트 호출 + 응답 파싱 + 필터링 + 페이지 수집.
 * UI·HTTP·설정은 의존하지 않고, DTO·Client·Parser에만 의존.
 */
public final class NaraApiService {

    private final NaraApiClient client = NaraApiClient.getInstance();

    /**
     * API 1회 호출 후 JSON 문자열 반환 (HTTP만 담당하도록 Client에 위임).
     */
    public String callApi(
            String baseUrl,
            String serviceKey,
            String numOfRows,
            String pageNo,
            String bidNtceBgnDt,
            String bidNtceEndDt,
            String type,
            int connTimeoutMs,
            int requestTimeoutMs
    ) throws Exception {
        return client.get(
                baseUrl, serviceKey, numOfRows, pageNo,
                bidNtceBgnDt, bidNtceEndDt, type,
                connTimeoutMs, requestTimeoutMs
        );
    }

    /**
     * JSON 문자열을 그리드용 결과로 변환 (메타 + 행 목록).
     */
    public GridResult toGridRows(String jsonData) throws Exception {
        JSONObject response = NaraResponseParser.getResponse(jsonData);
        JSONObject header = NaraResponseParser.getResponseHeader(response);
        JSONObject body = NaraResponseParser.getResponseBody(response);

        GridResult gr = new GridResult();
        gr.resultCode = NaraResponseParser.getAsString(header, "resultCode");
        gr.resultMsg = NaraResponseParser.getAsString(header, "resultMsg");
        gr.numOfRows = toLong(body.get("numOfRows"));
        gr.pageNo = toLong(body.get("pageNo"));
        gr.totalCount = toLong(body.get("totalCount"));

        JSONArray items = NaraResponseParser.getItemArray(body);
        for (int i = 0; i < items.size(); i++) {
            Object obj = items.get(i);
            if (!(obj instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) obj;

            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            row.put("순번", String.valueOf(i + 1));

            for (String key : BidItemColumn.KEY_LIST) {
                String val = NaraResponseParser.getAsString(item, key);
                if ("asignBdgtAmt".equals(key) || "presmptPrce".equals(key)) {
                    val = formatAmount(val);
                }
                row.put(key, val);
            }
            gr.rows.add(row);
        }
        return gr;
    }

    /**
     * 그리드 결과로 변환한 뒤, 최소 배정예산금액(설계금액) 조건으로 필터링.
     * minAsignBdgtAmt가 null/빈 문자열이면 필터 없이 전체 반환.
     */
    public GridResult toGridRows(String jsonData, String minAsignBdgtAmt) throws Exception {
        GridResult gr = toGridRows(jsonData);
        if (minAsignBdgtAmt != null && !minAsignBdgtAmt.trim().isEmpty()) {
            gr.rows.removeIf(row -> !isMinAsignBdgtAmt(row.get("asignBdgtAmt"), minAsignBdgtAmt));
            for (int i = 0; i < gr.rows.size(); i++) {
                gr.rows.get(i).put("순번", String.valueOf(i + 1));
            }
        }
        return gr;
    }

    /**
     * 최소 배정예산금액 조건이 있을 때, API를 여러 페이지 호출해
     * 조건을 만족하는 공고만 모두 모아 반환 (한 페이지당 50건씩 표시하기 위함).
     *
     * @param maxApiPages API 호출 최대 횟수 (무한 루프 방지)
     */
    public List<LinkedHashMap<String, String>> fetchAllFilteredRows(
            String baseUrl,
            String serviceKey,
            String bidNtceBgnDt,
            String bidNtceEndDt,
            String minAsignBdgtAmt,
            int maxApiPages,
            int connTimeoutMs,
            int requestTimeoutMs
    ) throws Exception {
        List<LinkedHashMap<String, String>> allFiltered = new ArrayList<>();
        final int rowsPerApiCall = 999;

        for (int apiPageNo = 1; apiPageNo <= maxApiPages; apiPageNo++) {
            if (apiPageNo > 1) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            String json = callApi(
                    baseUrl, serviceKey,
                    String.valueOf(rowsPerApiCall),
                    String.valueOf(apiPageNo),
                    bidNtceBgnDt, bidNtceEndDt,
                    "json",
                    connTimeoutMs, requestTimeoutMs
            );
            GridResult gr = toGridRows(json, minAsignBdgtAmt);
            if (gr.rows.isEmpty()) break;
            allFiltered.addAll(gr.rows);
        }

        for (int i = 0; i < allFiltered.size(); i++) {
            allFiltered.get(i).put("순번", String.valueOf(i + 1));
        }
        return allFiltered;
    }

    private static boolean isMinAsignBdgtAmt(String asignBdgtAmt, String minAsignBdgtAmt) {
        if (asignBdgtAmt == null || asignBdgtAmt.trim().isEmpty()) return false;
        if (minAsignBdgtAmt == null || minAsignBdgtAmt.trim().isEmpty()) return true;
        try {
            long a = Long.parseLong(asignBdgtAmt.replace(",", "").trim());
            long m = Long.parseLong(minAsignBdgtAmt.replace(",", "").trim());
            return a >= m;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static long toLong(Object o) {
        if (o == null) return 0;
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formatAmount(String value) {
        if (value == null || value.trim().isEmpty()) return value;
        try {
            String num = value.replace(",", "").trim();
            long n = Long.parseLong(num);
            return String.format("%,d", n);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private NaraApiService() {}

    /** 싱글톤 또는 DI 시 사용할 수 있도록 인스턴스 제공 */
    private static final NaraApiService INSTANCE = new NaraApiService();

    public static NaraApiService getInstance() {
        return INSTANCE;
    }
}
