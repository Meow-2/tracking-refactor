package com.wisdri.tracking.controller;

import com.wisdri.tracking.application.usecase.passHistoryTracking.PassHistoryTrackingUseCase;
import com.wisdri.tracking.application.usecase.passHistoryTracking.PassOutputResult;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.controller.retracking.PassHistoryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/openapi/retracking")
public class ReTrackingController {
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss").optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
            .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    @Resource
    private PassHistoryTrackingUseCase passHistoryTrackingUseCase;

    /** 同步返回每道次的实际写入结果，不创建后台任务。 */
    @PostMapping("/zrm1/status/passHistory")
    public R<List<PassOutputResult>> passOutput(@Valid @RequestBody PassHistoryRequest request) {
        LocalDateTime start = parse(request.getStartTime());
        LocalDateTime end = parse(request.getEndTime());
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime 必须早于 endTime");
        }
        return R.data(passHistoryTrackingUseCase.execute("zrm1", start, end));
    }

    private LocalDateTime parse(String time) {
        try {
            if (time == null || !time.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?")) {
                throw new IllegalArgumentException();
            }
            return LocalDateTime.parse(time, TIME);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "时间格式应为 yyyy-MM-dd HH:mm:ss，可带 1～9 位小数秒");
        }
    }

}
