<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="92%"
    top="3vh"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="true"
    :before-close="handleBeforeClose"
    class="analysis-progress-dialog"
    destroy-on-close
  >
    <div class="progress-container">
      <!-- 头部信息 -->
      <div class="progress-header">
        <div class="header-left">
          <el-tag :type="statusTagType" size="large">{{ statusLabel }}</el-tag>
          <span class="task-label">{{ config.label }}</span>
          <span class="file-name">{{ fileName }}</span>
        </div>
        <div class="header-right">
          <span class="elapsed-time">已用时间: {{ formattedElapsed }}</span>
        </div>
      </div>

      <!-- 主体区域 -->
      <div class="progress-body">
        <!-- 左侧：步骤时间线 + 日志 -->
        <div class="left-panel">
          <!-- 步骤时间线 -->
          <div class="panel-section step-timeline-section">
            <div class="section-title">
              <el-icon><List /></el-icon> 分析步骤
            </div>
            <div class="step-timeline">
              <div
                v-for="(step, idx) in progress.steps.value"
                :key="step.stage"
                class="step-item"
                :class="step.status"
              >
                <div class="step-dot">
                  <el-icon v-if="step.status === 'done'" class="done-icon"><Check /></el-icon>
                  <el-icon v-else-if="step.status === 'active'" class="active-icon"><Loading /></el-icon>
                  <span v-else class="pending-dot"></span>
                </div>
                <div class="step-info">
                  <div class="step-label">{{ step.label }}</div>
                  <div class="step-time" v-if="step.timestamp">
                    {{ new Date(step.timestamp).toLocaleTimeString() }}
                  </div>
                </div>
              </div>
              <!-- 空状态 -->
              <div v-if="progress.steps.value.length === 0" class="empty-steps">
                <el-icon><Clock /></el-icon>
                <span>等待分析启动...</span>
              </div>
            </div>
          </div>

          <!-- 日志终端 -->
          <div class="panel-section log-section">
            <div class="section-title">
              <el-icon><Monitor /></el-icon> 日志终端
            </div>
            <div class="log-terminal" ref="logTerminalRef">
              <div
                v-for="(log, idx) in progress.logs.value"
                :key="idx"
                class="log-line"
                :class="'log-' + log.type"
              >
                <span class="log-time">{{ log.time }}</span>
                <span v-if="log.type === 'ai'" class="log-badge">AI</span>
                <span v-else-if="log.type === 'warn'" class="log-badge warn">!</span>
                <span v-else-if="log.type === 'error'" class="log-badge error">E</span>
                <span class="log-text">{{ log.text }}</span>
              </div>
              <div v-if="progress.logs.value.length === 0" class="empty-logs">
                <span>等待日志输出...</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：AI 思考轨迹 + 发现 -->
        <div class="right-panel">
          <!-- AI 思考轨迹 -->
          <div class="panel-section thinking-section">
            <div class="section-title">
              <el-icon><Cpu /></el-icon> AI 思考轨迹
              <span v-if="progress.status.value === 'running'" class="thinking-indicator">
                <span class="pulse-dot"></span> 实时分析中...
              </span>
            </div>
            <div class="thinking-content" ref="thinkingContentRef">
              <div v-if="progress.aiThinking.value" class="thinking-text markdown-body" v-html="renderedThinking"></div>
              <div v-else class="thinking-empty">
                <el-icon class="brain-icon"><Cpu /></el-icon>
                <span>{{ progress.status.value === 'running' ? 'AI 正在思考中...' : '等待 AI 分析启动' }}</span>
              </div>
            </div>
          </div>

          <!-- 阶段性发现 -->
          <div class="panel-section findings-section">
            <div class="section-title">
              <el-icon><WarningFilled /></el-icon> 阶段性发现
              <el-badge :value="progress.findings.value.length" :max="99" v-if="progress.findings.value.length > 0" />
            </div>
            <div class="findings-list">
              <div
                v-for="(finding, idx) in progress.findings.value"
                :key="idx"
                class="finding-item"
                :class="'severity-' + (finding.severity || 'info').toLowerCase()"
              >
                <el-tag :type="severityTagType(finding.severity)" size="small" effect="dark">
                  {{ finding.severity }}
                </el-tag>
                <div class="finding-body">
                  <div class="finding-title">{{ finding.title }}</div>
                  <div class="finding-desc" v-if="finding.description">{{ finding.description }}</div>
                </div>
              </div>
              <div v-if="progress.findings.value.length === 0" class="empty-findings">
                <span>暂无发现</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="progress-footer">
        <div class="progress-bar-wrap">
          <el-progress
            :percentage="Math.round(progress.percent.value)"
            :status="progressBarStatus"
            :stroke-width="16"
            :text-inside="true"
          />
        </div>
        <div class="footer-actions">
          <el-button
            v-if="progress.status.value === 'running'"
            type="danger"
            :icon="Close"
            @click="handleCancel"
          >
            取消分析
          </el-button>
          <el-button
            v-if="progress.status.value === 'complete'"
            type="success"
            :icon="Check"
            @click="handleViewResult"
          >
            查看结果
          </el-button>
          <el-button
            v-if="progress.status.value === 'error' || progress.status.value === 'cancelled'"
            type="info"
            :icon="Close"
            @click="handleClose"
          >
            关闭
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, Monitor, Cpu, WarningFilled, Clock, Loading, Check, Close } from '@element-plus/icons-vue'
import { useAnalysisProgress } from '@/composables/useAnalysisProgress'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  moduleType: { type: String, required: true }, // apk | so | protocol | decompile
  fileName: { type: String, default: '' },
  fileSize: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'complete', 'cancel', 'close'])

