<template>
  <div class="chat-dialog">
    <div class="chat-header">
      <div class="header-left">
        <div class="chat-title">
          <el-icon size="20" color="#409eff"><ChatRound /></el-icon>
          <h3>AI 智能助手</h3>
        </div>
        <span class="chat-subtitle">基于知识库的智能问答</span>
        <div class="category-filter" style="margin-top:6px; display: flex; align-items: center; gap: 10px;">
          <el-select
            v-model="categoryFilter"
            placeholder="限定知识库分类(可选)"
            size="small"
            clearable
            style="width:200px"
          >
            <el-option v-for="cat in blogCategories" :key="cat.id" :label="cat.name" :value="cat.name" />
          </el-select>
          <div style="display: flex; align-items: center; gap: 4px;">
            <span style="font-size: 12px; color: #6c7086; white-space: nowrap;">{{ chatMode === 'strict' ? '严格' : '宽松' }}</span>
            <el-switch
              v-model="isStrictMode"
              size="small"
              active-text="严格"
              inactive-text="宽松"
              inline-prompt
              style="--el-switch-on-color: #e6a23c; --el-switch-off-color: #67c23a;"
            />
          </div>
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
            <div
              v-if="message.role === 'assistant' && ((message.thinkingTrace && message.thinkingTrace.length) || message.thinking)"
              class="thinking-trace-panel"
            >
              <details :open="message.isThinking">
                <summary>
                  <span>AI思考过程</span>
                  <span v-if="message.isThinking" class="thinking-live">生成中</span>
                </summary>
                <div v-if="message.thinkingTrace && message.thinkingTrace.length" class="trace-steps">
                  <div
                    v-for="(item, ti) in message.thinkingTrace"
                    :key="ti"
                    class="trace-step"
                  >
                    <span class="trace-title">{{ item.title }}</span>
                    <span class="trace-content">{{ item.content }}</span>
                  </div>
                </div>
                <div v-if="message.thinking" class="thinking-stream" v-html="renderMarkdown(message.thinking)"></div>
              </details>
            </div>
            <div v-if="message.role === 'user' || message.content" class="message-text">
              <div v-if="message.role === 'user'" class="user-message">
                {{ message.content }}
              </div>
              <div v-else class="assistant-message" v-html="renderMarkdown(message.content)"></div>
            </div>
            <!-- 知识库来源引用 -->
            <div v-if="message.role === 'assistant' && message.sources && message.sources.length > 0 && message.content" class="message-sources-footer">
              <div class="sources-divider"></div>
              <div class="sources-row">
                <el-icon size="14" color="#8b5cf6"><Link /></el-icon>
                <span class="sources-label">参考来源：</span>
                <el-tag
                  v-for="(src, si) in message.sources"
                  :key="si"
                  size="small"
                  effect="plain"
                  class="source-citation-tag"
                >
                  {{ src.fileName }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 流式状态提示 -->
        <div v-if="isLoading && !isThinking && !streamPhase" class="message assistant loading">
          <div class="message-avatar">
            <el-avatar :size="36" :icon="Avatar" :style="{ backgroundColor: '#67c23a' }" />
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-role">AI助手</span>
              <span class="message-time">{{ streamPhaseLabel || '正在连接...' }}</span>
            </div>
            <div class="message-text">
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 知识检索状态 -->
        <div v-if="knowledgeResult" class="knowledge-status">
          <el-alert
            :type="knowledgeResult.hasContext ? 'success' : 'warning'"
            :closable="false"
            show-icon
            class="kb-alert"
          >
            <template #title>
              <div class="kb-result">
                <span>{{ knowledgeResult.hasContext ? `已检索到 ${knowledgeResult.segmentCount} 条相关知识` : '知识库中未找到相关内容' }}</span>
                <span v-if="knowledgeResult.sources && knowledgeResult.sources.length > 0" class="kb-source-chips">
                  <el-tag
                    v-for="(src, si) in knowledgeResult.sources"
                    :key="si"
                    size="small"
                    type="info"
                    class="source-tag"
                  >
                    {{ src.fileName }}
                    <span v-if="src.fileType" class="source-type">{{ src.fileType }}</span>
                  </el-tag>
                </span>
              </div>
            </template>
          </el-alert>
        </div>

        <!-- AI思考中 -->
        <div v-if="isThinking" class="message assistant loading">
          <div class="message-avatar">
            <el-avatar :size="36" :icon="Avatar" :style="{ backgroundColor: '#67c23a' }" />
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-role">AI助手</span>
              <span class="message-time">正在思考...</span>
            </div>
            <div class="message-text">
              <div class="thinking-box">
                <span class="thinking-dot">●</span>
                <span>AI正在分析知识库内容并生成回答</span>
                <span class="thinking-dot">●</span>
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
import { ref, reactive, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useUserStore } from '@/store'
import { ElMessage } from 'element-plus'
import { ChatRound, Close, Position, Delete, User, Avatar, Link } from '@element-plus/icons-vue'
import { getMyCategories } from '@/api/blog'
import { renderMarkdown } from '@/utils/markdown'
import { normalizeKnowledgeResult, normalizeSourceCitations } from '@/utils/chatDisplay'

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
const isStrictMode = ref(false)
const chatMode = ref('relaxed')

// 流式状态
const streamPhase = ref('')        // knowledge_retrieval / ai_thinking / done
const streamPhaseLabel = ref('')
const knowledgeResult = ref(null)  // knowledge_result event data
const isThinking = ref(false)      // AI思考中
const currentSources = ref([])     // 当前消息的知识库来源

watch(isStrictMode, (val) => {
  chatMode.value = val ? 'strict' : 'relaxed'
})

// 消息处理
const addMessage = (role, content) => {
  messages.value.push({
    role,
    content,
    timestamp: new Date(),
    thinking: '',
    thinkingTrace: [],
    isThinking: false,
    sources: []
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

  addMessage('user', userMessage)

  isLoading.value = true
  isThinking.value = false
  streamPhase.value = ''
  streamPhaseLabel.value = ''
  knowledgeResult.value = null
  currentSources.value = []

  abortController.value = new AbortController()

  try {
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
        categoryFilter: categoryFilter.value || undefined,
        mode: chatMode.value
      }),
      signal: abortController.value.signal
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    const aiMessageIndex = messages.value.length
    addMessage('assistant', '')

    let buffer = ''

    const timeoutId = setTimeout(() => {
      isLoading.value = false
      isThinking.value = false
    }, 120000)

    while (true) {
      const { done, value } = await reader.read()
      if (done) { clearTimeout(timeoutId); break }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.startsWith('data:')) continue

        let data = line.startsWith('data: ') ? line.slice(6).trim() : line.slice(5).trim()
        if (!data) continue

        if (data === '[DONE]') {
          clearTimeout(timeoutId)
          isLoading.value = false
          isThinking.value = false
          return
        }

        try {
          const event = JSON.parse(data)
          handleChatEvent(event, aiMessageIndex)
        } catch (e) {
          // 兼容旧格式: 纯文本回退
          messages.value[aiMessageIndex].content += data.replace(/\\n/g, '\n')
        }
      }
    }

    clearTimeout(timeoutId)
  } catch (error) {
    if (error.name === 'AbortError') {
      ElMessage.info('已停止回复')
    } else {
      console.error('发送消息失败:', error)
      ElMessage.error('发送消息失败，请稍后重试')
    }
  } finally {
    isLoading.value = false
    isThinking.value = false
    abortController.value = null
  }
}

