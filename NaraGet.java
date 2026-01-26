import java.io.IOException; // 입출력 작업 중 발생할 수 있는 예외를 처리하기 위한 예외 클래스 (파일 읽기/쓰기, 네트워크 통신 등의 I/O 오류 처리)
import java.net.URI; // 통합 자원 식별자(Uniform Resource Identifier)를 표현하는 클래스로, HTTP 요청의 URL을 구성하고 파싱하는 데 사용
import java.net.http.HttpClient; // HTTP 프로토콜을 통해 서버와 통신하기 위한 클라이언트 객체로, HTTP 요청을 보내고 응답을 받는 역할을 담당
import java.net.http.HttpRequest; // HTTP 요청 메시지를 구성하는 빌더 클래스로, 요청 URL, 메서드(GET/POST 등), 헤더, 본문 등을 설정
import java.net.http.HttpResponse; // HTTP 서버로부터 받은 응답을 나타내는 인터페이스로, 상태 코드, 헤더, 응답 본문 등의 정보를 포함
import java.nio.charset.StandardCharsets; // 표준 문자 인코딩 방식을 정의한 상수 클래스 (UTF-8, ISO-8859-1 등)로, 문자열과 바이트 간 변환 시 사용
import java.time.Duration; // 시간의 지속 기간을 나타내는 불변 클래스로, HTTP 요청의 타임아웃 설정 등 시간 간격을 표현할 때 사용
import java.util.LinkedList; // 이중 연결 리스트 자료구조를 구현한 클래스로, 순서가 있는 데이터를 동적으로 추가/삭제할 때 효율적
import java.util.Map; // 키-값 쌍으로 데이터를 저장하는 인터페이스로, JSON 데이터를 파싱하거나 키를 통해 값을 조회할 때 사용

// json-simple 라이브러리 import
import org.json.simple.JSONObject;

public class NaraGet {
    
    private static final String Url = "https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo";
    private static final String ServiceKey = "0dc4858adf4ae98e1e29f7e89ca8e88d97b5a98b51621f3bb915d3eb73546458";
    public static void main(String[] args) {

        String JsonData = "";
        
        // response type 설정
        String type = "json";
        
        // request 파라미터 설정
        String numOfRows = "10";
        String pageNo = "1";

        // 조회 날짜 형식: YYYYMMDDHHMM
        // 예: 202507010000 ~ 202507012359
        String bidNtceBgnDt = "202601200000";
        String bidNtceEndDt = "202601231100";

        int connTimeoutMs = 5000;
        int readTimeoutMs = 3000;
        
        StringBuilder buffer = new StringBuilder();

        try {

            // HttpClient 생성 (커넥션 타임아웃, 읽기 타임아웃 설정)
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connTimeoutMs))
                .build();

            // 문서에 맞는 요청 URL 조립 (ServiceKey 포함)
            String apiUrl = Url
                + "?numOfRows=" + numOfRows
                + "&pageNo=" + pageNo
                + "&bidNtceBgnDt=" + bidNtceBgnDt
                + "&bidNtceEndDt=" + bidNtceEndDt
                + "&ServiceKey=" + ServiceKey
                + "&type=" + type;

            // HttpRequest 생성 (요청 URL, 요청 전체 타임아웃 설정)
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Accept", "application/json")
                .GET()
                .build();

            // 전송 + 응답 받기 (body를 String 형태로 바로 받음)
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = response.statusCode();
            String body = response.body();

            // 결과 처리
            if (status == 200) {
                buffer.append(body).append("\n");
            } else {
                buffer.append("code : ").append(status).append("\n");
                // HttpClient는 getResponseMessage() 메서드를 제공하지 않음
                // 그렇기 때문에 직접 상태 코드를 확인해야 함
                // 필요하면 헤더/바디를 같이 출력하는 방식으로 운영
                buffer.append("message : ").append(body).append("\n");
                buffer.append("body : ").append(body).append("\n");
            }

        } catch (IOException e) {
            System.out.println("네트워크/입출력 오류 : " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("요청이 중단되었습니다 : " + e.getMessage());
        } catch (Exception e) {
            System.out.println("예상치 못한 오류 : " + e.getMessage());
        }

        JsonData = buffer.toString();

        System.out.println(JsonData);
    }
}
