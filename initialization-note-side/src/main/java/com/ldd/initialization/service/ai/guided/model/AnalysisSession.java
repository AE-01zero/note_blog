package com.ldd.initialization.service.ai.guided.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class AnalysisSession {
    private String sessionId;
    private String userId;
    private String moduleType;
    private SessionStatus status = SessionStatus.PLANNING;
    private List<AnalysisStep> steps = new ArrayList<>();
    private int currentStepIndex = 0;
    private Map<String, Object> context = new HashMap<>();
    private String filePath;
    private String fileName;
    private Instant createdAt;
    private Instant lastActiveAt;

    public enum SessionStatus {
        PLANNING, EXECUTING, WAITING, COMPLETED, TERMINATED
    }

    public AnalysisStep getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    public boolean hasNextStep() {
        return currentStepIndex < steps.size() - 1;
    }
}
