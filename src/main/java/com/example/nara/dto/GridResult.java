package com.example.nara.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 그리드(테이블) 표시용 API 결과 DTO.
 * 헤더 메타(결과코드, 페이지 정보) + 행 데이터를 한 번에 전달할 때 사용.
 */
public final class GridResult {

    /** 결과 코드 (예: 00) */
    public String resultCode;
    /** 결과 메시지 */
    public String resultMsg;
    /** 한 페이지당 행 수 */
    public long numOfRows;
    /** 현재 페이지 번호 */
    public long pageNo;
    /** 전체 건수 */
    public long totalCount;
    /** 그리드에 표시할 행 목록 (컬럼 키 → 값) */
    public final List<LinkedHashMap<String, String>> rows = new ArrayList<>();

    public GridResult() {}
}
