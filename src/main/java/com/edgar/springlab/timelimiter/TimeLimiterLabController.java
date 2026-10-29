package com.edgar.springlab.timelimiter;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/lab/time-limiter")
@RequiredArgsConstructor
public class TimeLimiterLabController {

    private final TimeLimiterLabService labService;

    @PostMapping("/call")
    public Map<String, Object> call(
        @RequestParam(defaultValue = "1500") long durationMillis
    ) {
        return labService.call(durationMillis);
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return labService.state();
    }
}
