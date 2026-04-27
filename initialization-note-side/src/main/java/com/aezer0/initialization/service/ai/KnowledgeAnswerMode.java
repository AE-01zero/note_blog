package com.aezer0.initialization.service.ai;

public enum KnowledgeAnswerMode {

    STRICT_KB(
            "strict_kb",
            "严格知识库",
            false,
            "仅依据命中的知识库内容作答；资料不足时必须明确说明，不补全未证实细节。"
    ),
    KB_HYBRID_REASONING(
            "kb_hybrid_reasoning",
            "知识库+混合思考",
            true,
            "以知识库内容为主；知识库有依据的部分按依据回答，知识库不足的部分继续用通用知识和逻辑推理补足，并明确区分依据来源。"
    );

    private final String code;
    private final String label;
    private final boolean allowGeneralReasoning;
    private final String boundaryPolicy;

    KnowledgeAnswerMode(String code, String label, boolean allowGeneralReasoning, String boundaryPolicy) {
        this.code = code;
        this.label = label;
        this.allowGeneralReasoning = allowGeneralReasoning;
        this.boundaryPolicy = boundaryPolicy;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAllowGeneralReasoning() {
        return allowGeneralReasoning;
    }

    public String getBoundaryPolicy() {
        return boundaryPolicy;
    }

    public static KnowledgeAnswerMode fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return STRICT_KB;
        }
        String normalized = code.trim();
        for (KnowledgeAnswerMode mode : values()) {
            if (mode.code.equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return STRICT_KB;
    }
}
