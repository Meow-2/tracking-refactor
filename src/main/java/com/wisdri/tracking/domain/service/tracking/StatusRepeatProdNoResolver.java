package com.wisdri.tracking.domain.service.tracking;

import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCoilCacheEntry;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;

import java.util.Objects;

/** 按钢卷号读取任务中固化的 STATUS 生产次数，供过程与铁损跟踪共用。 */
public final class StatusRepeatProdNoResolver {
    private StatusRepeatProdNoResolver() {
    }

    /** 当前设备优先，其次候选设备；状态缺失或钢卷未匹配时返回 null。 */
    public static Integer find(StatusTrackingContext context, String coilNo) {
        if (context == null) {
            return null;
        }
        if (context.getCurrent() != null) {
            for (StatusCurrentRuntime current : context.getCurrent().values()) {
                if (current != null && Objects.equals(coilNo, current.getCoilNo())) {
                    return current.getRepeatProdNo();
                }
            }
        }
        if (context.getCandidates() != null) {
            for (StatusCandidateRuntime candidate : context.getCandidates().values()) {
                if (candidate != null && Objects.equals(coilNo, candidate.getCoilNo())) {
                    return candidate.getRepeatProdNo();
                }
            }
        }
        StatusCoilCacheEntry cached = context.getCoilCache() == null || coilNo == null
                ? null : context.getCoilCache().get(coilNo);
        return cached == null ? null : cached.getRepeatProdNo();
    }
}
