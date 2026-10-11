package com.wisdri.tracking.infrastructure.service.feign.config.timeseries.history;

import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.infrastructure.properties.feign.timeseries.history.TimeSeriesHistoryProperties;
import feign.Request;
import feign.codec.Decoder;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.concurrent.TimeUnit;

/** 仅由历史查询客户端引用，不注册为全局配置。 */
public class TimeSeriesHistoryFeignConfig {
    /** 保留点位小数精度，时间字符串原样传递。 */
    @Bean
    public Decoder timeSeriesHistoryDecoder() {
        return new SpringDecoder(() -> new HttpMessageConverters(
                new MappingJackson2HttpMessageConverter(JsonUtils.decimalPreservingMapper())));
    }

    /** 使用历史查询独立超时配置，默认读取超时一分钟。 */
    @Bean
    public Request.Options timeSeriesHistoryRequestOptions(TimeSeriesHistoryProperties properties) {
        return new Request.Options(properties.getConnectTimeout(), TimeUnit.MILLISECONDS,
                properties.getReadTimeout(), TimeUnit.MILLISECONDS, true);
    }
}
