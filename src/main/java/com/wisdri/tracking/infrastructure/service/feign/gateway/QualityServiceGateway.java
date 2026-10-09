package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.domain.model.tracking.status.RollingPassOutput;
import com.wisdri.tracking.common.response.ResponseCode;
import com.wisdri.tracking.infrastructure.dto.feign.quality.CellBloodOutputRequest;
import com.wisdri.tracking.infrastructure.service.feign.client.QualityServiceFeignClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 将旧道次结算数据转换并写入质量服务；只允许异步工作线程调用。 */
@Component
public class QualityServiceGateway {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    @Resource
    private QualityServiceFeignClient client;

    @Resource
    private ExternalServiceRetryExecutor retryExecutor;

    /** 将机组代码转换为接口要求的大写形式；请求和所有重试在调用线程完成，成功条件仅为响应 code=200。 */
    public void write(RollingPassOutput output) {
        CellBloodOutputRequest request = new CellBloodOutputRequest(
                output.getUnitCode().toUpperCase(Locale.ROOT), output.getInMatNo(),
                String.valueOf(output.getInMatRepeatProdNo()), output.getCellCode(),
                TIME_FORMAT.format(output.getStartAt()), TIME_FORMAT.format(output.getEndAt()),
                output.getOutMatThick(), output.getOutMatLength());
        retryExecutor.invoke("质量服务", "cell-blood-output", () -> client.writeCellBloodOutput(request),
                response -> response != null && ResponseCode.SUCCESS.getCode().equals(response.getCode()));
    }
}
