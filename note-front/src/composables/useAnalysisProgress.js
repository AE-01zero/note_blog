import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  startApkAnalysis, startSoAnalysis, startProtocolAnalysis, startDecompileAnalysis,
  getApkStreamUrl, getSoStreamUrl, getProtocolStreamUrl, getDecompileStreamUrl
} from '@/api/aiAnalysis'

const MAX_FILE_SIZE = 200 * 1024 * 1024

const MODULE_CONFIG = {
  apk: { startApi: startApkAnalysis, streamUrlFn: getApkStreamUrl, label: 'APK 分析' },
  so: { startApi: startSoAnalysis, streamUrlFn: getSoStreamUrl, label: 'SO 分析' },
  protocol: { startApi: startProtocolAnalysis, streamUrlFn: getProtocolStreamUrl, label: '协议分析' },
  decompile: { startApi: startDecompileAnalysis, streamUrlFn: getDecompileStreamUrl, label: 'APK 反编译' }
}

/**
 * 流式分析进度管理 composable
 * @param {'apk'|'so'|'protocol'|'decompile'} moduleType
 */
export function useAnalysisProgress(moduleType) {
  const config = MODULE_CONFIG[moduleType]
  if (!config) throw new Error(`Unknown moduleType: ${moduleType}`)

  const percent = ref(0)
  const status = ref('idle') // idle | running | complete | error | cancelled
  const currentStage = ref('')
  const steps = ref([])        // { stage, label, status: 'pending'|'active'|'done', timestamp }
  const logs = ref([])         // { time, text, type: 'info'|'warn'|'error'|'ai' }
  const aiThinking = ref('')   // current AI thinking stream text (accumulated)
  const aiStages = ref({})     // { stage: accumulatedContent }
  const findings = ref([])     // { severity, title, description, stage }
  const finalResult = ref(null)
  const elapsed = ref(0)
  const taskId = ref(null)

  let eventSource = null
  let elapsedTimer = null
  let aborted = false
  let reconnectCount = 0
  const MAX_RECONNECT = 5

  function validateFile(file) {
    if (!file) {
      ElMessage.error('请先选择文件')
      return false
    }
    if (file.size > MAX_FILE_SIZE) {
      ElMessage.error(`文件大小超过限制 (最大200MB)，当前文件: ${formatSize(file.size)}`)
      return false
    }
    return true
  }

  function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  function addLog(text, type = 'info') {
    logs.value.push({
      time: new Date().toLocaleTimeString(),
      text,
      type
    })
  }

  function updateStep(stage, label) {
    const existing = steps.value.find(s => s.stage === stage)
    if (existing) {
      existing.status = 'active'
    } else {
      steps.value.forEach(s => { if (s.status === 'active') s.status = 'done' })
      steps.value.push({ stage, label, status: 'active', timestamp: Date.now() })
    }
    currentStage.value = label
  }

  function handleEvent(evt) {
    if (aborted) return
    try {
      const data = JSON.parse(evt.data)
      const type = data.type

      switch (type) {
        case 'progress': {
          if (data.percent != null) percent.value = data.percent
          if (data.stage) updateStep(data.stage, data.message)
          if (data.message) addLog(data.message, 'info')
          break
        }
        case 'ai_chunk': {
          const stage = data.stage || ''
          if (!aiStages.value[stage]) aiStages.value[stage] = ''
          aiStages.value[stage] += data.content
          aiThinking.value = Object.values(aiStages.value).join('')
          addLog(data.content.trim().substring(0, 100) + '...', 'ai')
          break
        }
        case 'ai_chunk_done': {
          addLog(`AI 阶段分析完成`, 'ai')
          break
        }
        case 'finding': {
          findings.value.push({
            severity: data.severity || 'INFO',
            title: data.title || '',
            description: data.description || '',
            stage: data.stage || ''
          })
          addLog(`[${data.severity}] ${data.title}`, 'warn')
          break
        }
        case 'step_result': {
          // store step results for later retrieval
          addLog(`完成: ${data.stepName}`, 'info')
          break
        }
        case 'complete': {
          status.value = 'complete'
          percent.value = 100
          finalResult.value = data.result
          addLog('分析完成', 'info')
          disconnect()
          break
        }
        case 'error': {
          status.value = 'error'
          addLog(`错误: ${data.message}`, 'error')
          ElMessage.error(data.message || '分析失败')
          disconnect()
          break
        }
        case 'timeout': {
          status.value = 'error'
          addLog('分析超时', 'error')
          ElMessage.error('分析超时')
          disconnect()
          break
        }
        default:
          break
      }
    } catch (e) {
      // ignore parse errors (e.g. ping frames)
    }
  }

  function connect(taskIdVal) {
    const url = config.streamUrlFn(taskIdVal)
    const fullUrl = url.startsWith('http') ? url : window.location.origin + url
    const token = sessionStorage.getItem('token') || localStorage.getItem('token') || ''
    eventSource = new EventSource(fullUrl + '?satoken=' + token)

    eventSource.onmessage = (evt) => {
      reconnectCount = 0
      handleEvent(evt)
    }

    eventSource.onerror = () => {
      if (status.value === 'complete' || status.value === 'error' || aborted) {
        if (eventSource) {
          eventSource.close()
          eventSource = null
        }
        return
      }
      reconnectCount++
      if (reconnectCount > MAX_RECONNECT) {
        addLog(`连接失败，已重试 ${MAX_RECONNECT} 次，请检查网络后重新分析`, 'error')
        status.value = 'error'
        if (eventSource) {
          eventSource.close()
          eventSource = null
        }
        return
      }
      addLog(`连接中断，第 ${reconnectCount}/${MAX_RECONNECT} 次重连...`, 'warn')
    }
  }

  function disconnect() {
    reconnectCount = 0
    if (elapsedTimer) {
      clearInterval(elapsedTimer)
      elapsedTimer = null
    }
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  async function start(file, options = {}) {
    if (!validateFile(file)) return

    reset()
    aborted = false
    status.value = 'running'
    percent.value = 0
    addLog(`开始${config.label}: ${file.name} (${formatSize(file.size)})`, 'info')

    // Start elapsed timer
    elapsedTimer = setInterval(() => { elapsed.value++ }, 1000)

    try {
      const response = await config.startApi(file, options.aiAssist || false)
      const tid = response.data?.data?.taskId || response.data?.taskId
      if (!tid) throw new Error('未能获取任务ID')
      taskId.value = tid
      addLog(`任务已创建: ${tid}`, 'info')
      connect(tid)
    } catch (err) {
      status.value = 'error'
      addLog(`启动失败: ${err.message}`, 'error')
      ElMessage.error('启动分析失败: ' + err.message)
    }
  }

  function cancel() {
    aborted = true
    status.value = 'cancelled'
    addLog('用户取消分析', 'warn')
    disconnect()
  }

  function reset() {
    disconnect()
    aborted = false
    percent.value = 0
    status.value = 'idle'
    currentStage.value = ''
    steps.value = []
    logs.value = []
    aiThinking.value = ''
    aiStages.value = {}
    findings.value = []
    finalResult.value = null
    elapsed.value = 0
    taskId.value = null
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    percent,
    status,
    currentStage,
    steps,
    logs,
    aiThinking,
    aiStages,
    findings,
    finalResult,
    elapsed,
    taskId,
    start,
    cancel,
    reset,
    formatSize
  }
}
