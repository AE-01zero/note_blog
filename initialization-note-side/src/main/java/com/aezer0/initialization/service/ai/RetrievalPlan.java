package com.aezer0.initialization.service.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalPlan {

    public enum RouteType {
        KEYWORD,
        VECTOR,
        HYBRID,
        SUMMARY_FIRST
    }

    private RouteType routeType;
    private String reason;
    private int maxResults;
    private double minScore;
    private int maxContextSegments;
    private int maxContextChars;
    private boolean preferFileSummary;
}
