import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;


public class NaraGetSwing {

    private static final String BaseUrl =
            "https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo";
    private static final String PersonalAuthKey =
            "0dc4858adf4ae98e1e29f7e89ca8e88d97b5a98b51621f3bb915d3eb73546458";

    /*
     * 출력 대상 키 목록
     * - resultCode/resultMsg 는 header
     * - numOfRows/pageNo/totalCount 는 body
     * - 나머지는 items의 각 item(공고 1건)
     */
    private static final List<String> KeyList = List.of(
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

    // ===== Swing UI 구성 =====

    private JFrame frame;
    private JTextField tfBgn, tfEnd, tfRows, tfPage;
    private JLabel lbStatus;

    private DefaultTableModel tableModel;
    private JTable table;

    public static void main(String[] args) {
        // Swing은 반드시 EDT(이벤트 디스패치 스레드)에서 UI를 띄우는 것이 정석
        SwingUtilities.invokeLater(() -> new NaraGetSwing().createAndShow());
    }

    private void createAndShow() {
        frame = new JFrame("나라장터 공고 조회 (Swing)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1200, 650);

        // 상단 입력 영역
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        tfBgn = new JTextField("202601200000", 12);
        tfEnd = new JTextField("202601231100", 12);
        tfRows = new JTextField("3", 5);
        tfPage = new JTextField("1", 5);

        top.add(new JLabel("시작(YYYYMMDDHHMM)"));
        top.add(tfBgn);
        top.add(new JLabel("종료(YYYYMMDDHHMM)"));
        top.add(tfEnd);
        top.add(new JLabel("건수"));
        top.add(tfRows);
        top.add(new JLabel("페이지"));
        top.add(tfPage);

        JButton btnSearch = new JButton("조회");
        JButton btnSaveCsv = new JButton("CSV 저장");

        top.add(btnSearch);
        top.add(btnSaveCsv);

        // 표 영역
        String[] colNames = KeyList.stream()
                .map(k -> KeyToFieldName.getOrDefault(k, k))
                .toArray(String[]::new);

        tableModel = new DefaultTableModel(colNames, 0);
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JScrollPane scrollPane = new JScrollPane(table);

        // 하단 상태바
        lbStatus = new JLabel("Ready");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(lbStatus, BorderLayout.WEST);

        // 이벤트 연결
        btnSearch.addActionListener(e -> onSearch());
        btnSaveCsv.addActionListener(e -> onSaveCsv());

        // 프레임에 배치
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(bottom, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void setStatus(String msg) {
        lbStatus.setText(msg);
    }

    // 조회 버튼 로직: SwingWorker로 UI 안 멈추게 처리
    private void onSearch() {
        String bidNtceBgnDt = tfBgn.getText().trim();
        String bidNtceEndDt = tfEnd.getText().trim();
        String numOfRows = tfRows.getText().trim();
        String pageNo = tfPage.getText().trim();

        // 간단 검증
        if (bidNtceBgnDt.isEmpty() || bidNtceEndDt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "시작/종료 일시를 입력하세요.", "입력 오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        tableModel.setRowCount(0);
        setStatus("조회 중...");

        SwingWorker<List<Map<String, String>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, String>> doInBackground() throws Exception {
                int connTimeoutMs = 5000;
                int requestTimeoutMs = 5000;

                String jsonData = callApi(
                        BaseUrl,
                        PersonalAuthKey,
                        numOfRows,
                        pageNo,
                        bidNtceBgnDt,
                        bidNtceEndDt,
                        
                        "json",
                        connTimeoutMs,
                        requestTimeoutMs
                );

                return parseItemsToKeyValueRows(jsonData, KeyList);
            }

            @Override
            protected void done() {
                try {
                    List<Map<String, String>> rows = get();

                    for (Map<String, String> row : rows) {
                        Object[] values = KeyList.stream()
                                .map(k -> row.getOrDefault(k, ""))
                                .toArray();
                        tableModel.addRow(values);
                    }

                    setStatus("완료: " + rows.size() + "건");
                } catch (Exception ex) {
                    setStatus("오류 발생");
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "조회 오류", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void onSaveCsv() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(frame, "저장할 데이터가 없습니다.", "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("nara_result.csv"));

        int ret = chooser.showSaveDialog(frame);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        java.io.File file = chooser.getSelectedFile();

        try (OutputStream os = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(osw)) {

            // UTF-8 BOM 추가 (엑셀이 UTF-8로 인식하도록)
            bw.write("\uFEFF");

            // header
            for (int c = 0; c < tableModel.getColumnCount(); c++) {
                bw.append(tableModel.getColumnName(c));
                if (c < tableModel.getColumnCount() - 1) bw.append(',');
            }
            bw.newLine();

            // rows
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                for (int c = 0; c < tableModel.getColumnCount(); c++) {
                    String v = String.valueOf(tableModel.getValueAt(r, c));
                    v = v.replace("\"", "\"\"");
                    bw.append('"').append(v).append('"');
                    if (c < tableModel.getColumnCount() - 1) bw.append(',');
                }
                bw.newLine();
            }

            JOptionPane.showMessageDialog(frame, "저장 완료:\n" + file.getAbsolutePath(), "OK", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "저장 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ======= 기존 통신 + 파싱 로직 =======

    private static String callApi(
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

    private static List<Map<String, String>> parseItemsToKeyValueRows(String jsonData, List<String> keyList) throws Exception {
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

    private static JSONObject getResponse(String jsonData) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(jsonData);

        JSONObject response = (JSONObject) root.get("response");
        if (response == null) throw new IllegalArgumentException("응답에 'response'가 없습니다.");

        return response;
    }

    private static JSONObject getResponseHeader(JSONObject response) {
        JSONObject header = (JSONObject) response.get("header");
        if (header == null) throw new IllegalArgumentException("응답에 'header'가 없습니다.");
        return header;
    }

    private static JSONObject getResponseBody(JSONObject response) {
        JSONObject body = (JSONObject) response.get("body");
        if (body == null) throw new IllegalArgumentException("응답에 'body'가 없습니다.");
        return body;
    }

    @SuppressWarnings("unchecked")
    private static JSONArray getItemArray(JSONObject body) {
        Object itemsObj = body.get("items");
        if (itemsObj == null) return new JSONArray();

        // body.items가 JSONObject인 경우: 내부에 item이 있을 수 있음(표준형)
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

        // 지금 네 응답처럼 body.items가 바로 JSONArray인 경우
        if (itemsObj instanceof JSONArray) return (JSONArray) itemsObj;

        return new JSONArray();
    }

    private static String getAsString(JSONObject obj, String key) {
        Object v = obj.get(key);
        return (v == null) ? "" : String.valueOf(v);
    }
}
