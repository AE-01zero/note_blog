<template>
  <div class="apk-analyzer-panel">
    <el-row :gutter="20">
      <!-- 左侧：上传和控制区 -->
      <el-col :span="8">
        <!-- 文件上传 -->
        <el-card class="upload-card">
          <template #header>
            <div class="card-header">
              <span>上传APK文件</span>
            </div>
          </template>

          <el-upload
            ref="uploadRef"
            class="apk-upload"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".apk"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-icon class="upload-icon" :size="60"><UploadFilled /></el-icon>
            <div class="upload-text">拖拽APK文件到此处或点击上传</div>
            <template #tip>
              <div class="upload-tip">支持Android APK文件，大小不超过200MB</div>
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

        <!-- 分析选项 -->
        <el-card class="options-card">
          <template #header>
            <span>极致 AI 静态审计配置</span>
          </template>

          <el-form label-position="top">
            <el-form-item label="AI 深度增强审计">
              <el-switch v-model="options.aiEnhance" />
              <span class="option-hint">使用 GPT 安全大模型对静态提取特征进行深度联合审计</span>
            </el-form-item>

            <el-form-item label="静态扫描深度">
              <el-radio-group v-model="options.depth">
                <el-radio label="standard">标准解析</el-radio>
                <el-radio label="deep">极致深度特征匹配</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="静态审计项目">
              <el-checkbox v-model="options.includeDecompiled">反编译 DEX 并扫描指令</el-checkbox>
              <el-checkbox v-model="options.includeStrings">审计包内敏感字符串</el-checkbox>
              <el-checkbox v-model="options.includeNetwork">提取包内网络通信域名</el-checkbox>
              <el-checkbox v-model="options.includeBehavior">扫描敏感与高危 API 调用</el-checkbox>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：分析结果 -->
      <el-col :span="16">
        <!-- APK基本信息 -->
        <el-card v-if="apkResult?.apkInfo" class="info-card">
          <template #header>
            <div class="card-header">
              <span>APK基本信息</span>
              <el-tag :type="getVerdictTagType(apkResult?.detection?.verdict)">
                {{ apkResult?.detection?.verdict || 'UNKNOWN' }}
              </el-tag>
            </div>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="包名">
              <code>{{ apkResult.apkInfo.packageName }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="版本">
              {{ apkResult.apkInfo.versionName }} ({{ apkResult.apkInfo.versionCode }})
            </el-descriptions-item>
            <el-descriptions-item label="文件大小">
              {{ formatFileSize(apkResult.apkInfo.fileSize) }}
            </el-descriptions-item>
            <el-descriptions-item label="最低SDK">
              {{ apkResult.apkInfo.minSdkVersion }}
            </el-descriptions-item>
            <el-descriptions-item label="MD5" :span="2">
              <code>{{ apkResult.apkInfo.md5 }}</code>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 权限分析 -->
        <el-card v-if="apkResult?.permissions?.length" class="permissions-card">
          <template #header>
            <div class="card-header">
              <span>权限分析</span>
              <el-badge :value="apkResult.permissions.length" type="warning" />
            </div>
          </template>

          <div class="permissions-grid">
            <div
              v-for="perm in apkResult.permissions"
              :key="perm.name"
              class="permission-item"
              :class="{ dangerous: perm.risk === 'HIGH' || perm.risk === 'CRITICAL' }"
            >
              <el-icon><Lock /></el-icon>
              <span class="perm-name">{{ perm.name }}</span>
              <span class="perm-desc">{{ perm.description || perm.purpose }}</span>
              <el-tag
                v-if="perm.risk"
                size="small"
                :type="perm.risk === 'CRITICAL' ? 'danger' : perm.risk === 'HIGH' ? 'warning' : 'info'"
              >
                {{ perm.risk }}
              </el-tag>
            </div>
          </div>
        </el-card>

        <!-- 恶意检测结果 -->
        <el-card v-if="apkResult?.detection" class="detection-card">
          <template #header>
            <span>恶意检测结果</span>
          </template>

          <el-alert
            v-if="apkResult.detection.verdict === 'CRITICAL_MALWARE'"
            title="检测为恶意软件"
            type="error"
            :description="apkResult.detection.summary"
            show-icon
          />
          <el-alert
            v-else-if="apkResult.detection.verdict?.includes('RISK')"
            title="存在安全风险"
            type="warning"
            :description="apkResult.detection.summary"
            show-icon
          />
          <el-alert
            v-else
            title="未检测到恶意行为"
            type="success"
            show-icon
          />

          <el-divider content-position="left">AI 智能深度分析报告</el-divider>

          <AiAnalysisReport
            v-if="apkAiData"
            :ai-response="apkAiData.response"
            :metadata="apkAiData.metadata"
            :show-identity="true"
            @probe-click="handleApkProbe"
          />

          <el-divider v-if="apkResult.detection.iocs" content-position="left">IoC 威胁指标</el-divider>

          <el-descriptions v-if="apkResult.detection.iocs" :column="1" border>
            <el-descriptions-item label="网络域名">
              <el-tag
                v-for="domain in apkResult.detection.iocs?.domains"
                :key="domain"
                class="domain-tag"
              >
                {{ domain }}
              </el-tag>
              <span v-if="!apkResult.detection.iocs?.domains?.length" class="empty-hint">未提取到敏感网络域名</span>
            </el-descriptions-item>
            <el-descriptions-item label="恶意权限">
              <el-tag
                v-for="perm in apkResult.detection.iocs?.permissions"
                :key="perm"
                type="danger"
              >
                {{ perm }}
              </el-tag>
              <span v-if="!apkResult.detection.iocs?.permissions?.length" class="empty-hint">未匹配到恶意高危权限</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">规则匹配详情</el-divider>

          <el-table v-if="apkResult.detection.ruleMatches?.length" :data="apkResult.detection.ruleMatches" stripe>
            <el-table-column prop="name" label="规则名称" />
            <el-table-column prop="severity" label="严重程度">
              <template #default="{ row }">
                <el-tag :type="getSeverityType(row.severity)">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" />
          </el-table>
        </el-card>

        <!-- OWASP -->
        <el-card v-if="apkResult?.owaspMatches?.length" class="owasp-card">
          <template #header>
            <div class="card-header">
              <span>OWASP 移动安全 Top 10 合规审计</span>
              <el-tag type="danger" effect="dark">OWASP Mobile Compliance</el-tag>
            </div>
          </template>
          <el-table :data="apkResult.owaspMatches" stripe style="width: 100%">
            <el-table-column prop="category" label="分类" width="220">
              <template #default="{ row }">
                <span style="font-weight: bold; color: #f56c6c;">{{ row.category }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="severity" label="等级" width="100">
              <template #default="{ row }">
                <el-tag :type="row.severity === 'HIGH' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="详细规则与风险描述" />
          </el-table>
        </el-card>

        <!-- 敏感凭据 -->
        <el-card v-if="apkResult?.secrets?.length" class="secrets-card">
          <template #header>
            <div class="card-header">
              <span>classes.dex 敏感凭据与 API Key 泄漏审计</span>
              <el-tag type="warning" effect="dark">High-Entropy Secrets</el-tag>
            </div>
          </template>
          <el-alert
            title="香农信息熵静态凭据扫描器"
            type="warning"
            description="静态扫描 classes.dex 常量字符串池，过滤高信息熵随机字符，提取潜在的第三方云平台令牌、OAuth 凭据及私钥。"
            show-icon :closable="false" style="margin-bottom: 16px"
          />
          <el-table :data="apkResult.secrets" stripe style="width: 100%">
            <el-table-column prop="type" label="凭据类别" width="200" />
            <el-table-column prop="value" label="凭据指纹特征 (已脱敏保护)">
              <template #default="{ row }"><code>{{ row.value }}</code></template>
            </el-table-column>
            <el-table-column prop="entropy" label="香农信息熵 (Entropy)" width="180">
              <template #default="{ row }">
                <div class="entropy-col">
                  <el-progress :percentage="Math.round(row.entropy * 10)" :stroke-width="12" style="width: 100px" />
                  <span class="entropy-val">{{ row.entropy }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 权限虚高声明 -->
        <el-card v-if="apkResult?.permissionBloat?.length" class="bloat-card">
          <template #header>
            <div class="card-header">
              <span>高危权限虚高与静默越权审查 (Permission Bloat)</span>
              <el-tag type="info">Excessive Permissions</el-tag>
            </div>
          </template>
          <el-alert
            title="权限与 DEX 代码调用依赖关系审计"
            type="info"
            description="检测到下列高危隐私权限在 AndroidManifest.xml 中进行了声明，但 classes.dex 代码中并无任何相关 API 的调用指令。"
            show-icon :closable="false" style="margin-bottom: 16px"
          />
          <el-table :data="apkResult.permissionBloat" stripe style="width: 100%">
            <el-table-column prop="permission" label="声明权限" width="280">
              <template #default="{ row }"><code>{{ row.permission }}</code></template>
            </el-table-column>
            <el-table-column prop="label" label="功能" width="120" />
            <el-table-column prop="description" label="静默审计判定描述" />
            <el-table-column prop="status" label="分析判定" width="140">
              <template #default><el-tag type="danger" effect="plain">未调用 (Bloat)</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 处置建议 -->
        <el-card v-if="apkResult?.recommendations" class="recommendations-card">
          <template #header><span>处置建议</span></template>
          <el-timeline>
            <el-timeline-item
              v-for="(rec, index) in apkResult.recommendations"
              :key="index"
              :type="rec.priority === 'HIGH' ? 'danger' : rec.priority === 'MEDIUM' ? 'warning' : 'info'"
            >
              <h4>{{ rec.title }}</h4>
              <p>{{ rec.description }}</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 空状态 -->
        <el-card v-if="!apkResult && !isAnalyzing" class="empty-state">
          <el-empty description="请上传APK文件开始分析">
            <template #image>
              <el-icon :size="80" color="#409EFF"><Files /></el-icon>
            </template>
          </el-empty>
        </el-card>
      </el-col>
    </el-row>

    <!-- 进度弹窗 -->
    <AnalysisProgressDialog
      v-model="showProgress"
      module-type="apk"
      :file-name="selectedFile?.name || ''"
      :file-size="selectedFile?.size || 0"
      @complete="handleAnalysisComplete"
      ref="progressDialogRef"
    />
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { UploadFilled, Lock, Files } from '@element-plus/icons-vue'
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

const options = reactive({
  depth: 'standard',
  aiEnhance: true,
  includeDecompiled: false,
  includeStrings: true,
  includeNetwork: true,
  includeBehavior: true
})

// Use local result from streaming, fall back to store
const apkResult = computed(() => store.streamingResult || store.apkResult)

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function handleFileRemove() {
  selectedFile.value = null
}

function startAnalysis() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择APK文件')
    return
  }
  if (selectedFile.value.size > MAX_FILE_SIZE) {
    ElMessage.error(`文件大小超过限制 (最大200MB)，当前文件: ${formatFileSize(selectedFile.value.size)}`)
    return
  }

  store.clearApkResult()
  store.streamingResult = null
  isAnalyzing.value = true
  showProgress.value = true

  // Trigger dialog to start analysis
  setTimeout(() => {
    if (progressDialogRef.value) {
      progressDialogRef.value.startAnalysis(selectedFile.value)
    }
  }, 200)
}

function handleAnalysisComplete(result) {
  isAnalyzing.value = false
  if (result?.success) {
    store.saveStreamingResult('apk', result)
    ElMessage.success('APK 分析完成')
  }
}

function getVerdictTagType(verdict) {
  const map = { CRITICAL_MALWARE: 'danger', HIGH_RISK: 'warning', MEDIUM_RISK: 'info', LOW_RISK: 'success', CLEAN: 'success' }
  return map[verdict] || 'info'
}

function getSeverityType(severity) {
  const map = { CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'warning', LOW: 'info' }
  return map[severity] || 'info'
}

const apkAiData = computed(() => {
  const result = apkResult.value
  const ai = result?.aiAnalysis
  const detection = result?.detection
  const analysisText = ai?.analysis || ai?.report || detection?.technicalAnalysis || ''
  if (!analysisText) return null
  return {
    response: analysisText,
    metadata: {
      moduleType: ai?.moduleType || 'APK',
      model: ai?.model || 'qwen-plus',
      timestamp: ai?.timestamp || Date.now(),
      confidence: ai?.confidence || 0.9
    }
  }
})

function handleApkProbe(probe) {
  ElMessage.info(`探针指令 [${probe.type}]: ${probe.label} — 请在 APK 逆向工作区中执行`)
}

function formatFileSize(bytes) {
  if (!bytes) return 'N/A'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}
</script>

<style scoped>
.apk-analyzer-panel { padding: 20px; }
.upload-card, .options-card, .info-card, .permissions-card,
.detection-card, .owasp-card, .secrets-card, .bloat-card, .empty-state,
.recommendations-card { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.apk-upload { margin-bottom: 16px; }
.upload-icon { color: #409EFF; margin-bottom: 10px; }
.upload-text { color: #606266; }
.upload-tip { color: #909399; font-size: 12px; margin-top: 8px; }
.analyze-btn { width: 100%; }
.option-hint { margin-left: 10px; color: #909399; font-size: 12px; }
.permissions-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 10px; }
.permission-item { display: flex; align-items: center; gap: 8px; padding: 10px; background: #f5f7fa; border-radius: 4px; }
.permission-item.dangerous { background: #fef0f0; border: 1px solid #fde2e2; }
.perm-name { font-weight: bold; font-size: 12px; }
.perm-desc { flex: 1; color: #606266; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.domain-tag { margin: 4px; }
.empty-hint { font-size: 12px; color: #909399; }
.entropy-col { display: flex; align-items: center; gap: 12px; }
.entropy-val { font-weight: bold; font-size: 13px; color: #e6a23c; }
</style>
