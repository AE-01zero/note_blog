<template>
  <div class="ai-config-panel">
    <div class="panel-header">
      <h3>AI 模型配置</h3>
      <p class="hint">仅默认管理员可修改 AI 配置与邀请码注册设置。</p>
    </div>

    <el-card class="config-card register-card">
      <template #header>
        <span class="card-title">注册设置</span>
      </template>

      <div v-if="registerLoading" class="loading-wrap">
        <el-skeleton :rows="3" animated />
      </div>

      <el-form v-else :model="registerSettings" label-width="140px">
        <el-form-item label="开启邀请码注册">
          <el-switch v-model="registerSettings.registerEnabled" />
        </el-form-item>
        <el-form-item label="邀请码">
          <el-input
            v-model="registerSettings.inviteCode"
            maxlength="64"
            show-word-limit
            placeholder="请输入邀请码，最大64位"
          />
          <div class="key-hint">关闭注册后会保留当前邀请码，重新开启时可继续使用或改成新邀请码。</div>
        </el-form-item>
        <div class="card-footer">
          <el-button type="primary" :loading="registerSaving" @click="saveRegisterSettings">
            保存注册设置
          </el-button>
        </div>
      </el-form>
    </el-card>

    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else class="config-cards">
      <el-card v-for="cfg in configs" :key="cfg.modelType" class="config-card">
        <template #header>
          <span class="card-title">{{ labelMap[cfg.modelType] || cfg.modelType }}</span>
        </template>

        <el-form :model="cfg" label-width="140px" size="default">
          <el-form-item label="模型名称">
            <el-input v-model="cfg.modelName" placeholder="如 qwen-plus、gpt-4o-mini" />
          </el-form-item>
          <el-form-item label="API 地址">
            <el-input v-model="cfg.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              v-model="cfg.apiKey"
              :type="showKey[cfg.modelType] ? 'text' : 'password'"
              placeholder="留空则保持原值"
            >
              <template #suffix>
                <el-icon style="cursor: pointer" @click="toggleKey(cfg.modelType)">
                  <View v-if="!showKey[cfg.modelType]" />
                  <Hide v-else />
                </el-icon>
              </template>
            </el-input>
            <div class="key-hint">留空则保留原密钥，填写新值则直接覆盖。</div>
          </el-form-item>
          <el-form-item label="请求日志">
            <el-switch v-model="cfg.logRequests" />
          </el-form-item>
          <el-form-item label="响应日志">
            <el-switch v-model="cfg.logResponses" />
          </el-form-item>
          <el-form-item v-if="cfg.modelType === 'embedding'" label="最大批次数">
            <el-input-number v-model="cfg.maxSegmentsPerBatch" :min="1" :max="100" />
          </el-form-item>
        </el-form>

        <div class="card-footer">
          <el-button type="primary" :loading="saving[cfg.modelType]" @click="save(cfg)">
            保存并热重载
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { View, Hide } from '@element-plus/icons-vue'
import {
  getAiConfigs,
  updateAiConfig,
  getAdminRegisterSettings,
  updateRegisterSettings
} from '@/api'

const loading = ref(true)
const registerLoading = ref(true)
const registerSaving = ref(false)
const configs = ref([])
const saving = reactive({})
const showKey = reactive({})
const registerSettings = reactive({
  registerEnabled: false,
  inviteCode: ''
})

const labelMap = {
  chat: '对话模型 (Chat Model)',
  streaming: '流式对话模型 (Streaming Model)',
  embedding: '向量模型 (Embedding Model)'
}

const loadConfigs = async () => {
  try {
    loading.value = true
    const res = await getAiConfigs()
    configs.value = (res.data?.data || []).map(c => ({ ...c, apiKey: '' }))
    configs.value.forEach(c => {
      saving[c.modelType] = false
      showKey[c.modelType] = false
    })
  } catch (e) {
    ElMessage.error('加载模型配置失败')
  } finally {
    loading.value = false
  }
}

const loadRegisterSettings = async () => {
  try {
    registerLoading.value = true
    const res = await getAdminRegisterSettings()
    const data = res.data?.data || {}
    registerSettings.registerEnabled = !!data.registerEnabled
    registerSettings.inviteCode = data.inviteCode || ''
  } catch (e) {
    ElMessage.error('加载注册设置失败')
  } finally {
    registerLoading.value = false
  }
}

const toggleKey = (type) => {
  showKey[type] = !showKey[type]
}

const save = async (cfg) => {
  if (!cfg.baseUrl || !cfg.modelName) {
    ElMessage.warning('API 地址和模型名称不能为空')
    return
  }

  saving[cfg.modelType] = true
  try {
    await updateAiConfig(cfg.modelType, {
      baseUrl: cfg.baseUrl,
      apiKey: cfg.apiKey || '',
      modelName: cfg.modelName,
      logRequests: cfg.logRequests,
      logResponses: cfg.logResponses,
      maxSegmentsPerBatch: cfg.maxSegmentsPerBatch
    })
    ElMessage.success(`${labelMap[cfg.modelType]} 配置已更新`)
    cfg.apiKey = ''
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving[cfg.modelType] = false
  }
}

const saveRegisterSettings = async () => {
  if (registerSettings.registerEnabled && !registerSettings.inviteCode.trim()) {
    ElMessage.warning('开启邀请码注册时必须填写邀请码')
    return
  }

  registerSaving.value = true
  try {
    await updateRegisterSettings({
      registerEnabled: registerSettings.registerEnabled,
      inviteCode: registerSettings.inviteCode.trim()
    })
    ElMessage.success('注册设置已更新')
  } catch (e) {
    ElMessage.error('保存注册设置失败: ' + (e.message || '未知错误'))
  } finally {
    registerSaving.value = false
  }
}

onMounted(() => {
  loadRegisterSettings()
  loadConfigs()
})
</script>

<style scoped>
.ai-config-panel {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
}

.panel-header h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}

.hint {
  color: #888;
  font-size: 13px;
  margin-bottom: 20px;
}

.config-cards {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-card {
  border-radius: 12px;
}

.register-card {
  margin-bottom: 20px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.key-hint {
  font-size: 12px;
  color: #aaa;
  margin-top: 4px;
}

.loading-wrap {
  padding: 20px;
}
</style>
