package com.softbase.nara.ui;

import com.softbase.nara.config.NaraApiConfig;
import com.softbase.nara.dto.BidItemColumn;
import com.softbase.nara.dto.GridResult;
import com.softbase.nara.service.NaraApiService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.BufferedWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.io.FileOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/*
나라장터 공고 조회 Swing UI.
UI 구성·이벤트·CSV 저장만 담당하고, API 호출·파싱·필터링은 Service에 위임.
 */
public final class NaraGetSwing {

    private static final Logger log = Logger.getLogger(NaraGetSwing.class.getName());

    // 한 페이지당 표시 하는 행의 개수
    private static final int ROWS_PER_PAGE = 50;

    // API 서비스 인스턴스
    private final NaraApiService apiService = NaraApiService.getInstance();

    // UI 컴포넌트
    private JFrame frame;
    private JSpinner spinnerBgn, spinnerEnd;
    private JTextField tfMinAsignBdgtAmt, tfSearch;
    private JComboBox<String> tfWorkType;
    private JLabel lbStatus;
    private JLabel lbSummary;

    // 테이블 모델
    private DefaultTableModel tableModel;
    private JTable table;

    // 페이징 패널
    private JPanel paginationPanel;

    // 마지막 조회 조건 (캐시 저장용)
    private String lastBgn, lastEnd, lastMinAmt, lastSearch, lastWorkType;
    private long lastTotalCount;
    private int currentPage = 1;

    // 캐시된 필터링된 행 (API 호출 결과 저장용)
    private List<Map<String, String>> cachedFilteredRows = null;

