package com.softbase.nara.config;

/**
 * 나라장터 API 설정.
 */
public final class NaraApiConfig {

    /** API 기본 End Point URL (공공데이터포털 나라장터 입찰공고 조회) */
    public static final String BASE_URL =
            "https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo";

    /** 일반 인증키 (공공데이터포털에서 발급한 서비스 키) */
    public static final String PersonalAuthKey =
            "0dc4858adf4ae98e1e29f7e89ca8e88d97b5a98b51621f3bb915d3eb73546458";

    private NaraApiConfig() {}
}
