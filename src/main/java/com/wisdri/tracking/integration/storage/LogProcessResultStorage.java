package com.wisdri.tracking.integration.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.port.storage.ProcessResultStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 以日志方式输出过程跟踪结果的存储适配器。
 *
 * <p>用于第三方数据存储接口未接入前，先验证算法输出格式。</p>
 */
@Slf4j
@Component
public class LogProcessResultStorage implements ProcessResultStorage {
    /**
     * JSON 序列化工具。
     */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 将过程跟踪结果以 pretty JSON 形式写入日志。
     */
    @Override
    public void save(List<ProcessResult> results) {
        try {
            log.info("过程跟踪结果: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("过程跟踪结果日志序列化失败", e);
        }
    }
}
