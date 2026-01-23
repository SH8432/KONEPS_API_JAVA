import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// json-simple 라이브러리 import
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class NaraGetTest {

    // JSON에서 특정 키의 값을 추출
    private static String extractJsonValue(Object obj, String key) {
        if (obj == null) return null;
        
        if (obj instanceof JSONObject) {
            JSONObject jsonObj = (JSONObject) obj;
            Object value = jsonObj.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    // JSON 파싱 및 값 추출
    private static void parseJson(String json) {
        try {
            JSONParser parser = new JSONParser();
            Object parsed = parser.parse(json);
            
            if (!(parsed instanceof JSONObject)) {
                System.out.println("JSON 구조가 예상과 다릅니다.");
                return;
            }
            
            JSONObject root = (JSONObject) parsed;
            
            // Header 정보 추출
            String resultCode = null;
            String resultMsg = null;
            String totalCount = null;
            
            // response 객체가 있는 경우
            if (root.containsKey("response")) {
                Object responseObj = root.get("response");
                if (responseObj instanceof JSONObject) {
                    JSONObject response = (JSONObject) responseObj;
                    
                    // header 추출
                    if (response.containsKey("header")) {
                        Object headerObj = response.get("header");
                        if (headerObj instanceof JSONObject) {
                            JSONObject header = (JSONObject) headerObj;
                            resultCode = extractJsonValue(header, "resultCode");
                            resultMsg = extractJsonValue(header, "resultMsg");
                        }
                    }
                    
                    // body 추출
                    if (response.containsKey("body")) {
                        Object bodyObj = response.get("body");
                        if (bodyObj instanceof JSONObject) {
                            JSONObject body = (JSONObject) bodyObj;
                            totalCount = extractJsonValue(body, "totalCount");
                            
                            // items 배열 추출
                            if (body.containsKey("items")) {
                                Object itemsObj = body.get("items");
                                if (itemsObj instanceof JSONArray) {
                                    JSONArray items = (JSONArray) itemsObj;
                                    
                                    System.out.println("\n=== ITEMS (" + items.size() + "개) ===");
                                    for (int i = 0; i < items.size(); i++) {
                                        Object itemObj = items.get(i);
                                        if (itemObj instanceof JSONObject) {
                                            JSONObject item = (JSONObject) itemObj;
                                            System.out.println("\n--- Item " + (i + 1) + " ---");
                                            
                                            String bidNtceNo = extractJsonValue(item, "bidNtceNo");
                                            String bidNtceOrd = extractJsonValue(item, "bidNtceOrd");
                                            String ppsNtceYn = extractJsonValue(item, "ppsNtceYn");
                                            String bidNtceNm = extractJsonValue(item, "bidNtceNm");
                                            
                                            System.out.println("bidNtceNo: " + (bidNtceNo != null ? bidNtceNo : "N/A"));
                                            System.out.println("bidNtceOrd: " + (bidNtceOrd != null ? bidNtceOrd : "N/A"));
                                            System.out.println("ppsNtceYn: " + (ppsNtceYn != null ? ppsNtceYn : "N/A"));
                                            System.out.println("bidNtceNm: " + (bidNtceNm != null ? bidNtceNm : "N/A"));
                                        }
                                    }
                                } else if (itemsObj instanceof JSONObject) {
                                    // items가 객체인 경우 (단일 항목)
                                    JSONObject item = (JSONObject) itemsObj;
                                    System.out.println("\n=== ITEMS (1개) ===");
                                    System.out.println("\n--- Item 1 ---");
                                    
                                    String bidNtceNo = extractJsonValue(item, "bidNtceNo");
                                    String bidNtceOrd = extractJsonValue(item, "bidNtceOrd");
                                    String ppsNtceYn = extractJsonValue(item, "ppsNtceYn");
                                    String bidNtceNm = extractJsonValue(item, "bidNtceNm");
                                    
                                    System.out.println("bidNtceNo: " + (bidNtceNo != null ? bidNtceNo : "N/A"));
                                    System.out.println("bidNtceOrd: " + (bidNtceOrd != null ? bidNtceOrd : "N/A"));
                                    System.out.println("ppsNtceYn: " + (ppsNtceYn != null ? ppsNtceYn : "N/A"));
                                    System.out.println("bidNtceNm: " + (bidNtceNm != null ? bidNtceNm : "N/A"));
                                }
                            }
                        }
                    }
                }
            } else {
                // response 객체가 없는 경우 직접 검색
                resultCode = extractJsonValue(root, "resultCode");
                resultMsg = extractJsonValue(root, "resultMsg");
                totalCount = extractJsonValue(root, "totalCount");
                
                // items 배열이 루트에 있는 경우
                if (root.containsKey("items")) {
                    Object itemsObj = root.get("items");
                    if (itemsObj instanceof JSONArray) {
                        JSONArray items = (JSONArray) itemsObj;
                        System.out.println("\n=== ITEMS (" + items.size() + "개) ===");
                        for (int i = 0; i < items.size(); i++) {
                            Object itemObj = items.get(i);
                            if (itemObj instanceof JSONObject) {
                                JSONObject item = (JSONObject) itemObj;
                                System.out.println("\n--- Item " + (i + 1) + " ---");
                                
                                String bidNtceNo = extractJsonValue(item, "bidNtceNo");
                                String bidNtceOrd = extractJsonValue(item, "bidNtceOrd");
                                String ppsNtceYn = extractJsonValue(item, "ppsNtceYn");
                                String bidNtceNm = extractJsonValue(item, "bidNtceNm");
                                
                                System.out.println("bidNtceNo: " + (bidNtceNo != null ? bidNtceNo : "N/A"));
                                System.out.println("bidNtceOrd: " + (bidNtceOrd != null ? bidNtceOrd : "N/A"));
                                System.out.println("ppsNtceYn: " + (ppsNtceYn != null ? ppsNtceYn : "N/A"));
                                System.out.println("bidNtceNm: " + (bidNtceNm != null ? bidNtceNm : "N/A"));
                            }
                        }
                    }
                } else {
                    // items 배열이 없으면 직접 검색
                    System.out.println("\n=== DIRECT SEARCH (items 배열 없음) ===");
                    
                    String bidNtceNo = extractJsonValue(root, "bidNtceNo");
                    String bidNtceOrd = extractJsonValue(root, "bidNtceOrd");
                    String ppsNtceYn = extractJsonValue(root, "ppsNtceYn");
                    String bidNtceNm = extractJsonValue(root, "bidNtceNm");
                    
                    System.out.println("bidNtceNo: " + (bidNtceNo != null ? bidNtceNo : "N/A"));
                    System.out.println("bidNtceOrd: " + (bidNtceOrd != null ? bidNtceOrd : "N/A"));
                    System.out.println("ppsNtceYn: " + (ppsNtceYn != null ? ppsNtceYn : "N/A"));
                    System.out.println("bidNtceNm: " + (bidNtceNm != null ? bidNtceNm : "N/A"));
                }
            }
            
            // 공통 필드 출력
            System.out.println("\n=== EXTRACTED VALUES ===");
            System.out.println("resultCode: " + (resultCode != null ? resultCode : "N/A"));
            System.out.println("resultMsg: " + (resultMsg != null ? resultMsg : "N/A"));
            System.out.println("totalCount: " + (totalCount != null ? totalCount : "N/A"));
            
        } catch (ParseException e) {
            System.out.println("\n=== JSON 파싱 오류 ===");
            System.out.println("오류 메시지: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("\n=== 오류 발생 ===");
            System.out.println("오류 메시지: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {

        // 1) 엔드포인트
        String baseUrl =
                "https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo";

        // 2) 발급받은 서비스키
        //    공공데이터 서비스키는 "이미 URL 인코딩된 키"를 주는 경우가 많아서
        //    여기서는 별도 인코딩하지 않고 그대로 붙임
        String serviceKey = "0dc4858adf4ae98e1e29f7e89ca8e88d97b5a98b51621f3bb915d3eb73546458";

        // 3) 테스트 파라미터
        String numOfRows = "10";
        String pageNo = "1";
        String type = "json"; // 또는 xml

        // 날짜 형식: YYYYMMDDHHMM
        // 예: 202507010000 ~ 202507012359
        String bidNtceBgnDt = "202601200000";
        String bidNtceEndDt = "202601231100";

        // 4) URL 조립 (ServiceKey 포함)
        String url = baseUrl
                + "?numOfRows=" + numOfRows
                + "&pageNo=" + pageNo
                + "&bidNtceBgnDt=" + bidNtceBgnDt
                + "&bidNtceEndDt=" + bidNtceEndDt
                + "&ServiceKey=" + serviceKey
                + "&type=" + type;

        // 5) 요청/응답
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        System.out.println("=== REQUEST URL ===");
        System.out.println(url);

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n=== STATUS ===");
        System.out.println("HTTP " + res.statusCode());

        System.out.println("\n=== BODY (first 3000 chars) ===");
        String body = res.body();
        if (body == null) body = "";
        System.out.println(body.length() > 3000 ? body.substring(0, 3000) + "\n... (truncated)" : body);

        // 6) JSON 파싱
        if (type.equals("json") && body != null && !body.isEmpty()) {
            parseJson(body);
        }

    }
}