    /*
    메인 메서드
    */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NaraGetSwing().createAndShow());
    }

    /*
    UI 생성 및 표시
    */
    private void createAndShow() {
        frame = new JFrame("나라장터 공고 조회 (Swing)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1200, 650);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        spinnerBgn = createDateTimeSpinner(2026, 1, 20, 0, 0);
        spinnerEnd = createDateTimeSpinner(2026, 1, 23, 11, 0);
        tfMinAsignBdgtAmt = new JTextField("20", 8);
        tfSearch = new JTextField("ISMP, ISP, 차세대, 시스템구축, 시스템재구축", 12);
        tfWorkType = new JComboBox<>(new String[] {"전체", "물품", "용역"});

        top.add(new JLabel("시작"));
        top.add(spinnerBgn);
        top.add(new JLabel("종료"));
        top.add(spinnerEnd);
        top.add(new JLabel("최소 배정예산금액(설계금액) (억)"));
        top.add(tfMinAsignBdgtAmt);
        top.add(new JLabel("검색"));
        top.add(tfSearch);
        top.add(new JLabel("업무구분명"));
        top.add(tfWorkType);

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

    /*
    컬럼 이름 생성
    */
    private String[] buildColumnNames() {
        String[] colNames = new String[BidItemColumn.KEY_LIST.size() + 1];
        colNames[0] = "순번";
        for (int i = 0; i < BidItemColumn.KEY_LIST.size(); i++) {
            String k = BidItemColumn.KEY_LIST.get(i);
            colNames[i + 1] = BidItemColumn.KEY_TO_DISPLAY_NAME.getOrDefault(k, k);
        }
        return colNames;
    }

    /*
    상태 설정
    */
    private void setStatus(String msg) {
        lbStatus.setText(msg);
    }

    /*
    컬럼 너비 설정
    */
    private void setPreferredColumnWidths() {
        int[] widths = {
                50, 120, 100, 100, 320, 90, 100, 90, 70, 120, 90, 180, 180, 130, 130, 420
        };
        for (int c = 0; c < table.getColumnCount() && c < widths.length; c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        }
    }

    /*
    컬럼 너비 조정
    */
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

    /** 입찰공고일시 형식 (API·문서 기준) */
    private static final DateTimeFormatter BID_DT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 스피너 표시 형식 (사용자용) */
    private static final String SPINNER_DATETIME_FORMAT = "yyyy-MM-dd HH:mm";

    /*
    날짜·시간 선택 스피너 생성. 표시는 "yyyy-MM-dd HH:mm", API 전달 시에는 YYYYMMDDHHMM으로 변환.
     */
    private static JSpinner createDateTimeSpinner(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        SpinnerDateModel model = new SpinnerDateModel(cal.getTime(), null, null, Calendar.MINUTE);
        JSpinner spinner = new JSpinner(model);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, SPINNER_DATETIME_FORMAT);
        spinner.setEditor(editor);
        return spinner;
    }

    /*
    스피너에서 선택된 날짜·시간을 API 형식(YYYYMMDDHHMM) 문자열로 반환.
     */
    private static String getBidDtFromSpinner(JSpinner spinner) {
        if (spinner == null) return "";
        Object value = spinner.getValue();
        if (!(value instanceof Date)) return "";
        java.time.LocalDateTime ldt = ((Date) value).toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return ldt.format(BID_DT_FORMAT);
    }

    /*
    조회 기간이 API 제한(1개월) 이내인지 검증.
    @return 검증 실패 시 사용자에게 보여줄 메시지, 통과 시 null
     */
    private String validateBidDateRange(String bidNtceBgnDt, String bidNtceEndDt) {
        if (bidNtceBgnDt == null || bidNtceEndDt == null) return null;
        if (bidNtceBgnDt.length() != 12 || bidNtceEndDt.length() != 12) {
            return "시작·종료 일시는 12자리(YYYYMMDDHHMM)로 입력하세요.";
        }
        LocalDateTime start;
        LocalDateTime end;
        try {
            start = LocalDateTime.parse(bidNtceBgnDt, BID_DT_FORMAT);
            end = LocalDateTime.parse(bidNtceEndDt, BID_DT_FORMAT);
        } catch (DateTimeParseException e) {
            return "날짜 형식이 올바르지 않습니다. (YYYYMMDDHHMM)";
        }
        if (!end.isAfter(start) && !end.isEqual(start)) {
            return "종료일시가 시작일시보다 앞설 수 없습니다.";
        }
        if (end.isAfter(start.plusMonths(1))) {
            return "조회 기간은 1개월을 초과할 수 없습니다. (API 제한)";
        }
        return null;
    }

    /*
    조회시에 억단위 금액으로 검색하기 위해서 변환
    */
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

    /*
    조회 이벤트 처리
    */
    private void onSearch() {
        String bidNtceBgnDt = getBidDtFromSpinner(spinnerBgn);
        String bidNtceEndDt = getBidDtFromSpinner(spinnerEnd);
        String minAsignBdgtAmt = minAsignBdgtAmtEokToWon(tfMinAsignBdgtAmt.getText().trim());
        String searchKeyword = tfSearch.getText().trim();
        String workType = (String) tfWorkType.getSelectedItem();

        String rangeError = validateBidDateRange(bidNtceBgnDt, bidNtceEndDt);
        if (rangeError != null) {
            JOptionPane.showMessageDialog(frame, rangeError, "조회 기간 제한", JOptionPane.WARNING_MESSAGE);
            return;
        }

        lastBgn = bidNtceBgnDt;
        lastEnd = bidNtceEndDt;
        lastMinAmt = minAsignBdgtAmt;
        lastSearch = searchKeyword.isEmpty() ? null : searchKeyword;
        lastWorkType = (workType == null || workType.trim().isEmpty() || "전체".equals(workType.trim())) ? null : workType.trim();
        currentPage = 1;

        boolean needAllFiltered = !minAsignBdgtAmt.isEmpty()
                || lastSearch != null
                || lastWorkType != null;

        if (!needAllFiltered) {
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
                        NaraApiConfig.PersonalAuthKey,
                        lastBgn, lastEnd, lastMinAmt,
                        lastSearch,
                        lastWorkType,
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

    /*
    캐시된 데이터로 테이블 채우기
    */
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

    /*
    페이지 로드
    */
    private void loadPage(int pageNo) {
        if (pageNo < 1) return;

        if (cachedFilteredRows != null) {
            currentPage = pageNo;
            fillTableFromCache(pageNo);
            updatePaginationPanel();
            setStatus("완료: " + tableModel.getRowCount() + "건");
            return;
        }

        String bgn = lastBgn != null ? lastBgn : getBidDtFromSpinner(spinnerBgn);
        String end = lastEnd != null ? lastEnd : getBidDtFromSpinner(spinnerEnd);
        String minAmt = lastMinAmt != null ? lastMinAmt : minAsignBdgtAmtEokToWon(tfMinAsignBdgtAmt.getText().trim());
        final String searchKeyword = lastSearch != null ? lastSearch : tfSearch.getText().trim();
        final String workType = lastWorkType != null ? lastWorkType : (String) tfWorkType.getSelectedItem();

        tableModel.setRowCount(0);
        lbSummary.setText(" ");
        setStatus("조회 중...");

        final String numOfRows = String.valueOf(ROWS_PER_PAGE);
        final String pageNoStr = String.valueOf(pageNo);

        SwingWorker<GridResult, Void> worker = new SwingWorker<>() {
            @Override
            protected GridResult doInBackground() throws Exception {
                long loadPageStartMs = System.currentTimeMillis();
                int connTimeoutMs = 5000;
                int requestTimeoutMs = 5000;
                String jsonData = apiService.callApi(
                        NaraApiConfig.BASE_URL,
                        NaraApiConfig.PersonalAuthKey,
                        numOfRows,
                        pageNoStr,
                        bgn,
                        end,
                        "json",
                        connTimeoutMs,
                        requestTimeoutMs
                );
                String searchOpt = searchKeyword.isEmpty() ? null : searchKeyword;
                String workOpt = (workType == null || workType.trim().isEmpty() || "전체".equals(workType.trim())) ? null : workType.trim();
                GridResult gr = apiService.toGridRows(jsonData,
                        (minAmt == null || minAmt.trim().isEmpty()) ? null : minAmt,
                        searchOpt,
                        workOpt);
                log.info(String.format("[시간측정] UI 조회(loadPage) | pageNo=%d | %d ms", pageNo, System.currentTimeMillis() - loadPageStartMs));
                return gr;
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

    /*
    페이징 패널 업데이트
    */
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

    /*
    CSV 저장 이벤트 처리
    */
    private void onSaveCsv() {
        if (tableModel.getRowCount() == 0 && (cachedFilteredRows == null || cachedFilteredRows.isEmpty())) {
            JOptionPane.showMessageDialog(frame, "저장할 데이터가 없습니다.", "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] options = {"현재 페이지(50건) 저장", "전체 결과 저장"};
        int choice = JOptionPane.showOptionDialog(
                frame,
                "CSV 저장 방식을 선택하세요.",
                "CSV 저장",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice != 0 && choice != 1) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("nara_result.csv"));

        int ret = chooser.showSaveDialog(frame);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();

        // 0: 현재 페이지(테이블에 보이는 50건) 저장
        if (choice == 0) {
            if (tableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(frame, "현재 페이지에 저장할 데이터가 없습니다.", "안내", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                writeCsvFromTableModel(file);
                JOptionPane.showMessageDialog(frame, "저장 완료:\n" + file.getAbsolutePath(), "OK", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "저장 오류", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }

        // 1: 전체 결과 저장
        setStatus("전체 결과 CSV 생성 중...");
        SwingWorker<List<Map<String, String>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, String>> doInBackground() throws Exception {
                // 이미 캐시(필터 조회)된 결과가 있으면 그대로 사용
                if (cachedFilteredRows != null && !cachedFilteredRows.isEmpty()) {
                    return cachedFilteredRows;
                }

                // 캐시가 없으면(=일반 조회) 전체 페이지를 API에서 모두 가져와 저장
                long saveAllStartMs = System.currentTimeMillis();
                String bgn = lastBgn != null ? lastBgn : getBidDtFromSpinner(spinnerBgn);
                String end = lastEnd != null ? lastEnd : getBidDtFromSpinner(spinnerEnd);

                String minAmt = (lastMinAmt == null || lastMinAmt.trim().isEmpty()) ? null : lastMinAmt;
                String searchOpt = (lastSearch == null || lastSearch.trim().isEmpty()) ? null : lastSearch;
                String workOpt = (lastWorkType == null || lastWorkType.trim().isEmpty()) ? null : lastWorkType;

                // 총건수 기반으로 필요한 페이지 수를 계산 (999건/페이지)
                long countStartMs = System.currentTimeMillis();
                String jsonForCount = apiService.callApi(
                        NaraApiConfig.BASE_URL,
                        NaraApiConfig.PersonalAuthKey,
                        "1",
                        "1",
                        bgn,
                        end,
                        "json",
                        5000,
                        5000
                );
                long totalCount = apiService.toGridRows(jsonForCount).totalCount;
                log.info(String.format("[시간측정] UI 전체저장 - totalCount 조회 | %d ms", System.currentTimeMillis() - countStartMs));

                int pagesNeeded = (int) Math.ceil(totalCount / 999.0);
                int maxApiPages = Math.max(1, Math.min(pagesNeeded, 500)); // 안전장치

                long fetchStartMs = System.currentTimeMillis();
                List<Map<String, String>> rows = new ArrayList<>(apiService.fetchAllFilteredRows(
                        NaraApiConfig.BASE_URL,
                        NaraApiConfig.PersonalAuthKey,
                        bgn,
                        end,
                        minAmt,
                        searchOpt,
                        workOpt,
                        maxApiPages,
                        15_000,
                        30_000
                ));
                log.info(String.format("[시간측정] UI 전체저장 - fetchAllFilteredRows | %d 건 | %d ms", rows.size(), System.currentTimeMillis() - fetchStartMs));
                log.info(String.format("[시간측정] UI 전체저장 전체 | %d ms", System.currentTimeMillis() - saveAllStartMs));
                return rows;
            }

            @Override
            protected void done() {
                try {
                    List<Map<String, String>> rows = get();
                    if (rows == null || rows.isEmpty()) {
                        setStatus("완료: 0건");
                        JOptionPane.showMessageDialog(frame, "저장할 데이터가 없습니다.", "안내", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    writeCsvFromRows(rows, file);
                    setStatus("완료: 전체 " + rows.size() + "건 CSV 저장");
                    JOptionPane.showMessageDialog(frame, "저장 완료:\n" + file.getAbsolutePath(), "OK", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    setStatus("오류 발생");
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "저장 오류", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /*
    현재 테이블(현재 페이지) 기준 CSV 저장
    */
    private void writeCsvFromTableModel(File file) throws Exception {
        try (OutputStream os = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(osw)) {

            bw.write("\uFEFF"); // UTF-8 BOM (엑셀 호환)

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
        }
    }

    /*
    전체 결과(행 목록) 기준 CSV 저장
    - 컬럼 순서는 화면 테이블과 동일(순번 + BidItemColumn.KEY_LIST)
    */
    private void writeCsvFromRows(List<Map<String, String>> rows, File file) throws Exception {
        try (OutputStream os = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(osw)) {

            bw.write("\uFEFF"); // UTF-8 BOM (엑셀 호환)

            // 헤더는 현재 테이블 컬럼명을 그대로 사용
            for (int c = 0; c < tableModel.getColumnCount(); c++) {
                bw.append(tableModel.getColumnName(c));
                if (c < tableModel.getColumnCount() - 1) bw.append(',');
            }
            bw.newLine();

            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> row = rows.get(i);

                // 0열: 순번 (없으면 i+1)
                writeCsvCell(bw, row.getOrDefault("순번", String.valueOf(i + 1)));
                bw.append(',');

                // 나머지 컬럼: BidItemColumn.KEY_LIST 순서대로
                for (int k = 0; k < BidItemColumn.KEY_LIST.size(); k++) {
                    String key = BidItemColumn.KEY_LIST.get(k);
                    String v = row.getOrDefault(key, "");
                    writeCsvCell(bw, v);
                    if (k < BidItemColumn.KEY_LIST.size() - 1) bw.append(',');
                }
                bw.newLine();
            }
        }
    }

    private void writeCsvCell(BufferedWriter bw, String value) throws Exception {
        String v = (value == null) ? "" : value;
        v = v.replace("\"", "\"\"");
        bw.append('\"').append(v).append('\"');
    }
}
