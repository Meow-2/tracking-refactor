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
         * 是否启用过程跟踪结果存储。
         */
        private StoreSwitch process = new StoreSwitch();

        /**
         * 是否启用批次跟踪结果存储。
         */
        private StoreSwitch batch = new StoreSwitch();

        /**
         * 是否启用剪切结果及运行态提交。
         */
        private StoreSwitch shear = new StoreSwitch();

        /**
         * 是否启用开卷卷取结果存储。
         */
        private StoreSwitch coiler = new StoreSwitch();

        /**
         * 是否启用切边结果存储及运行态提交。
         */
        private StoreSwitch trimming = new StoreSwitch();

        /** 是否启用铁损跟踪结果存储；默认启用。 */
        private StoreSwitch ironloss = new StoreSwitch();
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

    public boolean processStorageEnabled() {
        return storage == null
                || storage.getProcess() == null
                || !Boolean.FALSE.equals(storage.getProcess().getEnabled());
    }

    public boolean batchStorageEnabled() {
        return storage == null
                || storage.getBatch() == null
                || !Boolean.FALSE.equals(storage.getBatch().getEnabled());
    }

    public boolean shearStorageEnabled() {
        return storage == null
                || storage.getShear() == null
                || !Boolean.FALSE.equals(storage.getShear().getEnabled());
    }

    public boolean coilerStorageEnabled() {
        return storage == null
                || storage.getCoiler() == null
                || !Boolean.FALSE.equals(storage.getCoiler().getEnabled());
    }

    public boolean trimmingStorageEnabled() {
        return storage == null
                || storage.getTrimming() == null
                || !Boolean.FALSE.equals(storage.getTrimming().getEnabled());
    }

    /** 铁损存储开关未配置时默认启用。 */
    public boolean ironlossStorageEnabled() {
        return storage == null
                || storage.getIronloss() == null
                || !Boolean.FALSE.equals(storage.getIronloss().getEnabled());
    }
}