const MODULE_LABELS = {
  apk: 'APK 静态分析',
  so: 'SO 二进制分析',
  protocol: '协议流量分析',
  decompile: 'APK 反编译'
}

const progress = useAnalysisProgress(props.moduleType)
const config = computed(() => ({
  label: MODULE_LABELS[props.moduleType] || '分析'
}))

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const dialogTitle = computed(() => `${config.value.label} - 进度详情`)

const statusLabel = computed(() => {
  switch (progress.status.value) {
    case 'idle': return '准备就绪'
    case 'running': return '分析中'
    case 'complete': return '分析完成'
    case 'error': return '分析失败'
    case 'cancelled': return '已取消'
    default: return '未知'
  }
})

const statusTagType = computed(() => {
  switch (progress.status.value) {
    case 'running': return 'warning'
    case 'complete': return 'success'
    case 'error': return 'danger'
    case 'cancelled': return 'info'
    default: return ''
  }
})

const progressBarStatus = computed(() => {
  if (progress.status.value === 'complete') return 'success'
  if (progress.status.value === 'error') return 'exception'
  return ''
})

const formattedElapsed = computed(() => {
  const s = progress.elapsed.value
  const min = Math.floor(s / 60)
  const sec = s % 60
  return `${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`
})

const renderedThinking = computed(() => {
  if (!progress.aiThinking.value) return ''
  return renderMarkdown(progress.aiThinking.value)
})

function severityTagType(sev) {
  switch ((sev || '').toUpperCase()) {
    case 'HIGH': return 'danger'
    case 'MEDIUM': return 'warning'
    case 'LOW': return 'info'
    default: return ''
  }
}

const logTerminalRef = ref(null)
const thinkingContentRef = ref(null)

// Auto-scroll logs
watch(() => progress.logs.value.length, () => {
  nextTick(() => {
    if (logTerminalRef.value) {
      logTerminalRef.value.scrollTop = logTerminalRef.value.scrollHeight
    }
  })
})

// Auto-scroll thinking
watch(() => progress.aiThinking.value, () => {
  nextTick(() => {
    if (thinkingContentRef.value) {
      thinkingContentRef.value.scrollTop = thinkingContentRef.value.scrollHeight
    }
  })
})

// Start analysis when dialog opens
watch(() => props.modelValue, (val) => {
  if (val && props.fileName) {
    // The parent should call start() via expose, or we auto-start here
    // We expose the start method so parent can call it
  }
})

function handleCancel() {
  ElMessageBox.confirm('确定要取消当前分析吗？已处理的步骤不会保存。', '取消分析', {
    confirmButtonText: '确定取消',
    cancelButtonText: '继续等待',
    type: 'warning'
  }).then(() => {
    progress.cancel()
    ElMessage.info('分析已取消')
  }).catch(() => {})
}

function handleViewResult() {
  emit('complete', progress.finalResult.value)
  emit('update:modelValue', false)
}

function handleClose() {
  emit('update:modelValue', false)
}

function handleBeforeClose(done) {
  if (progress.status.value === 'running') {
    ElMessageBox.confirm('分析正在进行中，确定要关闭吗？', '确认关闭', {
      confirmButtonText: '确定关闭',
      cancelButtonText: '继续等待',
      type: 'warning'
    }).then(() => {
      progress.cancel()
      done()
    }).catch(() => {})
  } else {
    done()
  }
}

function startAnalysis(file, options = {}) {
  if (file) {
    progress.start(file, options)
  }
}

// Expose for parent component
defineExpose({ startAnalysis, progress })

onBeforeUnmount(() => {
  progress.reset()
})
</script>

<style scoped>
.analysis-progress-dialog :deep(.el-dialog) {
  display: flex;
  flex-direction: column;
  height: 92vh;
  max-height: 92vh;
  border-radius: 8px;
}

.analysis-progress-dialog :deep(.el-dialog__header) {
  padding: 10px 20px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color-page);
  flex-shrink: 0;
}

