package com.wisdri.tracking.domain.service.tracking.shear;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.shear.GratingPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.tracking.shear.ShearDeviceSnapshot;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.service.point.PointReader;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 将 status candidates 按配置顺序转换为剪切设备快照，并集中处理设备、颜色、卷号和光栅匹配。
 */
@Component
public class ShearDeviceResolver {
    /**
     * 严格按 status 设备配置顺序构造候选快照，不读取 current，也不接受未配置的候选。
     * current 是延迟保留的侧别识别结果，不代表本帧所有 candidates 的设备数据，不能参与剪切选料。
     *
     * @param context 本帧 status candidates
     * @param config status 设备配置；用于确定候选设备顺序和设备侧别
     * @return 稳定有序的设备快照列表
     */
    public List<ShearDeviceSnapshot> resolveCandidates(StatusTrackingContext context,
                                                        StatusTrackingConfig config) {
        if (context == null || context.getCandidates() == null || config == null
                || config.getTracking() == null || config.getTracking().getPoints() == null) {
            return Collections.emptyList();
        }
        List<ShearDeviceSnapshot> devices = new ArrayList<>();
        Set<String> resolvedCodes = new HashSet<>();
        for (StatusPointGroup group : config.getTracking().getPoints()) {
            if (group == null || group.getCode() == null || !resolvedCodes.add(group.getCode())) {
                continue;
            }
            StatusCandidateRuntime candidate = context.getCandidates().get(group.getCode());
            if (candidate == null || !group.getCode().equals(candidate.getDeviceCode())) {
                continue;
            }
            devices.add(snapshot(group, candidate));
        }
        return devices;
    }

    /**
     * 按剪刀绑定设备代码顺序选择 candidates 中的首个设备。
     * 连续线出口剪要求卷号非空；其他位置允许空卷，以便进入出口切头逻辑。
     *
     * @param point 当前剪刀配置
     * @param devices status 配置顺序的设备快照
     * @param requireCoilNo 是否要求触发设备当前卷号非空
     * @return 命中的设备；无候选匹配时返回 {@code null}
     */
    public ShearDeviceSnapshot resolveShearDevice(ShearPointConfig point,
                                                   List<ShearDeviceSnapshot> devices,
                                                   boolean requireCoilNo) {
        for (String code : deviceCodes(point)) {
            for (ShearDeviceSnapshot device : devices) {
                if (code.equals(device.getDeviceCode())
                        && (!requireCoilNo || hasText(device.getCoilNo()))) {
                    return device;
                }
            }
        }
        return null;
    }

    /**
     * 按 status 配置顺序查找除指定设备外的首个同色完整物料。
     *
     * @param devices status 配置顺序的设备快照
     * @param colorNo 已规范化颜色号
     * @param excludedDeviceCode 不参与匹配的设备代码；为空时不排除设备
     * @return 首个匹配物料；没有匹配时返回 {@code null}
     */
    public ShearDeviceSnapshot findOtherMaterialByColor(List<ShearDeviceSnapshot> devices,
                                                         String colorNo,
                                                         String excludedDeviceCode) {
        for (ShearDeviceSnapshot device : devices) {
            if (!Objects.equals(excludedDeviceCode, device.getDeviceCode())
                    && device.hasCompleteMaterial()
                    && Objects.equals(colorNo, device.getColorNo())) {
                return device;
            }
        }
        return null;
    }

    /**
     * 按 status 配置顺序查找除触发设备外的首个同卷完整物料。
     *
     * @param devices status 配置顺序的设备快照
     * @param coilNo 需要匹配的卷号
     * @param excludedDeviceCode 不参与匹配的触发设备代码
     * @return 首个匹配物料；没有匹配时返回 {@code null}
     */
    public ShearDeviceSnapshot findOtherMaterialByCoil(List<ShearDeviceSnapshot> devices,
                                                        String coilNo,
                                                        String excludedDeviceCode) {
        for (ShearDeviceSnapshot device : devices) {
            if (!Objects.equals(excludedDeviceCode, device.getDeviceCode())
                    && device.hasCompleteMaterial()
                    && Objects.equals(coilNo, device.getCoilNo())) {
                return device;
            }
        }
        return null;
    }

