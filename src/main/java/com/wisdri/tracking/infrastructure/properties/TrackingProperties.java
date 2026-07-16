package com.wisdri.tracking.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * tracking.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tracking")
public class TrackingProperties {
    /**
     * 当前实例负责的机组代码。
     */
    private String unit;

    /**
     * 存储开关配置。
     */
    private Storage storage = new Storage();

    @Data
    public static class Storage {
        /**
         * 是否启用异常数据存储。
         */
        private StoreSwitch abnormal = new StoreSwitch();

        /**
         * 是否启用跟踪结果存储。
         */
        private StoreSwitch trackingResult = new StoreSwitch();
    }

    @Data
    public static class StoreSwitch {
        /**
         * 是否启用该存储。
         */
        private Boolean enabled = true;
    }

    public boolean abnormalStorageEnabled() {
        return storage == null
                || storage.getAbnormal() == null
                || !Boolean.FALSE.equals(storage.getAbnormal().getEnabled());
    }

    public boolean trackingResultStorageEnabled() {
        return storage == null
                || storage.getTrackingResult() == null
                || !Boolean.FALSE.equals(storage.getTrackingResult().getEnabled());
    }
}
