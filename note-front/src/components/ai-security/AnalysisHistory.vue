<template>
  <div class="analysis-history">
    <!-- 模块筛选 -->
    <div class="filter-tabs">
      <el-radio-group v-model="filterModule" size="small" @change="onFilterChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="APK">APK分析</el-radio-button>
        <el-radio-button value="APK_REVERSE">APK逆向</el-radio-button>
        <el-radio-button value="SO">SO分析</el-radio-button>
        <el-radio-button value="PROTOCOL">协议分析</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 标签页：分析历史 / 反编译文件 -->
    <el-tabs v-model="activeTab" class="history-tabs" @tab-change="onTabChange">
      <el-tab-pane label="分析历史" name="history">
        <!-- 加载状态 -->
        <div v-if="store.historyLoading" class="loading-wrap">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p>加载中...</p>
        </div>

        <!-- 历史列表 -->
        <div v-else-if="store.analysisHistory.length" class="history-list">
          <div
            v-for="item in store.analysisHistory"
            :key="item.id"
            class="history-item"
            @click="loadHistoryItem(item)"
          >
            <div class="history-icon">
              <el-icon :size="24" :color="getTypeColor(item.moduleType)">
                <component :is="getTypeIcon(item.moduleType)" />
              </el-icon>
            </div>

            <div class="history-info">
              <div class="history-filename">{{ item.fileName }}</div>
              <div class="history-meta">
                <el-tag size="small" type="info">{{ item.moduleType }}</el-tag>
                <el-tag size="small" :type="getVerdictType(item.verdict)">
                  {{ item.verdict }}
                </el-tag>
              </div>
              <div class="history-time">{{ item.createdAt }}</div>
            </div>

            <div class="history-actions">
              <el-button text size="small" @click.stop="viewDetail(item)">
                <el-icon><View /></el-icon>
              </el-button>
              <el-button text size="small" @click.stop="deleteItem(item.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <el-empty v-else description="暂无分析记录">
          <template #image>
            <el-icon :size="80" color="#909399"><Clock /></el-icon>
          </template>
        </el-empty>

        <!-- 统计和操作 -->
        <div v-if="store.analysisHistory.length" class="history-stats">
          <el-divider content-position="left">统计</el-divider>
          <div class="stats-grid">
            <div class="stat-item">
              <span class="stat-value">{{ store.historyPagination.total }}</span>
              <span class="stat-label">总分析数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ malwareCount }}</span>
              <span class="stat-label">恶意样本</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ cryptoCount }}</span>
              <span class="stat-label">加密SO</span>
            </div>
          </div>

          <el-button type="danger" plain size="small" @click="clearHistory">
            清空历史记录
          </el-button>
        </div>
      </el-tab-pane>

      <!-- 反编译文件管理 -->
      <el-tab-pane label="反编译文件管理" name="decompile">
        <div v-if="decompileLoading" class="loading-wrap">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        </div>

        <div v-else-if="store.decompileRecords.length" class="decompile-list">
          <el-table :data="store.decompileRecords" stripe size="small">
            <el-table-column prop="apkFileName" label="APK文件" min-width="180" show-overflow-tooltip />
            <el-table-column prop="packageName" label="包名" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.packageName || '未识别' }}
              </template>
            </el-table-column>
            <el-table-column label="文件数" width="90" align="center">
              <template #default="{ row }">
                {{ row.fileCount || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="大小" width="100" align="center">
              <template #default="{ row }">
                {{ formatFileSize(row.totalSize) }}
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="170" />
            <el-table-column label="操作" width="140" align="center" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="accessDecompileDir(row)">
                  访问
                </el-button>
                <el-button text type="danger" size="small" @click="removeDecompileRecord(row.id)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-empty v-else description="暂无反编译文件">
          <template #image>
            <el-icon :size="80" color="#909399"><FolderOpened /></el-icon>
          </template>
        </el-empty>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情抽屉 -->
    <el-drawer v-model="showDetail" title="分析详情" size="60%" direction="rtl">
      <div v-if="selectedItem" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文件名">{{ selectedItem.fileName }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatFileSize(selectedItem.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="分析类型">{{ selectedItem.moduleType }}</el-descriptions-item>
          <el-descriptions-item label="判定结果">
            <el-tag :type="getVerdictType(selectedItem.verdict)">{{ selectedItem.verdict }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ selectedItem.riskLevel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="摘要">{{ selectedItem.summary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分析时间" :span="2">{{ selectedItem.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">详细结果</el-divider>
        <pre class="detail-json">{{ JSON.stringify(selectedItem.analysisResult || selectedItem.result, null, 2) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Files, Document, Connection, Clock, View, Delete, Loading, FolderOpened } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAiSecurityStore } from '@/store/aiSecurity'

const store = useAiSecurityStore()

const activeTab = ref('history')
const showDetail = ref(false)
const selectedItem = ref(null)
const filterModule = ref('')
const decompileLoading = ref(false)

const malwareCount = computed(() => {
  return store.analysisHistory.filter(h =>
    h.verdict?.includes('MALWARE') || h.verdict?.includes('RISK') || h.verdict?.includes('CRITICAL')
  ).length
})

const cryptoCount = computed(() => {
  return store.analysisHistory.filter(h =>
    (h.moduleType === 'SO' || h.moduleType === 'so') && h.verdict === 'HAS_CRYPTO'
  ).length
})

function getTypeIcon(moduleType) {
  const map = { APK: 'Files', APK_REVERSE: 'Files', SO: 'Document', PROTOCOL: 'Connection' }
  return map[moduleType] || 'Files'
}

function getTypeColor(moduleType) {
  const map = { APK: '#409EFF', APK_REVERSE: '#409EFF', SO: '#67C23A', PROTOCOL: '#E6A23C' }
  return map[moduleType] || '#909399'
}

function getVerdictType(verdict) {
  if (!verdict) return 'info'
  const v = verdict.toUpperCase()
  if (v.includes('CRITICAL') || v.includes('MALWARE')) return 'danger'
  if (v.includes('HIGH')) return 'warning'
  if (v.includes('MEDIUM')) return ''
  if (v.includes('CLEAN') || v.includes('SUCCESS')) return 'success'
  if (v.includes('HAS_CRYPTO')) return 'warning'
  return 'info'
}

function formatTime(date) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(date))
}

function formatFileSize(bytes) {
  if (!bytes) return 'N/A'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

function loadHistoryItem(item) {
  selectedItem.value = item
  showDetail.value = true
}

function viewDetail(item) {
  loadHistoryItem(item)
}

async function deleteItem(id) {
  try {
    await ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await store.removeFromHistory(id)
  } catch (e) {
    // 取消或错误
  }
}

async function clearHistory() {
  try {
    await ElMessageBox.confirm('确定要清空所有历史记录吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await store.clearHistory()
  } catch (e) {
    // 取消或错误
  }
}

function onFilterChange() {
  store.loadHistoryFromBackend(filterModule.value || undefined)
}

async function loadDecompileRecords() {
  decompileLoading.value = true
  try {
    await store.loadDecompileRecords()
  } finally {
    decompileLoading.value = false
  }
}

async function removeDecompileRecord(id) {
  try {
    await ElMessageBox.confirm('删除后将同时清理磁盘上的反编译文件，确定删除？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await store.deleteDecompileRecord(id)
  } catch (e) {
    // 取消或错误
  }
}

function accessDecompileDir(row) {
  ElMessage.info(`工作目录: ${row.workDir || '未知路径'}`)
}

// 切换 tab 时加载反编译记录
function onTabChange(tab) {
  if (tab === 'decompile') {
    loadDecompileRecords()
  }
}

onMounted(() => {
  store.loadHistoryFromBackend()
})
</script>

<style scoped>
.analysis-history {
  padding: 16px;
}

.filter-tabs {
  margin-bottom: 16px;
}

.history-tabs {
  margin-top: 4px;
}

.loading-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
  color: #909399;
}

.history-list {
  margin-bottom: 20px;
  max-height: 500px;
  overflow-y: auto;
}

.history-item {
  display: flex;
  align-items: center;
  padding: 16px;
  margin-bottom: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.history-item:hover {
  background: #ecf5ff;
}

.history-icon {
  margin-right: 16px;
}

.history-info {
  flex: 1;
}

.history-filename {
  font-weight: bold;
  margin-bottom: 4px;
}

.history-meta {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}

.history-time {
  color: #909399;
  font-size: 12px;
}

.history-actions {
  display: flex;
  gap: 8px;
}

.history-stats {
  margin-top: 20px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-item {
  text-align: center;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.stat-value {
  display: block;
  font-size: 20px;
  font-weight: bold;
  color: #409EFF;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.decompile-list {
  margin-top: 8px;
}

.detail-content {
  padding: 20px;
}

.detail-json {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  font-size: 12px;
  overflow: auto;
  max-height: 500px;
}
</style>