    /**
     * 非连续线出口剪切头从入口剪对应设备中优先选择唯一的全占位设备；命中数量不为一时取长度最小者。
     *
     * @param snapshot 当前点位快照，用于读取入口剪光栅状态
     * @param shearConfig 当前机组剪切配置
     * @param devices status 配置顺序的候选快照
     * @return 被选中的开卷机物料；没有有效开卷机时返回 {@code null}
     */
    public ShearDeviceSnapshot selectHeadMaterial(PointSnapshot snapshot,
                                                  ShearTrackingSection shearConfig,
                                                  List<ShearDeviceSnapshot> devices) {
        List<ShearDeviceSnapshot> uncoilers = new ArrayList<>();
        for (ShearDeviceSnapshot device : devices) {
            if (device.getSide() == DeviceSide.UNCOILER && device.hasCompleteMaterial()) {
                uncoilers.add(device);
            }
        }
        List<ShearDeviceSnapshot> occupied = new ArrayList<>();
        Set<String> occupiedCodes = new HashSet<>();
        List<ShearPointConfig> entryPoints = shearConfig.getUncoilerShearPoint();
        if (entryPoints != null) {
            for (ShearPointConfig point : entryPoints) {
                List<String> codes = deviceCodes(point);
                for (ShearDeviceSnapshot device : uncoilers) {
                    if (codes.contains(device.getDeviceCode())
                            && allGratingsOccupied(snapshot, shearConfig, point)
                            && occupiedCodes.add(device.getDeviceCode())) {
                        occupied.add(device);
                    }
                }
            }
        }
        if (occupied.size() == 1) {
            return occupied.get(0);
        }
        ShearDeviceSnapshot shortest = null;
        for (ShearDeviceSnapshot device : uncoilers) {
            if (device.getRemainingLength() != null
                    && (shortest == null || device.getRemainingLength()
                    .compareTo(shortest.getRemainingLength()) < 0)) {
                shortest = device;
            }
        }
        return shortest;
    }

    /**
     * 读取并规范化连续线剪刀颜色点。
     *
     * @param snapshot 当前帧点位快照
     * @param tracking 剪切点位前缀配置
     * @param point 当前剪刀配置
     * @return 去除不可见字符并数值规范化后的颜色；点位无效时返回 {@code null}
     */
    public String shearColor(PointSnapshot snapshot,
                             ShearTrackingSection tracking,
                             ShearPointConfig point) {
        return normalizedColor(PointReader.stringValue(snapshot,
                path(tracking, point.getColorPoint())));
    }

    /**
     * 判断一把剪刀绑定的所有光栅是否均处于各自配置的占位值。
     *
     * @param snapshot 当前帧点位快照
     * @param tracking 剪切点位前缀配置
     * @param point 绑定光栅点位的剪刀配置
     * @return 所有光栅均有效且匹配各自 hasCoil 值时为 true；缺失或异常值视为未全部占位
     */
    public boolean allGratingsOccupied(PointSnapshot snapshot,
                                       ShearTrackingSection tracking,
                                       ShearPointConfig point) {
        List<GratingPointConfig> gratings = point.getGratingPoints();
        if (gratings == null || gratings.isEmpty()) {
            return false;
        }
        for (GratingPointConfig grating : gratings) {
            if (grating == null || grating.getHasCoil() == null) {
                return false;
            }
            Boolean value = booleanValue(snapshot, path(tracking, grating.getName()));
            if (value == null || !grating.getHasCoil().equals(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 读取 boolean 点位，无法识别的值按无效值返回空。
     *
     * @param snapshot 待读取的点位快照
     * @param pointPath 已解析的完整点位路径
     * @return 解析后的布尔值；缺失或不可识别时返回 {@code null}
     */
    public Boolean booleanValue(PointSnapshot snapshot, String pointPath) {
        Object value = PointReader.rawValue(snapshot, pointPath);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized)) {
            return Boolean.TRUE;
        }
        if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private ShearDeviceSnapshot snapshot(StatusPointGroup group,
                                         StatusCandidateRuntime candidate) {
        List<BigDecimal> lengths = candidate.getLengths();
        BigDecimal remainingLength = lengths == null || lengths.isEmpty()
                ? null : lengths.get(lengths.size() - 1);
        boolean complete = !Boolean.FALSE.equals(candidate.getDataComplete())
                && hasText(candidate.getCoilNo()) && candidate.getProductNo() != null
                && remainingLength != null;
        return ShearDeviceSnapshot.builder()
                .side(group.getSide())
                .deviceCode(candidate.getDeviceCode())
                .deviceName(candidate.getDeviceName() == null ? group.getName() : candidate.getDeviceName())
                .coilNo(candidate.getCoilNo())
                .productNo(candidate.getProductNo())
                .colorNo(normalizedColor(candidate.getColorNo()))
                .remainingLength(remainingLength)
                .maxLength(candidate.getMaxLength())
                .dataComplete(complete)
                .build();
    }

    private List<String> deviceCodes(ShearPointConfig point) {
        if (point.getDeviceCodes() != null && !point.getDeviceCodes().isEmpty()) {
            return point.getDeviceCodes();
        }
        return point.getDeviceCode() == null
                ? Collections.emptyList() : Collections.singletonList(point.getDeviceCode());
    }

    private String path(ShearTrackingSection tracking, PointConfig point) {
        return PointReader.pathResolve(tracking.getPointPrefix(), point == null ? null : point.getName());
    }

    private String path(ShearTrackingSection tracking, String pointName) {
        return PointReader.pathResolve(tracking.getPointPrefix(), pointName);
    }

    private String normalizedColor(String value) {
        String trimmed = trimInvisible(value);
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    private String trimInvisible(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && invisible(value.charAt(start))) {
            start++;
        }
        while (end > start && invisible(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean invisible(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value)
                || Character.isISOControl(value) || Character.getType(value) == Character.FORMAT;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
