<template>
  <div class="ai-analysis-report">
    <!-- 1. AI 身份标识栏 -->
    <AiIdentityBar
      v-if="showIdentity"
      :module-type="parsed.identity.moduleType"
      :model="parsed.identity.model"
      :timestamp="parsed.identity.timestamp"
      :confidence="parsed.identity.confidence"
    >
      <template v-if="$slots['identity-extra']" #default>
        <slot name="identity-extra" />
      </template>
    </AiIdentityBar>

    <!-- 2. AI 思考轨迹 (有 thinking 内容时显示) -->
    <AiThinkingTrajectory
      v-if="parsed.thinking"
      :thinking="parsed.thinking"
      :thinking-items="parsed.thinkingItems"
      :default-collapsed="collapsibleThinking ? false : false"
      :is-loading="isStreaming && !parsed.reportBody"
    />

    <!-- 3. 主报告体 -->
    <div class="report-body">
      <slot name="report-before" />
      <div
        v-if="parsed.reportBody"
        class="markdown-content"
        v-html="renderedReport"
      />
      <div v-else-if="isStreaming" class="streaming-wait">
        <el-icon class="is-loading" :size="20"><Loading /></el-icon>
        <span>等待 AI 生成分析报告...</span>
      </div>
      <div v-else class="empty-report">
        暂无分析报告内容
      </div>
      <span v-if="isStreaming && parsed.reportBody" class="typing-cursor" />
      <slot name="report-after" />
    </div>

    <!-- 4. PROBE 交互按钮 -->
    <div v-if="showProbes && parsed.probes.length" class="probe-actions">
      <div class="probe-header">
        <el-icon :size="16"><Aim /></el-icon>
        <span>AI 建议深度排查探针</span>
      </div>
      <div class="probe-buttons">
        <el-button
          v-for="(probe, idx) in parsed.probes"
          :key="idx"
          :type="getProbeButtonType(probe.type)"
          size="small"
          plain
          class="probe-btn"
          @click="$emit('probe-click', probe)"
        >
          <el-icon><Search /></el-icon>
          [{{ probeLabel(probe.type) }}] {{ probe.label }}
          <span class="probe-query-hint">{{ probe.query }}</span>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Loading, Aim, Search } from '@element-plus/icons-vue'
import AiIdentityBar from './AiIdentityBar.vue'
import AiThinkingTrajectory from './AiThinkingTrajectory.vue'
import { parseFullAiResponse } from '@/utils/aiResponseParser'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  aiResponse: { type: String, default: '' },
  metadata: {
    type: Object,
    default: () => ({})
  },
  showIdentity: { type: Boolean, default: true },
  collapsibleThinking: { type: Boolean, default: false },
  isStreaming: { type: Boolean, default: false },
  showProbes: { type: Boolean, default: true }
})

defineEmits(['probe-click'])

const parsed = computed(() => parseFullAiResponse(props.aiResponse, props.metadata))
const renderedReport = computed(() => renderMarkdown(parsed.value.reportBody))

const PROBE_LABELS = {
  regex_search: '正则搜索',
  grep_string: '关键字搜索',
  list_sensitive_files: '敏感文件扫描'
}

function probeLabel(type) {
  return PROBE_LABELS[type] || type
}

function getProbeButtonType(type) {
  switch (type) {
    case 'regex_search': return 'warning'
    case 'grep_string': return 'primary'
    case 'list_sensitive_files': return 'danger'
    default: return 'info'
  }
}
</script>

<style scoped>
.ai-analysis-report {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.report-body {
  background: var(--ai-report-body-bg, #fff);
  border: 1px solid var(--ai-identity-border, #e2e8f0);
  border-radius: 8px;
  padding: 16px 20px;
  min-height: 40px;
}

.markdown-content {
  color: var(--ai-report-body-text, #334155);
  line-height: 1.8;
  font-size: 14px;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  color: #303133;
  margin: 16px 0 8px;
}

.markdown-content :deep(h2) {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 6px;
}

.markdown-content :deep(code) {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
  color: #e6a23c;
}

.markdown-content :deep(pre) {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 14px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}

.markdown-content :deep(pre code) {
  background: none;
  color: inherit;
  padding: 0;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 8px 12px;
  text-align: left;
}

.markdown-content :deep(th) {
  background: #f8fafc;
  font-weight: 600;
}

.markdown-content :deep(blockquote) {
  border-left: 3px solid #8b5cf6;
  padding: 8px 14px;
  margin: 12px 0;
  background: rgba(139, 92, 246, 0.04);
  color: #5b21b6;
}

.streaming-wait {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #909399;
  padding: 20px;
  justify-content: center;
}

.empty-report {
  color: #c0c4cc;
  text-align: center;
  padding: 20px;
}

.probe-actions {
  background: var(--ai-probe-bg, rgba(249, 115, 22, 0.04));
  border: 1px solid var(--ai-probe-border, rgba(249, 115, 22, 0.2));
  border-radius: 8px;
  padding: 14px 16px;
}

.probe-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: var(--ai-probe-accent, #ea580c);
  margin-bottom: 10px;
}

.probe-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.probe-btn {
  font-size: 12px;
}

.probe-query-hint {
  display: block;
  font-size: 11px;
  color: #909399;
  font-family: monospace;
  margin-top: 2px;
}
</style>
