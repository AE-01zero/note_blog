<template>
  <div class="ai-identity-bar" :style="{ borderLeftColor: accentColor }">
    <div class="identity-left">
      <el-tag effect="dark" :color="accentColor" class="identity-badge">
        <el-icon class="badge-icon"><Cpu /></el-icon>
        AI 移动安全分析引擎
      </el-tag>
      <span class="module-label">{{ moduleLabel }}</span>
    </div>
    <div class="identity-meta">
      <el-tag size="small" type="info" class="meta-tag">模型: {{ model }}</el-tag>
      <span class="meta-time">{{ formattedTime }}</span>
      <div class="confidence-wrap">
        <el-progress
          :percentage="confidencePercent"
          :stroke-width="8"
          :color="confidenceColor"
          :show-text="false"
          style="width: 70px"
        />
        <span class="confidence-text">置信度 {{ confidencePercent }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Cpu } from '@element-plus/icons-vue'

const props = defineProps({
  moduleType: { type: String, default: 'GENERAL' },
  model: { type: String, default: 'qwen-plus' },
  timestamp: { type: Number, default: () => Date.now() },
  confidence: { type: Number, default: 0.9 }
})

const MODULE_LABELS = {
  APK: 'APK 静态审计',
  SO: 'Native 层逆向分析',
  PROTOCOL: '协议流量分析',
  SANDBOX: '沙箱行为分析',
  FILE: '单文件代码审计',
  GLOBAL: '全局源码合规评估'
}

const ACCENT_COLORS = {
  APK: '#3b82f6',
  SO: '#f59e0b',
  PROTOCOL: '#8b5cf6',
  SANDBOX: '#ef4444',
  FILE: '#10b981',
  GLOBAL: '#6366f1'
}

const moduleLabel = computed(() => MODULE_LABELS[props.moduleType] || props.moduleType)
const accentColor = computed(() => ACCENT_COLORS[props.moduleType] || '#6366f1')
const confidencePercent = computed(() => Math.round((props.confidence ?? 0.9) * 100))
const formattedTime = computed(() => {
  if (!props.timestamp) return ''
  return new Date(props.timestamp).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
})

const confidenceColor = computed(() => {
  const pct = confidencePercent.value
  if (pct >= 90) return '#67c23a'
  if (pct >= 70) return '#e6a23c'
  return '#f56c6c'
})
</script>

<style scoped>
.ai-identity-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--ai-identity-bg, linear-gradient(135deg, #f8fafc 0%, #eff6ff 100%));
  border: 1px solid var(--ai-identity-border, #e2e8f0);
  border-left: 4px solid;
  border-radius: 6px;
  flex-wrap: wrap;
  gap: 8px;
}

.identity-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.identity-badge {
  font-weight: 600;
  font-size: 13px;
  border: none;
}

.badge-icon {
  margin-right: 4px;
}

.module-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.identity-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.meta-tag {
  font-size: 11px;
}

.meta-time {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.confidence-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.confidence-text {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}
</style>
