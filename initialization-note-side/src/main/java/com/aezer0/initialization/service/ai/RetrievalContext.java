package com.aezer0.initialization.service.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RetrievalContext {
    private RetrievalPlan plan;
    private List<EmbeddingMatch<TextSegment>> matches;
    private String context;
}
