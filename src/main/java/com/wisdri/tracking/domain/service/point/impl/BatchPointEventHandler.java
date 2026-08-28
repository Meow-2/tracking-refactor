package com.wisdri.tracking.domain.service.point.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.batch.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandler;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 批次跟踪点位事件处理服务。
 */
@Component
public class BatchPointEventHandler implements PointEventHandler<BatchTrackingConfig> {
    private static final String TEMPLATE_PLACEHOLDER = "{template}";

    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    @Resource
    private TrackingStepLogger trackingStepLogger;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.BATCH == trackingType;
    }

    /**
     * 最新帧和上一帧的钢卷集合发生变化时刷新全局跟踪配置。
     */
    @Override
    public void handle(TrackingInput input, BatchTrackingConfig config) {
        if (input == null || config == null) {
            return;
        }
        Set<String> latestCoils = coilSet(input.getLatestSnapshot(), config.getTracking(), input.getTemplateCode());
        Set<String> previousCoils = coilSet(input.getPreviousSnapshot(), config.getTracking(), input.getTemplateCode());
        boolean previousAvailable = input.getPreviousSnapshot() != null;
        boolean changed = previousAvailable && !latestCoils.equals(previousCoils);
        trackingStepLogger.log(input, "钢卷集合检查", TrackingStepLogger.details(
                "previousAvailable", previousAvailable,
                "previousCoils", previousCoils,
                "latestCoils", latestCoils,
                "refreshTriggered", changed
        ));
        if (changed) {
            runtimeRepositoryDispatcher.refreshConfig();
        }
    }

    private Set<String> coilSet(PointSnapshot snapshot,
                                TrackingSection tracking,
                                String templateCode) {
        Set<String> coils = new LinkedHashSet<>();
        if (snapshot == null || tracking == null || tracking.getPoints() == null) {
            return coils;
        }
        for (TrackingPointGroup group : tracking.getPoints()) {
            String coilNo = PointReader.stringValue(snapshot,
                    trackingPointPath(tracking, group == null ? null : group.getCoilNo(), templateCode));
            coilNo = trimInvisible(coilNo);
            if (coilNo != null && !coilNo.isEmpty()) {
                coils.add(coilNo);
            }
        }
        return coils;
    }

    private String trackingPointPath(TrackingSection tracking,
                                     PointConfig point,
                                     String templateCode) {
        String prefix = tracking.getPointPrefix();
        if (prefix != null && templateCode != null) {
            prefix = prefix.replace(TEMPLATE_PLACEHOLDER, templateCode);
        }
        return PointReader.pathResolve(prefix, point == null ? null : point.getName());
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
        return Character.isWhitespace(value)
                || Character.isSpaceChar(value)
                || Character.isISOControl(value)
                || Character.getType(value) == Character.FORMAT;
    }
}
