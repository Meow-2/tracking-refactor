package com.wisdri.tracking.common.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用接口响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class R<T> implements Serializable {
    /**
     * 响应码。
     */
    private String code;

    /**
     * 响应消息。
     */
    @JsonAlias("msg")
    private String message;

    /**
     * 响应数据。
     */
    private T data;

    /**
     * 是否成功。
     */
    private Boolean success;

    /**
     * 构造指定响应码和数据的响应。
     */
    public static <T> R<T> of(ResponseCode responseCode, T data) {
        return new R<>(responseCode.getCode(), responseCode.getMessage(), data, null);
    }

    /**
     * 构造失败响应。
     */
    public static <T> R<T> fail() {
        return of(ResponseCode.FAIL, null);
    }

    /**
     * 构造成功响应。
     */
    public static <T> R<T> success() {
        return of(ResponseCode.SUCCESS, null);
    }

    /**
     * 构造成功数据响应。
     */
    public static <T> R<T> data(T data) {
        return of(ResponseCode.SUCCESS, data);
    }

    /**
     * 根据布尔状态构造响应。
     */
    public static <T> R<T> status(boolean flag) {
        return flag ? success() : fail();
    }

    /**
     * 判断响应是否成功。
     */
    public static <T> Boolean isSuccess(R<T> result) {
        return result != null
                && (Boolean.TRUE.equals(result.getSuccess())
                || ResponseCode.SUCCESS.getCode().equals(result.getCode()));
    }
}
