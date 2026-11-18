package com.example.baedalhankki.order.entity;

import com.example.baedalhankki.global.exception.ErrorCode;

import com.example.baedalhankki.global.exception.ApiException;

import lombok.NoArgsConstructor;

import lombok.AccessLevel;

import lombok.Getter;

import com.example.baedalhankki.menu.entity.Menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(nullable = false, length = 100)
    private String menuName;

    @Column(nullable = false)
    private int unitPrice;

    @Column(nullable = false)
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", insertable = false, updatable = false)
    private Menu menu;

    public static OrderItem create(Long menuId, String name, int price, int quantity) {
        if (price < 0 || quantity < 1) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        OrderItem orderItem = new OrderItem();
        orderItem.menuId = menuId;
        orderItem.menuName = name;
        orderItem.unitPrice = price;
        orderItem.quantity = quantity;
        return orderItem;
    }

    void attach(Order order) {
        this.order = order;
    }

}
