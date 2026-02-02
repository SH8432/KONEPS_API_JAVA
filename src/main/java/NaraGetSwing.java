import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 나라장터 공고 조회 Swing UI
 * API 통신/파싱 로직은 NaraApiService에 위임
 */
public class NaraGetSwing {

    private JFrame frame;
    private JTextField tfBgn, tfEnd, tfRows, tfPage;
    private JLabel lbStatus;

    private DefaultTableModel tableModel;
    private JTable table;

    /*
    Swing UI 생성 메서드(main 메서드)
    */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NaraGetSwing().createAndShow());
    }

    /*
    Swing UI 생성 메서드
    */
    private void createAndShow() {
        frame = new JFrame("나라장터 공고 조회 (Swing)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1200, 650);

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

        String[] colNames = NaraApiService.KEY_LIST.stream()
                .map(k -> NaraApiService.KEY_TO_FIELD_NAME.getOrDefault(k, k))
                .toArray(String[]::new);

        tableModel = new DefaultTableModel(colNames, 0);
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JScrollPane scrollPane = new JScrollPane(table);

        lbStatus = new JLabel("Ready");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(lbStatus, BorderLayout.WEST);

        btnSearch.addActionListener(e -> onSearch());
        btnSaveCsv.addActionListener(e -> onSaveCsv());

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(bottom, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /*
    UI 상태 설정 메서드
    */
    private void setStatus(String msg) {
        lbStatus.setText(msg);
    }

    /*
    검색 버튼 클릭 이벤트 처리 메서드
    */
    private void onSearch() {
        String bidNtceBgnDt = tfBgn.getText().trim();
        String bidNtceEndDt = tfEnd.getText().trim();
        String numOfRows = tfRows.getText().trim();
        String pageNo = tfPage.getText().trim();

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

                String jsonData = NaraApiService.callApi(
                        NaraApiService.BASE_URL,
                        NaraApiService.PERSONAL_AUTH_KEY,
                        numOfRows,
                        pageNo,
                        bidNtceBgnDt,
                        bidNtceEndDt,
                        "json",
                        connTimeoutMs,
                        requestTimeoutMs
                );

                return NaraApiService.parseItemsToKeyValueRows(jsonData, NaraApiService.KEY_LIST);
            }

            @Override
            protected void done() {
                try {
                    List<Map<String, String>> rows = get();

                    for (Map<String, String> row : rows) {
                        Object[] values = NaraApiService.KEY_LIST.stream()
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

    /*
    CSV 저장 버튼 클릭 이벤트 처리 메서드
    */
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

            bw.write("\uFEFF");

            for (int c = 0; c < tableModel.getColumnCount(); c++) {
                bw.append(tableModel.getColumnName(c));
                if (c < tableModel.getColumnCount() - 1) bw.append(',');
            }
            bw.newLine();

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
}
