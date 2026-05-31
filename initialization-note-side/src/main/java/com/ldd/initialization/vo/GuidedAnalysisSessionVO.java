package com.ldd.initialization.vo;

import com.ldd.initialization.service.ai.guided.model.AnalysisSession;
import com.ldd.initialization.service.ai.guided.model.AnalysisStep;
import lombok.Data;

import java.util.List;

@Data
public class GuidedAnalysisSessionVO {
    private String sessionId;
    private String moduleType;
    private String status;
    private int currentStepIndex;
    private int totalSteps;
    private List<StepVO> steps;

    @Data
    public static class StepVO {
        private int index;
        private String title;
        private String description;
        private String status;
        private Object result;
        private String aiInterpretation;
    }

    public static GuidedAnalysisSessionVO from(AnalysisSession session) {
        GuidedAnalysisSessionVO vo = new GuidedAnalysisSessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setModuleType(session.getModuleType());
        vo.setStatus(session.getStatus().name());
        vo.setCurrentStepIndex(session.getCurrentStepIndex());
        vo.setTotalSteps(session.getSteps().size());
        vo.setSteps(session.getSteps().stream().map(step -> {
            StepVO sv = new StepVO();
            sv.setIndex(step.getIndex());
            sv.setTitle(step.getTitle());
            sv.setDescription(step.getDescription());
            sv.setStatus(step.getStatus().name());
            sv.setResult(step.getResult());
            sv.setAiInterpretation(step.getAiInterpretation());
            return sv;
        }).toList());
        return vo;
    }
}
