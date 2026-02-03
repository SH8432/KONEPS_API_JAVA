package com.example.nara.config;

/**
 * 나라장터 API 설정.
 * 실무에서는 환경변수·시스템 프로퍼티·설정 파일에서 읽어 오는 것이 일반적.
 * 보안: API 키는 소스에 직접 두지 않고, 배포 시 환경변수 등으로 주입.
 */
public final class NaraApiConfig {

    /** API 기본 URL (공공데이터포털 나라장터 입찰공고 조회) */
    public static final String BASE_URL =
            "https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo";

    /**
     * 인증 키. 우선순위: 환경변수 NARA_SERVICE_KEY > 시스템 프로퍼티 nara.service.key > 기본값.
     * 실무에서는 기본값을 제거하고 배포 시 반드시 설정하도록 하는 경우가 많음.
     */
    public static String getServiceKey() {
        String key = System.getenv("NARA_SERVICE_KEY");
        if (key != null && !key.isBlank()) return key.trim();
        key = System.getProperty("nara.service.key");
        if (key != null && !key.isBlank()) return key.trim();
        return DEFAULT_SERVICE_KEY;
    }

    /** 로컬 개발용 기본 키 (실서비스에서는 사용하지 않도록 주의) */
    private static final String DEFAULT_SERVICE_KEY =
            "0dc4858adf4ae98e1e29f7e89ca8e88d97b5a98b51621f3bb915d3eb73546458";

    private NaraApiConfig() {}
}
