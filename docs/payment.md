# 온라인 결제 API와 DB 설계

## 범위

토스페이먼츠 개별 연동 테스트 키를 사용하는 선결제를 지원한다. 기존 만나서 결제도 유지한다. 온라인 주문은 결제 성공 후 사장님이 접수한다. 결제 취소는 접수 전 전액 취소만 지원한다.

카드 결제창 연동을 기준으로 한다. 가상계좌·부분 취소·정기결제·정산·운영 결제·웹훅·주기적 결과 복구는 포함하지 않는다. 현재 결과물은 백엔드 API이며 결제창을 띄우는 프론트엔드는 별도로 연결해야 한다.

## 환경 설정

로컬 MySQL 실행 환경에 다음 환경변수를 추가한다. IntelliJ로 실행할 때는 실행 구성의 환경변수에도 지정한다.

```powershell
$env:TOSS_CLIENT_KEY = '개발자센터에서 발급한 test_ck_ 키'
$env:TOSS_SECRET_KEY = '개발자센터에서 발급한 test_sk_ 키'
./gradlew.bat bootRun --args='--spring.profiles.active=local'
```

키가 없으면 서버와 기존 API는 실행되지만 결제 준비·승인은 503 PAYMENT_UNAVAILABLE이다. 실제 출금을 방지하기 위해 live 키는 거절한다. orderId 조회를 지원하는 개별 연동 키(test_ck_/test_sk_)만 사용한다. test_gck_/test_gsk_는 지원하지 않는다. 시크릿 키는 서버 환경변수에만 보관한다.

## 주문 생성 변경

GET `/api/restaurants/{id}`는 기존 기본 방식 필드 `paymentMethod: "PAY_ON_DELIVERY"`를 유지하고 선택 가능한 방식 `paymentMethods: ["PAY_ON_DELIVERY", "ONLINE"]`을 추가한다. 내 주문 목록에도 선택한 `paymentMethod`를 추가한다. ONLINE 준비에는 테스트 키 설정이 필요하다.

기존 POST `/api/orders` 요청에 선택 필드 `paymentMethod`를 추가한다. 생략 또는 null은 기존 PAY_ON_DELIVERY이고, ONLINE은 결제 대기 주문을 생성한다. 금액은 메뉴 가격과 수량·배달비로 서버에서 계산한다.

```json
{
  "restaurantId": 1,
  "items": [{"menuId": 1, "quantity": 2}],
  "deliveryAddress": "서울시 마포구 한끼로 12",
  "addressDetail": "101호",
  "contactPhone": "01012345678",
  "paymentMethod": "ONLINE"
}
```

성공 응답은 기존 형태인 201 `{"orderId": 1, "status": "WAITING_PAYMENT"}`이다. 만나서 결제는 PENDING이다. 사장님은 WAITING_PAYMENT와 CANCELING 주문을 접수할 수 없다. 기존 주문 목록에도 이 상태가 나타날 수 있다. 접수 대기 수는 PENDING만 센다.

## 결제 준비

POST `/api/orders/{orderId}/payments`

로그인한 CUSTOMER의 본인 ONLINE 주문만 가능하다. 주문이 WAITING_PAYMENT일 때 준비하며, 같은 주문에 대한 재요청은 같은 결제 식별자를 반환한다. 주문별 결제는 하나다. 이미 승인을 시도했다면 준비 대신 같은 승인 요청을 재시도한다.

200 응답 예시:

```json
{
  "orderId": 1,
  "providerOrderId": "5f45c973-88e7-4a23-87f4-f8ece76f82a5",
  "amount": 21000,
  "orderName": "배달한끼 주문 1",
  "clientKey": "test_ck_..."
}
```

클라이언트는 토스 v2 SDK의 카드 결제창에 clientKey, providerOrderId를 토스의 orderId로, amount를 KRW 금액으로 전달한다. 성공·실패 URL은 프론트엔드 경로다. 성공 리다이렉트만으로 주문을 결제 완료 처리하지 않는다.

