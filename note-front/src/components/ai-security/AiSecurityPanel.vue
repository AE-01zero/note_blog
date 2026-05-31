<template>
  <div class="ai-security-panel">
    <el-tabs v-model="activeTab" type="border-card" class="analysis-tabs">
      <!-- Prompt注入检测 -->
      <el-tab-pane label="Prompt注入检测" name="injection">
        <PromptInjectionPanel />
      </el-tab-pane>

      <!-- APK分析 -->
      <el-tab-pane label="APK分析" name="apk">
        <ApkAnalyzerPanel />
      </el-tab-pane>

      <!-- APK逆向 -->
      <el-tab-pane label="APK逆向" name="reverse">
        <ApkReversePanel />
      </el-tab-pane>

      <!-- SO分析 -->
      <el-tab-pane label="SO分析" name="so">
        <SoAnalyzerPanel />
      </el-tab-pane>

      <!-- 协议分析 -->
      <el-tab-pane label="协议分析" name="protocol">
        <ProtocolAnalyzerPanel />
      </el-tab-pane>

      <!-- 知识检索 -->
      <el-tab-pane label="知识检索" name="search">
        <KnowledgeSearchPanel />
      </el-tab-pane>

      <!-- 工具管理 -->
      <el-tab-pane label="工具管理" name="tools" v-if="isAdmin">
        <ToolManagementPanel />
      </el-tab-pane>
    </el-tabs>

    <!-- 历史记录按钮 -->
    <div class="history-trigger">
      <el-button type="primary" circle @click="showHistory = true">
        <el-icon><Clock /></el-icon>
      </el-button>
    </div>

    <!-- 历史记录抽屉 -->
    <el-drawer v-model="showHistory" title="分析历史" size="400px" direction="rtl">
      <AnalysisHistory />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store'
import './ai-theme.css'
import PromptInjectionPanel from './PromptInjectionPanel.vue'
import ApkAnalyzerPanel from './ApkAnalyzerPanel.vue'
import ApkReversePanel from './ApkReversePanel.vue'
import SoAnalyzerPanel from './SoAnalyzerPanel.vue'
import ProtocolAnalyzerPanel from './ProtocolAnalyzerPanel.vue'
import KnowledgeSearchPanel from './KnowledgeSearchPanel.vue'
import AnalysisHistory from './AnalysisHistory.vue'
import ToolManagementPanel from './ToolManagementPanel.vue'

const userStore = useUserStore()

const activeTab = ref('injection')
const showHistory = ref(false)
const isAdmin = computed(() => userStore.isDefaultAdmin)

onMounted(() => {
  if (!userStore.userInfo) {
    userStore.fetchUserInfo()
  }
})
</script>

<style scoped>
.ai-security-panel {
  height: 100%;
  position: relative;
}

.analysis-tabs {
  height: 100%;
}

.analysis-tabs :deep(.el-tabs__content) {
  height: calc(100% - 40px);
  overflow: auto;
  padding: 0;
}

.analysis-tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow: auto;
}

.history-trigger {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 100;
}
</style>