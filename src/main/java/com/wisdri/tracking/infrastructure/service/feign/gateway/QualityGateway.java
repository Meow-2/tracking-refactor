package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.domain.repository.quality.QualityRepository;
import org.springframework.stereotype.Component;

/**
 * 质量服务访问网关。
 *
 * <p>生产次数查询接口契约确定前固定返回 1，后续在此处调用 QualityFeignClient
 * 并从 {@code R<Integer>.data} 中提取结果。</p>
 */
@Component
public class QualityGateway implements QualityRepository {
    @Override
    public Integer queryProductNo(String coilNo) {
        return 1;
    }
}
