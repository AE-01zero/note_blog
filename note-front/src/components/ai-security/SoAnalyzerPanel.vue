<template>
  <div class="so-analyzer-panel">
    <el-row :gutter="20">
      <!-- 左侧：上传和控制 -->
      <el-col :span="8">
        <el-card class="upload-card">
          <template #header><span>上传SO文件</span></template>

          <el-upload
            ref="uploadRef"
            class="so-upload" drag :auto-upload="false" :limit="1" accept=".so"
            :on-change="handleFileChange"
          >
            <el-icon class="upload-icon" :size="60"><UploadFilled /></el-icon>
            <div class="upload-text">拖拽SO文件到此处或点击上传</div>
            <template #tip>
              <div class="upload-tip">支持Android .so文件，大小不超过200MB</div>
            </template>
          </el-upload>

          <el-button
            type="primary"
            :loading="isAnalyzing"
            :disabled="!selectedFile"
            class="analyze-btn"
            @click="startAnalysis"
          >
            {{ isAnalyzing ? '分析中...' : '开始分析' }}
          </el-button>
        </el-card>

        <el-card class="options-card">
          <template #header>分析选项</template>
          <el-form label-position="top">
            <el-form-item label="AI 深度分析">
              <el-switch v-model="aiAssist" />
              <span class="option-hint">{{ aiAssist ? '开启AI深度分析' : '仅使用规则引擎' }}</span>
            </el-form-item>
            <el-form-item label="分析模式">
              <el-radio-group v-model="options.mode">
                <el-radio label="functions">函数识别</el-radio>
                <el-radio label="crypto">加密识别</el-radio>
                <el-radio label="full">完整分析</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="分析内容">
              <el-checkbox v-model="options.includePseudocode">函数伪代码</el-checkbox>
              <el-checkbox v-model="options.includeStrings">字符串分析</el-checkbox>
              <el-checkbox v-model="options.includeXrefs">交叉引用</el-checkbox>
              <el-checkbox v-model="options.includeObfuscation">混淆检测</el-checkbox>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：分析结果 -->
      <el-col :span="16">
        <!-- SO基本信息 -->
        <el-card v-if="soResult?.soInfo" class="info-card">
          <template #header><span>SO文件信息</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="文件名">{{ soResult.soInfo.fileName }}</el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ formatFileSize(soResult.soInfo.fileSize) }}</el-descriptions-item>
            <el-descriptions-item label="架构">
              <el-tag>{{ soResult.soInfo.architecture || 'ARM' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="入口点">
              <code>{{ soResult.soInfo.entryPoint || 'N/A' }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="导出函数">{{ soResult.soInfo.exportedFunctions || 0 }} 个</el-descriptions-item>
            <el-descriptions-item label="段信息">
              <el-tag v-for="seg in soResult.soInfo.segments" :key="seg" size="small">{{ seg }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 加密算法识别 -->
        <el-card v-if="soResult?.algorithms?.length" class="crypto-card">
          <template #header>
            <div class="card-header">
              <span>识别的加密算法</span>
              <el-tag type="warning">{{ soResult.algorithms.length }} 个</el-tag>
            </div>
          </template>

          <el-alert
            v-if="soResult.algorithms.length > 0"
            title="发现加密实现"
            type="warning"
            :description="`该SO文件包含 ${soResult.algorithms.length} 种加密算法实现`"
            show-icon
          />

          <el-table :data="soResult.algorithms" stripe style="margin-top: 16px">
            <el-table-column prop="name" label="算法" width="100">
              <template #default="{ row }"><el-tag type="success">{{ row.name }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="function" label="函数" />
            <el-table-column prop="address" label="地址" width="120">
              <template #default="{ row }"><code>{{ row.address }}</code></template>
            </el-table-column>
            <el-table-column prop="confidence" label="置信度" width="120">
              <template #default="{ row }">
                <el-progress :percentage="Math.round(row.confidence * 100)" :status="row.confidence > 0.8 ? 'success' : 'warning'" />
              </template>
            </el-table-column>
            <el-table-column prop="variant" label="变种" width="150">
              <template #default="{ row }"><el-tag size="small">{{ row.variant || 'Standard' }}</el-tag></template>
            </el-table-column>
          </el-table>

          <el-divider content-position="left">AI 加密算法分析</el-divider>

          <AiAnalysisReport
            v-if="soAiData"
            :ai-response="soAiData.response"
            :metadata="soAiData.metadata"
            :show-identity="true"
            @probe-click="handleSoProbe"
          />
        </el-card>

        <!-- 函数列表 -->
        <el-card v-if="soResult?.functions?.length" class="functions-card">
          <template #header><span>函数列表 ({{ soResult.functions.length }})</span></template>
          <el-table :data="soResult.functions" height="400" stripe>
            <el-table-column prop="name" label="函数名" width="200" />
            <el-table-column prop="address" label="地址" width="120">
              <template #default="{ row }"><code>{{ row.address }}</code></template>
            </el-table-column>
            <el-table-column prop="size" label="大小" width="80" />
            <el-table-column prop="category" label="类别" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="getCategoryType(row.category)">{{ row.category || 'Unknown' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="purpose" label="用途" />
          </el-table>
        </el-card>

        <!-- 混淆检测 -->
        <el-card v-if="soResult?.obfuscation" class="obfuscation-card">
          <template #header><span>混淆检测结果</span></template>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="obf-stat" :class="{ detected: soResult.obfuscation.controlFlow }">
                <el-icon :size="30" :color="soResult.obfuscation.controlFlow ? '#E6A23C' : '#67C23A'"><Switch /></el-icon>
                <span class="obf-label">控制流混淆</span>
                <el-tag :type="soResult.obfuscation.controlFlow ? 'warning' : 'success'">
                  {{ soResult.obfuscation.controlFlow ? '检测到' : '未检测' }}
                </el-tag>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="obf-stat" :class="{ detected: soResult.obfuscation.stringEncryption }">
                <el-icon :size="30" :color="soResult.obfuscation.stringEncryption ? '#E6A23C' : '#67C23A'"><Key /></el-icon>
                <span class="obf-label">字符串加密</span>
                <el-tag :type="soResult.obfuscation.stringEncryption ? 'warning' : 'success'">
                  {{ soResult.obfuscation.stringEncryption ? '检测到' : '未检测' }}
                </el-tag>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="obf-stat" :class="{ detected: soResult.obfuscation.symbolObfuscation }">
                <el-icon :size="30" :color="soResult.obfuscation.symbolObfuscation ? '#E6A23C' : '#67C23A'"><Hide /></el-icon>
                <span class="obf-label">符号混淆</span>
                <el-tag :type="soResult.obfuscation.symbolObfuscation ? 'warning' : 'success'">
                  {{ soResult.obfuscation.symbolObfuscation ? '检测到' : '未检测' }}
                </el-tag>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="obf-stat" :class="{ detected: soResult.obfuscation.ollvm }">
                <el-icon :size="30" :color="soResult.obfuscation.ollvm ? '#F56C6C' : '#67C23A'"><Lock /></el-icon>
                <span class="obf-label">OLLVM</span>
                <el-tag :type="soResult.obfuscation.ollvm ? 'danger' : 'success'">
                  {{ soResult.obfuscation.ollvm ? '检测到' : '未检测' }}
                </el-tag>
              </div>
            </el-col>
          </el-row>

          <div v-if="soResult.obfuscation.details" class="obfuscation-details">
            <el-row :gutter="12">
              <el-col :span="8" v-if="soResult.obfuscation.details.symbol">
                <el-card shadow="never" style="background: #fafafa;">
                  <h5 style="margin: 0 0 8px 0; color: #303133;"><strong>符号混淆率</strong></h5>
                  <p style="margin: 4px 0; font-size: 13px;">混淆比例: <el-tag size="small" type="warning">{{ soResult.obfuscation.details.symbol.ratio }}</el-tag></p>
                  <p style="margin: 4px 0; font-size: 13px;">混淆函数: {{ soResult.obfuscation.details.symbol.count }} 个</p>
                </el-card>
              </el-col>
              <el-col :span="8" v-if="soResult.obfuscation.details.string">
                <el-card shadow="never" style="background: #fafafa;">
                  <h5 style="margin: 0 0 8px 0; color: #303133;"><strong>数据/字符串加密</strong></h5>
                  <p style="margin: 4px 0; font-size: 13px;">信息熵: <el-tag size="small" type="danger">{{ soResult.obfuscation.details.string.entropy }}</el-tag></p>
                  <p style="margin: 4px 0; font-size: 12px; color: #909399;">{{ soResult.obfuscation.details.string.msg }}</p>
                </el-card>
              </el-col>
              <el-col :span="8" v-if="soResult.obfuscation.details.ollvm">
                <el-card shadow="never" style="background: #fafafa;">
                  <h5 style="margin: 0 0 8px 0; color: #303133;"><strong>OLLVM 分支跳转</strong></h5>
                  <p style="margin: 4px 0; font-size: 13px;">跳转指令: <el-tag size="small" type="danger">{{ soResult.obfuscation.details.ollvm.branchCount }} 个</el-tag></p>
                  <p style="margin: 4px 0; font-size: 12px; color: #909399;">{{ soResult.obfuscation.details.ollvm.msg }}</p>
                </el-card>
              </el-col>
            </el-row>

            <div v-if="soResult.obfuscation.deobfuscationGuide" class="deobfuscation-guide-box" style="margin-top: 16px;">
              <el-divider content-position="left">AI 反混淆与动态脱壳指引</el-divider>
              <div class="guide-report-content" v-html="formatReport(soResult.obfuscation.deobfuscationGuide)"></div>
            </div>
          </div>
        </el-card>

        <!-- 字符串分析 -->
        <el-card v-if="soResult?.strings?.length" class="strings-card">
          <template #header><span>敏感字符串</span></template>
          <el-table :data="soResult.strings" stripe>
            <el-table-column prop="value" label="字符串" />
            <el-table-column prop="address" label="地址" width="120">
              <template #default="{ row }"><code>{{ row.address }}</code></template>
            </el-table-column>
            <el-table-column prop="category" label="类别" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="getStringCategoryType(row.category)">{{ row.category }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 空状态 -->
        <el-card v-if="!soResult && !isAnalyzing" class="empty-state">
          <el-empty description="请上传SO文件开始分析">
            <template #image>
              <el-icon :size="80" color="#409EFF"><Document /></el-icon>
            </template>
          </el-empty>
        </el-card>
      </el-col>
    </el-row>

  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { UploadFilled, Document, Switch, Key, Hide, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAiSecurityStore } from '@/store/aiSecurity'
import AiAnalysisReport from './AiAnalysisReport.vue'
import { renderMarkdown } from '@/utils/markdown'

const store = useAiSecurityStore()
const MAX_FILE_SIZE = 200 * 1024 * 1024

const uploadRef = ref()
const selectedFile = ref(null)
const isAnalyzing = ref(false)
const aiAssist = ref(false)

const options = reactive({
  mode: 'full',
  includePseudocode: true,
  includeStrings: true,
  includeXrefs: false,
  includeObfuscation: true
})

const soResult = computed(() => store.soResult)

function handleFileChange(file) {
  selectedFile.value = file.raw
}

async function startAnalysis() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择SO文件')
    return
  }
  if (selectedFile.value.size > MAX_FILE_SIZE) {
    ElMessage.error(`文件大小超过限制 (最大200MB)，当前文件: ${formatFileSize(selectedFile.value.size)}`)
    return
  }

  store.clearSoResult()
  isAnalyzing.value = true

  try {
    await store.analyzeSo(selectedFile.value, aiAssist.value)
    ElMessage.success('SO 分析完成')
  } catch (err) {
    ElMessage.error('SO分析失败: ' + (err.message || '网络错误'))
  } finally {
    isAnalyzing.value = false
  }
}

function formatFileSize(bytes) {
  if (!bytes) return 'N/A'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

function getCategoryType(category) {
  const map = { Crypto: 'success', Network: 'warning', JNI: 'primary', String: 'info', File: 'danger' }
  return map[category] || 'info'
}

function getStringCategoryType(category) {
  const map = { URL: 'warning', IP: 'danger', Key: 'success', API: 'primary', Path: 'info' }
  return map[category] || 'info'
}

const soAiData = computed(() => {
  const ai = soResult.value?.aiAnalysis
  const analysisText = ai?.analysis || ai?.report || ''
  if (!analysisText) return null
  return {
    response: analysisText,
    metadata: {
      moduleType: ai?.moduleType || 'SO',
      model: ai?.model || 'qwen-plus',
      timestamp: ai?.timestamp || Date.now(),
      confidence: 0.9
    }
  }
})

function handleSoProbe(probe) {
  ElMessage.info(`探针指令 [${probe.type}]: ${probe.label}`)
}

function formatReport(report) {
  if (!report) return ''
  return renderMarkdown(report)
}
</script>

<style scoped>
.so-analyzer-panel { padding: 20px; }
.upload-card, .options-card, .info-card, .crypto-card, .functions-card,
.obfuscation-card, .strings-card, .empty-state { margin-bottom: 20px; }
.so-upload { margin-bottom: 16px; }
.upload-icon { color: #409EFF; margin-bottom: 10px; }
.upload-text { color: #606266; }
.upload-tip { color: #909399; font-size: 12px; margin-top: 8px; }
.analyze-btn { width: 100%; }
.option-hint { margin-left: 10px; color: #909399; font-size: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.obf-stat { text-align: center; padding: 20px; background: #f5f7fa; border-radius: 8px; }
.obf-stat.detected { background: #fdf6ec; }
.obf-label { display: block; margin: 8px 0; color: #606266; }
.obfuscation-details { margin-top: 12px; }
.obfuscation-details h5 { margin: 8px 0; color: #303133; }
.obfuscation-details p { margin: 4px 0; color: #606266; }
</style>
