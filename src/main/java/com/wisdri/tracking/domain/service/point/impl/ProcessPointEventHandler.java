package com.wisdri.tracking.domain.service.point.impl;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandler;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.trace.TrackingStepLogger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 过程跟踪点位事件处理服务。
 */
@Component
public class ProcessPointEventHandler implements PointEventHandler<ProcessTrackingConfig> {
    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    @Resource
    private TrackingStepLogger trackingStepLogger;

    /**
     * 仅支持过程跟踪。
     */
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.PROCESS == trackingType;
    }

    /**
     * 处理过程跟踪点位事件。
     */
    @Override
    public void handle(TrackingInput input, ProcessTrackingConfig config) {
        if (config == null) {
            return;
        }
        PointSnapshot latest = input == null ? null : input.getLatestSnapshot();
        PointSnapshot previous = input == null ? null : input.getPreviousSnapshot();
        Set<String> latestCoils = coilSet(latest, config);
        Set<String> previousCoils = coilSet(previous, config);
        boolean changed = previous != null && !latestCoils.equals(previousCoils);
        trackingStepLogger.log(input, "钢卷集合检查", TrackingStepLogger.details(
                "previousAvailable", previous != null,
                "previousCoils", previousCoils,
                "latestCoils", latestCoils,
                "refreshTriggered", changed
        ));
        if (!changed) {
            return;
        }
        runtimeRepositoryDispatcher.refreshConfig();
    }

    /**
     * 提取配置中所有钢卷号点位对应的非空钢卷号集合。
     */
    private Set<String> coilSet(PointSnapshot snapshot, ProcessTrackingConfig processConfig) {
        Set<String> coils = new LinkedHashSet<>();
        if (snapshot == null) {
            return coils;
        }
        if (processConfig.getTracking() == null || processConfig.getTracking().getPoints() == null) {
            return coils;
        }
        for (TrackingPointGroup group : processConfig.getTracking().getPoints()) {
            String coilNo = PointReader.stringValue(snapshot, trackingPointPath(processConfig, group.getCoilNo()));
            if (coilNo != null && !coilNo.trim().isEmpty()) {
                coils.add(coilNo.trim());
            }
        }
        return coils;
    }

    /**
     * 构造跟踪段完整点位路径。
     */
    private String trackingPointPath(ProcessTrackingConfig processConfig, PointConfig point) {
        String prefix = processConfig.getTracking() == null ? null : processConfig.getTracking().getPointPrefix();
        return PointReader.pathResolve(prefix, point == null ? null : point.getName());
    }

}
