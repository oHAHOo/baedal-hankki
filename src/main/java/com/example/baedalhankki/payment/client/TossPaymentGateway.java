package com.example.baedalhankki.payment.client;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class TossPaymentGateway implements PaymentGateway {
    private final String secretKey;
    private final String clientKey;
    private final RestClient client;

    @Autowired
    public TossPaymentGateway(
            @Value("${app.payment.toss.secret-key:}") String secretKey,
            @Value("${app.payment.toss.client-key:}") String clientKey,
            RestClient.Builder builder) {
        this(secretKey, clientKey, builder, "https://api.tosspayments.com");
    }

    TossPaymentGateway(String secretKey, String clientKey, RestClient.Builder builder, String baseUrl) {
        this.secretKey = secretKey;
        this.clientKey = clientKey;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(secretKey, ""))
                .build();
    }

    @Override
    public void checkAvailable() {
        // 테스트 개별 연동 키만 허용해 실제 청구와 지원하지 않는 조회 방식 사용을 방지한다.
        if (!secretKey.startsWith("test_sk_") || !clientKey.startsWith("test_ck_")) {
            throw new ApiException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
    }

    @Override
    public String getClientKey() {
        checkAvailable();
        return clientKey;
    }

    @Override
    public Optional<ProviderPayment> find(String providerOrderId) {
        checkAvailable();
        try {
            return Optional.ofNullable(client.get().uri("/v1/payments/orders/{orderId}", providerOrderId)
                    .retrieve().body(ProviderPayment.class));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        } catch (ResourceAccessException exception) {
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        }
    }

    @Override
    public ProviderPayment confirm(String paymentKey, String providerOrderId, int amount, String idempotencyKey) {
        return post("/v1/payments/confirm", Map.of(
                "paymentKey", paymentKey, "orderId", providerOrderId, "amount", amount), idempotencyKey);
    }

    @Override
    public ProviderPayment cancel(String paymentKey, String idempotencyKey) {
        return post("/v1/payments/{paymentKey}/cancel", Map.of("cancelReason", "고객의 접수 전 주문 취소"),
                idempotencyKey, paymentKey);
    }

    private ProviderPayment post(String path, Map<String, Object> body, String idempotencyKey, Object... variables) {
        checkAvailable();
        try {
            return client.post().uri(path, variables)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(ProviderPayment.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError() && exception.getStatusCode().value() != 429) {
                throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
            }
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        } catch (ResourceAccessException exception) {
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        }
    }
}
