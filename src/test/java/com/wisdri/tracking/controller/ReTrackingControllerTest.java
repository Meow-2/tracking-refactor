package com.wisdri.tracking.controller;

import com.wisdri.tracking.application.usecase.passHistoryTracking.PassHistoryTrackingUseCase;
import com.wisdri.tracking.application.usecase.passHistoryTracking.PassOutputResult;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.tracking.status.RollingPassOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReTrackingControllerTest {
    private static final String URL = "/openapi/retracking/zrm1/status/passHistory";
    private final PassHistoryTrackingUseCase useCase = mock(PassHistoryTrackingUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ReTrackingController controller = new ReTrackingController();
        ReflectionTestUtils.setField(controller, "passHistoryTrackingUseCase", useCase);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonUtils.decimalPreservingMapper())).build();
    }

    @Test
    void returnsActualPassResultsSynchronouslyWithShanghaiInputPrecision() throws Exception {
        PassOutputResult success = result("SUCCESS", null);
        PassOutputResult skipped = result("SKIPPED", "没有速度达标帧");
        PassOutputResult failed = result("FAILED", "质量写入失败");
        when(useCase.execute(anyString(), any(), any())).thenReturn(Arrays.asList(success, skipped, failed));
        mvc.perform(post(URL).contentType("application/json")
                        .content(request("2026-10-09 00:00:00.123456789", "2026-11-09 00:00:00.123456789")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[1].reason").value("没有速度达标帧"))
                .andExpect(jsonPath("$.data[2].status").value("FAILED"))
                .andExpect(jsonPath("$.taskId").doesNotExist());
        verify(useCase).execute("zrm1", LocalDateTime.parse("2026-10-09T00:00:00.123456789"),
                LocalDateTime.parse("2026-11-09T00:00:00.123456789"));
    }

    @Test
    void displaysOutputTimesInShanghaiWithPrecisionAndDayRollover() throws Exception {
        Instant startAt = Instant.parse("2026-09-20T11:58:57.406Z");
        Instant endAt = Instant.parse("2026-09-20T16:04:11.123456789Z");
        RollingPassOutput output = RollingPassOutput.builder().startAt(startAt).endAt(endAt).build();
        PassOutputResult success = result("SUCCESS", null);
        success.setSwitchTime(LocalDateTime.parse("2026-09-21T00:05:50.406"));
        success.setOutput(output);
        when(useCase.execute(anyString(), any(), any())).thenReturn(Collections.singletonList(success));

        mvc.perform(post(URL).contentType("application/json")
                        .content(request("2026-09-20 19:00:00", "2026-09-21 01:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].switchTime").value("2026-09-21T00:05:50.406"))
                .andExpect(jsonPath("$.data[0].output.startAt").value("2026-09-20T19:58:57.406"))
                .andExpect(jsonPath("$.data[0].output.endAt").value("2026-09-21T00:04:11.123456789"));
        // 返回时只格式化展示，质量网关继续使用原始绝对时间。
        assertThat(output.getStartAt()).isEqualTo(startAt);
        assertThat(output.getEndAt()).isEqualTo(endAt);
    }

    @Test
    void rejectsInvalidDatesAndOrderBeforeQuerying() throws Exception {
        for (String body : Arrays.asList("{}", "{", request("2026-02-30 00:00:00", "2026-03-01 00:00:00"),
                request("2026-10-09T00:00:00Z", "2026-10-10 00:00:00"),
                request("2026-10-09 00:00:00", "2026-10-09 00:00:00"),
                request("2026-10-10 00:00:00", "2026-10-09 00:00:00"))) {
            mvc.perform(post(URL).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void returnsEmptyListForNoEvents() throws Exception {
        when(useCase.execute(anyString(), any(), any())).thenReturn(Collections.emptyList());
        mvc.perform(post(URL).contentType("application/json")
                        .content(request("2026-10-09 00:00:00", "2026-10-09 01:00:00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/openapi/retracking/tasks/unknown")).andExpect(status().isNotFound());
    }

    private String request(String start, String end) {
        return "{\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    private PassOutputResult result(String status, String reason) {
        PassOutputResult result = new PassOutputResult();
        result.setPassNo(1);
        result.setStatus(status);
        result.setReason(reason);
        return result;
    }
}
