package com.softbase.nara.service;

import com.softbase.nara.client.NaraApiClient;
import com.softbase.nara.dto.BidItemColumn;
import com.softbase.nara.dto.GridResult;
import com.softbase.nara.parser.NaraResponseParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 나라장터 API 비즈니스 로직: 클라이언트 호출 + 응답 파싱 + 필터링 + 페이지 수집.
 * UI·HTTP·설정은 의존하지 않고, DTO·Client·Parser에만 의존.
 */
public final class NaraApiService {

    private static final Logger log = Logger.getLogger(NaraApiService.class.getName());
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

    /*
    JSON 문자열을 그리드용 결과로 변환 (메타 + 결과 목록).
     */
    public GridResult toGridRows(String jsonData) throws Exception {
        long startMs = System.currentTimeMillis();
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
        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info(String.format("[시간측정] toGridRows(파싱) | rows=%d | %d ms", gr.rows.size(), elapsedMs));
        return gr;
    }

    /*
    그리드 결과로 변환한 뒤, 최소 배정예산금액(설계금액) 조건으로 필터링.
    minAsignBdgtAmt가 null/빈 문자열이면 필터 없이 전체 반환.
     */
    public GridResult toGridRows(String jsonData, String minAsignBdgtAmt) throws Exception {
        return toGridRows(jsonData, minAsignBdgtAmt, null, null);
    }

    /*
    그리드 결과로 변환한 뒤, 최소 배정예산금액·공고명 검색어·업무구분명 조건으로 필터링.
    - searchKeywords: 쉼표 구분 키워드. 공고명(bidNtceNm)에 하나라도 포함되면 통과(OR). null/빈 문자열이면 검색어 필터 없음.
    - bsnsDivNmFilter: "물품" 또는 "용역". 업무구분명(bsnsDivNm)이 일치하는 행만 통과. null/빈 문자열이면 업무구분 필터 없음.
     */
    public GridResult toGridRows(String jsonData, String minAsignBdgtAmt, String searchKeywords, String bsnsDivNmFilter) throws Exception {
        GridResult gr = toGridRows(jsonData);
        if (minAsignBdgtAmt != null && !minAsignBdgtAmt.trim().isEmpty()) {
            gr.rows.removeIf(row -> !isMinAsignBdgtAmt(row.get("asignBdgtAmt"), minAsignBdgtAmt));
        }
        if (searchKeywords != null && !searchKeywords.trim().isEmpty()) {
            gr.rows.removeIf(row -> !matchesSearchKeywords(row.get("bidNtceNm"), searchKeywords));
        }
        if (bsnsDivNmFilter != null && !bsnsDivNmFilter.trim().isEmpty()) {
            gr.rows.removeIf(row -> !matchesBsnsDivNm(row.get("bsnsDivNm"), bsnsDivNmFilter));
        }
        for (int i = 0; i < gr.rows.size(); i++) {
            gr.rows.get(i).put("순번", String.valueOf(i + 1));
        }
        return gr;
    }

    /** 문서 가이드: 동시 요청 수 3~5 권장, 초당 30 tps 이하 */
    private static final int PARALLEL_CONCURRENCY = 5;
    /** 배치 간 최소 간격(ms) — 30 tps 이하 유지용 */
    private static final int BATCH_DELAY_MS = 200;

