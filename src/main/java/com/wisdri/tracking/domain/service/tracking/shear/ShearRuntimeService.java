package com.wisdri.tracking.domain.service.tracking.shear;

import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearRuntimeCommit;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearDeviceSnapshot;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 为剪切算法准备独立 runtime 工作副本，并在正确的持久化阶段提交设备状态和幂等时间。
 */
@Component
public class ShearRuntimeService {
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    /**
     * 深拷贝当前设备 runtime，并用当前候选刷新设备信息。卷号或生产次数变化会先清空三类计数。
     *
     * @param unitCode 当前机组代码
     * @param devices 当前帧按 status 配置顺序得到的设备快照
     * @param tracking 当前机组剪刀绑定设备配置
     * @return 本帧专用的 runtime 工作副本和成功事件暂存表
     */
    public ShearRuntimeCommit prepare(String unitCode,
                                      List<ShearDeviceSnapshot> devices,
                                      ShearTrackingSection tracking) {
        Map<String, ShearTrackingRuntime> runtimes = new LinkedHashMap<>();
        for (ShearDeviceSnapshot device : devices) {
            if (device == null || device.getDeviceCode() == null) {
                continue;
            }
            ShearTrackingRuntime runtime = currentRuntime(unitCode, device.getDeviceCode());
            boolean sameMaterial = Objects.equals(runtime.getCoilNo(), device.getCoilNo())
                    && Objects.equals(runtime.getRepeatProdNo(), device.getRepeatProdNo());
            runtime.setSide(device.getSide());
            runtime.setDeviceName(device.getDeviceName());
            runtime.setCoilNo(device.getCoilNo());
            runtime.setRepeatProdNo(device.getRepeatProdNo());
            runtime.setColorNo(device.getColorNo());
            runtime.setRemainingLength(device.getRemainingLength());
            runtime.setMaxLength(device.getMaxLength());
            if (!sameMaterial) {
                resetCounters(runtime);
            } else {
                ensureCounters(runtime);
            }
            runtimes.put(device.getDeviceCode(), runtime);
        }
        addConfiguredDevices(unitCode, runtimes, tracking);
        return ShearRuntimeCommit.builder().runtimes(runtimes)
                .persistedTriggerTimes(new LinkedHashMap<>()).build();
    }

    /**
     * 判断设备此前是否已成功持久化相同触发帧。
     *
     * @param runtime 触发设备当前 runtime
     * @param receivedAt 当前帧接收时间
     * @return 时间相同且非空时表示此设备的当前触发帧已成功入库
     */
    public boolean alreadyPersisted(ShearTrackingRuntime runtime, Instant receivedAt) {
        return runtime != null && receivedAt != null
                && receivedAt.equals(runtime.getLastPersistedTriggerTime());
    }

    /**
     * 将完整剪切事件的计数合入本帧工作副本，并登记触发设备的待提交入库时间。
     *
     * @param commit 本帧所有设备的工作副本
     * @param inMatDevice 计数归属物料设备
     * @param kind 当前剪切类型
     * @param nextCounter 更新前状态推演出的下一刀计数
     * @param shearDevice 本次触发设备
     * @param receivedAt 触发帧接收时间；为空时不登记幂等标记
     */
    public void stage(ShearRuntimeCommit commit,
                      ShearDeviceSnapshot inMatDevice,
                      ShearKind kind,
                      ShearCounterRuntime nextCounter,
                      ShearDeviceSnapshot shearDevice,
                      Instant receivedAt) {
        ShearTrackingRuntime materialRuntime = commit.getRuntimes().get(inMatDevice.getDeviceCode());
        ShearTrackingRuntime triggerRuntime = commit.getRuntimes().get(shearDevice.getDeviceCode());
        if (materialRuntime == null) {
            throw new IllegalStateException("物料设备 runtime 不存在: " + inMatDevice.getDeviceCode());
        }
        if (triggerRuntime == null) {
            throw new IllegalStateException("剪切设备 runtime 不存在: " + shearDevice.getDeviceCode());
        }
        materialRuntime.setCounter(kind, copyCounter(nextCounter));
        if (receivedAt != null) {
            commit.getPersistedTriggerTimes().put(shearDevice.getDeviceCode(), receivedAt);
        }
    }

    /**
     * 计算阶段直接提交状态和计数，不更改成功入库时间；存储关闭或本帧无剪切记录时使用。
     *
     * @param commit 本帧已计算完成的 runtime 副本
     */
    public void commitCalculated(ShearRuntimeCommit commit) {
        save(commit);
    }

