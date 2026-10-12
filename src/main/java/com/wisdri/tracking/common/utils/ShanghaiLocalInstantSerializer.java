package com.wisdri.tracking.common.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 将绝对时间展示为上海本地 ISO 时间，不带时区后缀，保留原始小数秒精度。 */
public class ShanghaiLocalInstantSerializer extends JsonSerializer<Instant> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            .withZone(ZoneId.of("Asia/Shanghai"));

    /** 仅改变 JSON 展示，不改变模型中的 Instant；空值由 Jackson 默认处理。 */
    @Override
    public void serialize(Instant value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        generator.writeString(FORMATTER.format(value));
    }
}
