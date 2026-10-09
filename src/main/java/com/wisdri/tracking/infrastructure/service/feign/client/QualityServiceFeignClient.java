package com.wisdri.tracking.infrastructure.service.feign.client;

import com.wisdri.tracking.infrastructure.dto.feign.quality.CellBloodOutputRequest;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.service.feign.config.QualityServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 质量服务机组内加工单元数据接口。
 * Kubernetes 使用 QUALITY_BASEURL；Feign 占位符先按环境变量可识别的驼峰名称取值，再回退到 YAML 配置。
 */
@FeignClient(name = "quality", url = "${quality.baseUrl:${quality.base-url}}",
        configuration = QualityServiceFeignConfig.class)
public interface QualityServiceFeignClient {
    /** 写入已完成道次的机组内物料数据。 */
    @PostMapping("/api/quality/material/cell-blood/output")
    R<Void> writeCellBloodOutput(@RequestBody CellBloodOutputRequest request);
}
