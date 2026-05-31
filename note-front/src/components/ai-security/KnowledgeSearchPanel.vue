<template>
  <div class="knowledge-search-panel">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <template #header>
        <span>知识检索</span>
      </template>

      <el-input
        v-model="searchQuery"
        placeholder="输入关键词搜索安全知识..."
        size="large"
        clearable
        @keyup.enter="search"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button :loading="isSearching" @click="search">搜索</el-button>
        </template>
      </el-input>

      <!-- 快速分类 -->
      <div class="quick-categories">
        <el-tag
          v-for="cat in categories"
          :key="cat.value"
          :type="selectedCategory === cat.value ? 'primary' : 'info'"
          class="category-tag"
          @click="selectCategory(cat.value)"
        >
          {{ cat.label }}
        </el-tag>
      </div>
    </el-card>

    <!-- 搜索结果 -->
    <el-card v-if="searchResults.length" class="results-card">
      <template #header>
        <span>搜索结果 ({{ searchResults.length }})</span>
      </template>

      <el-scrollbar height="500px">
        <div class="results-list">
          <div
            v-for="(result, index) in searchResults"
            :key="index"
            class="result-item"
            @click="viewDocument(result)"
          >
            <div class="result-header">
              <h4 class="result-title">{{ result.title }}</h4>
              <el-tag size="small" :type="getDocTypeColor(result.docType)">
                {{ result.docType }}
              </el-tag>
            </div>

            <div class="result-content">
              <p class="result-excerpt">{{ result.excerpt }}</p>
            </div>

            <div class="result-meta">
              <span class="meta-item">
                <el-icon><Document /></el-icon>
                {{ result.platform }}
              </span>
              <span class="meta-item">
                <el-icon><Collection /></el-icon>
                {{ result.difficulty }}
              </span>
              <span v-if="result.tags?.length" class="meta-item">
                <el-tag
                  v-for="tag in result.tags.slice(0, 3)"
                  :key="tag"
                  size="small"
                  type="info"
                >
                  {{ tag }}
                </el-tag>
              </span>
            </div>

            <div class="result-score">
              <span class="score-label">相似度</span>
              <el-progress
                :percentage="Math.round(result.score * 100)"
                :stroke-width="12"
                :status="getScoreStatus(result.score)"
              />
            </div>
          </div>
        </div>
      </el-scrollbar>
    </el-card>

    <!-- 推荐知识 -->
    <el-card v-if="!searchResults.length && !isSearching" class="recommend-card">
      <template #header>
        <span>推荐知识</span>
      </template>

      <el-row :gutter="20">
        <el-col :span="8" v-for="doc in recommendedDocs" :key="doc.id">
          <div class="recommend-item" @click="viewDocument(doc)">
            <div class="recommend-icon">
              <el-icon :size="40" :color="doc.color">
                <component :is="doc.icon" />
              </el-icon>
            </div>
            <h4>{{ doc.title }}</h4>
            <p>{{ doc.description }}</p>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 文档预览抽屉 -->
    <el-drawer v-model="showPreview" title="文档预览" size="70%" direction="rtl">
      <div v-if="previewDoc" class="document-preview">
        <h2>{{ previewDoc.title }}</h2>

        <div class="preview-meta">
          <el-tag>{{ previewDoc.platform }}</el-tag>
          <el-tag type="success">{{ previewDoc.difficulty }}</el-tag>
          <el-tag type="info">{{ previewDoc.docType }}</el-tag>
        </div>

        <el-divider />

        <div class="preview-content" v-html="previewDoc.content"></div>

        <div class="preview-actions">
          <el-button type="primary" @click="copyToNote">复制到笔记</el-button>
          <el-button @click="openInNewTab">在新标签页打开</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Search, Document, Collection } from '@element-plus/icons-vue'
import { useAiSecurityStore } from '@/store/aiSecurity'
import { searchKnowledge } from '@/api/aiAnalysis'

const store = useAiSecurityStore()

const searchQuery = ref('')
const isSearching = ref(false)
const searchResults = ref([])
const selectedCategory = ref('all')
const showPreview = ref(false)
const previewDoc = ref(null)

const categories = [
  { label: '全部', value: 'all' },
  { label: 'AI安全', value: 'ai_security' },
  { label: 'Android安全', value: 'android' },
  { label: 'iOS安全', value: 'ios' },
  { label: '二进制安全', value: 'binary' },
  { label: '协议分析', value: 'protocol' },
  { label: '工具使用', value: 'tool' }
]

const recommendedDocs = [
  {
    id: 1,
    title: 'APK恶意检测',
    description: '学习如何识别广告木马、隐私窃取等恶意软件',
    icon: 'Files',
    color: '#409EFF',
    platform: 'android'
  },
  {
    id: 2,
    title: 'SO文件分析',
    description: '掌握Native层代码的逆向和分析技巧',
    icon: 'Cpu',
    color: '#67C23A',
    platform: 'android'
  },
  {
    id: 3,
    title: 'Prompt注入检测',
    description: '了解AI安全中的Prompt注入攻击与防御',
    icon: 'Warning',
    color: '#E6A23C',
    platform: 'ai_security'
  }
]

async function search() {
  if (!searchQuery.value.trim()) return

  isSearching.value = true

  try {
    const response = await searchKnowledge(
      searchQuery.value,
      selectedCategory.value === 'all' ? 'all' : selectedCategory.value
    )

    searchResults.value = response.data.data || []
  } catch (error) {
    console.error('搜索失败:', error)
  } finally {
    isSearching.value = false
  }
}

function selectCategory(category) {
  selectedCategory.value = category
  if (searchQuery.value) {
    search()
  }
}

function viewDocument(doc) {
  previewDoc.value = doc
  showPreview.value = true
}

function copyToNote() {
  // 复制到笔记功能
  ElMessage.success('已复制到剪贴板')
}

function openInNewTab() {
  window.open(`/knowledge/${previewDoc.value.id}`, '_blank')
}

function getDocTypeColor(docType) {
  const map = {
    tutorial: 'primary',
    writeup: 'success',
    tool: 'warning',
    reference: 'info'
  }
  return map[docType] || 'info'
}

function getScoreStatus(score) {
  if (score > 0.8) return 'success'
  if (score > 0.5) return 'warning'
  return 'exception'
}
</script>

<style scoped>
.knowledge-search-panel {
  padding: 20px;
}

.search-card, .results-card, .recommend-card {
  margin-bottom: 20px;
}

.quick-categories {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.category-tag {
  cursor: pointer;
}

.results-list {
  padding: 10px;
}

.result-item {
  padding: 16px;
  margin-bottom: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.result-item:hover {
  background: #ecf5ff;
  transform: translateX(4px);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.result-title {
  margin: 0;
  font-size: 16px;
}

.result-content {
  margin-bottom: 8px;
}

.result-excerpt {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}

.result-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
  color: #909399;
  font-size: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.result-score {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-label {
  font-size: 12px;
  color: #909399;
}

.recommend-item {
  padding: 20px;
  text-align: center;
  background: #f5f7fa;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.recommend-item:hover {
  background: #ecf5ff;
  transform: translateY(-4px);
}

.recommend-icon {
  margin-bottom: 12px;
}

.recommend-item h4 {
  margin: 0 0 8px 0;
}

.recommend-item p {
  margin: 0;
  color: #606266;
  font-size: 12px;
}

.document-preview {
  padding: 20px;
}

.preview-meta {
  display: flex;
  gap: 8px;
  margin: 16px 0;
}

.preview-content {
  line-height: 1.8;
}

.preview-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}
</style>