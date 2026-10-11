package com.wisdri.tracking.domain.service.tracking.status;

import com.wisdri.tracking.domain.model.config.status.*;
import java.util.Locale;

/** 实时和历史共用的设备职责判断，不读取运行态。 */
public final class StatusDeviceResolver {
    private StatusDeviceResolver() {
    }

    /** 首道次由 POR 开卷、TR 卷取；后续道次由两侧 TR 换向。 */
    public static DeviceSide positionSide(StatusPointGroup group, int passNo, boolean direction, boolean reverse) {
        DevicePosition source = direction ^ reverse ? DevicePosition.LEFT : DevicePosition.RIGHT;
        boolean sourceDevice = group.getPosition() == source;
        String code = group.getCode().toLowerCase(Locale.ROOT);
        if (passNo == 1) {
            if (sourceDevice && code.startsWith("por")) {
                return DeviceSide.UNCOILER;
            }
            return !sourceDevice && code.startsWith("tr") ? DeviceSide.COILER : null;
        }
        return code.startsWith("tr") ? sourceDevice ? DeviceSide.UNCOILER : DeviceSide.COILER : null;
    }


    /** 未配置物理位置时，沿用原配置侧别的换向规则。 */
    public static DeviceSide side(StatusPointGroup group, int passNo, boolean direction, boolean reverse) {
        if (group.getPosition() != null) {
            return positionSide(group, passNo, direction, reverse);
        }
        DeviceSide side = group.getSide();
        return side == null || !(direction ^ reverse) ? side
                : side == DeviceSide.UNCOILER ? DeviceSide.COILER : DeviceSide.UNCOILER;
    }
}
