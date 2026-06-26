package com.wisdri.tracking.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wisdri.tracking.common.exception.TrackingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtils {

    /**
     * 创建保留小数精度的 Jackson mapper。
     */
    public static ObjectMapper decimalPreservingMapper() {
        return decimalPreservingMapperBuilder().build();
    }

    /**
     * 创建保留小数精度的 Jackson mapper builder。
     * <p>
     * 调用方可以在该基础配置上追加命名策略、时间模块等场景配置。
     */
    public static JsonMapper.Builder decimalPreservingMapperBuilder() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 序列化为格式化 JSON。
     */
    public static String toPrettyJson(Object value) {
        try {
            return toPrettyJson(decimalPreservingMapper(), value);
        } catch (JsonProcessingException e) {
            throw new TrackingException("对象序列化为 JSON 失败", e);
        }
    }

    /**
     * 序列化为格式化 JSON。
     */
    public static String toPrettyJson(ObjectMapper objectMapper, Object value) throws JsonProcessingException {
        return objectMapper.writer(arrayLineFeedPrinter()).writeValueAsString(value);
    }

    /**
     * 创建数组元素换行的 Jackson pretty printer。
     */
    public static DefaultPrettyPrinter arrayLineFeedPrinter() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter("  ", System.lineSeparator());
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

}