    /*
    최소 배정예산금액·공고명 검색·업무구분 조건이 있을 때, API를 여러 페이지 호출해
    조건을 만족하는 공고만 모두 모아 반환 (한 페이지당 50건씩 표시하기 위함).
    2페이지부터는 병렬 요청(동시성 5, 배치 간 200ms).
     */
    public List<LinkedHashMap<String, String>> fetchAllFilteredRows(
            String baseUrl,
            String serviceKey,
            String bidNtceBgnDt,
            String bidNtceEndDt,
            String minAsignBdgtAmt,
            String searchKeywords,
            String bsnsDivNmFilter,
            int maxApiPages,
            int connTimeoutMs,
            int requestTimeoutMs
    ) throws Exception {
        long fetchAllStartMs = System.currentTimeMillis();
        List<LinkedHashMap<String, String>> allFiltered = new ArrayList<>();
        final int rowsPerApiCall = 999;

        // 1페이지 호출: totalCount 확보 및 첫 페이지 데이터 수집
        long page1StartMs = System.currentTimeMillis();
        String json1 = callApiWithRetry(baseUrl, serviceKey, String.valueOf(rowsPerApiCall), "1",
                bidNtceBgnDt, bidNtceEndDt, connTimeoutMs, requestTimeoutMs);
        GridResult gr1 = toGridRows(json1);
        int rawCount1 = gr1.rows.size(); // API 원본 건수 (필터 전)
        applyFiltersAndRenumber(gr1.rows, minAsignBdgtAmt, searchKeywords, bsnsDivNmFilter);
        allFiltered.addAll(gr1.rows);
        log.info(String.format("[시간측정] fetchAllFilteredRows 1페이지 | %d ms", System.currentTimeMillis() - page1StartMs));

        if (rawCount1 < rowsPerApiCall) {
            renumberAll(allFiltered);
            log.info(String.format("[시간측정] fetchAllFilteredRows 전체 | 총 %d 건 | %d ms", allFiltered.size(), System.currentTimeMillis() - fetchAllStartMs));
            return allFiltered;
        }

        long totalCount = gr1.totalCount;
        int totalPagesNeeded = (int) Math.ceil((double) totalCount / rowsPerApiCall);
        totalPagesNeeded = Math.min(totalPagesNeeded, maxApiPages);
        if (totalPagesNeeded <= 1) {
            renumberAll(allFiltered);
            log.info(String.format("[시간측정] fetchAllFilteredRows 전체 | 총 %d 건 | %d ms", allFiltered.size(), System.currentTimeMillis() - fetchAllStartMs));
            return allFiltered;
        }

        // 2페이지부터 병렬 호출 (동시성 PARALLEL_CONCURRENCY, 배치 간 BATCH_DELAY_MS)
        log.info(String.format("[시간측정] fetchAllFilteredRows 병렬 시작 | 2~%d페이지 (%d페이지)", totalPagesNeeded, totalPagesNeeded - 1));
        List<PageTaskResult> results = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_CONCURRENCY);

        try {
            for (int batchStart = 2; batchStart <= totalPagesNeeded; batchStart += PARALLEL_CONCURRENCY) {
                int batchEnd = Math.min(batchStart + PARALLEL_CONCURRENCY - 1, totalPagesNeeded);
                long batchStartMs = System.currentTimeMillis();
                List<Callable<PageTaskResult>> tasks = new ArrayList<>();
                for (int pageNo = batchStart; pageNo <= batchEnd; pageNo++) {
                    final int p = pageNo;
                    tasks.add(() -> fetchOnePage(baseUrl, serviceKey, rowsPerApiCall, p,
                            bidNtceBgnDt, bidNtceEndDt, minAsignBdgtAmt, searchKeywords, bsnsDivNmFilter,
                            connTimeoutMs, requestTimeoutMs));
                }
                List<Future<PageTaskResult>> futures = executor.invokeAll(tasks);
                for (Future<PageTaskResult> f : futures) {
                    PageTaskResult r = f.get();
                    if (r != null) results.add(r);
                }
                log.info(String.format("[시간측정] fetchAllFilteredRows 병렬 배치 | %d~%d페이지 | %d ms", batchStart, batchEnd, System.currentTimeMillis() - batchStartMs));
                if (batchEnd < totalPagesNeeded && BATCH_DELAY_MS > 0) {
                    Thread.sleep(BATCH_DELAY_MS);
                }
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        }

        results.sort((a, b) -> Integer.compare(a.pageNo, b.pageNo));
        for (PageTaskResult r : results) {
            allFiltered.addAll(r.rows);
        }
        renumberAll(allFiltered);
        log.info(String.format("[시간측정] fetchAllFilteredRows 전체 | 총 %d 건 | %d ms", allFiltered.size(), System.currentTimeMillis() - fetchAllStartMs));
        return allFiltered;
    }

    private static final class PageTaskResult {
        final int pageNo;
        final List<LinkedHashMap<String, String>> rows;

        PageTaskResult(int pageNo, List<LinkedHashMap<String, String>> rows) {
            this.pageNo = pageNo;
            this.rows = rows;
        }
    }

