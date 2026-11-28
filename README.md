# 배달한끼

동네 음식점의 메뉴를 고르고 간편하게 주문할 수 있는 음식 배달 앱

동네 음식점 한 곳의 메뉴를 주문하고 사장님이 접수·배달 상태를 관리하는 Spring Boot REST API입니다.

## 실행

Java **JDK 21**을 설치한 뒤 실행합니다. Gradle Wrapper가 의존성과 Gradle을 내려받습니다.

```bash
./gradlew test
```

로컬 실행은 MySQL에 `baedal_hankki` 데이터베이스와 해당 데이터베이스에 접근할 계정을 준비한 뒤 `local` 프로필을 사용합니다. PowerShell 실행 예시는 다음과 같습니다.

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/baedal_hankki'
$env:DB_USERNAME = 'baedal'
$env:DB_PASSWORD = '설정한 비밀번호'
./gradlew.bat bootRun --args='--spring.profiles.active=local'
```

`DB_URL`의 기본값은 `jdbc:mysql://localhost:3306/baedal_hankki`입니다. `DB_USERNAME`과 `DB_PASSWORD`는 필수 환경변수입니다. 로컬에서는 Hibernate가 테이블을 생성·갱신합니다.

공통 설정은 `application.yml`, 로컬 MySQL 설정은 `application-local.yml`에 있습니다. 테스트는 `src/test/resources/application-test.yml`과 테스트 클래스의 `@ActiveProfiles("test")`를 통해 H2 메모리 DB를 사용합니다. 테스트 실행에는 MySQL과 접속 환경변수가 필요하지 않습니다.

데모 데이터가 필요하면 `./gradlew.bat bootRun --args='--spring.profiles.active=local,demo'`로 실행합니다. `demo` 프로필은 고객·사장님과 음식점 1곳, 메뉴 4개를 등록합니다.

| 역할 | 아이디 | 비밀번호 |
| --- | --- | --- |
| 고객 | customer01 | customer123! |
| 사장님 | owner01 | owner123! |

일반 프로필은 데모 데이터를 등록하지 않습니다. 사장님 주문 API는 인증 회원 ID와 음식점의 `owner_id`를 연결해 운영 음식점을 조회합니다. 현재 사장님 계정당 음식점 한 곳을 연결하며, 음식점은 여러 곳 등록할 수 있습니다.

기존 DB에 저장된 음식점에는 `owner_id`가 비어 있을 수 있습니다. 실제 운영 사장님의 회원 ID로 값을 지정해야 사장님 API를 사용할 수 있습니다. 연결 전에는 404를 반환합니다. 새 데모 DB는 `owner01` 계정과 음식점을 함께 연결합니다.

## 사용 예시

```bash
curl -c customer.cookies -H 'Content-Type: application/json' \
  -d '{"loginId":"customer01","password":"customer123!"}' \
  http://localhost:8080/api/auth/login

curl -b customer.cookies http://localhost:8080/api/restaurants/1/menus

curl -b customer.cookies -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: example-purchase-uuid' \
  -d '{"restaurantId":1,"items":[{"menuId":1,"quantity":2},{"menuId":2,"quantity":1}],"deliveryAddress":"서울시 마포구 한끼로 12","addressDetail":"101동 202호","contactPhone":"01012345678"}' \
  http://localhost:8080/api/orders
```

## API

주문 생성에는 구매 시도마다 생성한 `Idempotency-Key` 헤더(영문·숫자·밑줄·하이픈 8~64자)가 필요합니다. 같은 구매를 재시도할 때는 같은 키를 유지하고, 새 구매에는 새 키를 사용합니다. 같은 키로 다른 내용을 보내면 409입니다.

[환불 정책](docs/refund-policy.md)과 [환불 API·자동 복구 실행 안내](docs/refund-implementation.md)를 참고하세요.

온라인 결제는 토스페이먼츠 테스트 개별 연동 키를 사용합니다. [결제 API·환경 설정·DB 설계](docs/payment.md)를 참고하세요. 결제창 프론트엔드 연결과 토스 실제 테스트 결제 검증은 별도로 필요합니다.

| 메서드 | 경로 | 권한 |
| --- | --- | --- |
| POST | /api/auth/signup | 비로그인 |
| POST | /api/auth/login | 비로그인 |
| GET | /api/members/me | 고객·사장님 |
| GET | /api/restaurants/{id} | 고객·사장님 |
| GET | /api/restaurants/{id}/menus | 고객·사장님 |
| POST | /api/orders | 고객 |
| GET | /api/orders | 고객 |
| GET | /api/orders/{id} | 고객 본인 주문 |
| PATCH | /api/orders/{id}/cancel | 고객 본인 주문 |
| POST | /api/orders/{id}/payments | 고객 본인 주문 |
| POST | /api/orders/{id}/payments/confirm | 고객 본인 주문 |
| GET | /api/orders/{id}/payments | 고객 본인 주문 |
| POST / GET | /api/orders/{id}/refunds | 고객 본인 주문 |
| POST / GET | /api/owner/orders/{id}/refunds | 운영 음식점 사장님 |
| GET | /api/owner/refund-alerts | 운영 음식점 사장님 |
| GET | /api/admin/refunds | 운영자 |
| POST | /api/admin/refunds/{id}/approve, reject, reconcile | 운영자 |
| POST | /api/admin/refunds/{id}/confirm-cash-return | 운영자 |
| GET | /api/admin/refund-alerts | 운영자 |
| POST | /api/admin/refund-alerts/{id}/acknowledge | 운영자 |
| POST | /api/payments/webhooks/toss | 서명 검증 |
| GET | /api/owner/orders | 사장님 |
| GET | /api/owner/orders/{id} | 사장님 |
| PATCH | /api/owner/orders/{id}/accept | 사장님 |
| PATCH | /api/owner/orders/{id}/dispatch | 사장님 |
| PATCH | /api/owner/orders/{id}/complete | 사장님 |

가격·배달비·합계는 서버에서 계산합니다. 주문 당시 메뉴명·단가·주소·연락처를 보존합니다. 취소는 결제 대기 또는 접수 대기에서 가능하며, 승인 확인 중에는 결과 확정 후 취소합니다. 완료·취소 상태는 최종 상태입니다.

현재 결과물은 백엔드 API입니다. 모바일 화면은 포함하지 않습니다. 주문 삭제는 취소 상태로 처리합니다.