## 결제 승인

POST `/api/orders/{orderId}/payments/confirm`

세션 인증이 필요하다. URL의 orderId는 내부 주문 ID이며 본문 orderId는 결제 준비 응답의 providerOrderId이다. 토스 성공 리다이렉트의 paymentKey·orderId·amount를 서버에 전달한다.

```json
{
  "paymentKey": "토스가 발급한 paymentKey",
  "orderId": "5f45c973-88e7-4a23-87f4-f8ece76f82a5",
  "amount": 21000
}
```

200 응답 예시:

```json
{
  "orderId": 1,
  "providerOrderId": "5f45c973-88e7-4a23-87f4-f8ece76f82a5",
  "amount": 21000,
  "status": "PAID"
}
```

서버는 주문 소유자·결제 주문번호·저장 금액을 확인하고 paymentKey를 고정한다. 이후 토스 조회·승인을 호출한다. 응답의 주문번호·paymentKey·KRW 통화·총액·잔액·DONE 상태를 확인한 뒤 결제를 PAID, 주문을 PENDING으로 함께 반영한다. 같은 결제키로 재요청하면 이미 완료된 결과를 반환한다. 다른 결제키로 바꿀 수 없다.

## 결제 조회

GET `/api/orders/{orderId}/payments`

로그인한 CUSTOMER의 본인 결제만 조회하며, 응답은 승인 응답과 같은 필드다. 로컬에 저장된 상태를 반환하고 결제사를 호출하거나 상태를 변경하지 않는다. 준비 전에는 404 PAYMENT_NOT_FOUND이다.

## 주문 취소와 결제 취소

기존 PATCH `/api/orders/{orderId}/cancel`을 사용한다.

* 만나서 결제는 기존 접수 전 취소 규칙을 유지한다.
* 결제 준비 전 또는 READY/FAILED 상태의 온라인 주문은 외부 취소 없이 취소한다.
* CONFIRMING은 결제 결과가 불명확할 수 있으므로 취소하지 않는다. 같은 승인 요청으로 먼저 결과를 확정한다.
* PAID 주문은 주문과 결제를 CANCELING으로 기록한 후 토스 전액 취소를 호출한다.
* 토스 CANCELED와 잔액 0을 확인한 뒤 주문·결제를 CANCELED로 함께 반영한다.
* CANCELING 중 접수는 차단한다. 같은 취소 API를 다시 호출하면 토스 조회 결과로 복구하거나 같은 멱등키로 취소한다.
* 온라인 주문 취소 완료 후 재요청은 CANCELED를 반환한다. 기존 만나서 결제의 중복 취소 동작은 변경하지 않는다.
* 이미 조리·배달·완료된 주문은 409 ORDER_NOT_CANCELABLE이다.

## 상태와 장애 복구

주문 상태:

```text
ONLINE: WAITING_PAYMENT → PENDING → COOKING → DELIVERING → COMPLETED
                          └→ CANCELING → CANCELED
        WAITING_PAYMENT ────→ CANCELING → CANCELED
PAY_ON_DELIVERY: 기존 PENDING 이후 흐름 유지
```

결제 상태:

```text
READY → CONFIRMING → PAID → CANCELING → CANCELED
  └→ CANCELED   └→ FAILED → CANCELED
```

* 외부 호출 전 상태와 paymentKey를 커밋한다. 외부 호출 중에는 DB 트랜잭션·잠금을 유지하지 않는다.
* 모든 결제 상태 변경은 해당 주문의 비관락을 먼저 획득한다. 사장님 접수와 같은 주문 잠금을 공유한다.
* 승인 멱등키는 confirm_{providerOrderId}, 취소는 cancel_{providerOrderId}로 고정한다.
* 타임아웃·5xx·조회 실패는 성공/실패로 단정하지 않고 CONFIRMING/CANCELING을 유지한다. 서버 재시작 후에도 같은 API 요청으로 복구한다.
* 결제사 조회에서 ABORTED·EXPIRED·CANCELED를 확인한 승인 시도는 FAILED로 기록한다. FAILED 주문은 취소하고 새 주문으로 다시 결제한다.
* 결제사 4xx 응답만으로 로컬 상태를 되돌리지 않는다. 같은 승인 요청으로 조회 결과를 확인한다. 조회로도 결과를 확정할 수 없는 건은 운영 확인이 필요하다.
* 자동 재시도·웹훅은 구현하지 않았다. 사용자의 동일 요청 재시도로 복구한다. 토스 멱등키 유효기간이 지난 장기 미확정 건은 수동 확인해야 한다.

