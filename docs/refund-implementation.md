# 환불 API와 자동 복구 실행 안내

이 문서는 [환불 정책](refund-policy.md)의 서버 구현을 설명한다. 제품 화면과 외부 알림 채널은 포함하지 않는다. 운영 알림은 DB에 저장하고 가게·운영자 API로 제공하며 이메일·Slack 메시지를 자동 발송하지 않는다.

## 설정

* 기존 TOSS_CLIENT_KEY·TOSS_SECRET_KEY는 테스트 개별 연동 키를 사용한다.
* TOSS_WEBHOOK_SECURITY_KEY에는 같은 상점에서 제공하는 **보안 키**를 설정한다. 결제 승인용 시크릿 키와 다른 설정이다. 원문 UTF-8 키로 HMAC SHA-256을 검증한다.
* POST `/api/payments/webhooks/toss`를 토스의 PAYMENT_STATUS_CHANGED 수신 URL로 등록한다. 공개 수신 경로이지만 서명·전송 시각 검증 없이 작업을 저장하지 않는다.
* 실제 토스 웹훅은 외부에서 접근 가능한 수신 URL과 상점의 웹훅 등록이 필요하다. 현재 작업에서 터널 공개나 토스 계정 설정은 수행하지 않는다.
* REFUND_RECOVERY_ENABLED 기본값은 true다. 테스트 프로파일에서는 false이며 테스트가 워커를 직접 실행한다.

app.refund 설정 기본값:

| 설정 | 값 | 의미 |
| --- | --- | --- |
| poll-interval | 60s | 스케줄러의 한 처리 회차가 끝난 뒤 다음 실행까지 간격 |
| recovery-delay | 60s | 일반 조회 간격, 실패 시 지수 백오프·지터 적용 |
| alert-after | 10m | 미확정 상태에 대한 운영 알림 생성 기준 |
| unpaid-expiry | 30m | 미결제 주문 만료 기준 |
| automatic-retry-limit | 14d | 최초 승인·취소 시작 이후 자동 PG 재호출 한도 |
| lease-duration | 60s | 복구 작업의 서버 간 선점 유효시간 |
| batch-size | 20 | 회차당 처리 상한, PG 호출 제한에 맞춰 조절 |

이 수치는 설정 가능한 초기값이며 입금 완료 보장 시간이 아니다. PG 멱등키 유효기간보다 긴 자동 재호출 한도는 설정하지 못한다. 완료·실패 결제는 외부 변경 대사를 위해 낮은 빈도로 조회한다.

## 고객·가게 환불 요청

POST `/api/orders/{orderId}/refunds`

POST `/api/owner/orders/{orderId}/refunds`

```json
{"reason":"품절로 주문을 제공할 수 없습니다"}
```

reason은 공백이 아닌 500자 이하 문자열이다. 고객은 본인 주문, OWNER는 본인 음식점 주문만 대상으로 한다.

* 고객의 접수 전 요청은 자동 허용한다. 접수 이후 고객 요청은 REQUESTED 문의로 남고 즉시 환불·작업 중단하지 않는다.
* 가게가 사유를 확인해 요청하면 전액 취소를 진행한다. 기존 고객 문의가 있으면 해당 문의에 가게 승인 근거를 남긴다.
* 완료 주문의 보상 환불은 주문 COMPLETED 이력을 유지한다.
* 응답은 202이며 이는 요청 수락이다. status가 COMPLETED일 때만 결제 취소 확인이 완료된 것이다.
* 기존 PATCH `/api/orders/{orderId}/cancel`은 접수 전 고객 취소를 유지한다. CONFIRMING이면 취소 의사를 저장해 자동 후속 처리한다.

응답 예시:

```json
{
  "refundId": 1,
  "orderId": 1,
  "amount": 12000,
  "status": "PROCESSING",
  "reason": "품절로 주문을 제공할 수 없습니다",
  "requestedAt": "2026-11-28T12:00:00Z",
  "resolvedAt": null
}
```

상태는 REQUESTED / PROCESSING / COMPLETED / REJECTED / MANUAL_REVIEW다. GET으로 동일한 고객·가게 경로를 호출하면 해당 주문의 환불 이력을 조회한다. 완료된 전액 환불의 중복 요청은 추가 환불을 만들지 않는다.

## 운영자 처리

ADMIN 역할은 공개 회원가입으로 만들 수 없고 데모 계정으로도 제공하지 않는다. 신뢰할 수 있는 별도 운영 계정 관리 절차로 부여해야 한다. 기존 CUSTOMER·OWNER는 `/api/admin/**`에 접근할 수 없다.

| API | 용도 |
| --- | --- |
| GET /api/admin/refunds | 문의·처리 중·운영 확인 요청, 최대 100건 |
| POST /api/admin/refunds/{id}/approve | 전액 취소 승인, reason에 확인 근거 필수 |
| POST /api/admin/refunds/{id}/reject | 문의 거절, reason에 근거 필수 |
| POST /api/admin/refunds/{id}/reconcile | PG 결과 재확인·안전한 재처리 |
| POST /api/admin/refunds/{id}/confirm-cash-return | 만나서 결제의 현장 반환 확인 근거 기록 |
| GET /api/admin/refund-alerts | 미확인 운영 알림, 최대 100건 |
| POST /api/admin/refund-alerts/{id}/acknowledge | 담당자와 확인 근거 기록 |
| GET /api/owner/refund-alerts | 본인 음식점의 환불·진행 보류 알림, 최대 100건 |

