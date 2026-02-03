package com.example.nara.ui;

import com.example.nara.config.NaraApiConfig;
import com.example.nara.dto.BidItemColumn;
import com.example.nara.dto.GridResult;
import com.example.nara.service.NaraApiService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 나라장터 공고 조회 Swing UI.
 * UI 구성·이벤트·CSV 저장만 담당하고, API 호출·파싱·필터링은 Service에 위임.
 */
public final class NaraGetSwing {

    private static final int ROWS_PER_PAGE = 50;

    private final NaraApiService apiService = NaraApiService.getInstance();

    private JFrame frame;
    private JTextField tfBgn, tfEnd, tfMinAsignBdgtAmt;
    private JLabel lbStatus;
    private JLabel lbSummary;

    private DefaultTableModel tableModel;
    private JTable table;
    private JPanel paginationPanel;

    private String lastBgn, lastEnd, lastMinAmt;
    private long lastTotalCount;
    private int currentPage = 1;

    private List<Map<String, String>> cachedFilteredRows = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NaraGetSwing().createAndShow());
    }

    private void createAndShow() {
        frame = new JFrame("나라장터 공고 조회 (Swing)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1200, 650);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        tfBgn = new JTextField("202601200000", 12);
        tfEnd = new JTextField("202601231100", 12);
        tfMinAsignBdgtAmt = new JTextField("20", 8);

        top.add(new JLabel("시작(YYYYMMDDHHMM)"));
        top.add(tfBgn);
        top.add(new JLabel("종료(YYYYMMDDHHMM)"));
        top.add(tfEnd);
        top.add(new JLabel("최소 배정예산금액(설계금액) (억)"));
        top.add(tfMinAsignBdgtAmt);

        JButton btnSearch = new JButton("조회");
        top.add(btnSearch);
        JButton btnSaveCsv = new JButton("CSV 저장");
        top.add(btnSaveCsv);

        lbSummary = new JLabel(" ");
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(top, BorderLayout.NORTH);
        northPanel.add(lbSummary, BorderLayout.SOUTH);

        String[] colNames = buildColumnNames();
        tableModel = new DefaultTableModel(colNames, 0);
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setPreferredColumnWidths();

        JScrollPane scrollPane = new JScrollPane(table);

        lbStatus = new JLabel("Ready");
        paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(lbStatus, BorderLayout.WEST);
        bottom.add(paginationPanel, BorderLayout.CENTER);

        btnSearch.addActionListener(e -> onSearch());
        btnSaveCsv.addActionListener(e -> onSaveCsv());

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(northPanel, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(bottom, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private String[] buildColumnNames() {
        String[] colNames = new String[BidItemColumn.KEY_LIST.size() + 1];
        colNames[0] = "순번";
        for (int i = 0; i < BidItemColumn.KEY_LIST.size(); i++) {
            String k = BidItemColumn.KEY_LIST.get(i);
            colNames[i + 1] = BidItemColumn.KEY_TO_DISPLAY_NAME.getOrDefault(k, k);
        }
        return colNames;
    }

    private void setStatus(String msg) {
        lbStatus.setText(msg);
    }

    private void setPreferredColumnWidths() {
        int[] widths = {
                50, 120, 100, 100, 320, 90, 100, 90, 70, 120, 90, 180, 180, 130, 130, 420
        };
        for (int c = 0; c < table.getColumnCount() && c < widths.length; c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        }
    }

    private void resizeColumnsToFitContent() {
        int margin = 12;
        for (int c = 0; c < table.getColumnCount(); c++) {
            TableColumn col = table.getColumnModel().getColumn(c);
            int width = 0;

            TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(
                    table, col.getHeaderValue(), false, false, -1, c);
            width = Math.max(width, headerComp.getPreferredSize().width);

            for (int r = 0; r < table.getRowCount(); r++) {
                TableCellRenderer cellRenderer = table.getCellRenderer(r, c);
                Component cellComp = cellRenderer.getTableCellRendererComponent(
                        table, table.getValueAt(r, c), false, false, r, c);
                width = Math.max(width, cellComp.getPreferredSize().width);
            }

            col.setPreferredWidth(width + margin);
        }
    }

    private String minAsignBdgtAmtEokToWon(String inputEok) {
        if (inputEok == null || inputEok.trim().isEmpty()) return "";
        try {
            double eok = Double.parseDouble(inputEok.trim().replace(",", ""));
            if (eok <= 0) return "";
            long won = Math.round(eok * 100_000_000L);
            return String.valueOf(won);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private void onSearch() {
        String bidNtceBgnDt = tfBgn.getText().trim();
        String bidNtceEndDt = tfEnd.getText().trim();
        String minAsignBdgtAmt = minAsignBdgtAmtEokToWon(tfMinAsignBdgtAmt.getText().trim());

        if (bidNtceBgnDt.isEmpty() || bidNtceEndDt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "시작/종료 일시를 입력하세요.", "입력 오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        lastBgn = bidNtceBgnDt;
        lastEnd = bidNtceEndDt;
        lastMinAmt = minAsignBdgtAmt;
        currentPage = 1;

        if (minAsignBdgtAmt.isEmpty()) {
            cachedFilteredRows = null;
            loadPage(1);
            return;
        }

        tableModel.setRowCount(0);
        lbSummary.setText(" ");
        setStatus("조회 중... (조건 통과 건 수집)");

        final int maxApiPages = 100;
        SwingWorker<List<Map<String, String>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, String>> doInBackground() throws Exception {
                int connTimeoutMs = 15_000;
                int requestTimeoutMs = 30_000;
                List<Map<String, String>> list = new ArrayList<>();
                for (Map<String, String> row : apiService.fetchAllFilteredRows(
                        NaraApiConfig.BASE_URL,
                        NaraApiConfig.getServiceKey(),
                        lastBgn, lastEnd, lastMinAmt,
                        maxApiPages,
                        connTimeoutMs,
                        requestTimeoutMs
                )) {
                    list.add(row);
                }
                return list;
            }

            @Override
            protected void done() {
                try {
                    cachedFilteredRows = get();
                    lastTotalCount = cachedFilteredRows.size();
                    currentPage = 1;
                    fillTableFromCache(1);
                    updatePaginationPanel();
                    setStatus("완료: 전체 " + lastTotalCount + "건 (50건씩 " + (int) Math.ceil((double) lastTotalCount / ROWS_PER_PAGE) + "페이지)");
                } catch (Exception ex) {
                    setStatus("오류 발생");
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "조회 오류", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void fillTableFromCache(int pageNo) {
        if (cachedFilteredRows == null) return;
        tableModel.setRowCount(0);
        int from = (pageNo - 1) * ROWS_PER_PAGE;
        int to = Math.min(pageNo * ROWS_PER_PAGE, cachedFilteredRows.size());
        if (from >= cachedFilteredRows.size()) return;

        lbSummary.setText(String.format("한페이지: %d건 | 현재: %d페이지 | 전체: %d건 (조건 통과)",
                ROWS_PER_PAGE, pageNo, lastTotalCount));

        for (int i = from; i < to; i++) {
            Map<String, String> row = cachedFilteredRows.get(i);
            Object[] values = new Object[BidItemColumn.KEY_LIST.size() + 1];
            values[0] = String.valueOf(i + 1);
            for (int j = 0; j < BidItemColumn.KEY_LIST.size(); j++) {
                values[j + 1] = row.getOrDefault(BidItemColumn.KEY_LIST.get(j), "");
            }
            tableModel.addRow(values);
        }
        resizeColumnsToFitContent();
    }

    private void loadPage(int pageNo) {
        if (pageNo < 1) return;

        if (cachedFilteredRows != null) {
            currentPage = pageNo;
            fillTableFromCache(pageNo);
            updatePaginationPanel();
            setStatus("완료: " + tableModel.getRowCount() + "건");
            return;
        }

        String bgn = lastBgn != null ? lastBgn : tfBgn.getText().trim();
        String end = lastEnd != null ? lastEnd : tfEnd.getText().trim();
        String minAmt = lastMinAmt != null ? lastMinAmt : minAsignBdgtAmtEokToWon(tfMinAsignBdgtAmt.getText().trim());

        tableModel.setRowCount(0);
        lbSummary.setText(" ");
        setStatus("조회 중...");

        final String numOfRows = String.valueOf(ROWS_PER_PAGE);
        final String pageNoStr = String.valueOf(pageNo);

        SwingWorker<GridResult, Void> worker = new SwingWorker<>() {
            @Override
            protected GridResult doInBackground() throws Exception {
                int connTimeoutMs = 5000;
                int requestTimeoutMs = 5000;
                String jsonData = apiService.callApi(
                        NaraApiConfig.BASE_URL,
                        NaraApiConfig.getServiceKey(),
                        numOfRows,
                        pageNoStr,
                        bgn,
                        end,
                        "json",
                        connTimeoutMs,
                        requestTimeoutMs
                );
                return apiService.toGridRows(jsonData, (minAmt == null || minAmt.trim().isEmpty()) ? null : minAmt);
            }

            @Override
            protected void done() {
                try {
                    GridResult gr = get();
                    lastTotalCount = gr.totalCount;
                    currentPage = pageNo;

                    lbSummary.setText(String.format("결과코드: %s | 결과메시지: %s | 한페이지: %d건 | 현재: %d페이지 | 전체: %d건",
                            gr.resultCode, gr.resultMsg, ROWS_PER_PAGE, currentPage, gr.totalCount));

                    for (Map<String, String> row : gr.rows) {
                        Object[] values = new Object[BidItemColumn.KEY_LIST.size() + 1];
                        values[0] = row.getOrDefault("순번", "");
                        for (int i = 0; i < BidItemColumn.KEY_LIST.size(); i++) {
                            values[i + 1] = row.getOrDefault(BidItemColumn.KEY_LIST.get(i), "");
                        }
                        tableModel.addRow(values);
                    }

                    resizeColumnsToFitContent();
                    updatePaginationPanel();
                    setStatus("완료: " + gr.rows.size() + "건");
                } catch (Exception ex) {
                    setStatus("오류 발생");
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "조회 오류", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void updatePaginationPanel() {
        paginationPanel.removeAll();
        final int totalPages = Math.max(1, lastTotalCount <= 0 ? 1 : (int) Math.ceil((double) lastTotalCount / ROWS_PER_PAGE));

        JButton btnPrev = new JButton("이전");
        btnPrev.setEnabled(currentPage > 1);
        btnPrev.addActionListener(e -> loadPage(currentPage - 1));
        paginationPanel.add(btnPrev);

        int showFrom = 1;
        int showTo = totalPages;
        if (totalPages > 10) {
            showFrom = Math.max(1, currentPage - 2);
            showTo = Math.min(totalPages, currentPage + 2);
            if (showFrom > 1) {
                JButton first = new JButton("1");
                first.addActionListener(e -> loadPage(1));
                paginationPanel.add(first);
                if (showFrom > 2) paginationPanel.add(new JLabel(" ... "));
            }
        }

        for (int p = showFrom; p <= showTo; p++) {
            final int page = p;
            JButton btn = new JButton(String.valueOf(page));
            if (page == currentPage) {
                btn.setEnabled(false);
                btn.setFont(btn.getFont().deriveFont(Font.BOLD));
            } else {
                btn.addActionListener(e -> loadPage(page));
            }
            paginationPanel.add(btn);
        }

        if (totalPages > 10 && showTo < totalPages) {
            if (showTo < totalPages - 1) paginationPanel.add(new JLabel(" ... "));
            JButton last = new JButton(String.valueOf(totalPages));
            last.addActionListener(e -> loadPage(totalPages));
            paginationPanel.add(last);
        }

        JButton btnNext = new JButton("다음");
        btnNext.setEnabled(currentPage < totalPages);
        btnNext.addActionListener(e -> loadPage(currentPage + 1));
        paginationPanel.add(btnNext);

        paginationPanel.revalidate();
        paginationPanel.repaint();
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
