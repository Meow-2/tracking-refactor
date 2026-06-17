package com.wisdri.tracking.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通用接口响应码。
 */
@Getter
@RequiredArgsConstructor
public enum ResponseCode {

    SUCCESS("200", "success"),
    FAIL("500", "fail");

    /**
     * 响应码。
     */
    private final String code;

    /**
     * 响应消息。
     */
    private final String message;
}
