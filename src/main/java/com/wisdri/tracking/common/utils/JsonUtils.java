package com.wisdri.tracking.common.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wisdri.tracking.common.exception.TrackingException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtils {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(DISPLAY_ZONE);

    /**
     * 创建保留小数精度的 Jackson mapper。
     */
    public static ObjectMapper decimalPreservingMapper() {
        return decimalPreservingMapperBuilder().build();
    }

    /**
     * 创建用于日志展示的 mapper，将 Instant 格式化为亚洲上海时间。
     */
    public static ObjectMapper shanghaiTimeDisplayMapper() {
        return shanghaiTimeDisplayMapperBuilder().build();
    }

    /**
     * 创建将 Instant 格式化为亚洲上海时间的 mapper builder。
     */
    public static JsonMapper.Builder shanghaiTimeDisplayMapperBuilder() {
        SimpleModule displayTimeModule = new SimpleModule();
        displayTimeModule.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator generator, SerializerProvider serializers)
                    throws IOException {
                generator.writeString(DISPLAY_TIME_FORMATTER.format(value));
            }
        });
        displayTimeModule.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            @Override
            public Instant deserialize(JsonParser parser, DeserializationContext context)
                    throws IOException {
                return OffsetDateTime.parse(
                        parser.getText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME
                ).toInstant();
            }
        });
        return decimalPreservingMapperBuilder()
                .addModule(displayTimeModule);
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
            return toPrettyJson(prettyJsonMapper(), value);
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

    private static ObjectMapper prettyJsonMapper() {
        return shanghaiTimeDisplayMapper();
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
