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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000")
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

    private Long customerId;
    private Long ownerId;
    private Long orderId;
    private String loginId;
    private final AtomicReference<ProviderPayment> remote = new AtomicReference<>();

    @Autowired
    PaymentIntegrationTest(OrderService orderService, OrderRepository orders, MemberRepository members,
            RestaurantRepository restaurants, MenuRepository menus, PaymentRepository payments,
            PaymentService paymentService, PaymentFacade facade, MockMvc mvc, ObjectMapper json,
            PasswordEncoder passwords) {
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
        assertError(ErrorCode.PAYMENT_STATE_CONFLICT, () -> facade.cancelOrder(orderId, customerId));
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
    void 승인_처리_중에는_취소와_접수와_다른_결제키를_차단한다() throws Exception {
        PaymentConfirmRequestDto request = prepare();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new ProviderPayment(request.paymentKey(), request.orderId(), request.amount(),
                    request.amount(), "KRW", "DONE");
        }).when(gateway).confirm(anyString(), anyString(), anyInt(), anyString());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> facade.confirm(orderId, customerId, request));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertError(ErrorCode.PAYMENT_STATE_CONFLICT, () -> facade.cancelOrder(orderId, customerId));
                assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.acceptOrder(orderId, ownerId));
                assertError(ErrorCode.PAYMENT_STATE_CONFLICT, () -> facade.confirm(orderId, customerId,
                        new PaymentConfirmRequestDto("different_key", request.orderId(), request.amount())));
            } finally {
                release.countDown();
            }
            assertEquals(PaymentStatus.PAID, future.get(5, TimeUnit.SECONDS).status());
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

    private PaymentConfirmRequestDto prepare() {
        PaymentPrepareResponseDto prepared = facade.prepare(orderId, customerId);
        return new PaymentConfirmRequestDto("key_" + orderId, prepared.providerOrderId(), prepared.amount());
    }

    private void assertError(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(ApiException.class, action::run).getErrorCode());
    }
}
