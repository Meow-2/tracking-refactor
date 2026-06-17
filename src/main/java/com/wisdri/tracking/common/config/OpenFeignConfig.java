package com.wisdri.tracking.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 第三方 HTTP 接口配置绑定。
 */
@Data
@Component
@ConfigurationProperties
public class OpenFeignConfig {
    /**
     * cube-api 配置。
     */
    private RemoteApi cubeApi = new RemoteApi();

    /**
     * data-storage 配置。
     */
    private RemoteApi dataStorage = new RemoteApi();

    @Data
    public static class RemoteApi {
        /**
         * 服务基础地址。
         */
        private String baseUrl;

        /**
         * 点位树根路径，仅 cube-api 使用。
         */
        private String treeRoot;

        /**
         * 连接超时时间，单位毫秒。
         */
        private Integer connectTimeout;

        /**
         * 读取超时时间，单位毫秒。
         */
        private Integer readTimeout;
    }
}
