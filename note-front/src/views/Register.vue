<template>
  <div class="register-container">
    <div class="register-box">
      <div class="register-header">
        <h1>创建账号</h1>
        <p>邀请码开启后才可注册新用户</p>
      </div>

      <el-alert
        v-if="settingsLoaded && !registerEnabled"
        title="当前未开启邀请码注册，请联系默认管理员"
        type="warning"
        :closable="false"
        style="margin-bottom: 20px"
      />

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        size="large"
        class="register-form"
      >
        <el-form-item prop="phone">
          <el-input
            v-model="registerForm.phone"
            placeholder="请输入手机号"
            :prefix-icon="User"
            clearable
            :disabled="!registerEnabled"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            clearable
            :disabled="!registerEnabled"
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请确认密码"
            :prefix-icon="Lock"
            show-password
            clearable
            :disabled="!registerEnabled"
          />
        </el-form-item>

        <el-form-item prop="inviteCode">
          <el-input
            v-model="registerForm.inviteCode"
            placeholder="请输入邀请码"
            :prefix-icon="Key"
            clearable
            maxlength="64"
            show-word-limit
            :disabled="!registerEnabled"
          />
        </el-form-item>

        <el-form-item prop="captcha">
          <div class="captcha-container">
            <el-input
              v-model="registerForm.captcha"
              placeholder="请输入验证码"
              :prefix-icon="Key"
              clearable
              :disabled="!registerEnabled"
            />
            <div
              class="captcha-image"
              :class="{ disabled: !registerEnabled }"
              @click="registerEnabled && getCaptchaImage()"
            >
              <img
                v-if="captchaData.imageBase64"
                :src="`data:image/png;base64,${captchaData.imageBase64}`"
                alt="验证码"
              />
              <span v-else>点击获取验证码</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleRegister"
            :loading="loading"
            :disabled="!registerEnabled"
            style="width: 100%"
          >
            注册
          </el-button>
        </el-form-item>

        <div class="register-footer">
          <span>已有账号？</span>
          <el-link type="primary" @click="$router.push('/login')">
            立即登录
          </el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCaptcha, getRegisterSettings, register } from '@/api'

const router = useRouter()

const registerFormRef = ref()
const loading = ref(false)
const registerEnabled = ref(false)
const settingsLoaded = ref(false)

const registerForm = reactive({
  phone: '',
  password: '',
  confirmPassword: '',
  inviteCode: '',
  captcha: '',
  captchaId: ''
})

const captchaData = reactive({
  imageBase64: '',
  captchaId: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  inviteCode: [
    { required: true, message: '请输入邀请码', trigger: 'blur' },
    { max: 64, message: '邀请码最大64位', trigger: 'blur' }
  ],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

const loadRegisterSettings = async () => {
  try {
    const response = await getRegisterSettings()
    registerEnabled.value = !!response.data?.data?.registerEnabled
  } catch (error) {
    registerEnabled.value = false
    console.error('获取注册设置失败:', error)
  } finally {
    settingsLoaded.value = true
  }
}

const getCaptchaImage = async () => {
  try {
    const response = await getCaptcha()
    const data = response.data.data
    captchaData.imageBase64 = data.imageBase64
    captchaData.captchaId = data.captchaId
    registerForm.captchaId = data.captchaId
  } catch (error) {
    ElMessage.error('获取验证码失败')
  }
}

const handleRegister = async () => {
  if (!registerEnabled.value) {
    ElMessage.warning('当前未开启邀请码注册')
    return
  }
  if (!registerFormRef.value) return

  try {
    await registerFormRef.value.validate()
    loading.value = true

    const { phone, password, inviteCode, captcha, captchaId } = registerForm
    await register({ phone, password, inviteCode, captcha, captchaId })

    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    getCaptchaImage()
    console.error('注册失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadRegisterSettings()
  if (registerEnabled.value) {
    getCaptchaImage()
  }
})
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
  background-image: url('data:image/svg+xml,%3Csvg width="100" height="100" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg"%3E%3Cpath d="M11 18c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm48 25c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm-48 0c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm48 25c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zM11 68c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm48 25c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm-48 0c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm48 25c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7z" fill="%23e0e0e0" fill-opacity="0.4" fill-rule="evenodd"/%3E%3C/svg%3E');
}

.register-box {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  padding: 40px;
  width: 100%;
  max-width: 420px;
  border: 1px solid #e8e8e8;
}

.register-header {
  text-align: center;
  margin-bottom: 30px;
}

.register-header h1 {
  color: #2c3e50;
  font-size: 28px;
  margin: 0 0 10px 0;
  font-weight: 600;
}

.register-header p {
  color: #7f8c8d;
  font-size: 14px;
  margin: 0;
}

.register-form {
  margin-top: 20px;
}

.captcha-container {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}

.captcha-image {
  width: 120px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  transition: all 0.3s;
}

.captcha-image:hover {
  border-color: #409eff;
}

.captcha-image.disabled {
  cursor: not-allowed;
  background: #f5f7fa;
  border-color: #e4e7ed;
}

.captcha-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.register-footer {
  text-align: center;
  margin-top: 20px;
  color: #909399;
  font-size: 14px;
}

:deep(.el-form-item__content) {
  line-height: 1;
}

:deep(.el-input__wrapper) {
  border-radius: 6px;
}

:deep(.el-button) {
  border-radius: 6px;
  font-weight: 500;
}
</style>
