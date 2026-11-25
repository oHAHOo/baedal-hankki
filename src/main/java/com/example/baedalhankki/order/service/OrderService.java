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

import lombok.RequiredArgsConstructor;
import com.example.baedalhankki.restaurant.entity.Restaurant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final MenuRepository menus;

    @Transactional
    public OrderStateResponseDto createOrder(Long customerId, OrderCreateRequestDto request) {
        var restaurant =
                restaurants
                        .findById(request.restaurantId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
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
                                items,
                                request.paymentMethod())));
    }

    private Restaurant findOwnerRestaurant(Long ownerId) {
        return restaurants.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    private Order findCustomerOrder(Long id, Long customerId) {
        return orders.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Order findOwnerOrder(Long id, Long ownerId) {
        Long restaurantId = findOwnerRestaurant(ownerId).getId();
        return orders.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Order findOwnerOrderForUpdate(Long id, Long ownerId) {
        Long restaurantId = findOwnerRestaurant(ownerId).getId();
        return orders.findRestaurantOrderForUpdate(id, restaurantId)
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
        var order = orders.findCustomerOrderForUpdate(id, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        order.cancel();
        LOGGER.info("Order {} changed to {}", order.getId(), order.getStatus());
        return OrderStateResponseDto.from(order);
    }

    public OwnerOrderListResponseDto getOwnerOrders(Long ownerId) {
        var restaurant = findOwnerRestaurant(ownerId);
        var list = orders.findByRestaurantIdOrderByIdDesc(restaurant.getId());
        String name = restaurant.getName();
        return OwnerOrderListResponseDto.of(
                name,
                list.stream().filter(order -> order.getStatus() == OrderStatus.PENDING).count(),
                list.stream()
                        .map(OwnerOrderSummaryResponseDto::from)
                        .toList());
    }

    public OrderDetailResponseDto getOwnerOrder(Long id, Long ownerId) {
        return OrderDetailResponseDto.from(findOwnerOrder(id, ownerId));
    }

    @Transactional
    public OrderStateResponseDto acceptOrder(Long id, Long ownerId) {
        var order = findOwnerOrderForUpdate(id, ownerId);
        order.accept();
        LOGGER.info("Order {} changed to {}", order.getId(), order.getStatus());
        return OrderStateResponseDto.from(order);
    }

    @Transactional
    public OrderStateResponseDto dispatchOrder(Long id, Long ownerId) {
        var order = findOwnerOrderForUpdate(id, ownerId);
        order.dispatch();
        LOGGER.info("Order {} changed to {}", order.getId(), order.getStatus());
        return OrderStateResponseDto.from(order);
    }

    @Transactional
    public OrderStateResponseDto completeOrder(Long id, Long ownerId) {
        var order = findOwnerOrderForUpdate(id, ownerId);
        order.complete();
        LOGGER.info("Order {} changed to {}", order.getId(), order.getStatus());
        return OrderStateResponseDto.from(order);
    }
}
