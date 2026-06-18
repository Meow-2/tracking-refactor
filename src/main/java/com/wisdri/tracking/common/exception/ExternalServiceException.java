package com.wisdri.tracking.common.exception;

/**
 * 外部服务调用异常。
 */
public class ExternalServiceException extends TrackingException {
    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
