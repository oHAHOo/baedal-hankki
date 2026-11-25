package com.example.baedalhankki.global.exception;

import lombok.RequiredArgsConstructor;

import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없어요."),
    PAYMENT_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 결제 상태에서는 처리할 수 없어요."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "주문 금액과 결제 금액이 일치하지 않아요."),
    PAYMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "결제 서비스를 사용할 수 없어요."),
    PAYMENT_RETRY_REQUIRED(HttpStatus.SERVICE_UNAVAILABLE, "결제 결과를 확인하지 못했어요. 같은 요청으로 다시 확인해 주세요."),
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "결제사에서 요청을 처리하지 못했어요. 결제 정보를 확인해 주세요."),
    LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 아이디예요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않아요."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "입력 내용을 확인해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했어요."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없어요."),
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없어요."),
    RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없어요."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없어요."),
    MENU_UNAVAILABLE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없어요."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없어요."),
    ORDER_UPDATE_CONFLICT(HttpStatus.CONFLICT, "다른 요청이 주문을 처리하고 있어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

}