.analysis-progress-dialog :deep(.el-dialog__body) {
  padding: 0;
  flex: 1;
  min-height: 0;
  height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.progress-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* ====== Header ====== */
.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 20px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-label { font-weight: 600; font-size: 15px; color: var(--el-text-color-primary); }
.file-name { color: var(--el-text-color-secondary); font-size: 13px; }
.elapsed-time { font-family: monospace; color: var(--el-text-color-regular); font-size: 14px; }

/* ====== Body ====== */
.progress-body {
  display: flex;
  flex: 1;
  min-height: 0;
  height: 0;
  gap: 0;
}

.left-panel {
  width: 40%;
  min-width: 340px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color-page);
  min-height: 0;
  overflow: hidden;
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.panel-section {
  padding: 10px 14px;
}

.panel-section:not(:last-child) {
  border-bottom: 1px solid var(--el-border-color-lighter);
}

/* Step timeline: don't take too much, allow scrolling */
.step-timeline-section {
  flex-shrink: 0;
}

/* Log terminal: takes remaining left panel space */
.panel-section.log-section { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }

/* Findings: flex share with thinking */
.panel-section.findings-section { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }

/* AI thinking: primary right panel area */
.panel-section.thinking-section { flex: 2; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }

.section-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-primary);
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

/* ====== Step Timeline ====== */
.step-timeline { max-height: 180px; overflow-y: auto; padding-left: 4px; }

.step-item {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  position: relative;
}

.step-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 11px;
  top: 28px;
  bottom: 0;
  width: 1px;
  background: var(--el-border-color);
}

.step-item.done:not(:last-child)::before { background: var(--el-color-success); }

.step-dot {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.done-icon { color: var(--el-color-success); font-size: 16px; }
.active-icon { color: var(--el-color-primary); font-size: 16px; animation: spin 1.5s linear infinite; }
.pending-dot {
  width: 8px; height: 8px; border-radius: 50%; background: var(--el-border-color);
  display: inline-block;
}

.step-info { flex: 1; min-width: 0; }
.step-label { font-size: 13px; color: var(--el-text-color-regular); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.step-item.active .step-label { color: var(--el-color-primary); font-weight: 600; }
.step-time { font-size: 11px; color: var(--el-text-color-placeholder); margin-top: 2px; }

.empty-steps {
  display: flex; align-items: center; justify-content: center;
  gap: 8px; padding: 20px; color: var(--el-text-color-placeholder);
}

/* ====== Log Terminal ====== */
.log-terminal {
  flex: 1;
  min-height: 0;
  background: #1a1a2e;
  color: #e0e0e0;
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 12px;
  padding: 10px;
  border-radius: 6px;
  overflow-y: auto;
}

.log-line {
  display: flex;
  gap: 8px;
  padding: 2px 0;
  align-items: baseline;
}

.log-time { color: #888; flex-shrink: 0; }
.log-badge {
  font-size: 10px; padding: 0 4px; border-radius: 3px; flex-shrink: 0;
  background: #2196f3; color: #fff; font-weight: bold;
}
.log-badge.warn { background: #ff9800; }
.log-badge.error { background: #f44336; }
.log-text { word-break: break-all; }

.log-ai .log-text { color: #64b5f6; }
.log-warn .log-text { color: #ffb74d; }
.log-error .log-text { color: #ef5350; }

.empty-logs { display: flex; justify-content: center; padding: 20px; color: #666; }

/* ====== Thinking Content ====== */
.thinking-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 16px;
  background: linear-gradient(135deg, #fafbff 0%, #f0f4ff 100%);
  border-radius: 6px;
}

.thinking-text { font-size: 14px; line-height: 1.8; }

.thinking-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  height: 100%;
  color: var(--el-text-color-placeholder);
}

.brain-icon { font-size: 48px; opacity: 0.3; }

.thinking-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: normal;
  color: var(--el-color-primary);
  margin-left: auto;
}

.pulse-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--el-color-primary);
  animation: pulse 1.5s ease-in-out infinite;
}

/* ====== Findings ====== */
.findings-list { flex: 1; min-height: 0; overflow-y: auto; }

.finding-item {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: flex-start;
}

.finding-body { flex: 1; min-width: 0; }
.finding-title { font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); }
.finding-desc { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 3px; }

.finding-item.severity-high { border-left: 3px solid var(--el-color-danger); padding-left: 10px; }
.finding-item.severity-medium { border-left: 3px solid var(--el-color-warning); padding-left: 10px; }

.empty-findings { display: flex; justify-content: center; padding: 20px; color: var(--el-text-color-placeholder); }

/* ====== Footer ====== */
.progress-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  display: flex;
  align-items: center;
  gap: 16px;
}

.progress-bar-wrap { flex: 1; }

.footer-actions { flex-shrink: 0; display: flex; gap: 8px; }

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.6); }
}
</style>
