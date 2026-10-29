package com.edgar.springlab.bulkhead;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/lab/bulkhead")
@RequiredArgsConstructor
public class BulkheadLabController {

    private final BulkheadLabService labService;

    @PostMapping("/call")
    public Map<String, Object> call(
        @RequestParam(defaultValue = "3000") long durationMillis
    ) {
        return labService.call(durationMillis);
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return labService.state();
    }

    @PostMapping("/reset")
    public Map<String, Object> resetCounters() {
        return labService.resetCounters();
    }
}
