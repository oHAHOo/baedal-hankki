package com.example.baedalhankki.payment.client;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TossPaymentGatewayTest {
    private HttpServer server;
    private TossPaymentGateway gateway;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> idempotencyKey = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void setUp() throws Exception {
        responseStatus = 200;
        responseBody = """
                {"paymentKey":"test_payment","orderId":"order_123456","totalAmount":21000,
                 "balanceAmount":21000,"currency":"KRW","status":"DONE","newProviderField":true}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        gateway = new TossPaymentGateway("test_sk_fixture", "test_ck_fixture", RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 승인_HTTP_요청의_인증과_멱등키와_본문을_확인한다() throws Exception {
        ProviderPayment result = gateway.confirm("test_payment", "order_123456", 21000, "confirm_operation");
        assertEquals("POST", method.get());
        assertEquals("/v1/payments/confirm", path.get());
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                "test_sk_fixture:".getBytes(StandardCharsets.UTF_8)), authorization.get());
        assertEquals("confirm_operation", idempotencyKey.get());
        assertEquals(new ObjectMapper().valueToTree(Map.of("paymentKey", "test_payment", "orderId", "order_123456",
                "amount", 21000)), new ObjectMapper().readTree(body.get()));
        assertEquals("DONE", result.status());
    }

    @Test
    void 결제_조회는_주문번호를_사용하고_404만_빈값으로_반환한다() {
        responseStatus = 404;
        responseBody = "{\"code\":\"NOT_FOUND_PAYMENT\"}";
        assertTrue(gateway.find("order_123456").isEmpty());
        assertEquals("GET", method.get());
        assertEquals("/v1/payments/orders/order_123456", path.get());
        responseStatus = 401;
        assertEquals(ErrorCode.PAYMENT_RETRY_REQUIRED,
                assertThrows(ApiException.class, () -> gateway.find("order_123456")).getErrorCode());
    }

    @Test
    void 취소는_결제키와_고정_멱등키를_보내고_전액_취소한다() throws Exception {
        gateway.cancel("test_payment", "cancel_operation");
        assertEquals("/v1/payments/test_payment/cancel", path.get());
        assertEquals("cancel_operation", idempotencyKey.get());
        assertTrue(new ObjectMapper().readTree(body.get()).has("cancelReason"));
        assertTrue(!new ObjectMapper().readTree(body.get()).has("cancelAmount"));
    }

    @Test
    void 결제사_서버_오류와_거절을_구분한다() {
        responseBody = "{\"code\":\"PROVIDER_ERROR\"}";
        responseStatus = 500;
        assertEquals(ErrorCode.PAYMENT_RETRY_REQUIRED, assertThrows(ApiException.class,
                () -> gateway.confirm("key", "order_123456", 21000, "fixed_key")).getErrorCode());
        responseStatus = 400;
        assertEquals(ErrorCode.PAYMENT_PROVIDER_ERROR, assertThrows(ApiException.class,
                () -> gateway.confirm("key", "order_123456", 21000, "fixed_key")).getErrorCode());
    }

    @Test
    void 운영키와_미설정키는_외부_호출_전에_거절한다() {
        for (String secret : new String[]{"", "live_sk_fixture", "test_gsk_fixture"}) {
            PaymentGateway disabled = new TossPaymentGateway(secret, "test_ck_fixture", RestClient.builder());
            assertEquals(ErrorCode.PAYMENT_UNAVAILABLE,
                    assertThrows(ApiException.class, disabled::checkAvailable).getErrorCode());
        }
    }
}
