<template>
  <div class="chat-dialog">
    <div class="chat-header">
      <div class="header-left">
        <div class="chat-title">
          <el-icon size="20" color="#409eff"><ChatRound /></el-icon>
          <h3>AI 智能助手</h3>
        </div>
        <span class="chat-subtitle">基于知识库的智能问答</span>
        <div class="category-filter" style="margin-top:6px">
          <el-select
            v-model="categoryFilter"
            placeholder="限定知识库分类(可选)"
            size="small"
            clearable
            style="width:200px"
          >
            <el-option v-for="cat in blogCategories" :key="cat.id" :label="cat.name" :value="cat.name" />
          </el-select>
        </div>
      </div>
      <div class="header-actions">
        <el-button 
          v-if="isLoading" 
          size="default" 
          type="warning" 
          :icon="Close"
          @click="stopGeneration"
        >
          停止回复
        </el-button>
        <el-button 
          size="default" 
          :icon="Delete"
          @click="clearChat"
        >
          清空对话
        </el-button>
      </div>
    </div>

    <div class="chat-container" ref="chatContainer">
      <div class="chat-messages">
        <div
          v-for="(message, index) in messages"
          :key="index"
          :class="['message', message.role]"
        >
          <div class="message-avatar">
            <el-avatar 
              :size="36" 
              :src="message.role === 'user' ? null : null"
              :icon="message.role === 'user' ? User : Avatar"
              :style="{ backgroundColor: message.role === 'user' ? '#409eff' : '#67c23a' }"
            />
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-role">
                {{ message.role === 'user' ? '您' : 'AI助手' }}
              </span>
              <span class="message-time">
                {{ formatTime(message.timestamp) }}
              </span>
            </div>
            <div class="message-text">
              <div v-if="message.role === 'user'" class="user-message">
                {{ message.content }}
              </div>
              <div v-else class="assistant-message" v-html="renderMarkdown(message.content)"></div>
            </div>
          </div>
        </div>
        
        <!-- 加载中提示 -->
        <div v-if="isLoading" class="message assistant loading">
          <div class="message-avatar">
            <el-avatar 
              :size="36" 
              :icon="Avatar"
              :style="{ backgroundColor: '#67c23a' }"
            />
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-role">AI助手</span>
              <span class="message-time">正在思考...</span>
            </div>
            <div class="message-text">
              <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <div class="input-container">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="请输入您的问题...（Enter发送，Shift+Enter换行）"
          @keydown="handleKeydown"
          :disabled="isLoading"
          class="message-input"
        />
        <div class="input-actions">
                      <el-button
              type="primary"
              :icon="Position"
              @click="sendMessage"
              :disabled="!inputMessage.trim() || isLoading"
              :loading="isLoading"
              class="send-button"
            >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '@/store'
import { ElMessage } from 'element-plus'
import { ChatRound, Close, Position, Delete, User, Avatar } from '@element-plus/icons-vue'
import { getMyCategories } from '@/api/blog'

const userStore = useUserStore()

// 数据
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const chatContainer = ref(null)
const memoryId = ref(`session_${Date.now()}`)
const abortController = ref(null)
const categoryFilter = ref('')
const blogCategories = ref([])