## DB

새 테이블 payments는 다음 필드를 갖는다. 생성·수정 시각은 기존 BaseEntity의 UTC 기준을 따른다.

| 컬럼 | 형식 | 제약/의미 |
| --- | --- | --- |
| id | BIGINT | PK, identity |
| order_id | BIGINT | NOT NULL, UNIQUE, 내부 주문 ID |
| provider_order_id | VARCHAR(64) | NOT NULL, UNIQUE, 서버 생성 UUID |
| payment_key | VARCHAR(200) | NULL 허용, UNIQUE, 첫 승인 시 고정 |
| amount | INT | NOT NULL, 서버 계산 주문 총액 |
| status | VARCHAR(20) | READY/CONFIRMING/PAID/FAILED/CANCELING/CANCELED |
| created_at, updated_at | DATETIME | NOT NULL, UTC |

order_id는 기존 도메인 ID 매핑과 같이 스칼라 ID로 저장하며 물리 FK는 추가하지 않는다. orders.payment_method는 ONLINE을 추가하고 orders.status는 WAITING_PAYMENT/CANCELING을 추가한다. 기존 PAY_ON_DELIVERY 데이터는 유지한다. 로컬의 ddl-auto=update가 스키마를 갱신한다. 운영 배포 전에는 실제 스키마·Enum 제약과 마이그레이션을 별도로 검증해야 한다.

## 오류

기존 공통 code/message 응답을 따른다.

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | DTO 검증 또는 결제 주문번호 불일치 |
| 400 | PAYMENT_AMOUNT_MISMATCH | 요청 금액 변조 |
| 401/403 | UNAUTHENTICATED/FORBIDDEN | 미인증 또는 고객 권한 없음 |
| 404 | ORDER_NOT_FOUND | 주문 없음 또는 타인 주문 |
| 404 | PAYMENT_NOT_FOUND | 결제 준비 없음 |
| 409 | PAYMENT_STATE_CONFLICT | 처리 불가 결제 상태 또는 다른 결제키 |
| 409 | ORDER_NOT_CANCELABLE | 접수 후 취소 등 |
| 409 | ORDER_UPDATE_CONFLICT | DB 잠금 충돌 |
| 502 | PAYMENT_PROVIDER_ERROR | 결제사 거절 또는 확정된 만료·실패 |
| 503 | PAYMENT_UNAVAILABLE | 테스트 키 누락 또는 지원하지 않는 키 |
| 503 | PAYMENT_RETRY_REQUIRED | 결과 불명확, 동일 요청으로 결과 재확인 필요 |

## 검증

PaymentIntegrationTest에서 실제 애플리케이션·H2와 대체 결제 게이트웨이로 승인·취소·금액 변조·권한·멱등성·외부 응답 유실·상태 변경 경쟁을 검증한다. 대체 게이트웨이 테스트는 토스 실제 테스트 결제나 MySQL 검증을 대신하지 않는다. 토스 테스트 키로 프론트엔드 결제창 인증 후 승인·취소까지 직접 확인해야 한다.

## 참고

* [토스 코어 API](https://docs.tosspayments.com/reference)
* [카드 결제창 연동](https://docs.tosspayments.com/guides/v2/payment-window/integration)
* [응답 유실과 멱등키 안내](https://docs.tosspayments.com/resources/faq)