현장 수납은 PG로 확인할 수 없다. 가게·운영자 취소 시 MANUAL_REVIEW로 남기며, 운영자가 수납·반환 또는 미수납 근거를 확인해 confirm-cash-return으로 기록한다. 해당 API는 온라인 결제 취소 확인을 대신할 수 없다. 알림 확인 역시 환불 완료나 가게 작업 재개를 의미하지 않는다.

## 웹훅과 복구 작업

1. 원문 바이트와 전송 시각을 이용해 HMAC SHA-256 서명을 검사한다. 여러 v1 서명을 허용하고 상수 시간 비교를 사용한다. 5분 이상 시각 차이가 나는 요청은 거절한다.
2. PAYMENT_STATUS_CHANGED의 결제사 주문번호와 원문 해시를 수신함에 저장한 뒤 200을 응답한다. 전송 ID를 중복 키로 사용하지 않는다. 불필요한 고객 정보와 원문 전체는 DB에 저장하지 않는다.
3. 워커는 결제 상태를 토스 API로 재조회한다. 웹훅에 적힌 상태만으로 환불 완료하지 않는다.
4. 결제 식별자·금액·통화를 대조하고 주문 비관락으로 상태를 함께 반영한다. 외부 통신은 트랜잭션 밖에서 한다.
5. 웹훅이 없어도 미확정 결제를 조회한다. 승인 성공을 확인하면 PENDING, 취소 의사가 있으면 CANCELING으로 이어서 전액 취소한다.
6. PG 취소 성공이 확인되면 CANCELED와 잔액 0을 검증하고 결제·주문·환불 요청을 함께 완료한다. 가능한 경우 PG 취소 transactionKey를 저장한다.
7. 서버 간 작업 중복은 DB 리스로 제한한다. 선점 만료 후 재선점되면 이전 작업자의 상태 반영·실패 기록을 토큰으로 차단한다. 외부 중복 호출은 같은 PG 멱등키로 제한한다.
8. 결과 불명확·거절·타임아웃은 CANCELING을 유지한다. 재시도 시 최초 처리 시각을 갱신해 유효기간을 연장하지 않는다.

수신함에 남은 미처리 작업과 결제의 nextCheckAt·recoveryAttempts·lastErrorCode는 서버 재시작 이후에도 유지한다. 처음 시작한 시각이 없는 과거 미확정 건과 자동 재호출 기한이 지난 건은 조회와 운영 알림으로만 처리한다.

## 외부 취소와 진행 보류

* 접수 전 결제가 외부에서 전액 취소되면 주문도 취소한다.
* 조리·배달 중 외부 취소는 onHold=true로 진행 API를 차단하고 가게·운영자 알림을 남긴다. 기존 진행 이력을 덮어쓰지 않는다.
* 완료 주문은 COMPLETED를 유지하고 결제 취소와 확인 이력을 기록한다.
* 부분 취소는 전액 취소로 오인하지 않고 작업 보류와 운영 알림으로 처리한다. 부분 취소를 실행하는 API는 제공하지 않는다.
* 대사 조회가 이뤄지기 전 외부 취소를 즉시 알 수 있다고 보장하지 않는다. 외부 관리자 취소는 가게 중단 연락과 함께 운영해야 한다.

주문 목록·상세에 onHold, 결제 조회에 cancellationRequested를 추가했다. 제품 화면은 해당 상태와 환불·알림 API를 연결해야 한다.

## DB 변경과 배포 전 확인

* orders: checkout_key·request_hash·on_hold 및 (customer_id, checkout_key) 유일성 제약
* payments: 취소 의사, 최초 처리 시각, 다음 조회 시각, 리스·재시도·최근 오류 정보
* refund_request: 요청자·역할·사유·실결제액·기존 주문 상태·운영 결정·확정 시각·PG 취소 거래번호
* refund_alert: 주문별 알림·운영자 확인 근거
* webhook_inbox: 검증된 이벤트의 원문 해시·결제사 주문번호·처리·재시도 상태

로컬 ddl-auto=update 외에 운영 DB 마이그레이션은 별도 검증해야 한다. 과거 주문의 checkout_key는 null로 유지할 수 있다. 과거 미확정 결제의 최초 요청 시각을 추정해 채우면 안 된다.

## 검증 범위

자동 테스트는 H2와 대체 PG·로컬 HTTP 서버를 사용한다. 동시 주문 생성, 중복·위조·오래된 웹훅, 취소 응답 유실·워커 복구, 리스 재선점, 외부 환불의 진행 보류, 가게·운영자 결정, 만료 경계, ADMIN 접근 제한을 검증한다.

실제 토스 웹훅 서명 수신·카드 인증·승인·전액 취소, 신규 스키마의 실제 MySQL 경쟁·마이그레이션 검증은 별도로 필요하다. 제품 화면과 실시간 외부 알림 채널은 이 서버 구현의 검증 대상이 아니다.

* [토스 웹훅 검증·재전송](https://docs.tosspayments.com/guides/v2/webhook)
* [토스 웹훅 헤더](https://docs.tosspayments.com/reference/using-api/webhook-events)
