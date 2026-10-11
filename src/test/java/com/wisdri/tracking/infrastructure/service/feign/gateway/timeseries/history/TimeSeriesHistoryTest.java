package com.wisdri.tracking.infrastructure.service.feign.gateway.timeseries.history;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.history.*;
import com.wisdri.tracking.infrastructure.service.feign.client.timeseries.history.TimeSeriesHistoryFeignClient;
import com.wisdri.tracking.infrastructure.service.feign.config.timeseries.history.TimeSeriesHistoryFeignConfig;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TimeSeriesHistoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void actualFeignDecoderPreservesTimeDecimalAndExistingWriteResponse() throws Exception {
        String json = "{\"success\":true,\"msg\":\"ok\",\"data\":[{\"database\":\"other_db\",\"table\":\"object_b\",\"field\":\"length\",\"found\":true,\"sourceTime\":\"2026-10-09 00:00:00.123456789\",\"value\":1234.567890123456789}]}";
        R<List<Latest>> result = (R<List<Latest>>) new TimeSeriesHistoryFeignConfig().timeSeriesHistoryDecoder()
                .decode(response(json), new ParameterizedTypeReference<R<List<Latest>>>() {}.getType());
        assertThat(R.isSuccess(result)).isTrue();
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getData().get(0).getDatabase()).isEqualTo("other_db");
        assertThat(result.getData().get(0).getTable()).isEqualTo("object_b");
        assertThat(result.getData().get(0).getSourceTime()).isEqualTo("2026-10-09 00:00:00.123456789");
        assertThat(result.getData().get(0).getValue().decimalValue()).isEqualByComparingTo(new BigDecimal("1234.567890123456789"));
        R<Boolean> write = (R<Boolean>) new TimeSeriesHistoryFeignConfig().timeSeriesHistoryDecoder()
                .decode(response("{\"success\":true,\"data\":true}"), new ParameterizedTypeReference<R<Boolean>>() {}.getType());
        assertThat(write.getData()).isTrue();
    }

    @Test
    void gatewayPassesRequestsAndAcceptsEmptyListAndEmptyBounds() {
        TimeSeriesHistoryFeignClient client = mock(TimeSeriesHistoryFeignClient.class);
        TimeSeriesHistoryGateway gateway = gateway(client);
        StatesRequest states = new StatesRequest();
        states.setStartTime("2026-10-09 00:00:00.123456789");
        when(client.queryStates(states)).thenReturn(success(Collections.emptyList()));
        BoundsRequest bounds = new BoundsRequest();
        bounds.setField("speed_pv");
        bounds.setMin(new BigDecimal("0.5"));
        Bounds emptyBounds = new Bounds();
        when(client.queryBounds(bounds)).thenReturn(success(emptyBounds));
        LatestRequest latest = new LatestRequest();
        when(client.queryLatest(latest)).thenReturn(success(Collections.emptyList()));
        assertThat(gateway.queryStates(states)).isEmpty();
        assertThat(gateway.queryBounds(bounds)).isSameAs(emptyBounds);
        assertThat(gateway.queryLatest(latest)).isEmpty();
        verify(client).queryStates(states);
        verify(client).queryBounds(bounds);
        verify(client).queryLatest(latest);
    }

    @Test
    void queryFailureMissingDataAndTransportErrorsPropagateWithoutRetryOrDetails() {
        TimeSeriesHistoryFeignClient client = mock(TimeSeriesHistoryFeignClient.class);
        TimeSeriesHistoryGateway gateway = gateway(client);
        when(client.queryStates(any())).thenReturn(R.fail());
        assertThatThrownBy(() -> gateway.queryStates(new StatesRequest())).isInstanceOf(ExternalServiceException.class);
        when(client.queryBounds(any())).thenReturn(success(null));
        assertThatThrownBy(() -> gateway.queryBounds(new BoundsRequest())).isInstanceOf(ExternalServiceException.class);
        when(client.queryLatest(any())).thenThrow(new IllegalStateException("private connection details"));
        assertThatThrownBy(() -> gateway.queryLatest(new LatestRequest())).isInstanceOf(ExternalServiceException.class)
                .hasMessage("quality-ts 历史查询请求失败").hasNoCause();
        verify(client, times(1)).queryLatest(any());
    }

    private static TimeSeriesHistoryGateway gateway(TimeSeriesHistoryFeignClient client) {
        TimeSeriesHistoryGateway gateway = new TimeSeriesHistoryGateway();
        ReflectionTestUtils.setField(gateway, "timeSeriesHistoryFeignClient", client);
        return gateway;
    }

    private static <T> R<T> success(T data) {
        R<T> result = new R<>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    private static Response response(String json) {
        Map<String, Collection<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        return Response.builder().status(200).reason("OK").headers(headers)
                .request(Request.create(Request.HttpMethod.POST, "http://localhost/data/query/latest",
                        Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
                .body(json, StandardCharsets.UTF_8).build();
    }
}
