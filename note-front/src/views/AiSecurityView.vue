<template>
  <div class="ai-security-view">
    <!-- 顶部导航 -->
    <div class="top-nav">
      <div class="nav-left">
        <h2>AI安全分析</h2>
      </div>
      <div class="nav-right">
        <el-button @click="showHistory = true">
          <el-icon><Clock /></el-icon>
          分析历史
        </el-button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <el-tabs v-model="activeTab" type="border-card" class="analysis-tabs">
        <!-- AI指导分析 -->
        <el-tab-pane label="AI指导分析" name="guided">
          <GuidedAnalysisPanel />
        </el-tab-pane>

        <!-- Prompt注入检测 -->
        <el-tab-pane label="Prompt注入检测" name="injection">
          <PromptInjectionPanel />
        </el-tab-pane>

        <!-- APK分析 -->
        <el-tab-pane label="APK分析" name="apk">
          <ApkAnalyzerPanel />
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
      </el-tabs>
    </div>

    <!-- 历史记录抽屉 -->
    <el-drawer v-model="showHistory" title="分析历史" size="400px" direction="rtl">
      <AnalysisHistory />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import GuidedAnalysisPanel from '@/components/ai-security/GuidedAnalysisPanel.vue'
import PromptInjectionPanel from '@/components/ai-security/PromptInjectionPanel.vue'
import ApkAnalyzerPanel from '@/components/ai-security/ApkAnalyzerPanel.vue'
import SoAnalyzerPanel from '@/components/ai-security/SoAnalyzerPanel.vue'
import ProtocolAnalyzerPanel from '@/components/ai-security/ProtocolAnalyzerPanel.vue'
import KnowledgeSearchPanel from '@/components/ai-security/KnowledgeSearchPanel.vue'
import AnalysisHistory from '@/components/ai-security/AnalysisHistory.vue'

const activeTab = ref('guided')
const showHistory = ref(false)
</script>

<style scoped>
.ai-security-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(135deg, #eef3ff 0%, #f5f8ff 32%, #fff8f3 72%, #f4f9ff 100%);
}

.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(124, 139, 167, 0.1);
}

.nav-left h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
}

.nav-right {
  display: flex;
  gap: 12px;
}

.main-content {
  flex: 1;
  padding: 20px 24px;
  overflow: auto;
}

.analysis-tabs {
  height: 100%;
}

.analysis-tabs :deep(.el-tabs__content) {
  height: calc(100% - 40px);
  overflow: auto;
}
</style>