    /**
     * 数据库批量写入成功后，同时提交本帧状态、刀次和触发设备幂等时间。
     *
     * @param commit 本帧已计算完成且数据库写入成功的 runtime 副本
     */
    public void commitPersisted(ShearRuntimeCommit commit) {
        for (Map.Entry<String, Instant> entry : commit.getPersistedTriggerTimes().entrySet()) {
            ShearTrackingRuntime runtime = commit.getRuntimes().get(entry.getKey());
            if (runtime != null) {
                runtime.setLastPersistedTriggerTime(entry.getValue());
            }
        }
        save(commit);
    }

    private void save(ShearRuntimeCommit commit) {
        if (commit == null || commit.getRuntimes() == null || commit.getRuntimes().isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<ShearTrackingRuntime> runtimes = new ArrayList<>(commit.getRuntimes().values());
        for (ShearTrackingRuntime runtime : runtimes) {
            runtime.setUpdatedAt(now);
        }
        runtimeRepositoryDispatcher.saveRuntimes(runtimes);
    }

    private void addConfiguredDevices(String unitCode,
                                      Map<String, ShearTrackingRuntime> runtimes,
                                      ShearTrackingSection tracking) {
        addConfiguredDevices(unitCode, runtimes, tracking.getUncoilerShearPoint(), true);
        addConfiguredDevices(unitCode, runtimes, tracking.getCoilerShearPoint(), false);
    }

    private void addConfiguredDevices(String unitCode,
                                      Map<String, ShearTrackingRuntime> runtimes,
                                      List<ShearPointConfig> points,
                                      boolean uncoilerSide) {
        if (points == null) {
            return;
        }
        for (ShearPointConfig point : points) {
            for (String code : deviceCodes(point)) {
                runtimes.computeIfAbsent(code, ignored -> {
                    ShearTrackingRuntime runtime = currentRuntime(unitCode, code);
                    runtime.setSide(uncoilerSide
                            ? com.wisdri.tracking.domain.model.config.status.DeviceSide.UNCOILER
                            : com.wisdri.tracking.domain.model.config.status.DeviceSide.COILER);
                    ensureCounters(runtime);
                    return runtime;
                });
            }
        }
    }

    private ShearTrackingRuntime currentRuntime(String unitCode, String deviceCode) {
        return runtimeRepositoryDispatcher.findRuntimeAs(unitCode, TrackingType.SHEAR,
                        deviceCode, ShearTrackingRuntime.class)
                .map(this::copyRuntime)
                .orElseGet(() -> emptyRuntime(unitCode, deviceCode));
    }

    private ShearTrackingRuntime emptyRuntime(String unitCode, String deviceCode) {
        return ShearTrackingRuntime.builder().unitCode(unitCode).trackingType(TrackingType.SHEAR)
                .deviceCode(deviceCode).head(zeroCounter()).slice(zeroCounter()).tail(zeroCounter()).build();
    }

    private ShearTrackingRuntime copyRuntime(ShearTrackingRuntime source) {
        return ShearTrackingRuntime.builder()
                .unitCode(source.getUnitCode()).trackingType(TrackingType.SHEAR)
                .deviceCode(source.getDeviceCode()).updatedAt(source.getUpdatedAt())
                .side(source.getSide()).deviceName(source.getDeviceName())
                .coilNo(source.getCoilNo()).repeatProdNo(source.getRepeatProdNo())
                .colorNo(source.getColorNo()).remainingLength(source.getRemainingLength())
                .maxLength(source.getMaxLength()).head(copyCounter(source.getHead()))
                .slice(copyCounter(source.getSlice())).tail(copyCounter(source.getTail()))
                .lastPersistedTriggerTime(source.getLastPersistedTriggerTime()).build();
    }

    private void resetCounters(ShearTrackingRuntime runtime) {
        runtime.setHead(zeroCounter());
        runtime.setSlice(zeroCounter());
        runtime.setTail(zeroCounter());
    }

    private void ensureCounters(ShearTrackingRuntime runtime) {
        if (runtime.getHead() == null) {
            runtime.setHead(zeroCounter());
        }
        if (runtime.getSlice() == null) {
            runtime.setSlice(zeroCounter());
        }
        if (runtime.getTail() == null) {
            runtime.setTail(zeroCounter());
        }
    }

    private ShearCounterRuntime zeroCounter() {
        return ShearCounterRuntime.builder().shearNo(0).cutNo(0)
                .lastRemainingLength(BigDecimal.ZERO).build();
    }

    private ShearCounterRuntime copyCounter(ShearCounterRuntime source) {
        return source == null ? zeroCounter() : ShearCounterRuntime.builder()
                .shearNo(source.getShearNo()).cutNo(source.getCutNo())
                .lastRemainingLength(source.getLastRemainingLength()).build();
    }

    private List<String> deviceCodes(ShearPointConfig point) {
        if (point.getDeviceCodes() != null && !point.getDeviceCodes().isEmpty()) {
            return point.getDeviceCodes();
        }
        return point.getDeviceCode() == null ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(point.getDeviceCode());
    }

}
