package com.softbase.nara.dto;

import java.util.List;
import java.util.Map;

/**
 * 나라장터 입찰 공고 항목의 컬럼 정의.
 * API 필드명(키)과 화면 표시명 매핑을 한 곳에서 관리 → 컬럼 추가/변경 시 이 클래스만 수정.
 */
public final class BidItemColumn {

    /** 그리드에 사용할 API 필드 키 목록 (순서 유지) */
    public static final List<String> KEY_LIST = List.of(
            "bidNtceNo", "bidNtceOrd", "refNtceNo", "ppsNtceYn", "bidNtceNm", "bidNtceSttusNm",
            "bidNtceDate", "bidNtceBgn", "bsnsDivNm", "cntrctCnclsSttusNm", "cntrctCnclsMthdNm",
            "ntceInsttNm", "dmndInsttNm", "asignBdgtAmt", "presmptPrce", "bidNtceUrl"
    );

    /** API 필드 키 → 한글 컬럼명 (테이블 헤더/CSV 헤더용) */
    public static final Map<String, String> KEY_TO_DISPLAY_NAME = Map.ofEntries(
            Map.entry("bidNtceNo", "입찰공고번호"),
            Map.entry("bidNtceOrd", "입찰공고차수"),
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

    private BidItemColumn() {}
}
