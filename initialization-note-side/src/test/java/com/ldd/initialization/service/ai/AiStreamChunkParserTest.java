package com.ldd.initialization.service.ai;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.document.Metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiStreamChunkParserTest {

    @Test
    void routesThinkingTagsAwayFromAnswerChunks() {
        AiStreamChunkParser parser = new AiStreamChunkParser();
        List<AiStreamChunkParser.ParsedChunk> chunks = new ArrayList<>();

        chunks.addAll(parser.accept("<thinking>检索知识库"));
        chunks.addAll(parser.accept("并整理依据</thinking>正式回答"));
        chunks.addAll(parser.finish());

        assertThat(chunks)
                .extracting(AiStreamChunkParser.ParsedChunk::type)
                .containsExactly(
                        AiStreamChunkParser.ChunkType.THINKING_START,
                        AiStreamChunkParser.ChunkType.THINKING,
                        AiStreamChunkParser.ChunkType.THINKING,
                        AiStreamChunkParser.ChunkType.THINKING_DONE,
                        AiStreamChunkParser.ChunkType.ANSWER
                );
        assertThat(join(chunks, AiStreamChunkParser.ChunkType.THINKING)).isEqualTo("检索知识库并整理依据");
        assertThat(join(chunks, AiStreamChunkParser.ChunkType.ANSWER)).isEqualTo("正式回答");
    }

    @Test
    void keepsStreamingWhenTagsAreSplitAcrossChunks() {
        AiStreamChunkParser parser = new AiStreamChunkParser();
        List<AiStreamChunkParser.ParsedChunk> chunks = new ArrayList<>();

        chunks.addAll(parser.accept("<thin"));
        chunks.addAll(parser.accept("king>步骤一</thin"));
        chunks.addAll(parser.accept("king>答案"));
        chunks.addAll(parser.finish());

        assertThat(join(chunks, AiStreamChunkParser.ChunkType.THINKING)).isEqualTo("步骤一");
        assertThat(join(chunks, AiStreamChunkParser.ChunkType.ANSWER)).isEqualTo("答案");
    }

    @Test
    void readsNumericMetadataValuesAsStringsForSourceLabels() {
        Metadata metadata = Metadata.from(Map.of(
                "fileId", 2,
                "fileName", "demo.md",
                "category", "AI安全"
        ));

        assertThat(UserConsultantService.metadataValueAsString(metadata, "fileId")).isEqualTo("2");
        assertThat(UserConsultantService.metadataValueAsString(metadata, "fileName")).isEqualTo("demo.md");
        assertThat(UserConsultantService.metadataValueAsString(metadata, "missing")).isNull();
    }

    private String join(List<AiStreamChunkParser.ParsedChunk> chunks, AiStreamChunkParser.ChunkType type) {
        return chunks.stream()
                .filter(chunk -> chunk.type() == type)
                .map(AiStreamChunkParser.ParsedChunk::content)
                .reduce("", String::concat);
    }
}
