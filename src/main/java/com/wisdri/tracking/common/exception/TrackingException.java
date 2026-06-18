package com.wisdri.tracking.common.exception;

/**
 * 跟踪服务基础运行时异常。
 */
public class TrackingException extends RuntimeException {
    public TrackingException(String message) {
        super(message);
    }

    public TrackingException(String message, Throwable cause) {
        super(message, cause);
    }
}
