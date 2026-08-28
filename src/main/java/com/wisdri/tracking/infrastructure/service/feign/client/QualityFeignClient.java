package com.wisdri.tracking.infrastructure.service.feign.client;

import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.service.feign.config.QualityFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 质量服务 OpenFeign 客户端。
 */
@FeignClient(
        name = "quality",
        url = "${quality.base-url}",
        configuration = QualityFeignConfig.class
)
public interface QualityFeignClient {
    /**
     * 查询物料下一次生产的重复生产次数。
     */
    @GetMapping("/mat/preprocess/nextProdCount")
    R<Integer> queryNextProductNo(@RequestParam("unitCode") String unitCode,
                                  @RequestParam("matNo") String matNo);
}
