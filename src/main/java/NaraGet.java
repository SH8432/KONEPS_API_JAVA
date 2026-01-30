package src.main.java;
import java.io.IOException; // 입출력 작업 중 발생할 수 있는 예외를 처리하기 위한 예외 클래스 (파일 읽기/쓰기, 네트워크 통신 등의 I/O 오류 처리)
import java.net.URI; // 통합 자원 식별자(Uniform Resource Identifier)를 표현하는 클래스로, HTTP 요청의 URL을 구성하고 파싱하는 데 사용
import java.net.http.HttpClient; // HTTP 프로토콜을 통해 서버와 통신하기 위한 클라이언트 객체로, HTTP 요청을 보내고 응답을 받는 역할을 담당
import java.net.http.HttpRequest; // HTTP 요청 메시지를 구성하는 빌더 클래스로, 요청 URL, 메서드(GET/POST 등), 헤더, 본문 등을 설정
import java.net.http.HttpResponse; // HTTP 서버로부터 받은 응답을 나타내는 인터페이스로, 상태 코드, 헤더, 응답 본문 등의 정보를 포함
import java.nio.charset.StandardCharsets; // 표준 문자 인코딩 방식을 정의한 상수 클래스 (UTF-8, ISO-8859-1 등)로, 문자열과 바이트 간 변환 시 사용
import java.time.Duration; // 시간의 지속 기간을 나타내는 불변 클래스로, HTTP 요청의 타임아웃 설정 등 시간 간격을 표현할 때 사용
import java.util.Map; // 키-값 쌍으로 데이터를 저장하는 인터페이스로, JSON 데이터를 파싱하거나 키를 통해 값을 조회할 때 사용

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

