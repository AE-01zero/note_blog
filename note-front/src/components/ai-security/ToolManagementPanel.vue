<template>
  <div class="tool-management-panel">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>插件管理</span>
          <el-tag v-if="canUpload" type="success">可上传插件</el-tag>
          <el-tag v-else type="info">查看模式</el-tag>
        </div>
      </template>

      <!-- 工具状态概览 -->
      <el-alert
        v-if="!allToolsAvailable"
        title="部分工具未配置"
        type="warning"
        description="请管理员上传 apktool.jar、jadx jar 和 tshark 文件以启用APK分析和协议分析功能"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-row :gutter="16" class="tool-cards">
        <el-col v-for="tool in displayTools" :key="tool.name" :span="12" :lg="6">
          <el-card shadow="hover" class="tool-card">
            <template #header>
              <div class="tool-header">
                <span>{{ tool.displayName }}</span>
                <el-tag :type="getStatusType(tool.status)" size="small">
                  {{ getStatusText(tool.status) }}
                </el-tag>
              </div>
            </template>

            <div class="tool-info">
              <p class="tool-desc">{{ tool.description }}</p>

              <div v-if="tool.path" class="tool-path">
                <el-tooltip :content="tool.path" placement="top">
                  <span class="path-text">{{ formatPath(tool.path) }}</span>
                </el-tooltip>
              </div>

              <div v-if="tool.version" class="tool-version">
                版本: {{ tool.version }}
              </div>

              <div v-if="tool.lastVerified" class="tool-verified">
                上次验证: {{ formatDate(tool.lastVerified) }}
              </div>

              <!-- 测试结果 -->
              <div v-if="tool.testResult" class="tool-test-result">
                <el-alert
                  :title="tool.testResult.success ? '测试通过' : '测试失败'"
                  :type="tool.testResult.success ? 'success' : 'error'"
                  :description="tool.testResult.message"
                  :closable="true"
                  @close="tool.testResult = null"
                  show-icon
                  style="margin-top: 8px"
                />
              </div>
            </div>

            <!-- 管理员操作 -->
            <div v-if="canUpload" class="tool-actions">
              <el-upload
                :action="`/api/tools/upload`"
                :data="{ toolName: tool.name }"
                :headers="uploadHeaders"
                :show-file-list="false"
                :on-success="handleUploadSuccess"
                :on-error="handleUploadError"
                :before-upload="(file) => beforeUpload(file, tool.name)"
                :accept="tool.name === 'tshark' ? '.exe,.bat,.sh' : '.jar'"
              >
                <el-button type="primary" size="small">
                  <el-icon><Upload /></el-icon>
                  {{ tool.status === 'NOT_CONFIGURED' ? '上传插件' : '更新插件' }}
                </el-button>
              </el-upload>

              <el-button
                v-if="tool.status !== 'NOT_CONFIGURED'"
                type="success"
                size="small"
                plain
                :loading="tool.testing"
                @click="handleTest(tool)"
              >
                <el-icon v-if="!tool.testing"><VideoPlay /></el-icon>
                测试
              </el-button>

              <el-button
                v-if="tool.status !== 'NOT_CONFIGURED'"
                type="danger"
                size="small"
                plain
                @click="handleDelete(tool.name)"
              >
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>

            <!-- 普通用户提示 -->
            <div v-else class="tool-locked">
              <el-icon><Lock /></el-icon>
              <span>查看模式</span>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 已启用功能 -->
      <el-divider content-position="left">已启用功能</el-divider>

      <div class="enabled-features">
        <el-tag
          v-for="feature in enabledFeatures"
          :key="feature"
          type="success"
          class="feature-tag"
        >
          {{ formatFeatureName(feature) }}
        </el-tag>
        <el-tag v-if="enabledFeatures.length === 0" type="info">
          暂无启用功能
        </el-tag>
      </div>

      <!-- 功能权限检查 -->
      <el-divider content-position="left">功能权限</el-divider>

      <div class="feature-checks">
        <el-space wrap>
          <div v-for="feature in featureList" :key="feature.name" class="feature-check">
            <span>{{ feature.displayName }}</span>
            <el-tag
              v-if="feature.allowed"
              type="success"
              size="small"
            >
              可用
            </el-tag>
            <el-tag
              v-else-if="feature.requiresAdmin"
              type="warning"
              size="small"
            >
              需管理员
            </el-tag>
            <el-tag
              v-else
              type="info"
              size="small"
            >
              不可用
            </el-tag>
          </div>
        </el-space>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Delete, Lock, VideoPlay } from '@element-plus/icons-vue'
import request from '@/api/request'
import { useUserStore } from '@/store'

const userStore = useUserStore()

const enabledFeatures = ref([])
const featureList = ref([
  { name: 'apk_decompile', displayName: 'APK反编译' },
  { name: 'apk_reverse', displayName: 'APK逆向分析' },
  { name: 'protocol_analysis', displayName: '协议分析' },
  { name: 'frida_hook', displayName: 'Frida Hook' },
  { name: 'dynamic_analysis', displayName: '动态分析' },
  { name: 'sandbox_analysis', displayName: '沙箱分析' }
])

const defaultTools = reactive([
  { name: 'apktool', displayName: 'ApkTool', description: 'APK逆向分析工具（apktool.jar）', status: 'NOT_CONFIGURED', path: null, version: null, lastVerified: null, testing: false, testResult: null },
  { name: 'jadx', displayName: 'Jadx', description: 'Java反编译器（jadx.jar），将Smali转为Java源码', status: 'NOT_CONFIGURED', path: null, version: null, lastVerified: null, testing: false, testResult: null },
  { name: 'tshark', displayName: 'TShark', description: 'Wireshark命令行工具，用于PCAP协议分析', status: 'NOT_CONFIGURED', path: null, version: null, lastVerified: null, testing: false, testResult: null }
])

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + sessionStorage.getItem('token')
}))

