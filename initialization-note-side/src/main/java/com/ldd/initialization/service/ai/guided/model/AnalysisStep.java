package com.ldd.initialization.service.ai.guided.model;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class AnalysisStep {
    private int index;
    private String title;
    private String description;
    private String executorMethod;
    private StepStatus status = StepStatus.PENDING;
    private Map<String, Object> result;
    private String aiInterpretation;
    private Instant startedAt;
    private Instant completedAt;

    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, SKIPPED, FAILED
    }
}
