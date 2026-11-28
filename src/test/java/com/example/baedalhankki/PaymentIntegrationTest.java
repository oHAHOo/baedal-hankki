package com.example.baedalhankki;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.member.entity.Member;
import com.example.baedalhankki.member.entity.MemberRole;
import com.example.baedalhankki.member.repository.MemberRepository;
import com.example.baedalhankki.menu.entity.Menu;
import com.example.baedalhankki.menu.repository.MenuRepository;
import com.example.baedalhankki.order.dto.OrderCreateRequestDto;
import com.example.baedalhankki.order.dto.OrderItemCreateRequestDto;
import com.example.baedalhankki.order.entity.OrderStatus;
import com.example.baedalhankki.order.entity.PaymentMethod;
import com.example.baedalhankki.order.repository.OrderRepository;
import com.example.baedalhankki.order.service.OrderService;
import com.example.baedalhankki.payment.client.PaymentGateway;
import com.example.baedalhankki.payment.client.ProviderPayment;
import com.example.baedalhankki.payment.dto.PaymentConfirmRequestDto;
import com.example.baedalhankki.payment.dto.PaymentPrepareResponseDto;
import com.example.baedalhankki.payment.entity.PaymentStatus;
import com.example.baedalhankki.payment.facade.PaymentFacade;
import com.example.baedalhankki.payment.repository.PaymentRepository;
import com.example.baedalhankki.payment.service.PaymentService;
import com.example.baedalhankki.restaurant.entity.Restaurant;
import com.example.baedalhankki.restaurant.repository.RestaurantRepository;
import com.example.baedalhankki.refund.facade.PaymentRecoveryFacade;
import com.example.baedalhankki.refund.facade.RefundFacade;
import com.example.baedalhankki.refund.service.PaymentRecoveryStore;
import com.example.baedalhankki.refund.service.RefundService;
import com.example.baedalhankki.refund.service.TossWebhookService;
import com.example.baedalhankki.refund.service.WebhookInboxService;
import com.example.baedalhankki.refund.repository.WebhookInboxRepository;
import com.example.baedalhankki.refund.repository.RefundAlertRepository;
import com.example.baedalhankki.refund.repository.RefundRequestRepository;
import com.example.baedalhankki.refund.entity.RefundStatus;
import com.example.baedalhankki.payment.client.ProviderCancel;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:payment-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "app.refund.webhook-security-key=test_webhook_security_key"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PaymentIntegrationTest {
    @MockitoBean
    private PaymentGateway gateway;

    private final OrderService orderService;
    private final OrderRepository orders;
    private final MemberRepository members;
    private final RestaurantRepository restaurants;
    private final MenuRepository menus;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final PaymentFacade facade;
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final PasswordEncoder passwords;
    private final PaymentRecoveryFacade recovery;
    private final PaymentRecoveryStore recoveryStore;
    private final RefundFacade refundFacade;
    private final RefundService refundService;
    private final TossWebhookService webhook;
    private final WebhookInboxRepository inbox;
    private final RefundAlertRepository alerts;
    private final RefundRequestRepository refundRequests;
    private final JdbcTemplate jdbc;
    private final WebhookInboxService inboxService;

    private Long customerId;
    private Long ownerId;
    private Long orderId;
    private String loginId;
    private final AtomicReference<ProviderPayment> remote = new AtomicReference<>();

    @Autowired
    PaymentIntegrationTest(OrderService orderService, OrderRepository orders, MemberRepository members,
            RestaurantRepository restaurants, MenuRepository menus, PaymentRepository payments,
            PaymentService paymentService, PaymentFacade facade, MockMvc mvc, ObjectMapper json,
            PasswordEncoder passwords, PaymentRecoveryFacade recovery, PaymentRecoveryStore recoveryStore,
            RefundFacade refundFacade, RefundService refundService, TossWebhookService webhook,
            WebhookInboxRepository inbox, RefundAlertRepository alerts, RefundRequestRepository refundRequests,
            JdbcTemplate jdbc, WebhookInboxService inboxService) {
        this.orderService = orderService;
        this.orders = orders;
        this.members = members;
        this.restaurants = restaurants;
        this.menus = menus;
        this.payments = payments;
        this.paymentService = paymentService;
        this.facade = facade;
        this.mvc = mvc;
        this.json = json;
        this.passwords = passwords;
        this.recovery = recovery;
        this.recoveryStore = recoveryStore;
        this.refundFacade = refundFacade;
        this.refundService = refundService;
        this.webhook = webhook;
        this.inbox = inbox;
        this.alerts = alerts;
        this.refundRequests = refundRequests;
        this.jdbc = jdbc;
        this.inboxService = inboxService;
    }

    @BeforeEach
    void setUp() {
        loginId = UUID.randomUUID().toString();
        customerId = members.save(Member.create(loginId, passwords.encode("password123"),
                "결제 고객", MemberRole.CUSTOMER)).getId();
        Member owner = members.save(Member.create(UUID.randomUUID().toString(), "unused", "사장님", MemberRole.OWNER));
        ownerId = owner.getId();
        Restaurant restaurant = restaurants.save(Restaurant.create("결제 식당", 3000, owner));
        Menu menu = menus.save(Menu.create(restaurant.getId(), "덮밥", 9000));
        orderId = orderService.createOrder(customerId, new OrderCreateRequestDto(restaurant.getId(),
                List.of(new OrderItemCreateRequestDto(menu.getId(), 2)), "서울시", "101호", "01012345678",
                PaymentMethod.ONLINE)).orderId();
        remote.set(null);
        when(gateway.getClientKey()).thenReturn("test_ck_example");
        when(gateway.find(anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.ofNullable(remote.get());
        });
        when(gateway.confirm(anyString(), anyString(), anyInt(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            ProviderPayment result = new ProviderPayment(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(2), "KRW", "DONE");
            remote.set(result);
            return result;
        });
        when(gateway.cancel(anyString(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            ProviderPayment previous = remote.get();
            ProviderPayment result = new ProviderPayment(previous.paymentKey(), previous.orderId(),
                    previous.totalAmount(), 0, "KRW", "CANCELED");
            remote.set(result);
            return result;
        });
    }

    @Test
    void 결제_대기_주문은_접수할_수_없다() {
        assertEquals(OrderStatus.WAITING_PAYMENT, orders.findById(orderId).orElseThrow().getStatus());
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.acceptOrder(orderId, ownerId));
    }

    @Test
    void 결제_준비는_서버_금액과_동일한_주문번호를_반환한다() {
        PaymentPrepareResponseDto first = facade.prepare(orderId, customerId);
        PaymentPrepareResponseDto second = facade.prepare(orderId, customerId);
        assertEquals(21000, first.amount());
        assertEquals(first, second);
        assertEquals(1, payments.findAll().stream().filter(payment -> payment.getOrderId().equals(orderId)).count());
    }

    @Test
    void 금액과_결제_주문번호_변조를_차단한다() {
        PaymentPrepareResponseDto prepared = facade.prepare(orderId, customerId);
        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> facade.confirm(orderId, customerId,
                new PaymentConfirmRequestDto("key", prepared.providerOrderId(), 1)));
        assertError(ErrorCode.INVALID_REQUEST, () -> facade.confirm(orderId, customerId,
                new PaymentConfirmRequestDto("key", "wrong_order_id", prepared.amount())));
        verify(gateway, never()).confirm(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 승인_재요청은_중복_청구하지_않고_접수가_가능해진다() {
        PaymentConfirmRequestDto request = prepare();
        assertEquals(PaymentStatus.PAID, facade.confirm(orderId, customerId, request).status());
        assertEquals(PaymentStatus.PAID, facade.confirm(orderId, customerId, request).status());
        verify(gateway, times(1)).confirm(anyString(), anyString(), anyInt(), anyString());
        assertEquals(OrderStatus.PENDING, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(OrderStatus.COOKING, orderService.acceptOrder(orderId, ownerId).status());
        assertError(ErrorCode.ORDER_NOT_CANCELABLE, () -> facade.cancelOrder(orderId, customerId));
    }

    @Test
    void 승인된_주문은_전액_취소하고_재요청해도_다시_취소하지_않는다() {
        facade.confirm(orderId, customerId, prepare());
        assertEquals(OrderStatus.CANCELED, facade.cancelOrder(orderId, customerId).status());
        assertEquals(OrderStatus.CANCELED, facade.cancelOrder(orderId, customerId).status());
        assertEquals(PaymentStatus.CANCELED, paymentService.getPayment(orderId, customerId).status());
        verify(gateway, times(1)).cancel(anyString(), anyString());
    }

    @Test
    void 미결제_주문은_외부_취소_없이_취소한다() {
        PaymentConfirmRequestDto request = prepare();
        assertEquals(OrderStatus.CANCELED, facade.cancelOrder(orderId, customerId).status());
        verify(gateway, never()).cancel(anyString(), anyString());
        assertError(ErrorCode.PAYMENT_STATE_CONFLICT, () -> facade.confirm(orderId, customerId,
                request));
    }

    @Test
    void 결제_준비_전에도_주문을_취소할_수_있다() {
        assertEquals(OrderStatus.CANCELED, facade.cancelOrder(orderId, customerId).status());
        assertTrue(payments.findByOrderId(orderId).isEmpty());
    }

    @Test
    void 승인_응답이_유실되면_조회로_복구한다() {
        PaymentConfirmRequestDto request = prepare();
        doAnswer(invocation -> {
            remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(),
                    request.amount(), "KRW", "DONE"));
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        }).when(gateway).confirm(anyString(), anyString(), anyInt(), anyString());
        assertError(ErrorCode.PAYMENT_RETRY_REQUIRED, () -> facade.confirm(orderId, customerId, request));
        assertEquals(PaymentStatus.CONFIRMING, paymentService.getPayment(orderId, customerId).status());
        assertEquals(PaymentStatus.PAID, facade.confirm(orderId, customerId, request).status());
        verify(gateway, times(1)).confirm(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 취소_응답이_유실되면_접수를_차단하고_조회로_복구한다() {
        facade.confirm(orderId, customerId, prepare());
        doAnswer(invocation -> {
            ProviderPayment previous = remote.get();
            remote.set(new ProviderPayment(previous.paymentKey(), previous.orderId(), previous.totalAmount(),
                    0, "KRW", "CANCELED"));
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        }).when(gateway).cancel(anyString(), anyString());
        assertError(ErrorCode.PAYMENT_RETRY_REQUIRED, () -> facade.cancelOrder(orderId, customerId));
        assertEquals(OrderStatus.CANCELING, orders.findById(orderId).orElseThrow().getStatus());
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.acceptOrder(orderId, ownerId));
        assertEquals(OrderStatus.CANCELED, facade.cancelOrder(orderId, customerId).status());
        verify(gateway, times(1)).cancel(anyString(), anyString());
    }

    @Test
    void 결제사_응답의_금액이나_주문이_다르면_승인하지_않는다() {
        PaymentConfirmRequestDto request = prepare();
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), 1, 1, "KRW", "DONE"));
        assertError(ErrorCode.PAYMENT_RETRY_REQUIRED, () -> facade.confirm(orderId, customerId, request));
        assertEquals(OrderStatus.WAITING_PAYMENT, orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void 만료된_결제는_실패로_기록하고_주문_취소가_가능하다() {
        PaymentConfirmRequestDto request = prepare();
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(),
                request.amount(), "KRW", "EXPIRED"));
        assertError(ErrorCode.PAYMENT_PROVIDER_ERROR, () -> facade.confirm(orderId, customerId, request));
        assertEquals(PaymentStatus.FAILED, paymentService.getPayment(orderId, customerId).status());
        assertEquals(OrderStatus.CANCELED, facade.cancelOrder(orderId, customerId).status());
    }

    @Test
    void 승인_처리_중_취소_의사를_저장하고_승인_후_취소한다() throws Exception {
        PaymentConfirmRequestDto request = prepare();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            ProviderPayment result = new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(),
                    request.amount(), "KRW", "DONE");
            remote.set(result);
            return result;
        }).when(gateway).confirm(anyString(), anyString(), anyInt(), anyString());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> facade.confirm(orderId, customerId, request));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(OrderStatus.CANCELING, facade.cancelOrder(orderId, customerId).status());
                assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.acceptOrder(orderId, ownerId));
                assertError(ErrorCode.PAYMENT_STATE_CONFLICT, () -> facade.confirm(orderId, customerId,
                        new PaymentConfirmRequestDto("different_key", request.orderId(), request.amount())));
            } finally {
                release.countDown();
            }
            assertEquals(PaymentStatus.CANCELED, future.get(5, TimeUnit.SECONDS).status());
        }
    }

    @Test
    void 타인_주문의_결제_준비와_조회와_승인을_차단한다() {
        PaymentConfirmRequestDto request = prepare();
        Long otherId = members.save(Member.create(UUID.randomUUID().toString(), "unused", "다른 고객",
                MemberRole.CUSTOMER)).getId();
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> facade.prepare(orderId, otherId));
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> paymentService.getPayment(orderId, otherId));
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> facade.confirm(orderId, otherId, request));
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> facade.cancelOrder(orderId, otherId));
    }

    @Test
    void 결제_API의_인증과_입력과_응답을_검증한다() throws Exception {
        mvc.perform(post("/api/orders/{id}/payments", orderId)).andExpect(status().isUnauthorized());
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("loginId", loginId, "password", "password123"))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        assertNotNull(session);
        Long restaurantId = orders.findById(orderId).orElseThrow().getRestaurantId();
        mvc.perform(get("/api/restaurants/{id}", restaurantId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentMethods[1]").value("ONLINE"));
        mvc.perform(get("/api/orders").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orders[0].paymentMethod").value("ONLINE"));
        mvc.perform(post("/api/orders/{id}/payments", orderId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(21000))
                .andExpect(jsonPath("$.clientKey").value("test_ck_example"))
                .andExpect(jsonPath("$.secretKey").doesNotExist());
        mvc.perform(post("/api/orders/{id}/payments/confirm", orderId).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        PaymentPrepareResponseDto prepared = facade.prepare(orderId, customerId);
        mvc.perform(post("/api/orders/{id}/payments/confirm", orderId).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                                new PaymentConfirmRequestDto("api_key", prepared.providerOrderId(), prepared.amount()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get("/api/orders/{id}/payments", orderId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(patch("/api/orders/{id}/cancel", orderId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void 같은_승인_동시_요청은_같은_멱등키를_사용한다() throws Exception {
        PaymentConfirmRequestDto request = prepare();
        CountDownLatch bothEntered = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothEntered.countDown();
            assertTrue(bothEntered.await(5, TimeUnit.SECONDS));
            assertEquals("confirm_" + request.orderId(), invocation.getArgument(3));
            return new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(),
                    request.amount(), "KRW", "DONE");
        }).when(gateway).confirm(anyString(), anyString(), anyInt(), anyString());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> facade.confirm(orderId, customerId, request));
            var second = executor.submit(() -> facade.confirm(orderId, customerId, request));
            assertEquals(PaymentStatus.PAID, first.get(10, TimeUnit.SECONDS).status());
            assertEquals(PaymentStatus.PAID, second.get(10, TimeUnit.SECONDS).status());
        }
        assertEquals(OrderStatus.PENDING, orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void 취소_처리_중에는_접수를_차단한다() throws Exception {
        facade.confirm(orderId, customerId, prepare());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            ProviderPayment previous = remote.get();
            return new ProviderPayment(previous.paymentKey(), previous.orderId(), previous.totalAmount(),
                    0, "KRW", "CANCELED");
        }).when(gateway).cancel(anyString(), anyString());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> facade.cancelOrder(orderId, customerId));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.acceptOrder(orderId, ownerId));
            } finally {
                release.countDown();
            }
            assertEquals(OrderStatus.CANCELED, future.get(5, TimeUnit.SECONDS).status());
        }
    }

    @Test
    void 주문_생성_멱등키는_동시_요청을_하나로_만든다() throws Exception {
        var original = orders.findById(orderId).orElseThrow();
        var menu = menus.findByRestaurantIdAndIsActiveTrueOrderByIdAsc(original.getRestaurantId()).getFirst();
        var request = new OrderCreateRequestDto(original.getRestaurantId(),
                List.of(new OrderItemCreateRequestDto(menu.getId(), 1)), "서울시", "101호", "01012345678", PaymentMethod.ONLINE);
        String key = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> executor.submit(() -> orderService.createOrder(customerId, request, key))).toList();
            Long createdId = futures.getFirst().get(10, TimeUnit.SECONDS).orderId();
            for (var future : futures) {
                assertEquals(createdId, future.get(10, TimeUnit.SECONDS).orderId());
            }
            var changed = new OrderCreateRequestDto(request.restaurantId(), request.items(),
                    "다른 주소", "101호", "01012345678", PaymentMethod.ONLINE);
            assertError(ErrorCode.IDEMPOTENCY_CONFLICT, () -> orderService.createOrder(customerId, changed, key));
        }
    }

    @Test
    void 고객_재요청_없이_승인_성공을_복구한다() {
        var request = prepare();
        paymentService.beginConfirmation(orderId, customerId, request);
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(), request.amount(), "KRW", "DONE"));
        assertTrue(recovery.reconcile(orderId));
        assertEquals(PaymentStatus.PAID, payments.findByOrderId(orderId).orElseThrow().getStatus());
        assertEquals(OrderStatus.PENDING, orders.findById(orderId).orElseThrow().getStatus());
        verify(gateway, never()).confirm(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 승인_중_저장된_취소_의사는_워커가_승인_확인_후_환불한다() {
        var request = prepare();
        paymentService.beginConfirmation(orderId, customerId, request);
        assertEquals(OrderStatus.CANCELING, facade.cancelOrder(orderId, customerId).status());
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(), request.amount(), "KRW", "DONE"));
        assertTrue(recovery.reconcile(orderId));
        assertEquals(PaymentStatus.CANCELED, payments.findByOrderId(orderId).orElseThrow().getStatus());
        assertEquals(OrderStatus.CANCELED, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(RefundStatus.COMPLETED, refundRequests.findFirstByOrderIdOrderByIdDesc(orderId).orElseThrow().getStatus());
    }

    @Test
    void 환불_성공_후_서버_반영이_유실돼도_워커가_추가_환불_없이_복구한다() {
        var request = prepare();
        facade.confirm(orderId, customerId, request);
        paymentService.beginCancellation(orderId, customerId);
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(), 0, "KRW", "CANCELED",
                List.of(new ProviderCancel("cancel_transaction_" + orderId))));
        assertTrue(recovery.reconcile(orderId));
        assertEquals(OrderStatus.CANCELED, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals("cancel_transaction_" + orderId,
                refundRequests.findFirstByOrderIdOrderByIdDesc(orderId).orElseThrow().getProviderTransactionKey());
        verify(gateway, never()).cancel(anyString(), anyString());
    }

    @Test
    void 외부_환불된_조리_주문은_진행을_보류하고_가게에_알린다() {
        var request = prepare();
        facade.confirm(orderId, customerId, request);
        orderService.acceptOrder(orderId, ownerId);
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(), 0, "KRW", "CANCELED"));
        assertTrue(recovery.reconcile(orderId));
        assertTrue(orders.findById(orderId).orElseThrow().isOnHold());
        assertEquals(OrderStatus.COOKING, orders.findById(orderId).orElseThrow().getStatus());
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.dispatchOrder(orderId, ownerId));
        assertTrue(refundService.getAlerts(ownerId).stream().anyMatch(alert -> alert.orderId().equals(orderId)));
        assertEquals(0, refundService.getAlerts(-1L).size());
    }

    @Test
    void 완료된_주문이_외부_환불돼도_배달_완료_이력을_유지한다() {
        var request = prepare();
        facade.confirm(orderId, customerId, request);
        orderService.acceptOrder(orderId, ownerId);
        orderService.dispatchOrder(orderId, ownerId);
        orderService.completeOrder(orderId, ownerId);
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(), 0, "KRW", "CANCELED"));
        assertTrue(recovery.reconcile(orderId));
        assertEquals(OrderStatus.COMPLETED, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.CANCELED, payments.findByOrderId(orderId).orElseThrow().getStatus());
    }

    @Test
    void 부분_환불은_전액_취소로_처리하지_않고_운영_확인한다() {
        var request = prepare();
        facade.confirm(orderId, customerId, request);
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(), 9000, "KRW", "PARTIAL_CANCELED"));
        assertTrue(recovery.reconcile(orderId));
        assertTrue(orders.findById(orderId).orElseThrow().isOnHold());
        assertTrue(alerts.existsByOrderIdAndCode(orderId, "PARTIAL_REFUND_REVIEW"));
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.acceptOrder(orderId, ownerId));
        verify(gateway, never()).cancel(anyString(), anyString());
    }

    @Test
    void 가게는_조리_이후에도_사유를_기록하고_전액_취소할_수_있다() {
        facade.confirm(orderId, customerId, prepare());
        orderService.acceptOrder(orderId, ownerId);
        var refund = refundFacade.request(orderId, ownerId, true, "품절로 제공 불가");
        assertEquals(RefundStatus.COMPLETED, refund.status());
        assertEquals(OrderStatus.CANCELED, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(OrderStatus.COOKING, refundRequests.findById(refund.refundId()).orElseThrow().getPreviousOrderStatus());
    }

    @Test
    void 접수_이후_고객_문의는_운영자_승인_전에는_환불하지_않는다() {
        facade.confirm(orderId, customerId, prepare());
        orderService.acceptOrder(orderId, ownerId);
        var inquiry = refundFacade.request(orderId, customerId, false, "음식 제공 불가 문의");
        assertEquals(RefundStatus.REQUESTED, inquiry.status());
        assertEquals(OrderStatus.COOKING, orders.findById(orderId).orElseThrow().getStatus());
        verify(gateway, never()).cancel(anyString(), anyString());
        Long adminId = members.save(Member.create(UUID.randomUUID().toString(), "unused", "운영자", MemberRole.ADMIN)).getId();
        var approved = refundFacade.decide(inquiry.refundId(), adminId, "가게 확인 완료, 전액 환불 승인", true);
        assertEquals(RefundStatus.COMPLETED, approved.status());
        assertEquals(adminId, refundRequests.findById(inquiry.refundId()).orElseThrow().getDecidedBy());
    }

    @Test
    void 운영자가_거절한_문의는_결제와_주문을_변경하지_않는다() {
        facade.confirm(orderId, customerId, prepare());
        orderService.acceptOrder(orderId, ownerId);
        var inquiry = refundService.request(orderId, customerId, false, "취소 문의");
        assertEquals(RefundStatus.REJECTED, refundService.decide(inquiry.refundId(), 1L, "사유 확인 후 거절", false).status());
        assertEquals(PaymentStatus.PAID, payments.findByOrderId(orderId).orElseThrow().getStatus());
        assertEquals(OrderStatus.COOKING, orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void 두_워커의_중복_선점을_막고_만료된_작업은_재선점한다() {
        prepare();
        var first = recoveryStore.claim(orderId);
        assertNotNull(first);
        assertNull(recoveryStore.claim(orderId));
        jdbc.update("update payments set lease_until = ? where order_id = ?", Instant.now().minusSeconds(1), orderId);
        var second = recoveryStore.claim(orderId);
        assertNotNull(second);
        assertFalse(recoveryStore.apply(first, new ProviderPayment("stale", "stale_order", 1, 0, "KRW", "CANCELED")));
        assertEquals(PaymentStatus.READY, payments.findByOrderId(orderId).orElseThrow().getStatus());
        recoveryStore.finish(first, "STALE_WORKER");
        assertEquals(second.token(), payments.findByOrderId(orderId).orElseThrow().getLeaseToken());
        recoveryStore.finish(second, null);
        assertNull(payments.findByOrderId(orderId).orElseThrow().getLeaseToken());
    }

    @Test
    void 장기_미확정은_알림을_남기고_유효기간_지난_승인을_재호출하지_않는다() {
        var request = prepare();
        paymentService.beginConfirmation(orderId, customerId, request);
        Instant old = Instant.now().minus(Duration.ofDays(16));
        jdbc.update("update payments set confirmation_started_at = ?, operation_started_at = ? where order_id = ?", old, old, orderId);
        assertFalse(recovery.reconcile(orderId));
        verify(gateway, never()).confirm(anyString(), anyString(), anyInt(), anyString());
        assertTrue(alerts.existsByOrderIdAndCode(orderId, "LONG_PENDING_PAYMENT"));
        assertEquals(PaymentStatus.CONFIRMING, payments.findByOrderId(orderId).orElseThrow().getStatus());
    }

    @Test
    void 유효시간이_지난_미결제만_자동_취소한다() {
        prepare();
        jdbc.update("update payments set prepared_at = ? where order_id = ?", Instant.now().minus(Duration.ofHours(1)), orderId);
        assertTrue(recovery.reconcile(orderId));
        assertEquals(OrderStatus.CANCELED, orders.findById(orderId).orElseThrow().getStatus());
        verify(gateway, never()).confirm(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 웹훅은_원문_서명_검증_후_DB에_저장하며_중복_수신을_허용한다() throws Exception {
        var prepared = facade.prepare(orderId, customerId);
        byte[] raw = json.writeValueAsBytes(Map.of("eventType", "PAYMENT_STATUS_CHANGED", "data", Map.of("orderId", prepared.providerOrderId())));
        String timestamp = Instant.now().toString();
        String signature = sign(raw, timestamp);
        webhook.receive(raw, timestamp, "v1:badbase64," + signature);
        webhook.receive(raw, timestamp, signature);
        assertEquals(1, inbox.findAll().stream().filter(event -> event.getProviderOrderId().equals(prepared.providerOrderId())).count());
        verify(gateway, never()).find(anyString());
    }

    @Test
    void 위조되거나_변조되거나_오래된_웹훅은_거절한다() throws Exception {
        var prepared = facade.prepare(orderId, customerId);
        byte[] raw = json.writeValueAsBytes(Map.of("eventType", "PAYMENT_STATUS_CHANGED", "data", Map.of("orderId", prepared.providerOrderId())));
        String timestamp = Instant.now().toString();
        String signature = sign(raw, timestamp);
        assertError(ErrorCode.INVALID_WEBHOOK, () -> webhook.receive(raw, timestamp, "v1:AAAA"));
        assertError(ErrorCode.INVALID_WEBHOOK, () -> webhook.receive("{}".getBytes(StandardCharsets.UTF_8), timestamp, signature));
        String stale = Instant.now().minusSeconds(600).toString();
        String staleSignature = sign(raw, stale);
        assertError(ErrorCode.INVALID_WEBHOOK, () -> webhook.receive(raw, stale, staleSignature));
    }

    @Test
    void 고객이_운영자_API로_환불을_승인할_수_없다() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("loginId", loginId, "password", "password123"))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(post("/api/admin/refunds/1/approve").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"권한 우회\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 시작_시각을_모르는_과거_승인은_새_시도로_간주하지_않는다() {
        var request = prepare();
        paymentService.beginConfirmation(orderId, customerId, request);
        jdbc.update("update payments set confirmation_started_at = null, operation_started_at = null where order_id = ?", orderId);
        assertError(ErrorCode.PAYMENT_RETRY_REQUIRED, () -> facade.confirm(orderId, customerId, request));
        assertFalse(recovery.reconcile(orderId));
        assertTrue(alerts.existsByOrderIdAndCode(orderId, "LEGACY_PENDING_PAYMENT_REVIEW"));
        verify(gateway, never()).confirm(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 웹훅_처리와_주기적_조회는_같은_상태_복구를_사용한다() throws Exception {
        var request = prepare();
        facade.confirm(orderId, customerId, request);
        paymentService.beginCancellation(orderId, customerId);
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(), 0, "KRW", "CANCELED"));
        byte[] raw = json.writeValueAsBytes(Map.of("eventType", "PAYMENT_STATUS_CHANGED", "data", Map.of("orderId", request.orderId())));
        String timestamp = Instant.now().toString();
        webhook.receive(raw, timestamp, sign(raw, timestamp));
        // 실제 워커의 다음 회차를 재현하고, 수신 직후의 시각·첫 배치 포함 여부에 의존하지 않는다.
        jdbc.update("update webhook_inbox set next_attempt_at = ? where provider_order_id = ?", Instant.EPOCH, request.orderId());
        var pending = inboxService.pending();
        assertTrue(pending.stream().anyMatch(item -> orderId.equals(item.orderId())),
                () -> "now=" + Instant.now() + ", pending=" + pending + ", stored="
                        + inbox.findAll().stream().map(item -> item.getId() + ":" + item.getProviderOrderId()
                                + ":" + item.getNextAttemptAt() + ":" + item.getProcessedAt()).toList());
        var event = pending.stream().filter(item -> orderId.equals(item.orderId())).findFirst().orElseThrow();
        inboxService.complete(event.id(), recovery.reconcile(orderId));
        assertNotNull(inbox.findById(event.id()).orElseThrow().getProcessedAt());
        assertTrue(recovery.reconcile(orderId));
        assertEquals(OrderStatus.CANCELED, orders.findById(orderId).orElseThrow().getStatus());
        verify(gateway, never()).cancel(anyString(), anyString());
    }

    @Test
    void 결제_준비도_하지_않은_미결제_주문을_만료시킨다() {
        jdbc.update("update orders set created_at = ? where id = ?",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1), orderId);
        assertTrue(recoveryStore.expiredUnpreparedOrderIds().contains(orderId));
        recoveryStore.expireUnprepared(orderId);
        assertEquals(OrderStatus.CANCELED, orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void 결제_확인_중이면_준비_시각이_오래돼도_미결제_만료시키지_않는다() {
        var request = prepare();
        paymentService.beginConfirmation(orderId, customerId, request);
        jdbc.update("update payments set prepared_at = ? where order_id = ?", Instant.now().minus(Duration.ofHours(1)), orderId);
        assertTrue(recovery.reconcile(orderId));
        assertEquals(OrderStatus.PENDING, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PAID, payments.findByOrderId(orderId).orElseThrow().getStatus());
    }

    @Test
    void 환불_거절_오류로_주문을_정상_진행_상태로_되돌리지_않는다() {
        facade.confirm(orderId, customerId, prepare());
        paymentService.beginCancellation(orderId, customerId);
        doAnswer(invocation -> { throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR); })
                .when(gateway).cancel(anyString(), anyString());
        assertFalse(recovery.reconcile(orderId));
        assertEquals(OrderStatus.CANCELING, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.CANCELING, payments.findByOrderId(orderId).orElseThrow().getStatus());
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.acceptOrder(orderId, ownerId));
    }

    @Test
    void 고객_문의가_있으면_가게는_기존_문의에_승인_근거를_남기고_취소한다() {
        facade.confirm(orderId, customerId, prepare());
        orderService.acceptOrder(orderId, ownerId);
        var inquiry = refundService.request(orderId, customerId, false, "주문 제공 불가 문의");
        var confirmed = refundFacade.request(orderId, ownerId, true, "품절 확인 후 취소");
        assertEquals(inquiry.refundId(), confirmed.refundId());
        assertEquals(RefundStatus.COMPLETED, confirmed.status());
        assertEquals(ownerId, refundRequests.findById(inquiry.refundId()).orElseThrow().getDecidedBy());
    }

    @Test
    void 운영자_승인으로_완료_주문을_환불해도_주문_이력을_유지한다() {
        facade.confirm(orderId, customerId, prepare());
        orderService.acceptOrder(orderId, ownerId);
        orderService.dispatchOrder(orderId, ownerId);
        orderService.completeOrder(orderId, ownerId);
        var inquiry = refundService.request(orderId, customerId, false, "누락 확인 요청");
        var result = refundFacade.decide(inquiry.refundId(), 1L, "가게 확인 후 전액 보상 승인", true);
        assertEquals(RefundStatus.COMPLETED, result.status());
        assertEquals(OrderStatus.COMPLETED, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.CANCELED, payments.findByOrderId(orderId).orElseThrow().getStatus());
    }

    @Test
    void 주문_생성_HTTP_요청은_구매_멱등키가_필수다() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("loginId", loginId, "password", "password123"))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        var original = orders.findById(orderId).orElseThrow();
        var menu = menus.findByRestaurantIdAndIsActiveTrueOrderByIdAsc(original.getRestaurantId()).getFirst();
        var request = new OrderCreateRequestDto(original.getRestaurantId(), List.of(new OrderItemCreateRequestDto(menu.getId(), 1)),
                "서울시", "101호", "01012345678", PaymentMethod.ONLINE);
        mvc.perform(post("/api/orders").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        String key = UUID.randomUUID().toString();
        var first = mvc.perform(post("/api/orders").session(session).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var second = mvc.perform(post("/api/orders").session(session).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first).get("orderId"), json.readTree(second).get("orderId"));
    }

    @Test
    void 같은_웹훅을_동시에_수신해도_작업은_한_건만_저장한다() throws Exception {
        var request = prepare();
        byte[] raw = json.writeValueAsBytes(Map.of("eventType", "PAYMENT_STATUS_CHANGED", "data", Map.of("orderId", request.orderId())));
        String timestamp = Instant.now().toString();
        String signature = sign(raw, timestamp);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4).mapToObj(index -> executor.submit(() -> {
                webhook.receive(raw, timestamp, signature);
                return true;
            })).toList();
            for (var future : futures) {
                assertTrue(future.get(10, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, inbox.findAll().stream().filter(event -> request.orderId().equals(event.getProviderOrderId())).count());
    }

    @Test
    void 한_배치보다_많은_미처리_웹훅도_재시도_지연으로_다음_작업을_처리한다() {
        var fixtures = java.util.stream.IntStream.range(0, 21).mapToObj(index ->
                com.example.baedalhankki.refund.entity.WebhookInbox.create(UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(), Instant.EPOCH)).toList();
        var ids = inbox.saveAllAndFlush(fixtures).stream().map(event -> event.getId()).toList();
        try {
            var first = inboxService.pending();
            assertEquals(20, first.size());
            first.forEach(event -> inboxService.complete(event.id(), false));
            var second = inboxService.pending();
            assertTrue(second.stream().anyMatch(event -> ids.contains(event.id())));
            assertTrue(second.stream().noneMatch(event -> first.stream().anyMatch(previous -> previous.id().equals(event.id()))));
        } finally {
            inbox.deleteAllById(ids);
        }
    }

    @Test
    void 늦은_승인_웹훅이_와도_토스의_현재_취소_상태를_반영한다() throws Exception {
        var request = prepare();
        facade.confirm(orderId, customerId, request);
        paymentService.beginCancellation(orderId, customerId);
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(), 0, "KRW", "CANCELED"));
        byte[] raw = json.writeValueAsBytes(Map.of("eventType", "PAYMENT_STATUS_CHANGED", "data",
                Map.of("orderId", request.orderId(), "status", "DONE")));
        String timestamp = Instant.now().toString();
        webhook.receive(raw, timestamp, sign(raw, timestamp));
        assertTrue(recovery.reconcile(orderId));
        assertEquals(PaymentStatus.CANCELED, payments.findByOrderId(orderId).orElseThrow().getStatus());
        assertEquals(OrderStatus.CANCELED, orders.findById(orderId).orElseThrow().getStatus());
        verify(gateway, never()).cancel(anyString(), anyString());
    }

    @Test
    void 취소_조회_응답의_금액이_다르면_환불_완료로_확정하지_않는다() {
        var request = prepare();
        facade.confirm(orderId, customerId, request);
        paymentService.beginCancellation(orderId, customerId);
        remote.set(new ProviderPayment(request.paymentKey(), request.orderId(), 1, 0, "KRW", "CANCELED"));
        assertFalse(recovery.reconcile(orderId));
        assertEquals(OrderStatus.CANCELING, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.CANCELING, payments.findByOrderId(orderId).orElseThrow().getStatus());
        verify(gateway, never()).cancel(anyString(), anyString());
    }

    private String sign(byte[] raw, String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test_webhook_security_key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(raw);
        return "v1:" + Base64.getEncoder().encodeToString(mac.doFinal((":" + timestamp).getBytes(StandardCharsets.UTF_8)));
    }

    private PaymentConfirmRequestDto prepare() {
        PaymentPrepareResponseDto prepared = facade.prepare(orderId, customerId);
        return new PaymentConfirmRequestDto("key_" + orderId, prepared.providerOrderId(), prepared.amount());
    }

    private void assertError(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(ApiException.class, action::run).getErrorCode());
    }
}
