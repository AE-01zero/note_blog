<template>
  <div class="document-manager">
    <div class="document-header">
      <h3>个人知识库</h3>
      <div class="header-actions">
        <el-input
            v-model="searchForm.keyword"
            placeholder="搜索文档..."
            size="default"
            clearable
            class="search-input"
            prefix-icon="Search"
            @change="searchDocuments"
        />
        <el-select
            v-model="searchForm.fileType"
            placeholder="文件类型"
            size="default"
            clearable
            class="type-select"
            @change="searchDocuments"
        >
          <el-option label="PDF" value="pdf" />
          <el-option label="Word" value="doc" />
          <el-option label="Markdown" value="md" />
          <el-option label="文本" value="txt" />
        </el-select>

        <el-select
            v-model="searchForm.category"
            placeholder="按分类筛选"
            size="default"
            clearable
            class="type-select"
            @change="searchDocuments"
        >
          <el-option label="全部分类" value="" />
          <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="cat.name" />
        </el-select>

        <!-- 新增的刷新按钮 -->
        <el-button
            size="default"
            circle
            :icon="Refresh"
            title="刷新列表"
            @click="loadDocuments"
        />

        <el-select
            v-model="uploadCategory"
            placeholder="选择或输入分类"
            size="default"
            clearable
            filterable
            allow-create
            style="width:160px"
        >
          <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="cat.name" />
        </el-select>

        <el-upload
            ref="uploadRef"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="handleDocumentUpload"
            accept=".pdf,.doc,.docx,.txt,.md"
        >
          <el-button type="primary" size="default" class="upload-btn">
            <el-icon><Upload /></el-icon>
            上传文档
          </el-button>
        </el-upload>
      </div>
    </div>

    <div class="document-content" v-loading="loading">
      <div v-if="documents.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无文档，点击上传按钮添加文档" />
      </div>

      <div v-else class="document-groups">
        <div v-for="group in groupedDocuments" :key="group.key" class="category-group">
          <div class="category-group-header" @click="toggleCategoryGroup(group.key)">
            <div class="group-title">
              <el-icon class="group-arrow">
                <ArrowDown v-if="isCategoryGroupExpanded(group.key)" />
                <ArrowRight v-else />
              </el-icon>
              <span>{{ group.label }}</span>
            </div>
            <el-tag size="small" effect="plain">{{ group.items.length }}</el-tag>
          </div>

          <div v-show="isCategoryGroupExpanded(group.key)" class="document-grid">
            <div
                v-for="doc in group.items"
                :key="doc.id"
                class="document-card"
                @click="previewDocument(doc)"
            >
              <div class="card-header">
                <div class="file-icon">
                  <el-icon size="32" :color="getFileTypeColor(doc.fileType)">
                    <Document v-if="doc.fileType === 'pdf'" />
                    <Edit v-else-if="doc.fileType === 'doc' || doc.fileType === 'docx'" />
                    <Memo v-else />
                  </el-icon>
                </div>
                <div class="file-type-tag">
                  <el-tag
                      :type="getFileTypeTagColor(doc.fileType)"
                      size="small"
                      round
                  >
                    {{ getFileTypeLabel(doc.mimeType) }}
                  </el-tag>
                </div>
              </div>

              <div class="card-body">
                <div class="file-name" :title="doc.originalFilename">
                  {{ doc.originalFilename }}
                </div>
                <div class="file-meta">
                  <span class="file-size">{{ formatFileSize(null, null, doc.fileSize) }}</span>
                  <span class="file-date">{{ formatTime(null, null, doc.createTime) }}</span>
                </div>
                <div v-if="doc.category" style="margin-top:4px">
                  <el-tag size="small" type="warning" round>{{ doc.category }}</el-tag>
                </div>
              </div>

              <div class="card-actions">
                <el-button
                    type="primary"
                    size="small"
                    text
                    @click.stop="previewDocument(doc)"
                >
                  预览
                </el-button>
                <el-button
                    type="warning"
                    size="small"
                    text
                    @click.stop="openEditCategory(doc)"
                >
                  分类
                </el-button>
                <el-button
                    type="danger"
                    size="small"
                    text
                    @click.stop="deleteDocument(doc)"
                >
                  删除
                </el-button>
              </div>

              <div class="status-indicator">
                <el-tag
                    :type="doc.processingStatus === 'SUCCESS' ? 'success' : 'warning'"
                    size="small"
                    round
                >
                  {{ doc.processingStatus === 'SUCCESS' ? '已处理' : '处理中' }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="documents.length > 0" class="pagination-container">
        <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :page-sizes="[12, 24, 36]"
            layout="total, sizes, prev, pager, next"
            :total="pagination.total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 文档预览对话框 -->
    <el-dialog
        v-model="previewDialog.visible"
        :title="previewDialog.title"
        width="80%"
        center
        destroy-on-close
        @closed="resetPreviewDialog"
    >
      <div class="preview-content" :class="{ 'text-preview-shell': previewDialog.fileType === 'txt' || previewDialog.fileType === 'md' }">
        <div
            v-if="previewDialog.fileType === 'txt' || previewDialog.fileType === 'md'"
            class="preview-meta-bar"
        >
          <div class="preview-meta-main">
            <div class="preview-meta-title">{{ previewDialog.metadata?.fileName }}</div>
            <div class="preview-meta-subtitle">
              <span>{{ previewDialog.metadata?.typeLabel }}</span>
              <span>{{ previewDialog.metadata?.fileSize }}</span>
              <span>{{ previewDialog.metadata?.updateTime }}</span>
            </div>
          </div>
          <div class="preview-meta-tags">
            <el-tag size="small" round>{{ previewDialog.metadata?.sourceLabel }}</el-tag>
            <el-tag size="small" round type="warning">{{ previewDialog.metadata?.category }}</el-tag>
          </div>
        </div>
        <!-- PDF -->
        <iframe
            v-if="previewDialog.fileType === 'pdf'"
            :src="previewDialog.fileUrl"
            style="width: 100%; height: 70vh; border: none; border-radius: 8px;"
        />
        <!-- TXT -->
        <pre
            v-else-if="previewDialog.fileType === 'txt'"
            class="doc-text-preview"
        >{{ previewDialog.textContent || '暂无文本内容' }}</pre>
        <!-- MD -->
        <div
            v-else-if="previewDialog.fileType === 'md'"
            class="doc-markdown-preview"
            v-html="previewDialog.markdownContent || '<p>暂无 Markdown 内容</p>'"
        />
        <!-- 兜底 -->
        <div v-else class="preview-placeholder">
          <el-empty description="文档预览暂不可用" />
        </div>
      </div>
    </el-dialog>

    <!-- 编辑分类 dialog -->
    <el-dialog v-model="editCategoryDialog.visible" title="设置分类" width="360px">
      <el-select
          v-model="editCategoryDialog.category"
          placeholder="选择或输入新分类名"
          filterable
          allow-create
          clearable
          style="width:100%"
      >
        <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="cat.name" />
      </el-select>
      <div style="font-size:12px;color:#999;margin-top:4px">可直接输入新分类名创建</div>
      <template #footer>
        <el-button @click="editCategoryDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <UploadTaskPanel :tasks="uploadTasks" title="文档上传进度" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useUserStore } from '@/store'
import { getDocumentList, deleteDocument as deleteDocumentApi, uploadDocument } from '@/api'
import { getMyCategories, createCategory } from '@/api/blog'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import UploadTaskPanel from '@/components/UploadTaskPanel.vue'
import {
  MAX_UPLOAD_SIZE_MB,
  createUploadTask,
  updateUploadTaskProgress,
  markUploadTaskSuccess,
  markUploadTaskError,
  scheduleUploadTaskCleanup
} from '@/utils/uploadProgress'
// 导入 Refresh 图标
import { Document, Edit, Memo, Upload, Search, Refresh, ArrowDown, ArrowRight } from '@element-plus/icons-vue'

const SUPPORTED_UPLOAD_EXTENSIONS = ['pdf', 'doc', 'docx', 'txt', 'md']
const SUPPORTED_UPLOAD_LABEL = 'PDF、Word、Markdown、文本文件'

const userStore = useUserStore()

const categoryOptions = ref([])

const loadCategories = async () => {
  try {
    const res = await getMyCategories()
    categoryOptions.value = res.data?.data || res.data || []
  } catch (e) {
    categoryOptions.value = []
  }
}

// 数据
const documents = ref([])
const loading = ref(false)
const uploadCategory = ref('')
const uploadTasks = ref([])
const categoryExpandState = ref({})

const groupedDocuments = computed(() => {
  const grouped = new Map()
  const uncategorizedKey = '__uncategorized__'

  documents.value.forEach((doc) => {
    const category = (doc.category || '').trim()
    const key = category || uncategorizedKey
    if (!grouped.has(key)) {
      grouped.set(key, {
        key,
        label: category || 'Uncategorized',
        items: []
      })
    }
    grouped.get(key).items.push(doc)
  })

  const groups = Array.from(grouped.values())
  return groups.sort((a, b) => {
    if (a.key === uncategorizedKey) return 1
    if (b.key === uncategorizedKey) return -1
    return a.label.localeCompare(b.label, 'zh-Hans-CN')
  })
})

const isCategoryGroupExpanded = (key) => {
  return categoryExpandState.value[key] !== false
}

const toggleCategoryGroup = (key) => {
  categoryExpandState.value[key] = !isCategoryGroupExpanded(key)
}

watch(groupedDocuments, (groups) => {
  const next = {}
  groups.forEach((group) => {
    next[group.key] = categoryExpandState.value[group.key] !== false
  })
  categoryExpandState.value = next
}, { immediate: true })

// 搜索表单
const searchForm = reactive({
  keyword: '',
  fileType: '',
  category: '',
  sortBy: 'create_time',
  sortOrder: 'desc'
})

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 预览对话框
const previewDialog = reactive({
  visible: false,
  title: '',
  fileUrl: '',
  fileType: '',
  textContent: '',
  markdownContent: '',
  metadata: null
})

const resetPreviewDialog = () => {
  previewDialog.title = ''
  previewDialog.fileUrl = ''
  previewDialog.fileType = ''
  previewDialog.textContent = ''
  previewDialog.markdownContent = ''
  previewDialog.metadata = null
}

const buildPreviewMetadata = (doc) => ({
  fileName: doc.originalFilename || doc.fileName || '未命名文件',
  category: doc.category || '未分类',
  fileSize: formatFileSize(null, null, doc.fileSize || 0),
  sourceLabel: doc.processingStatus === 'SUCCESS' ? '个人知识库 · 已处理' : '个人知识库 · 处理中',
  updateTime: formatTime(null, null, doc.updateTime || doc.createTime),
  typeLabel: getFileTypeLabel(doc.mimeType || doc.fileType || '')
})

// 获取文档列表
const loadDocuments = async () => {
  loading.value = true
  try {
    const response = await getDocumentList({
      page: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword,
      fileType: searchForm.fileType,
      category: searchForm.category,
      sortBy: searchForm.sortBy,
      sortOrder: searchForm.sortOrder
    })

    console.log('API响应数据:', response.data) // 调试信息

    // 处理不同的响应格式
    let responseData = response.data
    let documentList = []

    // 如果响应数据有code字段
    if (responseData.code !== undefined) {
      if (responseData.code === 0) {
        // 标准格式：{ code: 0, data: [...], message: "" }
        if (Array.isArray(responseData.data)) {
          documentList = responseData.data
          pagination.total = responseData.data.length
        } else if (responseData.data && Array.isArray(responseData.data.data)) {
          documentList = responseData.data.data
          pagination.total = responseData.data.total || responseData.data.data.length
        } else if (responseData.data && Array.isArray(responseData.data.records)) {
          documentList = responseData.data.records
          pagination.total = responseData.data.total || responseData.data.records.length
        } else if (responseData.data) {
          // 单个对象
          documentList = [responseData.data]
          pagination.total = 1
        }
      } else {
        ElMessage.error(responseData.message || '获取文档列表失败')
        return
      }
    } else {
      // 没有code字段，直接处理数据
      if (Array.isArray(responseData)) {
        // 直接是数组
        documentList = responseData
        pagination.total = responseData.length
      } else if (responseData && typeof responseData === 'object') {
        // 单个对象
        documentList = [responseData]
        pagination.total = 1
      }
    }

    // 处理文档数据，统一格式
    documents.value = documentList.map(doc => ({
      ...doc,
      // 处理文件类型映射
      fileType: getFileTypeFromExtension(doc.extension || doc.fileType),
      // 处理时间格式
      createTime: doc.createTime || doc.updateTime || new Date().toISOString(),
      updateTime: doc.updateTime || doc.createTime || new Date().toISOString()
    }))

    console.log('处理后的文档列表:', documents.value) // 调试信息
  } catch (error) {
    console.error('加载文档列表失败:', error)
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索文档
const searchDocuments = () => {
  pagination.current = 1
  loadDocuments()
}

const pushUploadTask = (file) => {
  const task = createUploadTask(file, { name: `文档上传 · ${file.name}` })
  uploadTasks.value.unshift(task)
  if (uploadTasks.value.length > 8) {
    uploadTasks.value = uploadTasks.value.slice(0, 8)
  }
  return task
}

const finishUploadTask = (task, error) => {
  if (!task) return

  if (error) {
    markUploadTaskError(task, error)
    scheduleUploadTaskCleanup(uploadTasks, task.id, 5000)
    return
  }

  markUploadTaskSuccess(task)
  scheduleUploadTaskCleanup(uploadTasks, task.id)
}

// 上传前：若分类是新建输入的则先创建
const beforeUpload = async (file) => {
  const extension = (file.name.split('.').pop() || '').toLowerCase()
  if (!SUPPORTED_UPLOAD_EXTENSIONS.includes(extension)) {
    ElMessage.error(`只支持 ${SUPPORTED_UPLOAD_LABEL}`)
    return false
  }
  if (file.size / 1024 / 1024 > MAX_UPLOAD_SIZE_MB) {
    ElMessage.error(`文件大小不能超过 ${MAX_UPLOAD_SIZE_MB}MB`)
    return false
  }
  // 若分类是新输入的（不在现有列表中），先创建
  const cat = uploadCategory.value
  if (cat && !categoryOptions.value.find(c => c.name === cat)) {
    try {
      const res = await createCategory({ name: cat })
      const newCat = res.data?.data || res.data
      categoryOptions.value.push(newCat)
    } catch (e) { /* 忽略，继续上传 */ }
  }
  return true
}

const handleDocumentUpload = async (options) => {
  const file = options.file
  const uploadTask = pushUploadTask(file)

  try {
    const response = await uploadDocument(file, uploadCategory.value || undefined, {
      onUploadProgress: (progressEvent) => updateUploadTaskProgress(uploadTask, progressEvent)
    })
    finishUploadTask(uploadTask)
    options.onSuccess?.(response.data, file)
    ElMessage.success('文档上传成功')
    await loadDocuments()
  } catch (error) {
    console.error('上传失败:', error)
    finishUploadTask(uploadTask, error)
    options.onError?.(error)
    ElMessage.error('上传失败')
  }
}

// 删除文档
const deleteDocument = async (row) => {
  try {
    await ElMessageBox.confirm(
        `确定要删除文档"${row.originalFilename}" 吗？`,
        '确认删除',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    const response = await deleteDocumentApi(row.id)
    console.log('删除响应:', response.data) // 调试信息
    if (response.data && (response.data.code === 0 || response.data.code === undefined)) {
      ElMessage.success('删除成功')
      loadDocuments()
    } else {
      ElMessage.error((response.data && response.data.message) || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 编辑分类
const editCategoryDialog = reactive({ visible: false, fileId: null, category: '' })

const openEditCategory = (doc) => {
  editCategoryDialog.fileId = doc.id
  editCategoryDialog.category = doc.category || ''
  editCategoryDialog.visible = true
}

const saveCategory = async () => {
  try {
    let categoryName = editCategoryDialog.category
    // 若是新分类名（不在现有列表中），先创建
    const exists = categoryOptions.value.find(c => c.name === categoryName)
    if (categoryName && !exists) {
      const res = await createCategory({ name: categoryName })
      const newCat = res.data?.data || res.data
      categoryOptions.value.push(newCat)
    }
    await axios.patch(`/api/documents/files/${editCategoryDialog.fileId}/category`, null, {
      params: { category: categoryName },
      headers: { 'Authorization': `Bearer ${userStore.token}` }
    })
    ElMessage.success('分类已更新')
    editCategoryDialog.visible = false
    loadDocuments()
  } catch (e) {
    ElMessage.error('更新分类失败')
  }
}

// 将fileUrl中的域名替换为当前访问的服务器地址，解决localhost问题
const fixFileUrl = (url) => {
  if (!url) return url
  try {
    const u = new URL(url)
    u.host = window.location.host
    u.protocol = window.location.protocol
    return u.toString()
  } catch (e) {
    return url
  }
}

// 预览文档
const previewDocument = async (row) => {
  if (!row.fileUrl) {
    ElMessage.warning('该文件暂不支持预览')
    return
  }
  const ext = (row.fileExtension || row.extension || row.fileType || (row.originalFilename || row.fileName || '').split('.').pop() || '').toLowerCase()
  const fileUrl = fixFileUrl(row.fileUrl)

  previewDialog.visible = true
  previewDialog.title = row.originalFilename || row.fileName || ''
  previewDialog.fileUrl = ''
  previewDialog.fileType = ''
  previewDialog.textContent = ''
  previewDialog.markdownContent = ''
  previewDialog.metadata = buildPreviewMetadata(row)

  try {
    if (ext === 'pdf') {
      previewDialog.fileType = 'pdf'
      previewDialog.fileUrl = fileUrl
      return
    }
    if (ext === 'txt') {
      const resp = await fetch(fileUrl)
      if (!resp.ok) throw new Error(`TXT加载失败: ${resp.status}`)
      previewDialog.fileType = 'txt'
      previewDialog.textContent = await resp.text()
      return
    }
    if (ext === 'md' || ext === 'markdown') {
      const { marked } = await import('marked')
      const resp = await fetch(fileUrl)
      if (!resp.ok) throw new Error(`Markdown加载失败: ${resp.status}`)
      const text = await resp.text()
      previewDialog.fileType = 'md'
      previewDialog.markdownContent = marked.parse(text)
      return
    }
    ElMessage.warning('当前仅支持 pdf、txt、md 预览')
    previewDialog.visible = false
  } catch (error) {
    console.error('文件预览失败:', error)
    ElMessage.error('文件预览失败')
    previewDialog.visible = false
  }
}

// 格式化文件大小
const formatFileSize = (row, column, cellValue) => {
  if (cellValue === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(cellValue) / Math.log(k))
  return parseFloat((cellValue / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化时间
const formatTime = (row, column, cellValue) => {
  if (!cellValue) return '未知时间'

  try {
    const date = new Date(cellValue)
    // 检查日期是否有效
    if (isNaN(date.getTime())) {
      return '未知时间'
    }
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch (error) {
    console.error('时间格式化失败', error)
    return '未知时间'
  }
}

// 根据文件扩展名获取文件类型
const getFileTypeFromExtension = (extension) => {
  if (!extension) return 'other'

  const ext = extension.toLowerCase().replace('.', '')
  const typeMap = {
    'pdf': 'pdf',
    'txt': 'txt',
    'docx': 'docx',
    'doc': 'docx',
    'other': 'txt' // 将OTHER映射为txt
  }

  return typeMap[ext] || 'other'
}

// 获取文件类型标签颜色
const getFileTypeTagColor = (fileType) => {
  const colorMap = {
    'pdf': 'danger',
    'docx': 'primary',
    'txt': 'info',
    'other': 'info'
  }
  return colorMap[fileType] || 'info'
}

// 获取文件类型标签文本
const getFileTypeLabel = (fileType) => {
  const labelMap = {
    'application/pdf': 'PDF',
    'application/docx': 'WORD',
    'text/plain': '文本',
    'other': '其他'
  }
  return labelMap[fileType] || '其他'
}

// 获取文件类型颜色
const getFileTypeColor = (fileType) => {
  const colorMap = {
    'pdf': '#f56565',
    'docx': '#4299e1',
    'txt': '#48bb78',
    'other': '#9f7aea'
  }
  return colorMap[fileType] || '#9f7aea'
}

// 排序处理
const handleSortChange = ({ column, prop, order }) => {
  searchForm.sortBy = prop
  searchForm.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  loadDocuments()
}

// 分页处理
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadDocuments()
}

const handleCurrentChange = (page) => {
  pagination.current = page
  loadDocuments()
}

// 测试API连接
const testApi = async () => {
  try {
    console.log('开始测试API连接...')

    // 直接测试API调用
    const response = await fetch('/api/documents/files/list', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userStore.token}`
      },
      body: JSON.stringify({
        page: 1,
        size: 10
      })
    })

    console.log('Fetch响应状态', response.status)
    console.log('Fetch响应头', response.headers)

    const data = await response.json()
    console.log('Fetch响应数据:', data)

  } catch (error) {
    console.error('Fetch测试失败:', error)
  }
}

// 生命周期
onMounted(() => {
  console.log('DocumentManager mounted, token:', userStore.token)
  testApi() // 先测试API
  loadDocuments()
  loadCategories()
})
</script>

<style scoped>
.document-manager {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 20%, #f093fb 60%, #f5576c 90%, #4facfe 100%);
  border-radius: 24px;
  overflow: hidden;
  margin: 20px;
  box-shadow: 0 20px 60px rgba(139, 92, 246, 0.2);
}

.document-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
  box-shadow: 0 10px 40px rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
}

.document-header h3 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.02em;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-input {
  width: 240px;
}

.type-select {
  width: 140px;
}

.upload-btn {
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(64, 153, 255, 0.2);
}

.upload-btn:hover {
  box-shadow: 0 4px 8px rgba(64, 153, 255, 0.3);
}

.document-content {
  flex: 1;
  min-height: 0;
  padding: 30px;
  overflow: auto;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(248, 250, 252, 0.9) 100%);
  backdrop-filter: blur(20px);
}

.empty-state {
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.document-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.category-group {
  border: 1px solid rgba(139, 92, 246, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.55);
  padding: 14px 16px 16px;
}

.category-group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding: 8px 10px;
  border-radius: 12px;
  cursor: pointer;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.08) 0%, rgba(59, 130, 246, 0.08) 100%);
}

.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #334155;
}

.group-arrow {
  color: #6366f1;
}

.document-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  padding: 0;
}

.document-card {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border: 1px solid rgba(139, 92, 246, 0.1);
  border-radius: 24px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(20px);
  box-shadow: 0 10px 40px rgba(139, 92, 246, 0.1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: relative;
  overflow: hidden;
}

.document-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border-color: #409eff;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.file-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 8px;
  background: rgba(64, 153, 255, 0.1);
}

.file-type-tag {
  position: absolute;
  top: 12px;
  right: 12px;
}

.card-body {
  margin-bottom: 16px;
}

.file-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.file-size {
  font-weight: 500;
}

.file-date {
  color: #c0c4cc;
}

.card-actions {
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.3s;
}

.document-card:hover .card-actions {
  opacity: 1;
}

.status-indicator {
  position: absolute;
  bottom: 12px;
  right: 12px;
}

.pagination-container {
  padding: 24px;
  display: flex;
  justify-content: center;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}

.preview-content {
  border-radius: 16px;
  overflow: hidden;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
}

.text-preview-shell {
  min-height: 70vh;
  display: flex;
  flex-direction: column;
}

.preview-meta-bar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid rgba(99, 102, 241, 0.12);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
}

.preview-meta-main {
  min-width: 0;
}

.preview-meta-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.4;
}

.preview-meta-subtitle {
  margin-top: 6px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #64748b;
}

.preview-meta-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
}

.doc-text-preview,
.doc-markdown-preview {
  box-sizing: border-box;
  width: 100%;
  min-height: calc(70vh - 78px);
  margin: 0;
  padding: 28px 32px;
  color: #334155;
  background: rgba(255, 255, 255, 0.96);
  line-height: 1.9;
  overflow: auto;
  max-width: 980px;
  align-self: center;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
}

.doc-text-preview {
  font-size: 15px;
  font-family: "JetBrains Mono", "Consolas", "Microsoft YaHei", monospace;
}

.doc-markdown-preview :deep(h1),
.doc-markdown-preview :deep(h2),
.doc-markdown-preview :deep(h3),
.doc-markdown-preview :deep(h4) {
  color: #0f172a;
  line-height: 1.35;
  margin-top: 1.6em;
  margin-bottom: 0.7em;
}

.doc-markdown-preview :deep(p),
.doc-markdown-preview :deep(li),
.doc-markdown-preview :deep(blockquote) {
  font-size: 15px;
}

.doc-markdown-preview :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
}

.doc-markdown-preview :deep(th),
.doc-markdown-preview :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 10px 12px;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .document-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  }
}

@media (max-width: 768px) {
  .document-header {
    flex-direction: column;
    gap: 16px;
    padding: 16px;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .search-input {
    width: 200px;
  }

  .type-select {
    width: 120px;
  }

  .document-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
  }

  .document-content {
    padding: 16px;
  }
}

.doc-markdown-preview {
  box-sizing: border-box;
  width: 100%;
  min-height: 70vh;
  padding: 16px;
  color: #303133;
  background: #fff;
  line-height: 1.8;
  word-break: break-word;
  overflow-wrap: break-word;
}

.doc-markdown-preview :deep(pre) {
  padding: 12px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 6px;
}

.doc-markdown-preview :deep(code) {
  padding: 2px 4px;
  background: #f5f7fa;
  border-radius: 4px;
}

.doc-markdown-preview :deep(img) {
  max-width: 100%;
}
</style>