    /** 1페이지 처리 (API 호출 + 파싱 + 필터). 병렬 태스크용. */
    private PageTaskResult fetchOnePage(String baseUrl, String serviceKey, int rowsPerApiCall, int pageNo,
                                       String bidNtceBgnDt, String bidNtceEndDt,
                                       String minAsignBdgtAmt, String searchKeywords, String bsnsDivNmFilter,
                                       int connTimeoutMs, int requestTimeoutMs) throws Exception {
        long pageStartMs = System.currentTimeMillis();
        String json = callApiWithRetry(baseUrl, serviceKey, String.valueOf(rowsPerApiCall), String.valueOf(pageNo),
                bidNtceBgnDt, bidNtceEndDt, connTimeoutMs, requestTimeoutMs);
        GridResult gr = toGridRows(json);
        applyFiltersAndRenumber(gr.rows, minAsignBdgtAmt, searchKeywords, bsnsDivNmFilter);
        log.info(String.format("[시간측정] fetchAllFilteredRows %d페이지 | %d ms", pageNo, System.currentTimeMillis() - pageStartMs));
        return new PageTaskResult(pageNo, gr.rows);
    }

    private void applyFiltersAndRenumber(List<LinkedHashMap<String, String>> rows,
                                        String minAsignBdgtAmt, String searchKeywords, String bsnsDivNmFilter) {
        if (minAsignBdgtAmt != null && !minAsignBdgtAmt.trim().isEmpty()) {
            rows.removeIf(row -> !isMinAsignBdgtAmt(row.get("asignBdgtAmt"), minAsignBdgtAmt));
        }
        if (searchKeywords != null && !searchKeywords.trim().isEmpty()) {
            rows.removeIf(row -> !matchesSearchKeywords(row.get("bidNtceNm"), searchKeywords));
        }
        if (bsnsDivNmFilter != null && !bsnsDivNmFilter.trim().isEmpty()) {
            rows.removeIf(row -> !matchesBsnsDivNm(row.get("bsnsDivNm"), bsnsDivNmFilter));
        }
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("순번", String.valueOf(i + 1));
        }
    }

    private void renumberAll(List<LinkedHashMap<String, String>> allFiltered) {
        for (int i = 0; i < allFiltered.size(); i++) {
            allFiltered.get(i).put("순번", String.valueOf(i + 1));
        }
    }

    /** 실패 시 지수 백오프 재시도 (429/timeout 등 대응) */
    private String callApiWithRetry(String baseUrl, String serviceKey, String numOfRows, String pageNo,
                                   String bidNtceBgnDt, String bidNtceEndDt,
                                   int connTimeoutMs, int requestTimeoutMs) throws Exception {
        int maxAttempts = 3;
        long backoffMs = 500;
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callApi(baseUrl, serviceKey, numOfRows, pageNo,
                        bidNtceBgnDt, bidNtceEndDt, "json", connTimeoutMs, requestTimeoutMs);
            } catch (Exception e) {
                last = e;
                if (attempt < maxAttempts) {
                    Thread.sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 4000);
                }
            }
        }
        throw last != null ? last : new RuntimeException("callApi failed");
    }

    /*
    최소 배정예산금액 조건 확인
    */
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

    /*
    객체를 long 타입으로 변환
    */
    private static long toLong(Object o) {
        if (o == null) return 0;
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    /*
    금액 형식 변환
    */
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

    /*
    공고명(bidNtceNm)이 검색 키워드 중 하나라도 포함하는지 확인.
    searchKeywords는 쉼표 구분. null/빈 문자열이면 true(필터 없음).
     */
    private static boolean matchesSearchKeywords(String bidNtceNm, String searchKeywords) {
        if (searchKeywords == null || searchKeywords.trim().isEmpty()) return true;
        if (bidNtceNm == null) return false;
        String name = bidNtceNm.trim();
        String[] keywords = searchKeywords.split(",");
        for (String kw : keywords) {
            if (kw != null && !kw.trim().isEmpty() && name.contains(kw.trim())) {
                return true;
            }
        }
        return false;
    }

    /*
    업무구분명(bsnsDivNm)이 선택한 업무구분("물품"/"용역")과 일치하는지 확인.
    API는 "용역", "물품"을 그대로 반환함. bsnsDivNmFilter가 null/빈 문자열이면 true(필터 없음).
     */
    private static boolean matchesBsnsDivNm(String rowBsnsDivNm, String bsnsDivNmFilter) {
        if (bsnsDivNmFilter == null || bsnsDivNmFilter.trim().isEmpty()) return true;
        if (rowBsnsDivNm == null) return false;
        return rowBsnsDivNm.trim().equals(bsnsDivNmFilter.trim());
    }

    private NaraApiService() {}

    /*
    싱글톤 인스턴스 생성
    */
    private static final NaraApiService INSTANCE = new NaraApiService();

    /*
    싱글톤 인스턴스 반환
    */
    public static NaraApiService getInstance() {
        return INSTANCE;
    }
}