// 消息处理
const addMessage = (role, content) => {
  messages.value.push({
    role,
    content,
    timestamp: new Date()
  })
  nextTick(() => {
    scrollToBottom()
  })
}

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim() || isLoading.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  
  // 添加用户消息
  addMessage('user', userMessage)
  
  // 开始流式响应
  isLoading.value = true
  
  // 创建AbortController以支持取消请求
  abortController.value = new AbortController()
  
  try {
    console.log('发送消息:', userMessage)
    console.log('使用Token:', userStore.token)
    
    // 使用 fetch 处理 Server-Sent Events
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userStore.token}`,
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({
        message: userMessage,
        memoryId: memoryId.value,
        categoryFilter: categoryFilter.value || undefined
      }),
      signal: abortController.value.signal
    })

    console.log('聊天API响应状态:', response.status)
    console.log('响应头:', response.headers.get('content-type'))

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    
    // 添加AI消息占位符
    const aiMessageIndex = messages.value.length
    addMessage('assistant', '')
    
    let buffer = ''
    
    // 添加30秒超时
    const timeoutId = setTimeout(() => {
      console.log('流式响应超时，自动结束')
      isLoading.value = false
    }, 30000)

    while (true) {
      const { done, value } = await reader.read()
      
      if (done) {
        console.log('流式响应自然结束')
        clearTimeout(timeoutId)
        break
      }
      
      buffer += decoder.decode(value, { stream: true })
      
      // 处理SSE数据
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 保留最后一个不完整的行
      
      for (const line of lines) {
        if (line.trim() === '') continue // 跳过空行
        
        // 处理 'data:' 开头的行（可能有空格也可能没有）
        if (line.startsWith('data:')) {
          let data = ''
          
          // 检查是 'data: ' 还是 'data:'
          if (line.startsWith('data: ')) {
            data = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            data = line.slice(5).trim()
          }
          
          console.log('SSE数据片段:', data) // 调试信息
          
          if (data === '[DONE]') {
            // 流式响应结束
            console.log('收到结束标记，流式响应结束')
            clearTimeout(timeoutId)
            isLoading.value = false
            return
          }
          
          if (data) {
            // 累积AI回复内容，还原后端编码的换行符
            messages.value[aiMessageIndex].content += data.replace(/\\n/g, '\n')
            console.log('当前累积内容长度:', messages.value[aiMessageIndex].content.length)
            nextTick(() => {
              scrollToBottom()
            })
          }
        }
      }
    }
    
    // 清理超时器
    clearTimeout(timeoutId)
  } catch (error) {
    if (error.name === 'AbortError') {
      console.log('用户取消了请求')
      ElMessage.info('已停止回复')
    } else {
      console.error('发送消息失败:', error)
      ElMessage.error('发送消息失败，请稍后重试')
    }
  } finally {
    isLoading.value = false
    abortController.value = null
  }
}

// 处理键盘事件
const handleKeydown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

// 测试AI回复（模拟流式效果）
const testAIReply = async () => {
  if (isLoading.value) {
    ElMessage.warning('请等待当前回复完成')
    return
  }
  
  const testText = '这是一个测试回复，用来验证AI对话界面的流式显示功能是否正常工作。每个字符都会逐个显示，模拟真实的AI流式回复效果。'
  
  // 添加AI消息占位符
  addMessage('assistant', '')
  const aiMessageIndex = messages.value.length - 1
  
  isLoading.value = true
  
  try {
    // 逐字符显示
    for (let i = 0; i < testText.length; i++) {
      await new Promise(resolve => setTimeout(resolve, 50)) // 50ms延迟
      messages.value[aiMessageIndex].content += testText[i]
      nextTick(() => {
        scrollToBottom()
      })
      
      // 如果用户停止了，就退出
      if (!isLoading.value) break
    }
    
    console.log('测试回复完成')
  } catch (error) {
    console.error('测试回复失败:', error)
  } finally {
    isLoading.value = false
  }
}

// 停止生成回复
const stopGeneration = () => {
  if (abortController.value) {
    abortController.value.abort()
    console.log('用户手动停止了回复生成')
  } else {
    // 如果是测试回复，直接停止
    isLoading.value = false
    console.log('用户手动停止了测试回复')
  }
}

// 清空对话
const clearChat = () => {
  // 如果正在生成，先停止
  if (isLoading.value && abortController.value) {
    abortController.value.abort()
  }
  
  messages.value = []
  memoryId.value = `session_${Date.now()}`
  isLoading.value = false
  ElMessage.success('对话已清空')
}

// 滚动到底部
const scrollToBottom = () => {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

// 格式化时间
const formatTime = (timestamp) => {
  return new Date(timestamp).toLocaleTimeString()
}

// Markdown渲染（简单版本）
const renderMarkdown = (content) => {
  if (!content) return ''
  
  // 简单的Markdown处理
  return content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
}

// 生命周期
onMounted(async () => {
  // 加载分类
  try {
    const res = await getMyCategories()
    blogCategories.value = res.data?.data || res.data || []
  } catch (e) { /* ignore */ }
  // 欢迎消息
  addMessage('assistant', '您好！我是AI助手，可以帮您解答关于知识库文档的问题。请随时向我提问！')
})
</script>

<style scoped>
.chat-dialog {
  height: 100%;
  max-height: calc(100vh - 40px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-radius: 20px;
  overflow: hidden;
  margin: 20px;
  box-shadow: 0 15px 40px rgba(139, 92, 246, 0.15);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
  box-shadow: 0 4px 16px rgba(139, 92, 246, 0.2);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-title h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: white;
  letter-spacing: -0.02em;
}

.chat-subtitle {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  margin-left: 32px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions .el-button {
  border-radius: 12px;
  font-weight: 600;
  transition: all 0.3s ease;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.1);
  color: white;
  backdrop-filter: blur(10px);
}

.header-actions .el-button:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.chat-container {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  padding: 20px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0 4px;
  max-height: calc(100vh - 320px);
}

.message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 100%;
  animation: messageSlideIn 0.3s ease-out;
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 75%;
  min-width: 120px;
  width: auto;
  position: relative;
  flex-shrink: 0;
}

.message.user .message-content {
  align-self: flex-end;
}

.message.assistant .message-content {
  align-self: flex-start;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
  opacity: 0.8;
}

.message-role {
  font-weight: 600;
  color: #3b82f6;
}

.message.user .message-role {
  color: #8b5cf6;
}

.message-time {
  font-size: 10px;
  color: #94a3b8;
}

.message-text {
  padding: 14px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
  word-wrap: break-word;
  word-break: break-word;
  overflow-wrap: break-word;
  white-space: pre-wrap;
  position: relative;
  max-width: 100%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.message-text:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.user-message {
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.assistant-message {
  background: rgba(255, 255, 255, 0.95);
  color: #1e293b;
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(10px);
  border-bottom-left-radius: 4px;
}

.message-text :deep(code) {
  background-color: rgba(0, 0, 0, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Courier New', monospace;
  font-size: 13px;
  word-break: break-all;
  white-space: pre-wrap;
}

.user-message :deep(code) {
  background-color: rgba(255, 255, 255, 0.2);
}

.message-text :deep(pre) {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
  max-width: 100%;
  background: rgba(0, 0, 0, 0.05);
  padding: 8px;
  border-radius: 6px;
  margin: 4px 0;
}

.user-message :deep(pre) {
  background: rgba(255, 255, 255, 0.1);
}

.message-text :deep(p) {
  margin: 0;
  word-break: break-word;
  overflow-wrap: break-word;
}

.message-text :deep(strong) {
  font-weight: 600;
}

.message-text :deep(em) {
  font-style: italic;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(10px);
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.chat-input {
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-top: 1px solid rgba(139, 92, 246, 0.1);
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  flex-shrink: 0;
}

.input-container {
  position: relative;
}

.message-input {
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.1);
  transition: all 0.3s ease;
}

.message-input :deep(.el-textarea__inner) {
  border-radius: 16px;
  border: 2px solid rgba(139, 92, 246, 0.1);
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
}

.message-input :deep(.el-textarea__inner):focus {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.1);
  background: white;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.send-button {
  border-radius: 12px;
  padding: 10px 24px;
  font-weight: 600;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  border: none;
  color: white;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3);
  transition: all 0.3s ease;
}

.send-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(139, 92, 246, 0.4);
  background: linear-gradient(135deg, #7c3aed 0%, #2563eb 100%);
}

.send-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: 0 2px 4px rgba(139, 92, 246, 0.2);
}

/* 滚动条样式 */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: rgba(139, 92, 246, 0.1);
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(135deg, #7c3aed 0%, #2563eb 100%);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-dialog {
    margin: 10px;
    border-radius: 16px;
  }
  
  .chat-header {
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }
  
  .header-actions {
    width: 100%;
    justify-content: center;
    flex-wrap: wrap;
  }
  
  .chat-container {
    padding: 16px;
  }
  
  .chat-input {
    padding: 16px;
  }
  
  .message-content {
    max-width: calc(100vw - 100px);
  }
  
  .chat-messages {
    gap: 12px;
    max-height: calc(100vh - 400px);
  }
  
  .message-text {
    padding: 12px 14px;
    font-size: 13px;
  }
  
  .chat-title h3 {
    font-size: 18px;
  }
  
  .chat-subtitle {
    font-size: 11px;
  }
}

/* 加载状态动画 */
.message.loading {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style> 
