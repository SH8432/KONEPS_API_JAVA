# NaraGetSwingApp (나라장터 공고 조회)

**공공데이터포털** [나라장터 공공데이터 개방 표준 서비스](https://www.data.go.kr/) API를 사용해 **입찰 공고 정보**를 조회하는 Java 데스크톱 애플리케이션입니다.

- **콘솔 실행**: `NaraGet` — 터미널에서 조회 결과 출력
- **GUI 실행**: `NaraGetSwing` — Swing 화면에서 조회·테이블 표시·CSV 저장
- **테스트/파싱**: `NaraGetTest` — JSON 파싱 및 API 응답 구조 확인용

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
├── src/main/java/
│   ├── NaraGet.java      # 콘솔용 조회 (main)
│   ├── NaraGetSwing.java # Swing GUI (main, CSV 저장 포함)
│   └── NaraGetTest.java  # JSON 파싱/테스트
├── document/             # API 참고 자료
└── README.md
```

---

## 빌드 및 실행

### 1. GUI 애플리케이션 실행 (권장)

```bash
# Windows
gradlew.bat run

# macOS / Linux
./gradlew run
```

기본 실행 클래스는 `NaraGetSwing`입니다.  
화면에서 **시작/종료 일시(YYYYMMDDHHMM)**, **건수**, **페이지**를 입력한 뒤 **조회** 후 **CSV 저장**으로 결과를 내보낼 수 있습니다.

### 2. Fat JAR로 실행

```bash
./gradlew shadowJar
java -jar build/libs/NaraGetSwingApp-1.0.0-all.jar
```

### 3. 콘솔 전용 실행 (NaraGet)

`build.gradle`의 `mainClass`를 `NaraGet`으로 바꾼 뒤 `gradlew run` 하거나, IDE에서 `NaraGet`의 `main`을 실행하면 됩니다.

---

## API 및 설정

- **API**: `getDataSetOpnStdBidPblancInfo` (공공데이터 개방 표준 서비스)
- **Base URL**: `https://apis.data.go.kr/1230000/ao/PubDataOpnStdService/getDataSetOpnStdBidPblancInfo`
- **인증**: 공공데이터포털에서 발급한 **서비스 키(ServiceKey)** 를 쿼리 파라미터로 전달

실제 서비스 키는 코드 내 상수(`PersonalAuthKey` 등) 또는 환경 변수/설정 파일로 관리하는 것을 권장합니다.

---

## CSV 저장 (엑셀 호환)

- **CSV 저장** 시 **UTF-8 BOM**을 붙여 저장합니다.
- Windows 엑셀에서 더블클릭으로 열어도 한글이 깨지지 않도록 되어 있습니다.

---

## 참고 자료

- `document/` 폴더: 조달청 Open API 참고자료 (나라장터 공공데이터 개방 표준 서비스)
- [공공데이터포털](https://www.data.go.kr/) 에서 서비스 키 신청 및 API 문서 확인

---

## 라이선스 / 비고

- 외부 라이브러리: **json-simple** (Maven Central)
- 프로젝트명: `NaraGetSwingApp` (Gradle root project)
