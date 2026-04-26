<template>
  <div v-if="tasks.length" class="upload-task-panel">
    <div class="panel-header">
      <span>{{ title }}</span>
      <span class="panel-count">{{ tasks.length }}</span>
    </div>

    <div class="task-list">
      <div v-for="task in tasks" :key="task.id" class="task-card">
        <div class="task-row">
          <div class="task-name" :title="task.name">{{ task.name }}</div>
          <el-tag
            size="small"
            :type="statusTypeMap[task.status] || 'info'"
            effect="light"
          >
            {{ statusLabelMap[task.status] || '等待中' }}
          </el-tag>
        </div>

        <el-progress
          :percentage="task.progressPercent || 0"
          :status="task.status === 'error' ? 'exception' : (task.status === 'success' ? 'success' : '')"
          :stroke-width="10"
        />

        <div class="task-meta">
          <span>{{ formatBytes(task.loadedBytes) }} / {{ formatBytes(task.totalBytes) }}</span>
          <span v-if="task.status === 'uploading'">{{ formatSpeed(task.speedBps) }}</span>
          <span v-else-if="task.status === 'success'">已完成</span>
          <span v-else-if="task.status === 'error'" class="error-text">{{ task.errorMessage || '上传失败' }}</span>
          <span v-else>等待上传</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatBytes, formatSpeed } from '@/utils/uploadProgress'

defineProps({
  title: {
    type: String,
    default: '上传进度'
  },
  tasks: {
    type: Array,
    default: () => []
  }
})

const statusTypeMap = {
  waiting: 'info',
  uploading: 'warning',
  success: 'success',
  error: 'danger'
}

const statusLabelMap = {
  waiting: '等待中',
  uploading: '上传中',
  success: '成功',
  error: '失败'
}
</script>

<style scoped>
.upload-task-panel {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: min(360px, calc(100vw - 32px));
  max-height: min(60vh, 520px);
  overflow: auto;
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(14px);
  z-index: 4000;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.panel-count {
  min-width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-card {
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.task-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.task-name {
  flex: 1;
  min-width: 0;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  word-break: break-all;
}

.task-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}

.error-text {
  color: #dc2626;
}

@media (max-width: 768px) {
  .upload-task-panel {
    right: 12px;
    left: 12px;
    bottom: 12px;
    width: auto;
  }
}
</style>
