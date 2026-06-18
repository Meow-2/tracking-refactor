package com.wisdri.tracking.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtils {

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
