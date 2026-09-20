package com.wisdri.tracking.domain.service.tracking.shear;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.shear.CutSetting;
import com.wisdri.tracking.domain.model.config.shear.ShearMode;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearSettings;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.config.shear.WelderShearSettings;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.shear.ShearDecision;
import com.wisdri.tracking.domain.model.tracking.shear.ShearDeviceSnapshot;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.service.point.PointReader;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按剪切模式、设备侧和当前帧点位计算剪切类型、物料归属、刀次与单刀长度。
 */
@Component
public class ShearDecisionService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 点位解析和设备顺序选择委托给设备解析器。 */
    private final ShearDeviceResolver deviceResolver;

    /**
     * @param deviceResolver 按配置顺序解析 status 设备和当前帧光栅、颜色点的服务
     */
    public ShearDecisionService(ShearDeviceResolver deviceResolver) {
        this.deviceResolver = deviceResolver;
    }

    /**
     * 完成单个剪切事件的判型、选料、计数推演与长度计算。
     *
     * @param snapshot 当前剪切点位快照
     * @param tracking 剪切算法配置
     * @param point 当前剪刀配置
     * @param uncoilerSide 当前剪刀配置所在设备侧
     * @param statusContext 当前状态候选
     * @param devices 按 status 配置顺序排列的有效设备快照
     * @param runtimes 本帧尚未正式提交的设备 runtime 副本
     * @return 完整剪切判定；必要输入不完整时抛出异常，由调用方隔离当前剪切点
     */
    public ShearDecision decide(PointSnapshot snapshot,
                                ShearTrackingSection tracking,
                                ShearPointConfig point,
                                boolean uncoilerSide,
                                StatusTrackingContext statusContext,
                                List<ShearDeviceSnapshot> devices,
                                Map<String, ShearTrackingRuntime> runtimes) {
        ShearKind kind = point.getShearSettings().getDefaultValue();
        ShearDeviceSnapshot shearDevice = deviceResolver.resolveShearDevice(point, devices,
                !uncoilerSide && tracking.getMode() == ShearMode.CONTINUOUS);
        if (shearDevice == null) {
            throw new IllegalArgumentException("剪切设备未在当前 candidates 中找到");
        }
        String shearColor = null;
        if (tracking.getMode() == ShearMode.CONTINUOUS && !uncoilerSide
                && (kind == null || kind == ShearKind.HEAD || kind == ShearKind.SLICE)) {
            shearColor = deviceResolver.shearColor(snapshot, tracking, point);
            if (shearColor == null) {
                throw new IllegalArgumentException("连续线出口剪判型或选料所需的颜色点无效");
            }
        }
        if (kind == null) {
            kind = classify(snapshot, tracking, point, uncoilerSide, shearDevice,
                    shearColor, devices, runtimes);
        }
        ShearDeviceSnapshot inMatDevice = selectMaterial(snapshot, tracking, point,
                uncoilerSide, kind, shearDevice, shearColor, devices);
        if (inMatDevice == null || !inMatDevice.hasCompleteMaterial()) {
            throw new IllegalArgumentException("剪切归属物料设备不存在或数据不完整");
        }
        ShearTrackingRuntime materialRuntime = runtime(runtimes, inMatDevice.getDeviceCode());
        ShearCounterRuntime oldCounter = materialRuntime.counter(kind);
        CounterDecision counterDecision = nextCounter(oldCounter,
                inMatDevice.getRemainingLength(), tracking.getShearExperience());
        LengthDecision length = calculateLength(snapshot, tracking, point, kind,
                uncoilerSide, counterDecision, oldCounter, inMatDevice);
        return ShearDecision.builder()
                .kind(kind)
                .shearDevice(shearDevice)
                .inMatDevice(inMatDevice)
                .nextCounter(counterDecision.nextCounter)
                .firstCut(counterDecision.firstCut)
                .lengthDelta(counterDecision.delta)
                .shearLength(length.length)
                .setNumber(length.setNumber)
                .build();
    }

    /** 自动判型入口；固定类型由调用方直接采用，不进入此方法。 */
    private ShearKind classify(PointSnapshot snapshot,
                               ShearTrackingSection tracking,
                               ShearPointConfig point,
                               boolean uncoilerSide,
                               ShearDeviceSnapshot shearDevice,
                               String shearColor,
                               List<ShearDeviceSnapshot> devices,
                               Map<String, ShearTrackingRuntime> runtimes) {
        if (tracking.getMode() == ShearMode.CONTINUOUS && uncoilerSide) {
            return classifyUncoiler(shearDevice, tracking.getTailExperience());
        }
        if (tracking.getMode() == ShearMode.DISCONTINUOUS && uncoilerSide) {
            if (shearDevice.getRemainingLength().compareTo(tracking.getTailExperience()) <= 0) {
                return ShearKind.TAIL;
            }
            if (shearDevice.getMaxLength() == null) {
                throw new IllegalArgumentException("开卷机最大长度缺失，无法判定切头");
            }
            if (shearDevice.getMaxLength().subtract(shearDevice.getRemainingLength())
                    .compareTo(tracking.getTailExperience()) <= 0) {
                return ShearKind.HEAD;
            }
            return ShearKind.SLICE;
        }
        if (tracking.getMode() == ShearMode.CONTINUOUS) {
            if (Objects.equals(shearColor, shearDevice.getColorNo())) {
                return ShearKind.SLICE;
            }
            int tailLimit = integerValue(snapshot, tracking, point.getShearSettings()
                    .getFrontWelder().getSamplePieces())
                    + integerValue(snapshot, tracking, point.getShearSettings()
                    .getFrontWelder().getScrapPieces())
                    + (welderPieces(snapshot, tracking, point.getShearSettings()) + 1) / 2;
            ShearTrackingRuntime triggerRuntime = runtime(runtimes, shearDevice.getDeviceCode());
            ShearCounterRuntime previousTail = triggerRuntime.getTail();
            int tailCutNo = previousTail == null || previousTail.getCutNo() == null
                    ? 0 : previousTail.getCutNo();
            return tailCutNo < tailLimit ? ShearKind.TAIL : ShearKind.HEAD;
        }
        if (uncoilerSide) {
            return classifyUncoiler(shearDevice, tracking.getTailExperience());
        }
        if (!hasText(shearDevice.getCoilNo())) {
            return ShearKind.HEAD;
        }
        return deviceResolver.findOtherMaterialByCoil(
                devices, shearDevice.getCoilNo(), shearDevice.getDeviceCode()) == null
                ? ShearKind.TAIL : ShearKind.SLICE;
    }

    /** 开卷机侧按剩余长度先判断切尾，再按已展开长度判断切头。 */
    private ShearKind classifyUncoiler(ShearDeviceSnapshot device, BigDecimal tailExperience) {
        if (device.getRemainingLength().compareTo(tailExperience) <= 0) {
            return ShearKind.TAIL;
        }
        if (device.getMaxLength() == null) {
            throw new IllegalArgumentException("开卷机最大长度缺失，无法判定切头");
        }
        return device.getMaxLength().subtract(device.getRemainingLength())
                .compareTo(tailExperience) <= 0 ? ShearKind.HEAD : ShearKind.SLICE;
    }

    /** 按剪切类型和产线模式选择记录归属物料设备。 */
    private ShearDeviceSnapshot selectMaterial(PointSnapshot snapshot,
                                               ShearTrackingSection tracking,
                                               ShearPointConfig point,
                                               boolean uncoilerSide,
                                               ShearKind kind,
                                               ShearDeviceSnapshot shearDevice,
                                               String shearColor,
                                               List<ShearDeviceSnapshot> devices) {
        if (uncoilerSide) {
            return shearDevice;
        }
        if (tracking.getMode() == ShearMode.CONTINUOUS) {
            if (kind == ShearKind.HEAD) {
                return deviceResolver.findOtherMaterialByColor(devices, shearColor, null);
            }
            if (kind == ShearKind.SLICE && Objects.equals(shearColor, shearDevice.getColorNo())) {
                ShearDeviceSnapshot sameColor = deviceResolver.findOtherMaterialByColor(
                        devices, shearColor, shearDevice.getDeviceCode());
                return sameColor == null ? shearDevice : sameColor;
            }
            return shearDevice;
        }
        if (kind == ShearKind.HEAD) {
            return deviceResolver.selectHeadMaterial(snapshot, tracking, devices);
        }
        if (kind == ShearKind.SLICE && hasText(shearDevice.getCoilNo())) {
            ShearDeviceSnapshot sameCoil = deviceResolver.findOtherMaterialByCoil(
                    devices, shearDevice.getCoilNo(), shearDevice.getDeviceCode());
            if (sameCoil != null) {
                return sameCoil;
            }
        }
        return shearDevice;
    }

    /**
     * 根据更新前的计数推演当前事件组号、刀号和绝对长度差。
     * 达到阈值意味着新组首刀；分切长度随后使用同一个 firstCut 判定归零。
     * 切头、切尾和分切均按物料设备分别计数。
     */
    private CounterDecision nextCounter(ShearCounterRuntime previous,
                                        BigDecimal remainingLength,
                                        BigDecimal experience) {
        if (remainingLength == null) {
            throw new IllegalArgumentException("剪切物料剩余长度无效");
        }
        if (previous == null || previous.getShearNo() == null || previous.getCutNo() == null
                || previous.getLastRemainingLength() == null
                || previous.getShearNo() == 0 || previous.getCutNo() == 0) {
            return new CounterDecision(counter(1, 1, remainingLength), true, null);
        }
        BigDecimal delta = previous.getLastRemainingLength().subtract(remainingLength).abs();
        if (delta.compareTo(experience) >= 0) {
            return new CounterDecision(counter(previous.getShearNo() + 1, 1, remainingLength),
                    true, delta);
        }
        return new CounterDecision(counter(previous.getShearNo(), previous.getCutNo() + 1,
                remainingLength), false, delta);
    }

    /** 使用当前点位和更新前计数计算本刀长度与设定数量，不修改 runtime。 */
    private LengthDecision calculateLength(PointSnapshot snapshot,
                                           ShearTrackingSection tracking,
                                           ShearPointConfig point,
                                           ShearKind kind,
                                           boolean uncoilerSide,
                                           CounterDecision counterDecision,
                                           ShearCounterRuntime oldCounter,
                                           ShearDeviceSnapshot inMatDevice) {
        if (kind == ShearKind.SLICE) {
            return new LengthDecision(counterDecision.firstCut ? ZERO : counterDecision.delta, null);
        }
        ShearSettings settings = point.getShearSettings();
        if (uncoilerSide || tracking.getMode() == ShearMode.DISCONTINUOUS) {
            CutSetting cutSetting = kind == ShearKind.HEAD ? settings.getHead() : settings.getTail();
            return new LengthDecision(requiredDecimal(snapshot, tracking, cutSetting.getLength()),
                    integerValue(snapshot, tracking, cutSetting.getNumber()));
        }
        WelderShearSettings welder = kind == ShearKind.TAIL
                ? settings.getFrontWelder() : settings.getBehindWelder();
        int totalWelderPieces = welderPieces(snapshot, tracking, settings);
        int allocatedWelderPieces = kind == ShearKind.TAIL
                ? (totalWelderPieces + 1) / 2 : totalWelderPieces / 2;
        int samplePieces = integerValue(snapshot, tracking, welder.getSamplePieces());
        int scrapPieces = integerValue(snapshot, tracking, welder.getScrapPieces());
        int setNumber = samplePieces + scrapPieces + allocatedWelderPieces;
        BigDecimal length;
        if (welder.getLength() != null) {
            length = requiredDecimal(snapshot, tracking, welder.getLength());
        } else if (kind == ShearKind.TAIL) {
            int oldCutNo = oldCounter == null || oldCounter.getCutNo() == null
                    ? 0 : oldCounter.getCutNo();
            if (oldCutNo == 0) {
                length = ZERO;
            } else {
                length = requiredDecimal(snapshot, tracking, oldCutNo <= samplePieces + 1
                        ? welder.getSampleLength() : welder.getScrapLength());
            }
        } else {
            int oldCutNo = oldCounter == null || oldCounter.getCutNo() == null
                    ? 0 : oldCounter.getCutNo();
            int scrapBoundary = totalWelderPieces / 2 + scrapPieces;
            length = requiredDecimal(snapshot, tracking, oldCutNo <= scrapBoundary
                    ? welder.getScrapLength() : welder.getSampleLength());
        }
        return new LengthDecision(length, setNumber);
    }

    private ShearCounterRuntime counter(int shearNo, int cutNo, BigDecimal remainingLength) {
        return ShearCounterRuntime.builder().shearNo(shearNo).cutNo(cutNo)
                .lastRemainingLength(remainingLength).build();
    }

    private ShearTrackingRuntime runtime(Map<String, ShearTrackingRuntime> runtimes, String deviceCode) {
        ShearTrackingRuntime runtime = runtimes.get(deviceCode);
        if (runtime == null) {
            throw new IllegalArgumentException("剪切 runtime 不存在: " + deviceCode);
        }
        return runtime;
    }

    private int welderPieces(PointSnapshot snapshot,
                             ShearTrackingSection tracking,
                             ShearSettings settings) {
        if (settings.getWelderPieces() == null) {
            return 0;
        }
        int pieces = integerValue(snapshot, tracking, settings.getWelderPieces());
        if (pieces < 0) {
            throw new IllegalArgumentException("welder_pieces 不能小于 0");
        }
        return pieces;
    }

    private int integerValue(PointSnapshot snapshot,
                             ShearTrackingSection tracking,
                             PointConfig point) {
        return requiredDecimal(snapshot, tracking, point).intValueExact();
    }

    private BigDecimal requiredDecimal(PointSnapshot snapshot,
                                      ShearTrackingSection tracking,
                                      PointConfig point) {
        BigDecimal value = PointReader.decimalValue(snapshot,
                PointReader.pathResolve(tracking.getPointPrefix(), point == null ? null : point.getName()));
        if (value == null) {
            throw new IllegalArgumentException("剪切点位值不存在: "
                    + (point == null ? null : point.getName()));
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 下一刀推演和长度计算共用的结果，避免长度规则重复推导剪切分组。 */
    private static final class CounterDecision {
        private final ShearCounterRuntime nextCounter;
        private final boolean firstCut;
        private final BigDecimal delta;

        private CounterDecision(ShearCounterRuntime nextCounter, boolean firstCut, BigDecimal delta) {
            this.nextCounter = nextCounter;
            this.firstCut = firstCut;
            this.delta = delta;
        }
    }

    /** 剪切长度和设定数量。 */
    private static final class LengthDecision {
        private final BigDecimal length;
        private final Integer setNumber;

        private LengthDecision(BigDecimal length, Integer setNumber) {
            this.length = length;
            this.setNumber = setNumber;
        }
    }
}
