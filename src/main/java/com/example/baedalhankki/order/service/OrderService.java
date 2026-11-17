package com.example.baedalhankki.order.service;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.menu.repository.MenuRepository;
import com.example.baedalhankki.order.dto.OrderCreateRequestDto;
import com.example.baedalhankki.order.dto.OrderDetailResponseDto;
import com.example.baedalhankki.order.dto.OrderListResponseDto;
import com.example.baedalhankki.order.dto.OwnerOrderListResponseDto;
import com.example.baedalhankki.order.dto.OwnerOrderSummaryResponseDto;
import com.example.baedalhankki.order.dto.OrderStateResponseDto;
import com.example.baedalhankki.order.dto.OrderSummaryResponseDto;
import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderItem;
import com.example.baedalhankki.order.entity.OrderStatus;
import com.example.baedalhankki.order.repository.OrderRepository;
import com.example.baedalhankki.restaurant.repository.RestaurantRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final MenuRepository menus;
    private final Long restaurantId;

    public OrderService(
            OrderRepository orders,
            RestaurantRepository restaurants,
            MenuRepository menus,
            @Value("${app.restaurant-id}") Long id) {
        this.orders = orders;
        this.restaurants = restaurants;
        this.menus = menus;
        restaurantId = id;
    }

    @Transactional
    public OrderStateResponseDto createOrder(Long customerId, OrderCreateRequestDto request) {
        var restaurant =
                restaurants
                        .findById(request.restaurantId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (!restaurant.getId().equals(restaurantId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        var seen = new HashSet<Long>();
        var items = new ArrayList<OrderItem>();
        for (var item : request.items()) {
            if (!seen.add(item.menuId())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST);
            }
            var menu =
                    menus.findById(item.menuId())
                            .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));
            if (!menu.getRestaurantId().equals(restaurant.getId())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST);
            }
            if (!menu.isActive()) {
                throw new ApiException(ErrorCode.MENU_UNAVAILABLE);
            }
            items.add(OrderItem.create(menu.getId(), menu.getName(), menu.getPrice(), item.quantity()));
        }
        String digits = request.contactPhone().replace("-", "");
        String phone =
                digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        return OrderStateResponseDto.from(
                orders.save(
                        Order.create(
                                restaurant.getId(),
                                customerId,
                                request.deliveryAddress(),
                                request.addressDetail(),
                                phone,
                                restaurant.getDeliveryFee(),
                                items)));
    }

    private String getRestaurantName(Long id) {
        return restaurants
                .findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND))
                .getName();
    }

    private Order findCustomerOrder(Long id, Long customerId) {
        return orders.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Order findOwnerOrder(Long id) {
        return orders.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
    }

    public OrderListResponseDto getOrders(Long customerId) {
        return OrderListResponseDto.of(
                orders.findByCustomerIdOrderByIdDesc(customerId).stream()
                        .map(OrderSummaryResponseDto::from)
                        .toList());
    }

    public OrderDetailResponseDto getOrder(Long id, Long customerId) {
        return OrderDetailResponseDto.from(findCustomerOrder(id, customerId));
    }

    @Transactional
    public OrderStateResponseDto cancelOrder(Long id, Long customerId) {
        var order = findCustomerOrder(id, customerId);
        order.cancel();
        LOGGER.info("Order {} changed to {}", order.getId(), order.getStatus());
        return OrderStateResponseDto.from(order);
    }

    public OwnerOrderListResponseDto getOwnerOrders() {
        var list = orders.findByRestaurantIdOrderByIdDesc(restaurantId);
        String name = getRestaurantName(restaurantId);
        return OwnerOrderListResponseDto.of(
                name,
                list.stream().filter(order -> order.getStatus() == OrderStatus.PENDING).count(),
                list.stream()
                        .map(OwnerOrderSummaryResponseDto::from)
                        .toList());
    }

    public OrderDetailResponseDto getOwnerOrder(Long id) {
        return OrderDetailResponseDto.from(findOwnerOrder(id));
    }

    @Transactional
    public OrderStateResponseDto acceptOrder(Long id) {
        var order = findOwnerOrder(id);
        order.accept();
        LOGGER.info("Order {} changed to {}", order.getId(), order.getStatus());
        return OrderStateResponseDto.from(order);
    }

    @Transactional
    public OrderStateResponseDto dispatchOrder(Long id) {
        var order = findOwnerOrder(id);
        order.dispatch();
        LOGGER.info("Order {} changed to {}", order.getId(), order.getStatus());
        return OrderStateResponseDto.from(order);
    }

    @Transactional
    public OrderStateResponseDto completeOrder(Long id) {
        var order = findOwnerOrder(id);
        order.complete();
        LOGGER.info("Order {} changed to {}", order.getId(), order.getStatus());
        return OrderStateResponseDto.from(order);
    }
}
