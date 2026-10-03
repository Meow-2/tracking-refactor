package com.wisdri.tracking.infrastructure.service.rocketmq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证旧版在途消息仍可读取，重新发送时只输出新字段。 */
class RepeatProdNoMessageCompatibilityTest {
    private final ObjectMapper mapper = JsonUtils.decimalPreservingMapperBuilder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    @Test
    void readsLegacyProductNoAndSerializesRepeatProdNo() throws Exception {
        String oldMessage = "{\"unitCode\":\"CP1\",\"trackingType\":\"COILER\","
                + "\"statusContext\":{\"candidates\":{\"TR1\":{\"coilNo\":\"C001\","
                + "\"productNo\":2}},\"current\":{\"UNCOILER\":{\"coilNo\":\"C001\","
                + "\"productNo\":2}},\"results\":[{\"coilNo\":\"C001\",\"productNo\":2}]}}";

        TrackingInput restored = mapper.readValue(oldMessage, TrackingInput.class);

        assertThat(restored.getStatusContext().getCandidates().get("TR1").getRepeatProdNo()).isEqualTo(2);
        assertThat(restored.getStatusContext().getCurrent().values().iterator().next()
                .getRepeatProdNo()).isEqualTo(2);
        assertThat(restored.getStatusContext().getResults().get(0).getRepeatProdNo()).isEqualTo(2);
        String newMessage = mapper.writeValueAsString(restored);
        assertThat(newMessage).contains("\"repeatProdNo\":2");
        assertThat(newMessage).doesNotContain("\"productNo\"");
    }
}
