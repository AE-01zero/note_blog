<template>
  <div class="protocol-analyzer-panel">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="upload-card">
          <template #header><span>上传PCAP文件</span></template>
          <el-upload
            ref="uploadRef" class="pcap-upload" drag
            :auto-upload="false" :limit="1" accept=".pcap,.pcapng"
            :on-change="handleFileChange"
          >
            <el-icon class="upload-icon" :size="60"><UploadFilled /></el-icon>
            <div class="upload-text">拖拽PCAP文件到此处或点击上传</div>
            <template #tip>
              <div class="upload-tip">支持.pcap和.pcapng格式，大小不超过200MB</div>
            </template>
          </el-upload>
          <el-button
            type="primary" :loading="isAnalyzing" :disabled="!selectedFile"
            class="analyze-btn" @click="startAnalysis"
          >
            {{ isAnalyzing ? '分析中...' : '开始分析' }}
          </el-button>
        </el-card>

        <el-card class="options-card">
          <template #header>分析选项</template>
          <el-form label-position="top">
            <el-form-item label="协议检测">
              <el-switch v-model="options.autoDetect" />
              <span class="option-hint">自动检测协议类型</span>
            </el-form-item>
            <el-form-item label="分析内容">
              <el-checkbox v-model="options.includeEncryption">加密分析</el-checkbox>
              <el-checkbox v-model="options.includeFormat">数据格式分析</el-checkbox>
            </el-form-item>
            <el-form-item label="AI 深度分析">
              <el-switch v-model="aiAssist" />
              <span class="option-hint">{{ aiAssist ? '开启AI深度协议推断' : '仅使用规则引擎' }}</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card v-if="protocolResult?.protocol" class="protocol-card">
          <template #header><span>协议分析结果</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="协议类型">
              <el-tag>{{ protocolResult.protocol.type || 'Unknown' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="传输层">{{ protocolResult.protocol.transport || 'N/A' }}</el-descriptions-item>
            <el-descriptions-item label="端口">{{ protocolResult.protocol.port || 'N/A' }}</el-descriptions-item>
            <el-descriptions-item label="请求数">{{ protocolResult.protocol.requestCount || 0 }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card v-if="protocolResult?.encryption" class="encryption-card">
          <template #header>
            <div class="card-header">
              <span>加密分析</span>
              <el-tag :type="protocolResult.encryption.identified ? 'warning' : 'success'">
                {{ protocolResult.encryption.identified ? '已加密' : '未加密' }}
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="加密类型">{{ protocolResult.encryption.type || 'N/A' }}</el-descriptions-item>
            <el-descriptions-item label="密钥交换">{{ protocolResult.encryption.keyExchange || 'N/A' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider v-if="protocolResult.encryption.algorithms?.length" content-position="left">识别算法</el-divider>
          <el-tag v-for="alg in protocolResult.encryption.algorithms" :key="alg" class="algo-tag" type="success">{{ alg }}</el-tag>
        </el-card>

        <el-card v-if="protocolResult?.dataFormat?.plaintextLeaks?.length" class="leaks-card">
          <template #header>
            <div class="card-header">
              <span>明文传输敏感凭据泄露审计</span>
              <el-tag type="danger" effect="dark">Plaintext Leaks Detected</el-tag>
            </div>
          </template>
          <el-alert title="静态网络协议明文密码提取" type="error"
            description="检测到该流量包在非加密通道中明文传输了敏感凭据！"
            show-icon :closable="false" style="margin-bottom: 16px" />
          <el-table :data="protocolResult.dataFormat.plaintextLeaks" stripe>
            <el-table-column prop="packetIndex" label="数据包序号" width="120" />
            <el-table-column prop="field" label="泄漏字段" width="180">
              <template #default="{ row }"><span style="font-weight: bold; color: #f56c6c;">{{ row.field }}</span></template>
            </el-table-column>
            <el-table-column prop="value" label="脱敏值" width="200">
              <template #default="{ row }"><code>{{ row.value }}</code></template>
            </el-table-column>
            <el-table-column prop="snippet" label="原始载荷上下文" />
          </el-table>
        </el-card>

        <el-card v-if="protocolResult?.dataFormat?.protoSchema" class="proto-schema-card">
          <template #header>
            <div class="card-header">
              <span>Protobuf/JSON 静态数据结构自动反向生成 (.proto)</span>
              <el-button type="success" size="small" @click="copyProtoSchema">一键复制 .proto 模板</el-button>
            </div>
          </template>
          <el-alert title="静态协议反向工程" type="success"
            description="AI 安全引擎已自动分析该流的字段布局，重构为符合 Protobuf 规范的静态消息结构声明文件。"
            show-icon :closable="false" style="margin-bottom: 16px" />
          <pre class="proto-code">{{ protocolResult.dataFormat.protoSchema }}</pre>
        </el-card>

        <el-card v-if="protocolResult?.dataFormat?.fields?.length" class="format-card">
          <template #header><span>数据格式</span></template>
          <el-table :data="protocolResult.dataFormat.fields" stripe>
            <el-table-column prop="name" label="字段名" width="150" />
            <el-table-column prop="offset" label="偏移" width="80" />
            <el-table-column prop="length" label="长度" width="80" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }"><el-tag size="small">{{ row.type }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="description" label="描述" />
          </el-table>
        </el-card>

        <el-card v-if="protocolResult?.stats" class="stats-card">
          <template #header><span>通信统计</span></template>
          <el-row :gutter="20">
            <el-col :span="6"><div class="stat-item"><div class="stat-value">{{ protocolResult.stats.totalPackets || 0 }}</div><div class="stat-label">总包数</div></div></el-col>
            <el-col :span="6"><div class="stat-item"><div class="stat-value">{{ protocolResult.stats.requestCount || 0 }}</div><div class="stat-label">请求数</div></div></el-col>
            <el-col :span="6"><div class="stat-item"><div class="stat-value">{{ protocolResult.stats.responseCount || 0 }}</div><div class="stat-label">响应数</div></div></el-col>
            <el-col :span="6"><div class="stat-item"><div class="stat-value">{{ protocolResult.stats.uniqueEndpoints || 0 }}</div><div class="stat-label">独立端点</div></div></el-col>
          </el-row>
        </el-card>

        <el-card v-if="protocolAiData" class="ai-report-card">
          <template #header><div class="card-header"><span>AI 协议安全分析报告</span></div></template>
          <AiAnalysisReport :ai-response="protocolAiData.response" :metadata="protocolAiData.metadata"
            :show-identity="true" @probe-click="handleProtocolProbe" />
        </el-card>

        <el-card v-if="!protocolResult && !isAnalyzing" class="empty-state">
          <el-empty description="请上传PCAP文件开始分析">
            <template #image><el-icon :size="80" color="#409EFF"><Connection /></el-icon></template>
          </el-empty>
        </el-card>
      </el-col>
    </el-row>

    <AnalysisProgressDialog
      v-model="showProgress" module-type="protocol"
      :file-name="selectedFile?.name || ''" :file-size="selectedFile?.size || 0"
      @complete="handleAnalysisComplete" ref="progressDialogRef"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { UploadFilled, Connection } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAiSecurityStore } from '@/store/aiSecurity'
import AiAnalysisReport from './AiAnalysisReport.vue'
import AnalysisProgressDialog from './AnalysisProgressDialog.vue'

const store = useAiSecurityStore()
const MAX_FILE_SIZE = 200 * 1024 * 1024

const uploadRef = ref()
const selectedFile = ref(null)
const showProgress = ref(false)
const progressDialogRef = ref(null)
const isAnalyzing = ref(false)
const aiAssist = ref(false)

const options = reactive({ autoDetect: true, includeEncryption: true, includeFormat: true })

const protocolResult = computed(() => store.streamingResult || store.protocolResult)

function handleFileChange(file) { selectedFile.value = file.raw }

function startAnalysis() {
  if (!selectedFile.value) { ElMessage.warning('请先选择PCAP文件'); return }
  if (selectedFile.value.size > MAX_FILE_SIZE) {
    ElMessage.error(`文件大小超过限制 (最大200MB)，当前文件: ${formatFileSize(selectedFile.value.size)}`)
    return
  }

  store.clearProtocolResult()
  store.streamingResult = null
  isAnalyzing.value = true
  showProgress.value = true

  setTimeout(() => {
    if (progressDialogRef.value) progressDialogRef.value.startAnalysis(selectedFile.value, { aiAssist: aiAssist.value })
  }, 200)
}

function handleAnalysisComplete(result) {
  isAnalyzing.value = false
  if (result?.success) {
    store.saveStreamingResult('protocol', result)
    ElMessage.success('协议分析完成')
  }
}

const protocolAiData = computed(() => {
  const ai = protocolResult.value?.aiAnalysis
  const text = ai?.analysis || ai?.report || ''
  if (!text) return null
  return { response: text, metadata: { moduleType: ai?.moduleType || 'PROTOCOL', model: ai?.model || 'qwen-plus', timestamp: ai?.timestamp || Date.now(), confidence: 0.9 } }
})

function handleProtocolProbe(probe) { ElMessage.info(`探针指令 [${probe.type}]: ${probe.label}`) }

function copyProtoSchema() {
  if (protocolResult.value?.dataFormat?.protoSchema) {
    navigator.clipboard.writeText(protocolResult.value.dataFormat.protoSchema)
    ElMessage.success('已将 .proto 模板代码复制到剪贴板！')
  }
}

function formatFileSize(bytes) {
  if (!bytes) return 'N/A'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}
</script>

<style scoped>
.protocol-analyzer-panel { padding: 20px; }
.upload-card, .options-card, .protocol-card, .encryption-card, .leaks-card,
.proto-schema-card, .format-card, .stats-card, .ai-report-card, .empty-state { margin-bottom: 20px; }
.pcap-upload { margin-bottom: 16px; }
.upload-icon { color: #409EFF; margin-bottom: 10px; }
.upload-text, .upload-tip { color: #606266; }
.upload-tip { font-size: 12px; margin-top: 8px; }
.analyze-btn { width: 100%; }
.option-hint { margin-left: 10px; color: #909399; font-size: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.algo-tag { margin: 4px; }
.stat-item { text-align: center; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 8px; color: #fff; }
.stat-value { font-size: 24px; font-weight: bold; }
.stat-label { font-size: 12px; opacity: 0.9; }
.proto-code { display: block; padding: 14px; background: #1e1e2e; color: #cdd6f4; border-radius: 6px; font-family: 'Fira Code', 'Consolas', monospace; font-size: 13px; line-height: 1.5; overflow-x: auto; white-space: pre-wrap; word-break: break-all; border: 1px solid rgba(139, 92, 246, 0.2); text-align: left; }
</style>
