import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * 나라장터 공공데이터 API 호출 및 파싱 로직 (UI 독립)
 */
public class NaraApiService {

    public static final String BASE_URL =
            "https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo";
    public static final String PERSONAL_AUTH_KEY =
            "0dc4858adf4ae98e1e29f7e89ca8e88d97b5a98b51621f3bb915d3eb73546458";

    public static final List<String> KEY_LIST = List.of(
            "resultCode",
            "resultMsg",
            "numOfRows",
            "pageNo",
            "totalCount",
            "bidNtceNo",
            "refNtceNo",
            "ppsNtceYn",
            "bidNtceNm",
            "bidNtceSttusNm",
            "bidNtceDate",
            "bidNtceBgn",
            "bsnsDivNm",
            "cntrctCnclsSttusNm",
            "cntrctCnclsMthdNm",
            "ntceInsttNm",
            "dmndInsttNm",
            "asignBdgtAmt",
            "presmptPrce",
            "bidNtceUrl"
    );

    public static final Map<String, String> KEY_TO_FIELD_NAME = Map.ofEntries(
            Map.entry("resultCode", "결과코드"),
            Map.entry("resultMsg", "결과메세지"),
            Map.entry("numOfRows", "한 페이지 결과수"),
            Map.entry("pageNo", "페이지 번호"),
            Map.entry("totalCount", "전체 결과수"),
            Map.entry("bidNtceNo", "입찰공고번호"),
            Map.entry("refNtceNo", "참조공고번호"),
            Map.entry("ppsNtceYn", "나라장터공고여부"),
            Map.entry("bidNtceNm", "입찰공고명"),
            Map.entry("bidNtceSttusNm", "입찰공고상태명"),
            Map.entry("bidNtceDate", "입찰공고일자"),
            Map.entry("bidNtceBgn", "입찰공고시각"),
            Map.entry("bsnsDivNm", "업무구분명"),
            Map.entry("cntrctCnclsSttusNm", "계약체결상태명"),
            Map.entry("cntrctCnclsMthdNm", "계약체결방법명"),
            Map.entry("ntceInsttNm", "공고기관명"),
            Map.entry("dmndInsttNm", "수요기관명"),
            Map.entry("asignBdgtAmt", "배정예산금액(설계금액)"),
            Map.entry("presmptPrce", "추정가격"),
            Map.entry("bidNtceUrl", "입찰공고URL")
    );

    /**
     * API 호출 후 JSON 문자열 반환
     */
    public static String callApi(
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

    /**
     * JSON 문자열을 파싱하여 키-값 행 목록으로 변환
     */
    public static List<Map<String, String>> parseItemsToKeyValueRows(String jsonData, List<String> keyList) throws Exception {
        JSONObject response = getResponse(jsonData);
        JSONObject header = getResponseHeader(response);
        JSONObject body = getResponseBody(response);
        JSONArray items = getItemArray(body);

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("resultCode", getAsString(header, "resultCode"));
        meta.put("resultMsg", getAsString(header, "resultMsg"));
        meta.put("numOfRows", getAsString(body, "numOfRows"));
        meta.put("pageNo", getAsString(body, "pageNo"));
        meta.put("totalCount", getAsString(body, "totalCount"));

        List<Map<String, String>> rows = new ArrayList<>();
        for (Object obj : items) {
            if (!(obj instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) obj;

            Map<String, String> row = new LinkedHashMap<>();
            for (String keyRaw : keyList) {
                String key = keyRaw.trim();
                if (meta.containsKey(key)) row.put(key, meta.get(key));
                else row.put(key, getAsString(item, key));
            }
            rows.add(row);
        }
        return rows;
    }

    /*
    response 데이터 파싱 메서드(JSONObject 타입)
    */
    private static JSONObject getResponse(String jsonData) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(jsonData);
        JSONObject response = (JSONObject) root.get("response");
        if (response == null) throw new IllegalArgumentException("응답에 'response'가 없습니다.");
        return response;
    }

    /*
    response header 데이터 파싱 메서드(JSONObject 타입)
    */
    private static JSONObject getResponseHeader(JSONObject response) {
        JSONObject header = (JSONObject) response.get("header");
        if (header == null) throw new IllegalArgumentException("응답에 'header'가 없습니다.");
        return header;
    }

    /*
    response body 데이터 파싱 메서드(JSONObject 타입)
    */
    private static JSONObject getResponseBody(JSONObject response) {
        JSONObject body = (JSONObject) response.get("body");
        if (body == null) throw new IllegalArgumentException("응답에 'body'가 없습니다.");
        return body;
    }

    /*
    response body items 데이터 파싱 메서드(JSONArray 타입) - 데이터 배열 파싱
    */
    @SuppressWarnings("unchecked")
    private static JSONArray getItemArray(JSONObject body) {
        Object itemsObj = body.get("items");
        if (itemsObj == null) return new JSONArray();

        if (itemsObj instanceof JSONObject) {
            Object itemObj = ((JSONObject) itemsObj).get("item");
            if (itemObj instanceof JSONArray) return (JSONArray) itemObj;
            if (itemObj instanceof JSONObject) {
                JSONArray arr = new JSONArray();
                arr.add(itemObj);
                return arr;
            }
            return new JSONArray();
        }

        if (itemsObj instanceof JSONArray) return (JSONArray) itemsObj;
        return new JSONArray();
    }

    /*
    JSON 객체에서 문자열 값 추출 메서드
    */
    private static String getAsString(JSONObject obj, String key) {
        Object v = obj.get(key);
        return (v == null) ? "" : String.valueOf(v);
    }
}
