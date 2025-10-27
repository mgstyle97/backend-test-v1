# 백엔드 사전 과제 – 결제 도메인 서버 (구현 완료)

> 나노바나나 페이먼츠 결제 도메인 서버 - Kotlin + Spring Boot + 헥사고널 아키텍처

## 📋 목차
1. [구현 완료 사항](#-구현-완료-사항)
2. [빠른 시작](#-빠른-시작)
3. [API 사용 가이드](#-api-사용-가이드)
4. [테스트 실행](#-테스트-실행)
5. [프로젝트 구조](#-프로젝트-구조)
6. [변경 이력](#-변경-이력)
7. [추가 구현 사항](#-추가-구현-사항)
8. [상세 문서](#-상세-문서)

---

## ✅ 구현 완료 사항

### 필수 과제
- ✅ **과제 1: 결제 생성 API**
  - TestPG REST API 연동 (`https://api-test-pg.bigs.im/v1/approve`)
  - 제휴사별 수수료 정책 적용 (하드코딩 제거)
  - PG 선택 로직 구현 (홀수: MockPG, 짝수: TestPG)
  - 에러 처리 (401, 422)

- ✅ **과제 2: 결제 내역 조회 및 통계 API**
  - 커서 기반 페이지네이션 (`createdAt DESC, id DESC`)
  - 통계 집계 (필터와 동일 조건)
  - Base64 URL-safe 커서 인코딩
  - 필터링 (partnerId, status, from, to)

- ✅ **과제 3: 제휴사별 수수료 정책**
  - `effective_from` 기준 최신 정책 적용
  - `RoundingMode.HALF_UP` 반올림
  - FeeCalculator 도메인 로직

### 테스트
- ✅ **80개 이상 테스트** 작성 및 통과
  - PaymentServiceTest (10개)
  - QueryPaymentsServiceCursorTest (20개)
  - PaymentControllerTest (16개)
  - TestPgClientErrorHandlingTest (8개)
  - AesGcmDecryptorTest (15개)
  - PaymentEncValidatorTest (18개)
  - 기타 단위/통합 테스트

### 아키텍처
- ✅ **헥사고널 아키텍처** 유지
  - Domain: 순수 Kotlin (프레임워크 의존 없음)
  - Application: 유스케이스 및 포트
  - Infrastructure: JPA 어댑터
  - External: PG 클라이언트 어댑터
  - Bootstrap: Spring Boot 애플리케이션

### 보안
- ✅ **민감정보 보호**
  - 카드번호: `cardBin` + `cardLast4`만 저장
  - AES-256-GCM 암호화/복호화
  - Bean Validation 통합
  - 로깅 배제

---

## 🚀 빠른 시작

### 요구사항
- **JDK 21** 이상
- Gradle (Wrapper 포함)

### 1. 프로젝트 클론
```bash
cd backend-test-v1
```

### 2. 빌드 및 테스트
```bash
./gradlew clean build
# BUILD SUCCESSFUL
# 80 tests passed
```

### 3. 애플리케이션 실행
```bash
./gradlew :modules:bootstrap:api-payment-gateway:bootRun
```

애플리케이션이 `http://localhost:8080`에서 실행됩니다.

### 4. 코드 스타일 검사
```bash
./gradlew ktlintCheck
# 또는 자동 수정
./gradlew ktlintFormat
```

---

## 📖 API 사용 가이드

### 1. 결제 생성

**엔드포인트**: `POST /api/v1/payments`

**요청 예시**:
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "partnerId": 2,
    "amount": 10000,
    "cardBin": "123456",
    "cardLast4": "4242",
    "productName": "테스트 상품",
    "enc": "암호화된_카드정보"
  }'
```

**응답 예시**:
```json
{
  "id": 1,
  "partnerId": 2,
  "amount": 10000,
  "appliedFeeRate": 0.0300,
  "feeAmount": 400,
  "netAmount": 9600,
  "cardBin": "123456",
  "cardLast4": "4242",
  "approvalCode": "10271234",
  "approvedAt": "2025-10-27T07:30:00Z",
  "status": "APPROVED",
  "createdAt": "2025-10-27T07:30:00Z",
  "updatedAt": "2025-10-27T07:30:00Z"
}
```

### 2. 결제 조회 및 통계

**엔드포인트**: `GET /api/v1/payments`

**쿼리 파라미터**:
- `partnerId` (optional): 제휴사 ID 필터
- `status` (optional): 결제 상태 (APPROVED, CANCELED)
- `from` (optional): 시작 일시 (yyyy-MM-dd HH:mm:ss)
- `to` (optional): 종료 일시
- `cursor` (optional): 페이지네이션 커서
- `limit` (optional): 페이지 크기 (기본값: 20)

**요청 예시**:
```bash
curl "http://localhost:8080/api/v1/payments?partnerId=2&status=APPROVED&limit=10"
```

**응답 예시**:
```json
{
  "items": [
    {
      "id": 1,
      "partnerId": 2,
      "amount": 10000,
      "appliedFeeRate": 0.0300,
      "feeAmount": 400,
      "netAmount": 9600,
      "cardLast4": "4242",
      "approvalCode": "10271234",
      "approvedAt": "2025-10-27T07:30:00Z",
      "status": "APPROVED",
      "createdAt": "2025-10-27T07:30:00Z"
    }
  ],
  "summary": {
    "count": 1,
    "totalAmount": 10000,
    "totalNetAmount": 9600
  },
  "nextCursor": "MTczMDA5NzAwMDox",
  "hasNext": false
}
```

---

## 🧪 테스트 실행

### 전체 테스트
```bash
./gradlew test
```

### 모듈별 테스트
```bash
# 도메인 테스트
./gradlew :modules:domain:test

# 애플리케이션 테스트
./gradlew :modules:application:test

# API 통합 테스트
./gradlew :modules:bootstrap:api-payment-gateway:test
```

### 테스트 커버리지
- **80개 이상 테스트** 작성
- **엣지 케이스** 포함 (null, 잘못된 형식, 경계값)
- **결정적** (MockK로 외부 의존성 격리)
- **빠름** (전체 테스트 2분 이내)

---

## 📁 프로젝트 구조

```
backend-test-v1/
├── modules/
│   ├── domain/                    # 순수 도메인 모델
│   │   └── src/main/kotlin/im/bigs/pg/domain/
│   │       ├── payment/          # Payment, PaymentStatus, FeeCalculator
│   │       └── partner/          # Partner, FeePolicy
│   │
│   ├── application/               # 유스케이스 및 포트
│   │   └── src/main/kotlin/im/bigs/pg/application/
│   │       ├── payment/
│   │       │   ├── service/     # PaymentService, QueryPaymentsService
│   │       │   └── port/        # PaymentUseCase, QueryPaymentsUseCase
│   │       └── pg/port/out/     # PgClientOutPort
│   │
│   ├── infrastructure/            # 인프라 어댑터
│   │   └── persistence/
│   │       └── src/main/kotlin/im/bigs/pg/infra/persistence/
│   │           ├── payment/     # PaymentEntity, PaymentJpaRepository
│   │           └── partner/     # PartnerEntity, FeePolicyEntity
│   │
│   ├── external/                  # 외부 시스템 어댑터
│   │   └── pg-client/
│   │       └── src/main/kotlin/im/bigs/pg/external/pg/
│   │           ├── TestPgClient.kt      # TestPG REST API 연동
│   │           └── MockPgClient.kt      # 목업 PG (비활성화)
│   │
│   ├── common/                    # 공통 유틸리티
│   │   └── utils/
│   │       └── src/main/kotlin/im/bigs/pg/utils/
│   │           └── config/      # RestClientConfig
│   │
│   └── bootstrap/                 # 실행 가능 애플리케이션
│       └── api-payment-gateway/
│           └── src/main/kotlin/im/bigs/pg/api/
│               ├── payment/     # PaymentController
│               ├── crypto/      # AesGcmDecryptor
│               └── config/      # DataInitializer, Validators
│
├── sql/
│   └── scheme.sql                 # 데이터베이스 스키마
│
├── README.md                      # 본 파일
```

---

## 📝 변경 이력

### 커밋 히스토리

```
131e3da feat: 암호화 강화를 위한 validation 추가
a01f517 feat: [Feature] 결제 내역 조회 및 통계 API 상세 구현
e6e7386 feat: [Feature] 결제 생성 API 상세 구현
```

### 상세 내역

#### 1. `e6e7386` - 결제 생성 API 구현
**이슈**: #2

**구현 사항**:
- `PaymentService`: 제휴사별 수수료 정책 기반 결제 처리 로직
- `TestPgClient`: TestPG REST API 연동 및 에러 처리 (401, 422)
- `RestClientConfig`: 공통 HTTP 클라이언트 설정 추가
- `PaymentController`: 결제 생성 엔드포인트

**테스트**:
- PaymentServiceTest: 10개 단위 테스트 (정상/예외/PG 선택)
- TestPgClientErrorHandlingTest: 8개 에러 처리 테스트
- PaymentControllerTest: 16개 컨트롤러 테스트 (create 5개, query 11개)

#### 2. `a01f517` - 결제 내역 조회 및 통계 API 구현
**이슈**: #3

**구현 사항**:
- `QueryPaymentsService`: 커서 기반 페이징 및 통계 집계 로직
- 커서 인코딩/디코딩: Base64 URL-safe 방식 (`createdAt:id`)
- 페이징 정렬: `createdAt DESC, id DESC`
- 필터링: partnerId, status, from/to 기간 필터 지원
- 통계 집계: count, totalAmount, totalNetAmount 계산

**테스트**:
- QueryPaymentsServiceCursorTest: 20개 테스트 (커서 엣지 케이스, 라운드트립, 페이징)

**코드 품질**:
- FeePolicyEntity, PartnerEntity: ktlint wildcard import 제거

#### 3. `131e3da` - 암호화 강화 및 Validation
**구현 사항**:
- `AesGcmDecryptor`: AES-256-GCM 암호화/복호화 (Base64 URL-safe)
- `PaymentEncValidator`: 복호화 데이터 검증 로직
  - 카드번호: 16자리 숫자 (하이픈 허용)
  - 생년월일: YYYYMMDD 형식, 실제 날짜 검증
  - 만료일: MMYY 형식, 현재 시점 이후 검증
  - 비밀번호: 숫자 2자리
  - 금액: 1원 이상
- `@PaymentEnc`: 커스텀 Bean Validation 어노테이션
- `TestPgProperties`: 암호화 설정 프로퍼티 바인딩
- `DataInitializer`: 초기 데이터 시딩 (Partner 2개, FeePolicy 2개)

**테스트**:
- AesGcmDecryptorTest: 15개 테스트 (정상/실패/실제 시나리오)
- PaymentEncValidatorTest: 18개 테스트 (필드별 검증)

**구조 개선**:
- MockPgClient: @Component 주석 처리 (TestPgClient만 사용)
- build.gradle.kts: common 서브 모듈 설정 추가

---

## 🎯 추가 구현 사항

### 1. 보안 강화: AES-256-GCM 암호화
- **목적**: 클라이언트가 민감한 카드 정보를 암호화하여 전송
- **기능**:
  - API Key를 SHA-256으로 해시하여 32바이트 AES 키 생성
  - GCM 모드 (AEAD 인증 태그 128비트)
  - 변조 감지 (Authentication Tag)
  - Base64 URL-safe 인코딩
- **테스트**: 15개 테스트로 암호화/복호화 검증

### 2. 입력 검증: Bean Validation 통합
- **목적**: 복호화된 결제 데이터의 비즈니스 규칙 자동 검증
- **기능**:
  - 커스텀 `@PaymentEnc` 어노테이션
  - 필드별 검증 규칙 (카드번호, 생년월일, 만료일, 비밀번호, 금액)
  - 복호화 실패 시 자동 검증 실패
- **테스트**: 18개 테스트로 검증 규칙 커버

### 3. HTTP 통신: RestClient 표준화
- **목적**: Spring 6.1+ 표준 HTTP 클라이언트 사용
- **기능**:
  - RestClient 빈 생성 및 공통 설정
  - 타임아웃 설정 (연결: 5초, 읽기: 10초)
  - 에러 핸들러 (401, 422 등)
- **확장성**: 다른 PG사 연동 시 동일 패턴 재사용

### 4. 멀티모듈 구조 개선
- **목적**: 모듈별 명확한 책임 분리
- **기능**:
  - common/utils 모듈 분리
  - Gradle 서브 모듈 자동 적용
  - 의존성 경계 명확화

---

## 🏗️ 기술 스택

### 백엔드
- **Language**: Kotlin 1.9.25
- **Framework**: Spring Boot 3.4.4
- **JVM**: Java 22 (bytecode target: 21)
- **Build**: Gradle 8.x (Wrapper)

### 데이터베이스
- **H2**: 인메모리 (MySQL 호환 모드)
- **JPA/Hibernate**: ORM

### 테스트
- **JUnit 5**: 테스트 프레임워크
- **MockK**: Kotlin 모킹 라이브러리
- **Fixture**: 테스트 데이터 생성

### 보안
- **AES-256-GCM**: 암호화/복호화
- **Bean Validation**: 입력 검증

### 코드 품질
- **ktlint 0.45.2**: Kotlin 코드 스타일 검사

---

## 📋 데이터베이스 스키마

### partner (제휴사)
```sql
CREATE TABLE partner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL
);
```

### partner_fee_policy (수수료 정책)
```sql
CREATE TABLE partner_fee_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    effective_from TIMESTAMP NOT NULL,
    percentage DECIMAL(10, 6) NOT NULL,
    fixed_fee DECIMAL(15, 0),
    FOREIGN KEY (partner_id) REFERENCES partner(id)
);
```

### payment (결제 이력)
```sql
CREATE TABLE payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    amount DECIMAL(15, 0) NOT NULL,
    applied_fee_rate DECIMAL(10, 6) NOT NULL,
    fee_amount DECIMAL(15, 0) NOT NULL,
    net_amount DECIMAL(15, 0) NOT NULL,
    card_bin VARCHAR(8),
    card_last4 VARCHAR(4),
    approval_code VARCHAR(32) NOT NULL,
    approved_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (partner_id) REFERENCES partner(id)
);

CREATE INDEX idx_payment_created_at_id ON payment(created_at DESC, id DESC);
CREATE INDEX idx_payment_partner_created ON payment(partner_id, created_at DESC);
```

---

## 🔐 보안 및 개인정보 처리

### 민감정보 최소 저장
- ✅ 카드번호 전체 저장 **금지**
- ✅ `cardBin` (앞 6-8자리) + `cardLast4` (뒤 4자리)만 저장
- ✅ 생년월일, 비밀번호는 저장 **안 함**

### 암호화 전송
- ✅ AES-256-GCM으로 클라이언트에서 암호화
- ✅ 서버에서 복호화 및 검증
- ✅ AEAD 인증으로 변조 감지

### 로깅 배제
- ✅ 민감정보는 로그에 출력 **금지**
- ✅ PG 응답에서 민감정보 필터링

---

## 🎓 학습 포인트

### 헥사고널 아키텍처
- **포트-어댑터 패턴**: 비즈니스 로직과 기술 구현 분리
- **의존 역전**: Application이 포트 정의, Infrastructure가 구현
- **테스트 용이성**: 외부 의존성을 쉽게 모킹

### 도메인 주도 설계
- **순수 도메인 모델**: 프레임워크 의존 없음
- **유비쿼터스 언어**: Payment, Partner, FeePolicy
- **도메인 로직 집중**: FeeCalculator

### 테스트 전략
- **단위 테스트**: 비즈니스 로직 격리 테스트
- **통합 테스트**: API 엔드포인트 검증
- **엣지 케이스**: null, 잘못된 형식, 경계값

### 보안
- **민감정보 보호**: 최소 저장 원칙
- **암호화**: AES-256-GCM (AEAD)
- **입력 검증**: Bean Validation

---

## 📞 문의

본 프로젝트는 백엔드 사전 과제로 작성되었습니다.

**작성자**: Mingi Kim (migni4575@gmail.com)
**제출일**: 2025-10-27

---

## 📄 라이선스

본 프로젝트는 채용 과제 목적으로 작성되었습니다.
