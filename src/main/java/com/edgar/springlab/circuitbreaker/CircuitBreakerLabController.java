package com.edgar.springlab.circuitbreaker;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/lab/circuit-breaker")
@RequiredArgsConstructor
public class CircuitBreakerLabController {

    private final CircuitBreakerLabService labService;

    @PostMapping("/call")
    public Map<String, Object> call(
        @RequestParam(defaultValue = "false") boolean fail
    ) {
        return labService.call(fail);
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return labService.state();
    }

    @GetMapping("/monitor")
    public Map<String, Object> monitor() {
        return labService.monitor();
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        return labService.reset();
    }
}
