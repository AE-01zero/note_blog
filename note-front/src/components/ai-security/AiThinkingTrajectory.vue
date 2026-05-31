<template>
  <div class="thinking-card" :class="{ collapsed: isCollapsed }">
    <div class="thinking-header" @click="toggle">
      <div class="header-left">
        <el-icon :class="{ 'arrow-icon': true, 'is-collapsed': isCollapsed }">
          <ArrowRight />
        </el-icon>
        <span class="brain-icon">🧠</span>
        <span class="header-title">AI 安全大脑深度思考轨迹</span>
        <el-tag v-if="isLoading" type="warning" size="small" class="pulse-tag">
          思考中...
        </el-tag>
        <span v-else-if="thinkingItems.length" class="step-count">
          {{ thinkingItems.length }} 个推理步骤
        </span>
      </div>
      <el-icon class="collapse-hint">
        <ArrowDown v-if="!isCollapsed" />
        <ArrowRight v-else />
      </el-icon>
    </div>

    <el-collapse-transition>
      <div v-show="!isCollapsed" class="thinking-body">
        <!-- 结构化步骤卡片 -->
        <div v-if="thinkingItems.length" class="thinking-steps">
          <div
            v-for="(item, index) in thinkingItems"
            :key="index"
            class="thinking-step-card"
          >
            <div class="step-header">
              <span class="step-icon">{{ item.icon }}</span>
              <span class="step-label">{{ item.label }}</span>
            </div>
            <div class="step-content">{{ item.content }}</div>
          </div>
        </div>
        <!-- 原始 thinking 文本兜底 -->
        <div v-else class="thinking-raw">{{ thinking }}</div>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ArrowRight, ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  thinking: { type: String, default: '' },
  thinkingItems: { type: Array, default: () => [] },
  defaultCollapsed: { type: Boolean, default: false },
  isLoading: { type: Boolean, default: false }
})

const isCollapsed = ref(props.defaultCollapsed)

function toggle() {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style scoped>
.thinking-card {
  border: 1px solid var(--ai-thinking-border, rgba(139, 92, 246, 0.15));
  border-radius: 8px;
  background: var(--ai-thinking-bg, rgba(139, 92, 246, 0.04));
  overflow: hidden;
}

.thinking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  cursor: pointer;
  background: var(--ai-thinking-header-bg, rgba(139, 92, 246, 0.06));
  user-select: none;
  transition: background 0.2s;
}

.thinking-header:hover {
  background: rgba(139, 92, 246, 0.1);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.arrow-icon {
  transition: transform 0.25s ease;
  color: var(--ai-thinking-accent, #7c3aed);
}
.arrow-icon.is-collapsed {
  transform: rotate(-90deg);
}

.brain-icon {
  font-size: 18px;
}

.header-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--ai-thinking-text, #5b21b6);
}

.pulse-tag {
  animation: ai-pulse 2s infinite ease-in-out;
}

.step-count {
  font-size: 12px;
  color: #909399;
}

.collapse-hint {
  color: #909399;
  font-size: 14px;
}

.thinking-body {
  padding: 16px;
}

.thinking-steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.thinking-step-card {
  padding: 12px 14px;
  background: var(--ai-step-card-bg, #f8fafc);
  border: 1px solid var(--ai-step-card-border, #e2e8f0);
  border-left: 3px solid var(--ai-thinking-accent, #7c3aed);
  border-radius: 6px;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.step-icon {
  font-size: 16px;
}

.step-label {
  font-weight: 600;
  font-size: 13px;
  color: var(--ai-thinking-text, #5b21b6);
}

.step-content {
  font-size: 13px;
  color: var(--ai-report-body-text, #334155);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.thinking-raw {
  font-size: 13px;
  color: var(--ai-report-body-text, #334155);
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
