package com.example.baedalhankki.order.entity;

import lombok.NoArgsConstructor;

import lombok.AccessLevel;

import lombok.Getter;

import com.example.baedalhankki.global.entity.BaseEntity;
import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.member.entity.Member;
import com.example.baedalhankki.restaurant.entity.Restaurant;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
@Getter
public class Order extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(nullable = false)
    private String addressDetail;

    @Column(nullable = false, length = 20)
    private String contactPhone;

    @Column(nullable = false)
    private int itemsAmount;

    @Column(nullable = false)
    private int deliveryFee;

    @Column(nullable = false)
    private int totalAmount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", insertable = false, updatable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    private Member customer;

    public static Order create(
            Long restaurantId,
            Long customerId,
            String address,
            String detail,
            String phone,
            int deliveryFee,
            List<OrderItem> items) {
        return create(restaurantId, customerId, address, detail, phone, deliveryFee, items,
                PaymentMethod.PAY_ON_DELIVERY);
    }

    public static Order create(Long restaurantId, Long customerId, String address, String detail,
            String phone, int deliveryFee, List<OrderItem> items, PaymentMethod paymentMethod) {
        if (items.isEmpty() || deliveryFee < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        long sum = 0;
        for (var item : items) {
            sum += (long) item.getUnitPrice() * item.getQuantity();
            if (sum + deliveryFee > Integer.MAX_VALUE) {
                throw new ApiException(ErrorCode.INVALID_REQUEST);
            }
        }
        Order order = new Order();
        order.restaurantId = restaurantId;
        order.customerId = customerId;
        order.deliveryAddress = address;
        order.addressDetail = detail;
        order.contactPhone = phone;
        order.deliveryFee = deliveryFee;
        order.itemsAmount = (int) sum;
        order.totalAmount = (int) sum + deliveryFee;
        order.paymentMethod = paymentMethod == null ? PaymentMethod.PAY_ON_DELIVERY : paymentMethod;
        if (order.isOnlinePayment() && order.totalAmount <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        order.status = order.isOnlinePayment() ? OrderStatus.WAITING_PAYMENT : OrderStatus.PENDING;
        for (var item : items) {
            item.attach(order);
            order.items.add(item);
        }
        return order;
    }

    public String getRestaurantName() {
        return restaurant.getName();
    }

    public void cancel() {
        if (isOnlinePayment()) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        if (status != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
        status = OrderStatus.CANCELED;
    }

    public boolean isOnlinePayment() {
        return paymentMethod == PaymentMethod.ONLINE;
    }

    public void markPaid() {
        transition(OrderStatus.WAITING_PAYMENT, OrderStatus.PENDING);
    }

    public void beginOnlineCancellation() {
        if (!isOnlinePayment() || (status != OrderStatus.WAITING_PAYMENT && status != OrderStatus.PENDING)) {
            throw new ApiException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
        status = OrderStatus.CANCELING;
    }

    public void finishOnlineCancellation() {
        transition(OrderStatus.CANCELING, OrderStatus.CANCELED);
    }

    private void transition(OrderStatus expected, OrderStatus next) {
        if (status != expected) {
            throw new ApiException(ErrorCode.INVALID_ORDER_STATUS);
        }
        status = next;
    }

    public void accept() {
        transition(OrderStatus.PENDING, OrderStatus.COOKING);
    }

    public void dispatch() {
        transition(OrderStatus.COOKING, OrderStatus.DELIVERING);
    }

    public void complete() {
        transition(OrderStatus.DELIVERING, OrderStatus.COMPLETED);
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public String getPaymentMethod() {
        return paymentMethod.name();
    }

}
