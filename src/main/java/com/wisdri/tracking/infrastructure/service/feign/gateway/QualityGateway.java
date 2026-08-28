package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.domain.repository.quality.QualityRepository;
import com.wisdri.tracking.infrastructure.service.feign.client.QualityFeignClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 质量服务访问网关。
 */
@Component
public class QualityGateway implements QualityRepository {
    @Resource
    private QualityFeignClient qualityFeignClient;

    @Override
    public Integer queryProductNo(String unitCode, String coilNo) {
        R<Integer> response = qualityFeignClient.queryNextProductNo(unitCode, coilNo);
        if (!R.isSuccess(response)) {
            String message = response == null ? null : response.getMessage();
            throw new ExternalServiceException(message == null || message.trim().isEmpty()
                    ? "物料重复生产次数查询失败"
                    : "物料重复生产次数查询失败: " + message);
        }
        return response.getData();
    }
}
