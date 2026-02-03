# NaraGetSwingApp (나라장터 공고 조회)

**공공데이터포털** [나라장터 공공데이터 개방 표준 서비스](https://www.data.go.kr/) API를 사용해 **입찰 공고 정보**를 조회하는 Java 데스크톱 애플리케이션입니다.

- **GUI**: Swing 화면에서 기간·최소 배정예산금액 조건으로 조회, 테이블 표시, 50건 단위 페이지네이션, CSV 저장

---

## 요구 사항

- **Java 21** (JDK 21)
- **Gradle 8.x** (래퍼 포함, 별도 설치 불필요)

---

## 프로젝트 구조

```
KONEPS_API_JAVA/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── src/main/java/com/example/nara/
│   ├── config/   NaraApiConfig      # API URL, 서비스 키(환경변수 지원)
│   ├── dto/      GridResult, BidItemColumn  # 결과·컬럼 정의
│   ├── client/   NaraApiClient      # HTTP 호출 전담
│   ├── parser/   NaraResponseParser # JSON 응답 파싱 전담
│   ├── service/  NaraApiService     # 비즈니스·오케스트레이션
│   └── ui/       NaraGetSwing      # Swing GUI (main, CSV 저장)
├── document/
│   ├── 아키텍처_설명.md   # 실무형 코드 분리·계층별 역할 설명
│   └── (API 참고 자료)
└── README.md
```

계층별 역할과 “왜 이렇게 나눴는지”는 **`document/아키텍처_설명.md`** 에 상세히 정리되어 있습니다.

---

## 빌드 및 실행

### 1. GUI 애플리케이션 실행 (권장)

```bash
# Windows
gradlew.bat run

# macOS / Linux
./gradlew run
```

실행 클래스: `com.example.nara.ui.NaraGetSwing`  
화면에서 **시작/종료 일시(YYYYMMDDHHMM)**, **최소 배정예산금액(억)** 을 입력한 뒤 **조회** 후 **CSV 저장**으로 결과를 내보낼 수 있습니다.

### 2. Fat JAR로 실행

```bash
./gradlew shadowJar
java -jar build/libs/NaraGetSwingApp-1.0.0-all.jar
```

### 3. jpackage로 Windows 앱 이미지 생성

```bash
./gradlew jpackage
# 결과: dist/NaraGetSwing/
```

---

## API 및 설정

- **API**: `getDataSetOpnStdBidPblancInfo` (공공데이터 개방 표준 서비스)
- **Base URL**: `https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo`
- **인증**: 공공데이터포털에서 발급한 **서비스 키(ServiceKey)** 사용

서비스 키는 **설정 분리**로 관리합니다 (소스에 직접 두지 않음).

| 우선순위 | 방법 | 설명 |
|----------|------|------|
| 1 | 환경변수 `NARA_SERVICE_KEY` | 배포·운영 시 권장 |
| 2 | JVM 옵션 `-Dnara.service.key=키값` | 로컬 실행 시 |
| 3 | 기본값 (NaraApiConfig) | 미설정 시 로컬 개발용 기본 키 사용 |

예시:

```bash
# 환경변수로 키 지정 후 실행
set NARA_SERVICE_KEY=발급받은키값   # Windows CMD
$env:NARA_SERVICE_KEY="발급받은키값" # PowerShell
gradlew run

# JVM 옵션으로 지정
gradlew run --args="" -Dnara.service.key=발급받은키값
```

---

## CSV 저장 (엑셀 호환)

- **CSV 저장** 시 **UTF-8 BOM**을 붙여 저장합니다.
- Windows 엑셀에서 더블클릭으로 열어도 한글이 깨지지 않도록 되어 있습니다.

---

## 참고 자료

- **`document/아키텍처_설명.md`**: 실무형 패키지·계층 분리, 유지보수·추가 개발 관점 설명
- **`document/`** 폴더: 조달청 Open API 참고자료 (나라장터 공공데이터 개방 표준 서비스)
- [공공데이터포털](https://www.data.go.kr/) 에서 서비스 키 신청 및 API 문서 확인

---

## 라이선스 / 비고

- 외부 라이브러리: **json-simple** (Maven Central)
- 프로젝트명: `NaraGetSwingApp` (Gradle root project)