const allToolsAvailable = computed(() => {
  return defaultTools.every(t => t.status === 'AVAILABLE')
})

const canUpload = computed(() => userStore.isDefaultAdmin)

const displayTools = computed(() => defaultTools)

onMounted(() => {
  loadToolStatus()
})

function loadToolStatus() {
  request({
    url: '/tools/status',
    method: 'get'
  })
    .then(res => {
      const resData = res.data.data || res.data
      const backendTools = resData.tools || []
      enabledFeatures.value = resData.enabledFeatures || []
      backendTools.forEach(bt => {
        const local = defaultTools.find(t => t.name === bt.name)
        if (local) {
          local.status = bt.status || 'NOT_CONFIGURED'
          local.path = bt.path || null
          local.version = bt.version || null
          local.lastVerified = bt.lastVerified || null
        }
      })
    })
    .catch(err => {
      console.error('加载工具状态失败', err)
    })
}

function handleTest(tool) {
  tool.testing = true
  tool.testResult = null
  request({
    url: `/tools/test/${tool.name}`,
    method: 'post'
  })
    .then(res => {
      const resData = res.data.data || res.data
      tool.testResult = {
        success: true,
        message: '版本: ' + (resData.version || resData.output || '验证通过')
      }
      loadToolStatus()
    })
    .catch(err => {
      tool.testResult = {
        success: false,
        message: err.message || '测试失败'
      }
    })
    .finally(() => {
      tool.testing = false
    })
}

function getStatusType(status) {
  const map = {
    NOT_CONFIGURED: 'info',
    UPLOADED: 'warning',
    AVAILABLE: 'success',
    UNAVAILABLE: 'danger'
  }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = {
    NOT_CONFIGURED: '未配置',
    UPLOADED: '已上传',
    AVAILABLE: '可用',
    UNAVAILABLE: '不可用'
  }
  return map[status] || status
}

function formatPath(path) {
  if (!path) return ''
  const parts = path.split('/')
  if (parts.length > 3) {
    return '...' + parts.slice(-3).join('/')
  }
  return path
}

function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleString()
}

function formatFeatureName(feature) {
  const map = {
    apk_decompile: 'APK反编译',
    apk_reverse: 'APK逆向',
    smali_to_java: 'Smali转Java',
    protocol_analysis: '协议分析',
    frida_hook: 'Frida Hook',
    dynamic_analysis: '动态分析',
    android_emulator: 'Android模拟器',
    sandbox_analysis: '沙箱分析'
  }
  return map[feature] || feature
}

function beforeUpload(file, toolName) {
  const ext = '.' + file.name.split('.').pop().toLowerCase()
  const noExt = file.name.indexOf('.') === -1

  // tshark允许exe/bat/sh或无扩展名的可执行文件
  if (toolName === 'tshark') {
    const allowedTsharkExts = ['.exe', '.bat', '.sh']
    if (!noExt && !allowedTsharkExts.includes(ext)) {
      ElMessage.error('tshark 应上传 .exe/.bat/.sh 或无扩展名的可执行文件')
      return false
    }
    return true
  }

  if (ext !== '.jar') {
    ElMessage.error('只允许上传 .jar 文件')
    return false
  }

  const lowerName = file.name.toLowerCase()
  if (toolName === 'apktool' && !lowerName.includes('apktool')) {
    ElMessage.error('apktool 插件应上传 apktool.jar 文件')
    return false
  }
  if (toolName === 'jadx' && !lowerName.includes('jadx')) {
    ElMessage.error('jadx 插件应上传 jadx jar 文件')
    return false
  }

  if (file.size > 100 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过100MB')
    return false
  }

  return true
}

function handleUploadSuccess(res) {
  if (res.code === 0) {
    ElMessage.success('工具上传成功')
    loadToolStatus()
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function handleUploadError(err) {
  ElMessage.error('上传失败: ' + err.message)
}

function handleDelete(toolName) {
  ElMessageBox.confirm('确定要删除此工具吗？', '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      request({
        url: `/tools/${toolName}`,
        method: 'delete'
      })
        .then(() => {
          ElMessage.success('工具已删除')
          loadToolStatus()
        })
        .catch(err => {
          ElMessage.error('删除失败: ' + (err.message || ''))
          console.error(err)
        })
    })
    .catch(() => {})
}

function checkFeaturePermission(feature) {
  request({
    url: '/tools/check-permission',
    method: 'get',
    params: { feature }
  })
    .then(res => {
      const resData = res.data.data || res.data
      const item = featureList.value.find(f => f.name === feature)
      if (item) {
        item.allowed = resData.allowed
        item.requiresAdmin = resData.requiresAdmin
      }
    })
    .catch(err => {
      console.error('检查权限失败', err)
    })
}

featureList.value.forEach(f => checkFeaturePermission(f.name))
</script>

<style scoped>
.tool-management-panel {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tool-cards {
  margin-bottom: 16px;
}

.tool-card {
  margin-bottom: 16px;
}

.tool-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tool-info {
  margin-bottom: 12px;
}

.tool-desc {
  color: #606266;
  font-size: 13px;
  margin: 0 0 8px 0;
}

.tool-path {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.path-text {
  font-family: monospace;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 2px;
}

.tool-version, .tool-verified {
  font-size: 12px;
  color: #909399;
}

.tool-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.upload-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.upload-hint {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.tool-locked {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  margin-top: 12px;
}

.enabled-features {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.feature-tag {
  margin: 4px;
}

.feature-checks {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.feature-check {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: white;
  border-radius: 4px;
}
</style>