// 处理结构化SSE事件
function handleChatEvent(event, aiMessageIndex) {
  switch (event.type) {
    case 'phase':
      streamPhase.value = event.phase
      streamPhaseLabel.value = event.label || ''
      break

    case 'knowledge_result':
      knowledgeResult.value = normalizeKnowledgeResult(event)
      if (knowledgeResult.value.sources && knowledgeResult.value.sources.length > 0) {
        currentSources.value = knowledgeResult.value.sources
        messages.value[aiMessageIndex].sources = [...knowledgeResult.value.sources]
      }
      break

    case 'thinking_trace':
      messages.value[aiMessageIndex].thinkingTrace = event.items || []
      break

    case 'thinking_start':
      isThinking.value = true
      messages.value[aiMessageIndex].isThinking = true
      break

    case 'thinking_chunk':
      isThinking.value = true
      messages.value[aiMessageIndex].isThinking = true
      messages.value[aiMessageIndex].thinking += event.content || ''
      nextTick(() => scrollToBottom())
      break

    case 'thinking_done':
      messages.value[aiMessageIndex].isThinking = false
      break

    case 'ai_chunk':
      // 收到第一个有效内容时立即隐藏思考状态，开始逐字展示
      if (isThinking.value && event.content?.trim()) {
        isThinking.value = false
      }
      messages.value[aiMessageIndex].isThinking = false
      messages.value[aiMessageIndex].content += event.content || ''
      nextTick(() => scrollToBottom())
      break

    case 'ai_error':
      messages.value[aiMessageIndex].content += '\n\n*[' + (event.message || 'AI响应异常') + ']*'
      break

    case 'done':
      streamPhase.value = 'done'
      streamPhaseLabel.value = ''
      isThinking.value = false
      messages.value[aiMessageIndex].isThinking = false
      // 将知识库来源附加到已完成的消息上，持久化展示
      if (currentSources.value.length > 0) {
        messages.value[aiMessageIndex].sources = normalizeSourceCitations(currentSources.value)
      }
      knowledgeResult.value = null
      currentSources.value = []
      break
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

/* 增强Markdown样式 */
.message-text :deep(h1) { font-size: 1.5em; font-weight: 700; margin: 12px 0 8px; color: #1e293b; }
.message-text :deep(h2) { font-size: 1.3em; font-weight: 700; margin: 10px 0 6px; color: #1e293b; }
.message-text :deep(h3) { font-size: 1.15em; font-weight: 600; margin: 8px 0 4px; color: #334155; }
.message-text :deep(h4) { font-size: 1.05em; font-weight: 600; margin: 6px 0 4px; color: #475569; }

.message-text :deep(pre) {
  background: #1e293b;
  color: #e2e8f0;
  padding: 14px 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 10px 0;
  font-family: 'Fira Code', 'Courier New', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-text :deep(code) {
  background: rgba(139, 92, 246, 0.1);
  color: #7c3aed;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Courier New', Consolas, monospace;
  font-size: 0.9em;
}

.message-text :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  border-radius: 0;
  font-size: inherit;
}

.message-text :deep(blockquote) {
  border-left: 4px solid #8b5cf6;
  padding: 8px 14px;
  margin: 8px 0;
  background: rgba(139, 92, 246, 0.05);
  border-radius: 0 6px 6px 0;
  color: #475569;
  font-style: italic;
}

.message-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 13px;
}

.message-text :deep(th) {
  background: rgba(139, 92, 246, 0.1);
  color: #1e293b;
  font-weight: 700;
  padding: 8px 12px;
  border: 1px solid rgba(139, 92, 246, 0.15);
  text-align: left;
}

.message-text :deep(td) {
  padding: 6px 12px;
  border: 1px solid rgba(139, 92, 246, 0.1);
  color: #334155;
}

.message-text :deep(tr:nth-child(even) td) {
  background: rgba(139, 92, 246, 0.02);
}

.message-text :deep(hr) {
  border: none;
  border-top: 2px solid rgba(139, 92, 246, 0.15);
  margin: 14px 0;
}

.message-text :deep(ul), .message-text :deep(ol) {
  margin: 6px 0;
  padding-left: 20px;
}

.message-text :deep(li) {
  margin: 3px 0;
  color: #334155;
  line-height: 1.6;
}

.message-text :deep(a) {
  color: #3b82f6;
  text-decoration: underline;
}

.message-text :deep(a:hover) {
  color: #8b5cf6;
}

.user-message :deep(h1), .user-message :deep(h2), .user-message :deep(h3), .user-message :deep(h4) {
  color: rgba(255,255,255,0.95);
}

.user-message :deep(blockquote) {
  border-left-color: rgba(255,255,255,0.4);
  background: rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.85);
}

.user-message :deep(table), .user-message :deep(th), .user-message :deep(td) {
  border-color: rgba(255,255,255,0.2);
  color: rgba(255,255,255,0.9);
}

.user-message :deep(th) {
  background: rgba(255,255,255,0.15);
}

.user-message :deep(tr:nth-child(even) td) {
  background: rgba(255,255,255,0.05);
}

.user-message :deep(a) {
  color: rgba(255,255,255,0.9);
}

/* 知识检索状态 */
.knowledge-status {
  margin: 0 0 8px 0;
}

.kb-alert {
  border-radius: 10px;
}

.kb-result {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.kb-source-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.source-tag {
  font-size: 11px;
}

.source-tag .source-type {
  opacity: 0.7;
  margin-left: 2px;
}

/* 消息来源引用 */
.message-sources-footer {
  margin-top: 6px;
  padding: 0 4px;
}

.sources-divider {
  height: 1px;
  background: linear-gradient(90deg, rgba(139, 92, 246, 0.2), transparent);
  margin-bottom: 6px;
}

.sources-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.sources-label {
  color: #64748b;
  font-weight: 500;
}

.source-citation-tag {
  font-size: 11px;
  cursor: default;
}

/* AI思考过程 */
.thinking-trace-panel {
  margin-bottom: 8px;
  border: 1px solid rgba(139, 92, 246, 0.16);
  background: rgba(255, 255, 255, 0.92);
  border-radius: 10px;
  overflow: hidden;
}

.thinking-trace-panel details {
  width: 100%;
}

.thinking-trace-panel summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
  padding: 9px 12px;
  color: #5b21b6;
  font-size: 13px;
  font-weight: 600;
  background: rgba(139, 92, 246, 0.06);
  list-style: none;
}

.thinking-trace-panel summary::-webkit-details-marker {
  display: none;
}

.thinking-live {
  color: #2563eb;
  font-size: 11px;
  font-weight: 500;
}

.trace-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px 0;
}

.trace-step {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.trace-title {
  color: #7c3aed;
  font-weight: 600;
}

.trace-content {
  color: #475569;
  word-break: break-word;
}

.thinking-stream {
  padding: 10px 12px 12px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

/* AI思考动画 */
.thinking-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  font-size: 14px;
  color: #64748b;
}

.thinking-dot {
  color: #8b5cf6;
  animation: dotPulse 1.2s ease-in-out infinite;
  font-size: 10px;
}

.thinking-dot:last-child {
  animation-delay: 0.4s;
}

@keyframes dotPulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.3); }
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