// json-simple 라이브러리 import
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class NaraGet {
    
    private static final String BaseUrl = "https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo";
    private static final String PersonalAuthKey = "0dc4858adf4ae98e1e29f7e89ca8e88d97b5a98b51621f3bb915d3eb73546458";

    public static void main(String[] args) {
        
        /*
        1. 요청 파라미터 설정
            - response type 파라미터 설정
            - request 파라미터 설정
            - request 조회 날짜 파라미터 설정
            - 커넥션 타임아웃, 읽기 타임아웃 설정
        */
        // response type 설정
        String type = "json";
        
        // request 파라미터 설정
        String numOfRows = "3";
        String pageNo = "1";

        // request 조회 날짜 파라미터 설정
        // 조회 날짜 형식: YYYYMMDDHHMM
        // 예: 202507010000 ~ 202507012359
        String bidNtceBgnDt = "202601200000";
        String bidNtceEndDt = "202601231100";

        // 커넥션 타임아웃, 읽기 타임아웃 설정
        int connTimeoutMs       = 5000;
        int requestTimeoutMs    = 3000;

        /*
         * 2. 출력 대상 키 목록
         * - resultCode/resultMsg 는 header
         * - numOfRows/pageNo/totalCount 는 body
         * - 나머지는 items의 각 item(공고 1건)
         */

        List<String> KeyList = List.of(
            "resultCode",           // 결과코드
            "resultMsg",            // 결과메세지
            "numOfRows",            // 한 페이지 결과수
            "pageNo",               // 페이지 번호
            "totalCount",           // 전체 결과수
            "bidNtceNo",            // 입찰공고번호
            "refNtceNo",            // 참조공고번호
            "ppsNtceYn",            // 나라장터공고여부부
            "bidNtceNm",            // 입찰공고명
            "bidNtceSttusNm",       // 입찰공고상태명
            "bidNtceDate",          // 입찰공고일자
            "bidNtceBgn",           // 입찰공고시각
            "bsnsDivNm",            // 업무구분명
            "cntrctCnclsSttusNm",   // 계약체결상태명
            "cntrctCnclsMthdNm",    // 계약체결방법명
            "ntceInsttNm",          // 공고기관명
            "dmndInsttNm",          // 수요기관명
            "asignBdgtAmt",         // 배정예산금액(설계금액)
            "presmptPrce",          // 추정가격
            "bidNtceUrl"            // 입찰공고URL
        );

        try {
            // 통신로직 (api 호출시 필요한 파라미터를 전달하고 json 문자열 받기)(callApi 메서드 호출)
            String jsonData = callApi(
                BaseUrl,
                PersonalAuthKey,
                numOfRows,
                pageNo,
                bidNtceBgnDt,
                bidNtceEndDt,
                type,
                connTimeoutMs,
                requestTimeoutMs
            );

            // json 문자열 파싱/정리
            List<Map<String, String>> rows = parseItemsToKeyValueRows(jsonData, KeyList);

            // 디버그 출력 (파싱된 데이터 확인용)
            // System.out.println("DEBUG row0 = " + rows.get(0));

            // 파싱된 데이터 출력 (키-값 쌍으로 출력)
            for (Map<String, String> row : rows) {
                printRowWithKeyToFieldName(row, KeyList);
                System.out.println(); // 줄 바꿈 용도
            }

        } catch (IOException e) {
            System.out.println("네트워크/입출력 오류 : " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("요청이 중단되었습니다 : " + e.getMessage());
        } catch (Exception e) {
            System.out.println("예상치 못한 오류 : " + e.getMessage());
        }
    }

    /*
    통신 로직 (API 호출해서 응답(json 문자열)만 반환)
    */
    private static String callApi(
        String BaseUrl,
        String PersonalAuthKey,
        String numOfRows,
        String pageNo,
        String bidNtceBgnDt,
        String bidNtceEndDt,
        String type,
        int connTimeoutMs,
        int requestTimeoutMs

    ) throws IOException, InterruptedException {

            // HttpClient 생성 (커넥션 타임아웃, 읽기 타임아웃 설정)
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connTimeoutMs))
                .build();

            // 문서에 맞는 요청 URL 조립 (ServiceKey 포함)
            String apiUrl = BaseUrl
                + "?numOfRows=" + numOfRows
                + "&pageNo=" + pageNo
                + "&bidNtceBgnDt=" + bidNtceBgnDt
                + "&bidNtceEndDt=" + bidNtceEndDt
                + "&ServiceKey=" + PersonalAuthKey
                + "&type=" + type;

            // HttpRequest 생성 (요청 URL, 요청 전체 타임아웃 설정)
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Accept", "application/json")
                .GET()
                .build();

            // 전송 + 응답 받기 (body를 String 형태로 바로 받음)
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            // 응답 상태 코드 확인
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " / body = " + response.body());
            }

            // 응답 body 반환
            // System.out.println(response.body());

            return response.body();

    }


    /*
    파싱/정리 로직
    - JSON 문자열에서 response/body/items(또는 items.item) 배열을 추출하고, 각 항목을 키-값 쌍으로 변환
    - item 들에서 KeyList에 있는 키에 해당하는 값을 추출하고, List<Map<key, value>> 형태로 반환
    */
    private static List<Map<String, String>> parseItemsToKeyValueRows(String jsonData, List<String> KeyList) throws Exception {
        // JSON 문자열을 파싱해서 response 추출
        JSONObject response = getResponse(jsonData);
        
        // header / body 추출
        JSONObject header = getResponseHeader(response);
        JSONObject body = getResponseBody(response);
        
        // body에서 items 배열 추출 (body.items 또는 body.items.item)
        JSONArray items = getItemArray(body);

        // header / body 메타(공통값) 먼저 Map으로 만들기
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("resultCode", getAsString(header, "resultCode"));
        meta.put("resultMsg", getAsString(header, "resultMsg"));
        meta.put("numOfRows", getAsString(body, "numOfRows"));
        meta.put("pageNo", getAsString(body, "pageNo"));
        meta.put("totalCount", getAsString(body, "totalCount"));

        // items를 돌면서 row 생성 (meta + item 필드)
        List<Map<String, String>> rows = new ArrayList<>();

        // item 들에서 KeyList에 있는 키에 해당하는 값을 추출하고, List<Map<key, value>> 형태로 반환
        for (Object obj : items) {
            // item 이 JSONObject 타입이 아닌 경우 건너뜀
            if (!(obj instanceof JSONObject)) continue;
            // item 을 JSONObject 타입으로 형변환
            JSONObject item = (JSONObject) obj;
            

            Map<String, String> row = new LinkedHashMap<>();

            // KeyList 순서대로 채워 넣되,
            // - meta에 있는 키면 meta에서 가져오고
            // - 아니라면 item에서 가져온다
            for (String key : KeyList) {
                if (meta.containsKey(key)) {
                    row.put(key, meta.get(key));

                } else {
                    row.put(key, getAsString(item, key));

                }

            }

            rows.add(row);

        }

        return rows;
    }

    /*
    JSON 문자열에서 response 객체 추출
    */
    private static JSONObject getResponse(String jsonData) throws Exception {
        JSONParser parser = new JSONParser();
        Object responseObj = parser.parse(jsonData);

        // 최상단 JSON이 {} 형태일때 JSONObject로 형변환
        JSONObject responseData = (JSONObject) responseObj;

        // response 객체 추출
        JSONObject response = (JSONObject) responseData.get("response");
        if (response == null) throw new IllegalArgumentException("응답에 'response'가 없습니다.");

        return response;
    }

    private static JSONObject getResponseHeader(JSONObject response) throws Exception {
        JSONObject header = (JSONObject) response.get("header");
        if (header == null) throw new IllegalArgumentException("응답에 'header'가 없습니다.");
        return header;
    }

    private static JSONObject getResponseBody(JSONObject response) throws Exception {
        JSONObject body = (JSONObject) response.get("body");
        if (body == null) throw new IllegalArgumentException("응답에 'body'가 없습니다.");
        return body;
    }

    /*
    // body에서 items 배열(또는 items.item)을 JSONArray로 반환
    - 추출된 body(JSONObject) 를 입력으로 받아서 처리함
    */
    @SuppressWarnings("unchecked")
    private static JSONArray getItemArray(JSONObject body) {
        // items 객체 추출
        Object itemsObj = body.get("items");
        if (itemsObj == null) return new JSONArray();

        // items 객체가 JSONObject 타입인 경우
        if (itemsObj instanceof JSONObject) {
            // item 객체 추출
            Object itemObj = ((JSONObject) itemsObj).get("item");

            // item 객체가 JSONArray 타입인 경우
            if (itemObj instanceof JSONArray) return (JSONArray) itemObj;
            // item 객체가 JSONObject 타입인 경우
            if (itemObj instanceof JSONObject) {
                // 단일 item(JSONObject)를 배열처럼 다루기 위해 JSONArray로 감싼다
                JSONArray arr = new JSONArray();
                arr.add(itemObj);

                // JSONArray 타입으로 변환된 item 객체 반환
                return arr;
            }
        }

        // items가 이미 배열이면 그대로 반환
        if (itemsObj instanceof JSONArray) {
            return (JSONArray) itemsObj;
        }

        // 예상한 구조가 아니면 빈 배열 반환
        return new JSONArray();

    }

    /*
    JSONObject 객체에서 특정 키에 해당하는 값을 문자열로 반환
    */
    private static String getAsString(JSONObject obj, String key) {
        // 특정 키에 해당하는 값 추출
        Object v = obj.get(key);
        // 값이 없으면 "" 반환, 있으면 문자열로 변환해 반환
        return (v == null) ? "" : String.valueOf(v);
    }

    private static final Map<String, String> KeyToFieldName = Map.ofEntries(
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

    private static void printRowWithKeyToFieldName(Map<String, String> row, List<String> KeyList) {
        for (String key : KeyList) {
            String fieldName    = KeyToFieldName.getOrDefault(key, key); // KeyToFieldName에 key가 있으면 해당 값 반환, 없으면 "" 반환
            String value        = row.getOrDefault(key, "");   // row에 key가 없으면 "" 반환
            System.out.printf("%-12s : %s%n", fieldName, value);
        }
    }



}

