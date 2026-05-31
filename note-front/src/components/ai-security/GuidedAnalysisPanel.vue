<template>
  <div class="guided-analysis-panel">
    <!-- 未开始状态：上传文件 -->
    <div v-if="!sessionId" class="start-section">
      <el-card class="upload-card">
        <template #header>
          <span>AI指导分析</span>
        </template>

        <el-form label-position="top">
          <el-form-item label="分析模块">
            <el-radio-group v-model="moduleType">
              <el-radio label="APK">APK分析</el-radio>
              <el-radio label="SO">SO分析</el-radio>
              <el-radio label="PROTOCOL">协议分析</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="上传文件">
            <el-upload
              ref="uploadRef"
              drag
              :auto-upload="false"
              :limit="1"
              :accept="acceptTypes"
              :on-change="handleFileChange"
              :on-remove="() => selectedFile = null"
            >
              <el-icon :size="48"><UploadFilled /></el-icon>
              <div class="upload-text">拖拽文件到此处或点击上传</div>
              <template #tip>
                <div class="upload-tip">{{ uploadTip }}</div>
              </template>
            </el-upload>
          </el-form-item>

          <el-form-item label="关注方向（可选）">
            <el-input
              v-model="focusAreas"
              type="textarea"
              :rows="2"
              placeholder="例如：重点关注加密算法实现、网络通信安全..."
            />
          </el-form-item>

          <el-button
            type="primary"
            :loading="starting"
            :disabled="!selectedFile"
            size="large"
            class="start-btn"
            @click="startAnalysis"
          >
            开始AI指导分析
          </el-button>
        </el-form>
      </el-card>
    </div>

    <!-- 分析进行中 -->
    <div v-else class="analysis-section">
      <!-- 步骤计划概览 -->
      <el-card class="plan-card">
        <template #header>
          <div class="plan-header">
            <span>分析计划</span>
            <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
          </div>
        </template>
        <el-steps :active="currentStepIndex" finish-status="success" process-status="process" align-center>
          <el-step
            v-for="step in steps"
            :key="step.index"
            :title="step.title"
            :status="getStepStatus(step)"
          />
        </el-steps>
      </el-card>

      <!-- 当前步骤详情 -->
      <el-card class="step-detail-card" v-if="currentStepData && status !== 'COMPLETED' && status !== 'TERMINATED'">
        <template #header>
          <div class="step-header">
            <span>步骤 {{ currentStepIndex + 1 }}/{{ steps.length }}: {{ currentStepData.title }}</span>
            <el-tag v-if="isExecuting" type="warning" size="small">
              <el-icon class="is-loading"><Loading /></el-icon> 执行中
            </el-tag>
          </div>
        </template>

        <p class="step-description">{{ currentStepData.description }}</p>

        <!-- 步骤执行错误 -->
        <el-alert
          v-if="stepError"
          :title="'步骤执行失败: ' + stepError"
          type="error"
          show-icon
          :closable="false"
          class="step-error-alert"
        />

        <!-- 步骤执行结果 -->
        <div v-if="currentStepResult" class="step-result">
          <el-divider content-position="left">执行结果</el-divider>
          <el-collapse>
            <el-collapse-item title="查看原始数据">
              <pre class="result-json">{{ formatJson(currentStepResult) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>

        <!-- AI解读（流式输出） -->
        <div v-if="aiInterpretation" class="ai-interpretation">
          <el-divider content-position="left">AI 分析解读</el-divider>
          <AiThinkingTrajectory
            v-if="currentThinking"
            :thinking="currentThinking"
            :thinking-items="[]"
            :default-collapsed="false"
          />
          <div class="ai-text" v-html="renderMarkdown(currentReportBody)"></div>
          <span v-if="isStreaming" class="typing-cursor">|</span>
        </div>
      </el-card>

      <!-- 历史步骤结果 -->
      <el-card v-if="completedSteps.length > 0" class="history-card">
        <template #header>
          <span>已完成步骤</span>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="step in completedSteps"
            :key="step.index"
            :type="getTimelineType(step.status)"
            :timestamp="step.title"
          >
            <el-tag v-if="step.status === 'SKIPPED'" size="small" type="info">已跳过</el-tag>
            <el-tag v-else-if="step.status === 'FAILED'" size="small" type="danger">执行失败</el-tag>
            <div v-if="step.aiInterpretation" class="history-ai-text">
              {{ step.aiInterpretation.substring(0, 200) }}{{ step.aiInterpretation.length > 200 ? '...' : '' }}
            </div>
            <div v-if="step.error" class="history-error-text">
              错误: {{ step.error }}
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <!-- 操作区 -->
      <div class="action-bar" v-if="status === 'WAITING'">
        <el-input
          v-model="userInstruction"
          placeholder="输入自定义指令调整分析方向（可选）"
          class="instruction-input"
          clearable
        >
          <template #prepend>用户指令</template>
        </el-input>
        <div class="action-buttons">
          <el-button type="primary" size="large" @click="nextStep" :loading="advancing">
            下一步
          </el-button>
          <el-button @click="skipStep" :loading="skipping">跳过</el-button>
          <el-button type="danger" plain @click="terminate">终止分析</el-button>
        </div>
      </div>

      <!-- 分析完成 -->
      <el-result v-if="status === 'COMPLETED'" icon="success" title="分析完成" :sub-title="completeSummary">
        <template #extra>
          <el-button type="primary" @click="reset">开始新分析</el-button>
        </template>
      </el-result>

      <!-- 分析终止 -->
      <el-result v-if="status === 'TERMINATED'" icon="warning" title="分析已终止" sub-title="分析已被用户手动终止">
        <template #extra>
          <el-button type="primary" @click="reset">开始新分析</el-button>
        </template>
      </el-result>

      <!-- SSE超时 -->
      <el-result v-if="status === 'TIMEOUT'" icon="info" title="会话超时" sub-title="SSE连接超过30分钟已自动断开">
        <template #extra>
          <el-button type="primary" @click="reset">开始新分析</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { UploadFilled, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { startGuidedAnalysis, guidedNextStep, guidedSkipStep, guidedTerminate, getGuidedStreamUrl } from '@/api/aiAnalysis'
import { renderMarkdown } from '@/utils/markdown'
import { parseFullAiResponse } from '@/utils/aiResponseParser'
import AiThinkingTrajectory from './AiThinkingTrajectory.vue'

const moduleType = ref('APK')
const selectedFile = ref(null)
const focusAreas = ref('')
const starting = ref(false)
const advancing = ref(false)
const skipping = ref(false)
const userInstruction = ref('')

const sessionId = ref(null)
const status = ref('')
const steps = ref([])
const currentStepIndex = ref(0)
const currentStepResult = ref(null)
const aiInterpretation = ref('')
const isStreaming = ref(false)
const isExecuting = ref(false)
const completeSummary = ref('')
const completedSteps = ref([])
const stepError = ref('')

let eventSource = null

const acceptTypes = computed(() => {
  switch (moduleType.value) {
    case 'APK': return '.apk'
    case 'SO': return '.so'
    case 'PROTOCOL': return '.pcap,.pcapng'
    default: return '*'
  }
})

const uploadTip = computed(() => {
  switch (moduleType.value) {
    case 'APK': return '支持 .apk 文件'
    case 'SO': return '支持 .so ELF文件'
    case 'PROTOCOL': return '支持 .pcap / .pcapng 文件'
    default: return ''
  }
})

const statusTagType = computed(() => {
  switch (status.value) {
    case 'EXECUTING': return 'warning'
    case 'WAITING': return 'success'
    case 'COMPLETED': return 'success'
    case 'TERMINATED': return 'danger'
    case 'TIMEOUT': return 'info'
    default: return 'info'
  }
})

const statusText = computed(() => {
  switch (status.value) {
    case 'PLANNING': return '规划中'
    case 'EXECUTING': return '执行中'
    case 'WAITING': return '等待操作'
    case 'COMPLETED': return '已完成'
    case 'TERMINATED': return '已终止'
    case 'TIMEOUT': return '已超时'
    default: return status.value
  }
})

const currentStepData = computed(() => {
  if (currentStepIndex.value < steps.value.length) {
    return steps.value[currentStepIndex.value]
  }
  return null
})

function handleFileChange(file) {
  selectedFile.value = file.raw
}

const currentThinking = computed(() => {
  return parseFullAiResponse(aiInterpretation.value)?.thinking || ''
})

const currentReportBody = computed(() => {
  return parseFullAiResponse(aiInterpretation.value)?.reportBody || aiInterpretation.value
})

function formatJson(obj) {
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

function getStepStatus(step) {
  if (step.index < currentStepIndex.value) {
    if (step.status === 'SKIPPED') return 'wait'
    if (step.status === 'FAILED') return 'error'
    return 'success'
  }
  if (step.index === currentStepIndex.value && isExecuting.value) return 'process'
  return 'wait'
}

function getTimelineType(stepStatus) {
  switch (stepStatus) {
    case 'COMPLETED': return 'success'
    case 'SKIPPED': return 'info'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

async function startAnalysis() {
  if (!selectedFile.value) return
  starting.value = true
  try {
    const res = await startGuidedAnalysis(selectedFile.value, moduleType.value, focusAreas.value)
    const resData = res.data
    if (resData.code === 0) {
      const data = resData.data
      sessionId.value = data.sessionId
      status.value = data.status
      steps.value = data.steps || []
      currentStepIndex.value = data.currentStepIndex || 0
      connectSSE()
    } else {
      ElMessage.error(resData.message || '创建分析会话失败')
    }
  } catch (e) {
    ElMessage.error('创建分析会话失败: ' + (e.message || '网络错误'))
  } finally {
    starting.value = false
  }
}

function connectSSE() {
  if (eventSource) {
    eventSource.close()
  }

  // EventSource 原生不支持自定义 header，通过 query 参数传递 token
  // Sa-Token 默认支持从 query 参数 ?token=xxx 读取 token
  const token = sessionStorage.getItem('token')
  let url = getGuidedStreamUrl(sessionId.value)
  if (token) {
    url += (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token)
  }

  eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    if (!event.data || event.data.startsWith(':')) return
    try {
      const msg = JSON.parse(event.data)
      handleSSEEvent(msg)
    } catch (e) {
      // ignore parse errors for heartbeat comments
    }
  }

  eventSource.onerror = () => {
    if (status.value !== 'COMPLETED' && status.value !== 'TERMINATED' && status.value !== 'TIMEOUT') {
      setTimeout(() => {
        if (sessionId.value && status.value !== 'COMPLETED' && status.value !== 'TERMINATED' && status.value !== 'TIMEOUT') {
          connectSSE()
        }
      }, 3000)
    }
  }
}

function handleSSEEvent(msg) {
  switch (msg.type) {
    case 'plan':
      steps.value = msg.steps || []
      status.value = 'WAITING'
      break

    case 'step_start':
      currentStepIndex.value = msg.stepIndex
      isExecuting.value = true
      isStreaming.value = false
      currentStepResult.value = null
      aiInterpretation.value = ''
      stepError.value = ''
      status.value = 'EXECUTING'
      break

    case 'step_result':
      currentStepResult.value = msg.data
      isExecuting.value = false
      break

    case 'ai_chunk':
      isStreaming.value = true
      aiInterpretation.value += msg.content
      break

    case 'step_complete':
      isStreaming.value = false
      isExecuting.value = false
      status.value = msg.status || 'WAITING'
      // 将当前步骤加入已完成列表
      if (msg.stepIndex !== undefined && msg.stepIndex < steps.value.length) {
        const step = steps.value[msg.stepIndex]
        completedSteps.value.push({
          ...step,
          status: 'COMPLETED',
          aiInterpretation: aiInterpretation.value
        })
      }
      // 重置当前步骤的显示数据
      currentStepResult.value = null
      aiInterpretation.value = ''
      stepError.value = ''
      break

    case 'step_skipped':
      // 后端跳过步骤时触发
      if (msg.stepIndex !== undefined && msg.stepIndex < steps.value.length) {
        const step = steps.value[msg.stepIndex]
        completedSteps.value.push({
          ...step,
          status: 'SKIPPED',
          aiInterpretation: ''
        })
      }
      currentStepIndex.value = (msg.stepIndex !== undefined ? msg.stepIndex : currentStepIndex.value) + 1
      if (currentStepIndex.value >= steps.value.length) {
        // 所有步骤已完成/跳过，等待后端发送 session_complete
      } else {
        status.value = 'WAITING'
      }
      skipping.value = false
      break

    case 'step_error':
      // 步骤执行失败
      isExecuting.value = false
      isStreaming.value = false
      stepError.value = msg.error || '未知错误'
      ElMessage.warning('步骤执行失败: ' + (msg.error || '未知错误'))
      // 将失败的步骤加入已完成列表
      if (msg.stepIndex !== undefined && msg.stepIndex < steps.value.length) {
        const step = steps.value[msg.stepIndex]
        completedSteps.value.push({
          ...step,
          status: 'FAILED',
          aiInterpretation: aiInterpretation.value,
          error: msg.error
        })
      }
      status.value = 'WAITING'
      // 重置当前步骤显示
      currentStepResult.value = null
      aiInterpretation.value = ''
      break

    case 'plan_updated':
      // 后端发送的是完整的新步骤列表（已完成 + 新规划）
      if (msg.steps && msg.steps.length > 0) {
        steps.value = msg.steps
        ElMessage.success('分析计划已根据您的指令调整')
      }
      break

    case 'session_complete':
      status.value = 'COMPLETED'
      completeSummary.value = msg.summary || '分析已完成'
      isExecuting.value = false
      isStreaming.value = false
      closeSSE()
      break

    case 'session_terminated':
      status.value = 'TERMINATED'
      isExecuting.value = false
      isStreaming.value = false
      closeSSE()
      break

    case 'timeout':
      status.value = 'TIMEOUT'
      isExecuting.value = false
      isStreaming.value = false
      ElMessage.warning('分析会话超时，SSE连接已断开')
      closeSSE()
      break

    case 'error':
      ElMessage.error(msg.message || '分析出错')
      isExecuting.value = false
      isStreaming.value = false
      break

    case 'session_state':
      // 断线重连时后端同步当前状态
      status.value = msg.status
      currentStepIndex.value = msg.currentStepIndex
      steps.value = msg.steps || steps.value
      break
  }
}

async function nextStep() {
  advancing.value = true
  try {
    const res = await guidedNextStep(sessionId.value, userInstruction.value || null)
    if (res.data.code !== 0) {
      ElMessage.error(res.data.message || '执行失败')
    }
    userInstruction.value = ''
  } catch (e) {
    ElMessage.error('执行失败: ' + (e.message || '网络错误'))
  } finally {
    advancing.value = false
  }
}

async function skipStep() {
  skipping.value = true
  try {
    const res = await guidedSkipStep(sessionId.value)
    if (res.data.code !== 0) {
      ElMessage.error(res.data.message || '跳过失败')
      skipping.value = false
    }
    // 状态更新由 SSE step_skipped 事件驱动，不在此处修改
  } catch (e) {
    ElMessage.error('跳过失败')
    skipping.value = false
  }
}

async function terminate() {
  try {
    await guidedTerminate(sessionId.value)
    // 状态更新由 SSE session_terminated 事件驱动
    // 如果 SSE 已断开，手动设置状态作为兜底
    if (!eventSource || eventSource.readyState === EventSource.CLOSED) {
      status.value = 'TERMINATED'
      closeSSE()
    }
    ElMessage.info('分析已终止')
  } catch (e) {
    ElMessage.error('终止失败')
  }
}

function closeSSE() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

function reset() {
  closeSSE()
  sessionId.value = null
  status.value = ''
  steps.value = []
  currentStepIndex.value = 0
  currentStepResult.value = null
  aiInterpretation.value = ''
  isStreaming.value = false
  isExecuting.value = false
  completeSummary.value = ''
  completedSteps.value = []
  selectedFile.value = null
  focusAreas.value = ''
  userInstruction.value = ''
  stepError.value = ''
}

onUnmounted(() => {
  closeSSE()
})
</script>

<style scoped>
.guided-analysis-panel {
  padding: 20px;
}

.upload-card {
  max-width: 600px;
  margin: 0 auto;
}

.upload-text {
  margin-top: 8px;
  color: #606266;
}

.upload-tip {
  color: #909399;
  font-size: 12px;
}

.start-btn {
  width: 100%;
  margin-top: 10px;
}

.plan-card {
  margin-bottom: 16px;
}

.plan-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.step-detail-card {
  margin-bottom: 16px;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.step-description {
  color: #606266;
  margin-bottom: 16px;
}

.step-error-alert {
  margin-bottom: 16px;
}

.step-result {
  margin-top: 16px;
}

.result-json {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.ai-interpretation {
  margin-top: 16px;
}

.ai-text {
  line-height: 1.8;
  color: #303133;
}

.typing-cursor {
  animation: blink 1s infinite;
  color: #409eff;
  font-weight: bold;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.history-card {
  margin-bottom: 16px;
}

.history-ai-text {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.history-error-text {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}

.action-bar {
  margin-top: 16px;
}

.instruction-input {
  margin-bottom: 12px;
}

.action-buttons {
  display: flex;
  gap: 10px;
}
</style>
