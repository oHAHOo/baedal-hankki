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
import com.example.baedalhankki.order.dto.OrderStateResponseDto;
import com.example.baedalhankki.order.entity.OrderStatus;
import com.example.baedalhankki.order.repository.OrderRepository;
import com.example.baedalhankki.order.service.OrderService;
import com.example.baedalhankki.restaurant.entity.Restaurant;
import com.example.baedalhankki.restaurant.repository.RestaurantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"
})
@ActiveProfiles("test")
class OrderConcurrencyTest {
    private final OrderService orderService;
    private final OrderRepository orders;
    private final MemberRepository members;
    private final RestaurantRepository restaurants;
    private final MenuRepository menus;
    private final TransactionTemplate transaction;

    private Long customerId;
    private Long ownerId;
    private Long restaurantId;
    private Long orderId;

    @Autowired
    OrderConcurrencyTest(OrderService orderService, OrderRepository orders, MemberRepository members,
            RestaurantRepository restaurants, MenuRepository menus, PlatformTransactionManager transactionManager) {
        this.orderService = orderService;
        this.orders = orders;
        this.members = members;
        this.restaurants = restaurants;
        this.menus = menus;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void createOrder() {
        transaction.executeWithoutResult(status -> {
            var customer = members.save(Member.create(UUID.randomUUID().toString(), "unused-hash",
                    "고객", MemberRole.CUSTOMER));
            var owner = members.save(Member.create(UUID.randomUUID().toString(), "unused-hash",
                    "사장님", MemberRole.OWNER));
            var restaurant = restaurants.save(Restaurant.create("동시성 식당", 3000, owner));
            var menu = menus.save(Menu.create(restaurant.getId(), "제육덮밥", 9000));
            customerId = customer.getId();
            ownerId = owner.getId();
            restaurantId = restaurant.getId();
            orderId = orderService.createOrder(customerId, new OrderCreateRequestDto(
                    restaurantId, List.of(new OrderItemCreateRequestDto(menu.getId(), 1)),
                    "서울시 마포구 한끼로 12", "101호", "01012345678")).orderId();
        });
    }

    @ParameterizedTest
    @CsvSource({
            "accept, cancel, PENDING, COOKING, ORDER_NOT_CANCELABLE",
            "cancel, accept, PENDING, CANCELED, INVALID_ORDER_STATUS",
            "dispatch, dispatch, COOKING, DELIVERING, INVALID_ORDER_STATUS",
            "complete, complete, DELIVERING, COMPLETED, INVALID_ORDER_STATUS"
    })
    void 잠금을_획득한_상태_변경만_성공한다(String firstAction, String secondAction,
            OrderStatus initialStatus, OrderStatus expectedStatus, ErrorCode expectedError) throws Exception {
        if (initialStatus != OrderStatus.PENDING) {
            orderService.acceptOrder(orderId, ownerId);
        }
        if (initialStatus == OrderStatus.DELIVERING) {
            orderService.dispatchOrder(orderId, ownerId);
        }

        try (var executor = Executors.newSingleThreadExecutor()) {
            var started = new CountDownLatch(1);
            Future<OrderStateResponseDto> waitingRequest = transaction.execute(status -> {
                orders.findRestaurantOrderForUpdate(orderId, restaurantId).orElseThrow();
                Future<OrderStateResponseDto> future = executor.submit(() -> {
                    started.countDown();
                    return changeStatus(secondAction);
                });
                try {
                    assertTrue(started.await(2, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> future.get(200, TimeUnit.MILLISECONDS));
                    assertEquals(expectedStatus, changeStatus(firstAction).status());
                    return future;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            });

            var failure = assertThrows(ExecutionException.class,
                    () -> waitingRequest.get(5, TimeUnit.SECONDS));
            var exception = assertInstanceOf(ApiException.class, failure.getCause());
            assertEquals(expectedError, exception.getErrorCode());
            assertEquals(expectedStatus, orders.findById(orderId).orElseThrow().getStatus());
        }
    }

    private OrderStateResponseDto changeStatus(String action) {
        return switch (action) {
            case "accept" -> orderService.acceptOrder(orderId, ownerId);
            case "cancel" -> orderService.cancelOrder(orderId, customerId);
            case "dispatch" -> orderService.dispatchOrder(orderId, ownerId);
            case "complete" -> orderService.completeOrder(orderId, ownerId);
            default -> throw new IllegalArgumentException("Unknown test action " + action);
        };
    }
}
