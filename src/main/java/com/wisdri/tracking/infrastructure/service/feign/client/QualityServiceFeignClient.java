package com.wisdri.tracking.infrastructure.service.feign.client;

import com.wisdri.tracking.infrastructure.dto.feign.quality.CellBloodOutputRequest;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.service.feign.config.QualityServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 质量服务机组内加工单元数据接口。
 * 从 Spring 环境读取 quality.base-url；Kubernetes 的 QUALITY_BASEURL 可覆盖 YAML 默认值。
 */
@FeignClient(name = "quality", url = "${quality.base-url}",
        configuration = QualityServiceFeignConfig.class)
public interface QualityServiceFeignClient {
    /** 写入已完成道次的机组内物料数据。 */
    @PostMapping("/api/quality/material/cell-blood/output")
    R<Void> writeCellBloodOutput(@RequestBody CellBloodOutputRequest request);
}
