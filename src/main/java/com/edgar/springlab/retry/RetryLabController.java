package com.edgar.springlab.retry;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/lab/retry")
@RequiredArgsConstructor
public class RetryLabController {

    private final RetryLabService labService;

    @PostMapping("/call")
    public Map<String, Object> call(
        @RequestParam(defaultValue = "recover") String scenario
    ) {
        return labService.call(scenario);
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return labService.state();
    }
}
