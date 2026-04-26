import { reactive } from 'vue'

let uploadTaskSeed = 0

const now = () => {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now()
  }
  return Date.now()
}

export const MAX_UPLOAD_SIZE_MB = 100
export const MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024

export const createUploadTask = (file, overrides = {}) => reactive({
  id: `upload-task-${++uploadTaskSeed}`,
  name: file?.name || overrides.name || '未命名文件',
  totalBytes: Number(file?.size || overrides.totalBytes || 0),
  loadedBytes: 0,
  progressPercent: 0,
  speedBps: 0,
  status: 'waiting',
  errorMessage: '',
  lastLoaded: 0,
  lastTimestamp: now(),
  ...overrides
})

export const updateUploadTaskProgress = (task, progressEvent) => {
  if (!task || !progressEvent) return

  const timestamp = now()
  const totalBytes = Number(progressEvent.total || task.totalBytes || 0)
  const loadedBytes = Math.max(task.loadedBytes || 0, Number(progressEvent.loaded || 0))
  const deltaBytes = Math.max(0, loadedBytes - (task.lastLoaded || 0))
  const deltaMs = Math.max(1, timestamp - (task.lastTimestamp || timestamp))
  const instantSpeed = deltaBytes / (deltaMs / 1000)

  task.totalBytes = totalBytes || task.totalBytes
  task.loadedBytes = loadedBytes
  task.progressPercent = task.totalBytes
    ? Math.min(100, Math.round((task.loadedBytes / task.totalBytes) * 100))
    : task.progressPercent
  task.speedBps = task.speedBps > 0
    ? Math.round(task.speedBps * 0.7 + instantSpeed * 0.3)
    : Math.round(instantSpeed)
  task.status = 'uploading'
  task.lastLoaded = loadedBytes
  task.lastTimestamp = timestamp
}

export const markUploadTaskSuccess = (task) => {
  if (!task) return
  task.loadedBytes = task.totalBytes || task.loadedBytes
  task.progressPercent = 100
  task.speedBps = 0
  task.status = 'success'
  task.errorMessage = ''
}

export const markUploadTaskError = (task, error) => {
  if (!task) return
  task.status = 'error'
  task.speedBps = 0
  task.errorMessage = normalizeUploadError(error)
}

export const scheduleUploadTaskCleanup = (tasksRef, taskId, delay = 3000) => {
  setTimeout(() => {
    const index = tasksRef.value.findIndex(task => task.id === taskId)
    if (index > -1) {
      tasksRef.value.splice(index, 1)
    }
  }, delay)
}

export const formatBytes = (bytes = 0) => {
  const value = Number(bytes || 0)
  if (value <= 0) return '0 B'

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const exponent = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  const result = value / Math.pow(1024, exponent)
  const precision = exponent === 0 ? 0 : 2
  return `${result.toFixed(precision)} ${units[exponent]}`
}

export const formatSpeed = (bytesPerSecond = 0) => {
  const value = Number(bytesPerSecond || 0)
  if (value <= 0) return '0 B/s'
  return `${formatBytes(value)}/s`
}

export const normalizeUploadError = (error) => {
  if (!error) return '上传失败'

  const responseMessage = error.response?.data?.message
  if (responseMessage) return responseMessage

  if (typeof error.message === 'string' && error.message.trim()) {
    return error.message.trim()
  }

  return '上传失败'
}
