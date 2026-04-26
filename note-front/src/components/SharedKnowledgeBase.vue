<template>
  <div class="shared-knowledge-base">
    <!-- 顶部导航 -->
    <div class="top-nav">
      <el-tabs v-model="activeTab" class="knowledge-tabs">
        <!-- 知识库广场 -->
        <el-tab-pane label="🌟 知识库广场" name="square">
          <div class="square-container">
            <!-- 搜索区域 -->
            <div class="search-section">
              <div class="search-header">
                <h3>探索知识库</h3>
                <p>发现和加入有趣的知识库</p>
              </div>
              <div class="search-bar">
                <el-input
                    v-model="searchKeyword"
                    placeholder="搜索知识库..."
                    size="large"
                    class="search-input"
                    @keyup.enter="searchKnowledgeBase"
                >
                  <template #prefix>
                    <el-icon><Search /></el-icon>
                  </template>
                </el-input>
                <el-button type="primary" size="large" @click="searchKnowledgeBase">
                  搜索
                </el-button>
              </div>
            </div>

            <!-- 知识库列表 -->
            <div class="knowledge-base-grid" v-if="squareKnowledgeBases.length > 0">
              <div
                  v-for="kb in squareKnowledgeBases"
                  :key="kb.id"
                  class="knowledge-base-card"
                  @click="viewKnowledgeBase(kb)"
              >
                <div class="card-cover">
                  <img :src="kb.coverUrl || 'https://pic.rmb.bdstatic.com/bjh/down/eca9a8e5e57bf50c91529e69f5ccffda.jpeg'" :alt="kb.name" />
                  <div class="card-overlay">
                    <el-button v-if="!kb.isJoined" type="primary" @click.stop="joinKnowledgeBase(kb)">
                      {{ kb.hasPassword ? '输入密码加入' : '立即加入' }}
                    </el-button>
                    <el-button v-else disabled>已加入</el-button>
                  </div>
                </div>
                <div class="card-content">
                  <div class="card-title">{{ kb.name }}</div>
                  <div class="card-description">{{ kb.description || '暂无描述' }}</div>
                  <div class="card-meta">
                    <span class="creator">创建者：{{ kb.creatorName }}</span>
                    <div class="stats">
                      <span><el-icon><User /></el-icon>{{ kb.memberCount }}</span>
                      <span><el-icon><Document /></el-icon>{{ kb.fileCount }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-else class="empty-state">
              <el-icon size="64"><Search /></el-icon>
              <p>暂未找到知识库</p>
              <p>试试搜索其他关键词</p>
            </div>

            <!-- 分页 -->
            <div class="pagination-container">
              <el-pagination
                  v-model:current-page="squarePagination.current"
                  v-model:page-size="squarePagination.size"
                  :total="squarePagination.total"
                  :page-sizes="[10, 20, 50]"
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="loadSquareKnowledgeBases"
                  @current-change="loadSquareKnowledgeBases"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 我创建的知识库 -->
        <el-tab-pane label="📚 我创建的" name="created">
          <div class="my-knowledge-base-container">
            <div class="section-header">
              <h3>我创建的知识库</h3>
              <div style="display:flex;gap:12px;margin-right:50px;margin-top:10px">
                <el-button type="success" plain @click="openCreateDialog('fromPersonal')">
                  <el-icon><CopyDocument /></el-icon>
                  从个人知识库映射
                </el-button>
                <el-button type="primary" @click="openCreateDialog('empty')">
                  <el-icon><Plus /></el-icon>
                  创建知识库
                </el-button>
              </div>

            </div>

            <div class="knowledge-base-grid" v-if="createdKnowledgeBases.length > 0">
              <div
                  v-for="kb in createdKnowledgeBases"
                  :key="kb.id"
                  class="knowledge-base-card my-card"
                  @click="viewKnowledgeBase(kb)"
              >
                <div class="card-cover">
                  <img :src="kb.coverUrl || 'https://pic.rmb.bdstatic.com/bjh/down/eca9a8e5e57bf50c91529e69f5ccffda.jpeg'" :alt="kb.name" />
                  <div class="card-overlay">
                    <el-button-group>
                      <el-button type="primary" size="small" @click.stop="editKnowledgeBase(kb)">
                        <el-icon><Edit /></el-icon>
                      </el-button>
                      <el-button type="danger" size="small" @click.stop="deleteKnowledgeBase(kb)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </el-button-group>
                  </div>
                </div>
                <div class="card-content">
                  <div class="card-title">{{ kb.name }}</div>
                  <div class="card-description">{{ kb.description || '暂无描述' }}</div>
                  <div class="card-meta">
                    <span class="status">{{ kb.isPublic ? '公开' : '私密' }}</span>
                    <div class="stats">
                      <span><el-icon><User /></el-icon>{{ kb.memberCount }}</span>
                      <span><el-icon><Document /></el-icon>{{ kb.fileCount }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-else class="empty-state">
              <el-icon size="64"><Plus /></el-icon>
              <p>您还没有创建任何知识库</p>
              <p>点击"创建知识库"开始您的知识管理之旅</p>
            </div>
          </div>
        </el-tab-pane>

        <!-- 我加入的知识库 -->
        <el-tab-pane label="📖 我加入的" name="joined">
          <div class="my-knowledge-base-container">
            <div class="section-header">
              <h3>我加入的知识库</h3>
            </div>

            <div class="knowledge-base-grid" v-if="joinedKnowledgeBases.length > 0">
              <div
                  v-for="kb in joinedKnowledgeBases"
                  :key="kb.id"
                  class="knowledge-base-card joined-card"
                  @click="viewKnowledgeBase(kb)"
              >
                <div class="card-cover">
                  <img :src="kb.coverUrl || 'https://pic.rmb.bdstatic.com/bjh/down/eca9a8e5e57bf50c91529e69f5ccffda.jpeg'" :alt="kb.name" />
                  <div class="card-overlay">
                    <el-button type="warning" size="small" @click.stop="leaveKnowledgeBase(kb)">
                      <el-icon><Close /></el-icon>
                      退出
                    </el-button>
                  </div>
                </div>
                <div class="card-content">
                  <div class="card-title">{{ kb.name }}</div>
                  <div class="card-description">{{ kb.description || '暂无描述' }}</div>
                  <div class="card-meta">
                    <span class="creator">创建者：{{ kb.creatorName }}</span>
                    <div class="stats">
                      <span><el-icon><User /></el-icon>{{ kb.memberCount }}</span>
                      <span><el-icon><Document /></el-icon>{{ kb.fileCount }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-else class="empty-state">
              <el-icon size="64"><Document /></el-icon>
              <p>您还没有加入任何知识库</p>
              <p>前往知识库广场探索更多有趣的知识库</p>
            </div>
          </div>
        </el-tab-pane>

        <!-- 知识库详情 -->
        <el-tab-pane label="📁 知识库详情" name="detail" v-if="currentKnowledgeBase">
          <div class="knowledge-base-detail">
            <!-- 知识库信息 -->
            <div class="detail-header">
              <div class="kb-info">
                <div class="kb-cover">
                  <img :src="currentKnowledgeBase.coverUrl || 'https://pic.rmb.bdstatic.com/bjh/down/eca9a8e5e57bf50c91529e69f5ccffda.jpeg'" :alt="currentKnowledgeBase.name" />
                </div>
                <div class="kb-meta">
                  <h2>{{ currentKnowledgeBase.name }}</h2>
                  <p>{{ currentKnowledgeBase.description || '暂无描述' }}</p>
                  <div class="kb-stats">
                    <el-tag>{{ currentKnowledgeBase.isPublic ? '公开' : '私密' }}</el-tag>
                    <span><el-icon><User /></el-icon>{{ currentKnowledgeBase.memberCount }} 成员</span>
                    <span><el-icon><Document /></el-icon>{{ currentKnowledgeBase.fileCount }} 文件</span>
                    <span>创建者：{{ currentKnowledgeBase.creatorName }}</span>
                  </div>
                </div>
              </div>
              <div class="kb-actions">
                <el-button @click="showUploadDialog = true" type="primary" class="modern-btn">
                  <el-icon><Upload /></el-icon>
                  上传文件
                </el-button>
                <el-button @click="showCopyDialog = true" type="success" class="modern-btn">
                  <el-icon><CopyDocument /></el-icon>
                  复制文件
                </el-button>
                <el-button
                    v-if="canManageMembers"
                    @click="showMembersDialog = true"
                    type="warning"
                    class="modern-btn"
                >
                  <el-icon><UserFilled /></el-icon>
                  成员管理
                </el-button>
                <el-button
                    @click="showAiChat = !showAiChat"
                    :type="showAiChat ? 'warning' : 'primary'"
                    class="modern-btn ai-toggle-btn"
                >
                  <el-icon><ChatRound /></el-icon>
                  {{ showAiChat ? '关闭AI助手' : '打开AI助手' }}
                </el-button>
                <el-button @click="goBackToList" type="info" class="modern-btn" style="margin-right: 25px">
                  <el-icon><Back /></el-icon>
                  返回
                  <el-icon></el-icon>
                </el-button>
              </div>
            </div>

            <!-- 主要内容区域 -->
            <div class="kb-content-container">
              <!-- 文件列表区域 -->
              <div class="file-list-section">
                <!-- 头部标题和操作 -->
                <div class="file-list-header">
                  <div class="header-left">
                    <el-icon><Document /></el-icon>
                    <h3>文件列表</h3>
                    <span class="file-count">{{ knowledgeBaseFiles.length }} 个文件</span>
                  </div>
                  <div class="header-right">
                    <el-input
                        v-model="fileSearchKeyword"
                        placeholder="搜索文件..."
                        @keyup.enter="searchFiles"
                        style="width: 200px"
                        clearable
                        @clear="searchFiles"
                        size="small"
                    >
                      <template #prefix>
                        <el-icon><Search /></el-icon>
                      </template>
                    </el-input>
                    <el-button @click="searchFiles" size="small">搜索</el-button>
                  </div>
                </div>

                <!-- 文件表格内容 -->
                <div class="file-table-wrapper">
                  <!-- 文件列表表格 -->
                  <div v-if="knowledgeBaseFiles.length > 0" class="table-content">
                    <el-table :data="knowledgeBaseFiles" style="width: 100%" stripe height="400">
                      <el-table-column width="60">
                        <template #default="{ row }">
                          <el-icon size="24" :color="getFileColor(row.fileType)">
                            <Document />
                          </el-icon>
                        </template>
                      </el-table-column>
                      <el-table-column label="文件名" min-width="320" show-overflow-tooltip>
                        <template #default="{ row }">
                          <div class="file-name-cell">
                            <div class="file-name-row">
                              <div class="file-name">{{ row.originalFilename }}</div>
                              <div class="file-entry-meta">
                                <span v-if="row.chunkCount > 0" class="entry-chip">
                                  {{ row.chunkCount }} 个片段
                                </span>
                                <span v-if="row.category" class="entry-chip category-chip">
                                  {{ row.category }}
                                </span>
                              </div>
                            </div>
                            <div v-if="buildFileEntrySummary(row)" class="file-summary">
                              {{ buildFileEntrySummary(row) }}
                            </div>
                          </div>
                        </template>
                      </el-table-column>
                      <el-table-column label="文件大小" width="120">
                        <template #default="{ row }">
                          {{ formatFileSize(row.fileSize) }}
                        </template>
                      </el-table-column>
                      <el-table-column label="上传者" width="150">
                        <template #default="{ row }">
                          {{row.uploaderName}}
                        </template>
                      </el-table-column>
                      <el-table-column label="上传时间" width="180">
                        <template #default="{ row }">
                          {{ formatTime(row.uploadTime) }}
                        </template>
                      </el-table-column>
                      <el-table-column label="来源" width="120">
                        <template #default="{ row }">
                          <el-tag v-if="row.sourceType === 1" type="info" size="small">{{ row.sourceTypeName || '本地上传' }}</el-tag>
                          <el-tag v-else-if="row.sourceType === 2" type="success" size="small">笔记转换</el-tag>
                          <el-tag v-else type="warning" size="small">其他</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column label="分类" width="150">
                        <template #default="{ row }">
                          <el-select
                              v-if="editingCategoryFileId === row.fileId"
                              v-model="editingCategoryValue"
                              size="small"
                              clearable
                              filterable
                              allow-create
                              style="width:110px"
                              @change="saveFileCategory(row)"
                              @blur="editingCategoryFileId = null"
                          >
                            <el-option v-for="cat in kbCategories" :key="cat.id" :label="cat.name" :value="cat.name" />
                          </el-select>
                          <span v-else style="cursor:pointer" @click="startEditCategory(row)">
                            <el-tag v-if="row.category" size="small">{{ row.category }}</el-tag>
                            <span v-else style="color:#999;font-size:12px">点击设置</span>
                          </span>
                        </template>
                      </el-table-column>
                      <!-- 新增/修改：操作列 -->
                      <el-table-column label="操作" width="200" fixed="right">
                        <template #default="{ row }">
                          <el-button
                              type="primary"
                              size="small"
                              text
                              :icon="View"
                              @click="previewFile(row)"
                          >
                            预览
                          </el-button>
                          <el-button
                              v-if="canEditKnowledgeBase"
                              type="danger"
                              size="small"
                              text
                              :icon="Delete"
                              @click="deleteFile(row)"
                              :loading="deletingFileId === row.fileId"
                          >
                            删除
                          </el-button>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>

                  <!-- 文件空状态 -->
                  <div v-else class="empty-state-wrapper">
                    <div class="empty-state">
                      <el-icon size="64"><Upload /></el-icon>
                      <p>暂无文件</p>
                      <p>点击"上传文件"或"复制文件"开始添加内容</p>
                    </div>
                  </div>
                </div>

                <!-- 底部分页和统计 -->
                <div class="file-footer">
                  <!-- 文件分页 -->
                  <div v-if="filePagination.total > 0" class="pagination-wrapper">
                    <div class="pagination-info">
                      <el-icon><Document /></el-icon>
                      <span>共 {{ filePagination.total }} 个文件</span>
                    </div>
                    <el-pagination
                        v-model:current-page="filePagination.current"
                        v-model:page-size="filePagination.size"
                        :total="filePagination.total"
                        :page-sizes="[5, 10, 20, 50]"
                        layout="sizes, prev, pager, next, jumper"
                        @size-change="handleFileSizeChange"
                        @current-change="handleFileCurrentChange"
                        background
                        small
                    />
                  </div>

                  <!-- 文件统计信息（当不需要分页时） -->
                  <div v-else-if="knowledgeBaseFiles.length > 0" class="stats-wrapper">
                    <div class="file-stats-info">
                      <el-icon><Document /></el-icon>
                      <span>共 {{ knowledgeBaseFiles.length }} 个文件</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- AI助手侧边栏 -->
              <div
                  v-if="showAiChat"
                  class="ai-chat-sidebar"
                  :style="{
                  left: aiChatPosition.x + 'px',
                  top: aiChatPosition.y + 'px',
                  right: 'auto'
                }"
                  @mousedown="handleMouseDown"
              >
                <div class="ai-chat-header" :class="{ 'dragging': isDragging }">
                  <div class="header-content">
                    <div class="header-left">
                      <el-icon class="ai-icon"><ChatRound /></el-icon>
                      <div class="header-text">
                        <h3>AI智能助手</h3>
                        <p>基于知识库内容的智能问答</p>
                      </div>
                      <div class="drag-indicator">
                        <el-icon><More /></el-icon>
                      </div>
                    </div>
                    <el-button
                        @click="showAiChat = false"
                        type="text"
                        class="close-btn"
                        size="small"
                    >
                      <el-icon><Close /></el-icon>
                    </el-button>
                  </div>
                </div>

                <div class="kb-chat-container">
                  <div class="chat-status-bar">
                    <div class="status-info">
                      <div class="kb-name">{{ currentKnowledgeBase.name }}</div>
                      <div class="chat-count">{{ kbChatMessages.length }} 条对话</div>
                    </div>
                    <div class="status-indicator" :class="{ 'online': !isKbChatting, 'thinking': isKbChatting }">
                      <span class="indicator-dot"></span>
                      <span class="indicator-text">{{ isKbChatting ? 'AI思考中...' : 'AI在线' }}</span>
                    </div>
                  </div>

                  <div class="chat-messages" ref="chatMessages">
                    <!-- 欢迎消息 -->
                    <div v-if="kbChatMessages.length === 0" class="welcome-message-modern">
                      <div class="welcome-animation">
                        <div class="ai-avatar-large">
                          <el-icon><Avatar /></el-icon>
                        </div>
                        <div class="pulse-rings">
                          <div class="pulse-ring"></div>
                          <div class="pulse-ring"></div>
                          <div class="pulse-ring"></div>
                        </div>
                      </div>
                      <div class="welcome-content">
                        <h4>👋 你好！我是AI智能助手</h4>
                        <p>我已经学习了「{{ currentKnowledgeBase.name }}」中的所有内容，可以为您提供精准的答案和建议。</p>
                        <div class="features-list">
                          <div class="feature-item">
                            <el-icon><Document /></el-icon>
                            <span>文档内容解读</span>
                          </div>
                          <div class="feature-item">
                            <el-icon><Search /></el-icon>
                            <span>快速信息检索</span>
                          </div>
                          <div class="feature-item">
                            <el-icon><ChatRound /></el-icon>
                            <span>智能问答对话</span>
                          </div>
                        </div>
                      </div>
                      <div class="quick-questions-modern">
                        <div class="questions-title">💡 试试这些问题：</div>
                        <div class="questions-grid">
                          <button
                              v-for="question in quickQuestions"
                              :key="question"
                              @click="kbChatInput = question"
                              class="question-card"
                          >
                            {{ question }}
                          </button>
                        </div>
                      </div>
                    </div>

                    <!-- 对话消息 -->
                    <div
                        v-for="(message, index) in kbChatMessages"
                        :key="index"
                        class="message"
                        :class="{ 'user-message': message.type === 'user', 'ai-message': message.type === 'ai' }"
                    >
                      <div class="message-avatar">
                        <el-avatar v-if="message.type === 'user'" :size="32" class="user-avatar">
                          <el-icon><User /></el-icon>
                        </el-avatar>
                        <el-avatar v-else :size="32" class="ai-avatar">
                          <el-icon><Avatar /></el-icon>
                        </el-avatar>
                      </div>
                      <div class="message-content">
                        <div class="message-bubble" :class="{ 'user-bubble': message.type === 'user', 'ai-bubble': message.type === 'ai' }">
                          <div v-if="message.type === 'ai' && !message.isStreaming && extractRetrievalMeta(message.content).routeLabel" class="message-route-meta">
                            <span class="route-chip">{{ extractRetrievalMeta(message.content).routeLabel }}</span>
                            <span v-if="extractRetrievalMeta(message.content).routeReason" class="route-reason">
                              {{ extractRetrievalMeta(message.content).routeReason }}
                            </span>
                          </div>
                          <div v-if="message.type === 'ai' && !message.isStreaming && extractSummaryLines(message.content).length" class="message-summary-meta">
                            <div class="sources-label">检索摘要</div>
                            <div v-for="(line, metaIndex) in extractSummaryLines(message.content)" :key="metaIndex" class="summary-line">
                              {{ line }}
                            </div>
                          </div>
                          <div class="message-text" v-html="formatMessage(message.content)"></div>
                          <div v-if="message.type === 'ai' && !message.isStreaming && extractReferenceLines(message.content).length" class="message-sources">
                            <div class="sources-label">参考来源</div>
                            <div v-for="(source, sourceIndex) in extractReferenceLines(message.content)" :key="sourceIndex" class="source-chip">
                              {{ source }}
                            </div>
                          </div>
                        </div>
                        <div class="message-time">{{ formatTime(message.time) }}</div>
                      </div>
                    </div>

                    <!-- 打字动画 -->
                    <div v-if="isKbChatting" class="message ai-message">
                      <div class="message-avatar">
                        <el-avatar :size="32" class="ai-avatar">
                          <el-icon><Avatar /></el-icon>
                        </el-avatar>
                      </div>
                      <div class="message-content">
                        <div class="message-bubble ai-bubble">
                          <div class="typing-indicator">
                            <span></span>
                            <span></span>
                            <span></span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div class="chat-input-container">
                    <div style="margin-bottom:8px">
                      <el-select
                          v-model="kbChatCategoryFilter"
                          placeholder="限定检索分类（可选）"
                          size="small"
                          clearable
                          style="width:200px"
                      >
                        <el-option v-for="cat in kbCategories" :key="cat.id" :label="cat.name" :value="cat.name" />
                      </el-select>
                    </div>
                    <div class="chat-input-wrapper">
                      <el-input
                          v-model="kbChatInput"
                          type="textarea"
                          :rows="2"
                          placeholder="向AI助手提问..."
                          @keydown.enter.prevent="sendKbMessage"
                          :disabled="isKbChatting"
                          class="chat-textarea"
                          resize="none"
                      />
                      <div class="input-actions">
                        <el-button
                            @click="clearKbChat"
                            size="small"
                            :icon="Delete"
                            circle
                            title="清空对话"
                        />
                        <el-button
                            type="primary"
                            @click="sendKbMessage"
                            :disabled="isKbChatting || !kbChatInput.trim()"
                            :loading="isKbChatting"
                            size="small"
                            :icon="ChatRound"
                        >
                          {{ isKbChatting ? '发送中...' : '发送' }}
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane style="display: none"></el-tab-pane>
      </el-tabs>
    </div>

    <!-- 创建/编辑知识库对话框 -->
    <el-dialog
        v-model="showCreateDialog"
        :title="isEditing ? '编辑知识库' : '创建知识库'"
        width="600px"
        @closed="resetForm"
    >
      <el-form :model="knowledgeBaseForm" :rules="knowledgeBaseRules" ref="knowledgeBaseFormRef" label-width="100px">
        <el-form-item v-if="!isEditing" label="创建方式">
          <el-radio-group v-model="createMode">
            <el-radio-button label="empty">空白新建</el-radio-button>
            <el-radio-button label="fromPersonal">映射个人知识库</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="knowledgeBaseForm.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
              v-model="knowledgeBaseForm.description"
              type="textarea"
              :rows="3"
              placeholder="请输入知识库描述"
          />
        </el-form-item>
        <el-form-item label="封面图片" prop="coverUrl">
          <el-input v-model="knowledgeBaseForm.coverUrl" placeholder="请输入封面图片URL" />
        </el-form-item>
        <el-form-item label="公开设置">
          <el-switch
              v-model="knowledgeBaseForm.isPublic"
              active-text="公开"
              inactive-text="私密"
          />
        </el-form-item>
        <el-form-item label="访问密码" prop="password">
          <el-input
              v-model="knowledgeBaseForm.password"
              type="password"
              placeholder="可选，设置后需要密码才能加入"
              show-password
          />
        </el-form-item>
        <template v-if="!isEditing && createMode === 'fromPersonal'">
          <el-form-item label="映射说明">
            <div style="color:#606266;line-height:1.6">
              创建后会把选中的个人知识库文件复制到当前共享知识库，后续仍可继续上传或追加复制文件。
            </div>
          </el-form-item>
          <el-form-item label="选择文件">
            <div style="width:100%">
              <el-select
                  v-model="selectedInitialFileIds"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="请选择要映射的个人知识库文件"
                  style="width:100%"
              >
                <el-option
                    v-for="file in personalFiles"
                    :key="file.id"
                    :label="file.originalFilename || file.fileName"
                    :value="file.id"
                >
                  <div style="display:flex;justify-content:space-between;gap:12px">
                    <span>{{ file.originalFilename || file.fileName }}</span>
                    <span style="color:#909399;font-size:12px">{{ formatFileSize(file.fileSize || 0) }}</span>
                  </div>
                </el-option>
              </el-select>
              <div style="margin-top:8px;color:#909399;font-size:12px">
                已选 {{ selectedInitialFileIds.length }} 个文件
                <span v-if="personalFiles.length === 0">，当前没有可映射的个人知识库文件</span>
              </div>
            </div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateOrUpdateKnowledgeBase">
          {{ isEditing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 加入知识库对话框 -->
    <el-dialog
        v-model="showJoinDialog"
        title="加入知识库"
        width="400px"
    >
      <div class="join-dialog-content">
        <div class="kb-info">
          <h4>{{ joinKnowledgeBaseData.name }}</h4>
          <p>{{ joinKnowledgeBaseData.description }}</p>
        </div>
        <el-form v-if="joinKnowledgeBaseData.hasPassword" :model="joinForm" ref="joinFormRef">
          <el-form-item label="访问密码" prop="password" :rules="[{ required: true, message: '请输入访问密码' }]">
            <el-input
                v-model="joinForm.password"
                type="password"
                placeholder="请输入访问密码"
                show-password
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="showJoinDialog = false">取消</el-button>
        <el-button type="primary" @click="handleJoinKnowledgeBase">加入</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件对话框 -->
    <el-dialog
        v-model="showUploadDialog"
        title="上传文件"
        width="500px"
    >
      <el-select
          v-model="uploadCategory"
          placeholder="选择文件分类（可选）"
          clearable
          filterable
          allow-create
          style="width:100%;margin-bottom:12px"
      >
        <el-option v-for="cat in kbCategories" :key="cat.id" :label="cat.name" :value="cat.name" />
      </el-select>
      <el-upload
          class="upload-demo"
          drag
          :auto-upload="false"
          :file-list="uploadFileList"
          :on-change="handleFileSelect"
          :before-remove="handleRemoveFile"
          multiple
          accept=".pdf,.doc,.docx,.txt,.md"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将文件拖拽到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 pdf、doc、docx、txt、md 格式，单文件最大 100MB
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUploadFiles" :disabled="uploadFileList.length === 0">
          确认上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 复制文件对话框 -->
    <el-dialog
        v-model="showCopyDialog"
        title="复制文件到知识库"
        width="1000px"
    >
      <div class="copy-dialog-content">
        <div class="file-selection">
          <div class="selection-header">
            <h4>选择要复制的文件</h4>
            <div class="selection-stats">
              <span>共 {{ personalFiles.length }} 个文件</span>
              <span v-if="selectedFiles.length > 0">已选择 {{ selectedFiles.length }} 个</span>
            </div>
          </div>

          <div class="file-filter">
            <el-input
                v-model="personalFileSearch"
                placeholder="搜索文件..."
                style="width: 200px"
                clearable
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select v-model="personalFileFilter" placeholder="按来源筛选" style="width: 120px" clearable>
              <el-option label="全部" value="" />
              <el-option label="直接上传" :value="1" />
              <el-option label="笔记转换" :value="2" />
            </el-select>
          </div>

          <el-table
              :data="filteredPersonalFiles"
              @selection-change="handleSelectionChange"
              style="width: 100%"
              max-height="400px"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column width="50">
              <template #default="{ row }">
                <el-icon size="20" :color="getFileColor(row.fileType)">
                  <Document />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column label="文件名" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="file-name-cell">
                  <div class="file-name">{{ row.fileName }}</div>
                  <div class="file-original-name" v-if="row.originalFilename && row.originalFilename !== row.fileName">
                    原文件名: {{ row.originalFilename }}
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="文件大小" width="100">
              <template #default="{ row }">
                {{ formatFileSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column label="来源" width="120">
              <template #default="{ row }">
                <div v-if="row.sourceType === 1">
                  <el-tag type="info" size="small">直接上传</el-tag>
                </div>
                <div v-else-if="row.sourceType === 2">
                  <el-tag type="success" size="small">笔记转换</el-tag>
                  <div class="source-note" v-if="row.sourceNoteTitle">
                    来自: {{ row.sourceNoteTitle }}
                  </div>
                </div>
                <div v-else>
                  <el-tag type="warning" size="small">其他</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="150">
              <template #default="{ row }">
                {{ formatTime(row.uploadTime || row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="showCopyDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCopyFiles" :disabled="selectedFiles.length === 0">
          复制选中文件
        </el-button>
      </template>
    </el-dialog>

    <!-- 文件预览对话框 -->
    <el-dialog
        v-model="previewDialog.visible"
        :title="previewDialog.title"
        width="80%"
        top="5vh"
        destroy-on-close
        append-to-body
        center
        @closed="resetPreviewDialog"
    >
      <div class="preview-container" :class="{ 'text-preview-shell': previewDialog.fileType === 'txt' || previewDialog.fileType === 'md' }">
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
        <!-- PDF 预览 -->
        <iframe
            v-if="previewDialog.fileType === 'pdf'"
            :src="previewDialog.fileUrl"
            class="preview-iframe"
        />
        <!-- TXT 预览 -->
        <pre
            v-else-if="previewDialog.fileType === 'txt'"
            class="text-preview"
        >{{ previewDialog.textContent || '暂无文本内容' }}</pre>
        <!-- Markdown 预览 -->
        <div
            v-else-if="previewDialog.fileType === 'md'"
            class="markdown-preview"
            v-html="previewDialog.markdownContent || '<p>暂无 Markdown 内容</p>'"
        />
        <!-- 不支持 -->
        <div v-else class="preview-placeholder">
          <el-empty description="无法加载文件预览" />
        </div>
      </div>
    </el-dialog>

    <!-- 成员管理对话框 -->
    <el-dialog
        v-model="showMembersDialog"
        title="成员管理"
        width="800px"
        top="5vh"
    >
      <div class="members-dialog-content">
        <div class="members-header">
          <div class="header-info">
            <h4>{{ currentKnowledgeBase?.name }} - 成员管理</h4>
            <div class="member-stats">
              <el-icon><User /></el-icon>
              <span>共 {{ members.length }} 名成员</span>
            </div>
          </div>
        </div>

        <div class="members-list">
          <el-table
              :data="members"
              style="width: 100%"
              height="400px"
              stripe
          >
            <el-table-column width="60">
              <template #default="{ row }">
                <el-avatar :size="40" :src="row.avatarUrl">
                  <el-icon><User /></el-icon>
                </el-avatar>
              </template>
            </el-table-column>
            <el-table-column label="用户名" prop="username" min-width="120" show-overflow-tooltip />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag
                    :type="row.role === 1 ? 'danger' : row.role === 2 ? 'primary' : 'info'"
                    size="small"
                >
                  {{ row.roleName }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="在线状态" width="100">
              <template #default="{ row }">
                <div class="online-status">
                  <span class="status-dot" :class="{ 'online': row.online, 'offline': !row.online }"></span>
                  <span class="status-text">{{ row.online ? '在线' : '离线' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="加入时间" width="160">
              <template #default="{ row }">
                {{ formatTime(row.joinTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                    v-if="row.role !== 1 && row.userId !== userStore.userInfo?.id"
                    type="danger"
                    size="small"
                    text
                    :icon="Delete"
                    @click="deleteMember(row)"
                    :loading="deletingMemberId === row.userId"
                >
                  移除
                </el-button>
                <span v-else class="disabled-action">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 空状态 -->
        <div v-if="members.length === 0" class="empty-state">
          <el-icon size="64"><User /></el-icon>
          <p>暂无成员数据</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="showMembersDialog = false">关闭</el-button>
        <el-button type="primary" @click="loadMembers">刷新</el-button>
      </template>
    </el-dialog>

    <UploadTaskPanel :tasks="uploadTasks" title="知识库上传进度" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import {
  Search, User, Document, Plus, Edit, Delete, Upload, CopyDocument, Back, Close, UploadFilled, ChatRound, Avatar, InfoFilled, More,
  View, // 新增/修改：导入 View 图标
  UserFilled // 新增：导入成员管理图标
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store'
import {
  getKnowledgeBaseSquare,
  getMyCreatedKnowledgeBase,
  getMyJoinedKnowledgeBase,
  createSharedKnowledgeBase,
  updateSharedKnowledgeBase,
  deleteSharedKnowledgeBase,
  getSharedKnowledgeBaseDetail,
  joinKnowledgeBase as joinKnowledgeBaseApi,
  leaveKnowledgeBase as leaveKnowledgeBaseApi,
  getSharedKnowledgeBaseFiles,
  uploadToSharedKnowledgeBase,
  copyFilesToSharedKnowledgeBase,
  deleteSharedKnowledgeBaseFile,
  getPersonalFileList,
  sharedKnowledgeBaseChatStream,
  getKnowledgeBaseMembers,
  deleteKnowledgeBaseMember,
  updateKnowledgeBaseFileCategory
} from '@/api'
import { getMyCategories, createCategory } from '@/api/blog'
import UploadTaskPanel from '@/components/UploadTaskPanel.vue'
import {
  MAX_UPLOAD_SIZE_MB,
  createUploadTask,
  updateUploadTaskProgress,
  markUploadTaskSuccess,
  markUploadTaskError,
  scheduleUploadTaskCleanup
} from '@/utils/uploadProgress'

// Store
const userStore = useUserStore()

// 响应式数据
const activeTab = ref('square')

const searchKeyword = ref('')
const fileSearchKeyword = ref('')

// AI对话相关
const kbChatInput = ref('')
const kbChatMessages = ref([])
const kbChatCategoryFilter = ref('')
const kbCategories = ref([])
const isKbChatting = ref(false)
const kbSessionId = ref('')
const chatMessages = ref()
const showAiChat = ref(false) // 控制AI助手显示/隐藏
const quickQuestions = ref([
  '这个知识库包含什么内容？',
  '有什么重要的文档？',
  '请总结一下主要信息',
  '有哪些常见问题？'
])

// AI助手拖拽相关
const aiChatPosition = reactive({
  x: 0,
  y: 80
})
const isDragging = ref(false)
const dragStart = reactive({
  x: 0,
  y: 0,
  mouseX: 0,
  mouseY: 0
})

// 文件管理相关
const deletingFileId = ref(null)
const personalFileSearch = ref('')
const personalFileFilter = ref('')

// 知识库列表数据
const squareKnowledgeBases = ref([])
const createdKnowledgeBases = ref([])
const joinedKnowledgeBases = ref([])
const currentKnowledgeBase = ref(null)
const knowledgeBaseFiles = ref([])
const personalFiles = ref([])
const selectedFiles = ref([])

// 分页数据
const squarePagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const filePagination = reactive({
  current: 1,
  size: 5,
  total: 0
})

// 对话框状态
const showCreateDialog = ref(false)
const showJoinDialog = ref(false)
const SUPPORTED_UPLOAD_EXTENSIONS = ['pdf', 'doc', 'docx', 'txt', 'md']
const SUPPORTED_UPLOAD_LABEL = 'pdf、doc、docx、txt、md'

const showCopyDialog = ref(false)
const isEditing = ref(false)
const createMode = ref('empty')
const selectedInitialFileIds = ref([])

// 成员管理相关
const showMembersDialog = ref(false)
const members = ref([])
const deletingMemberId = ref(null)

// 文件预览对话框数据
const previewDialog = reactive({
  visible: false,
  title: '',
  fileUrl: '',
  fileType: '',
  textContent: '',
  markdownContent: '',
  metadata: null
})

// 表单数据
const knowledgeBaseForm = reactive({
  name: '',
  description: '',
  coverUrl: '',
  isPublic: true,
  password: ''
})

const joinForm = reactive({
  password: ''
})

const joinKnowledgeBaseData = ref({})
const uploadFileList = ref([])
const uploadCategory = ref('')
const uploadTasks = ref([])
const editingCategoryFileId = ref(null)
const editingCategoryValue = ref('')

// 表单引用
const knowledgeBaseFormRef = ref()
const joinFormRef = ref()

// 表单验证规则
const knowledgeBaseRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
}

// 来源Tab记录
const sourceTab = ref('square')

// 计算属性
const canEditKnowledgeBase = computed(() => {
  if (!currentKnowledgeBase.value || !userStore.userInfo) return false
  return currentKnowledgeBase.value.creatorId === userStore.userInfo.id ||
      currentKnowledgeBase.value.isAdmin === true ||
      currentKnowledgeBase.value.canEdit === true
})

const canManageMembers = computed(() => {
  if (!currentKnowledgeBase.value || !userStore.userInfo) return false
  return currentKnowledgeBase.value.creatorId === userStore.userInfo.id ||
      currentKnowledgeBase.value.isAdmin === true
})

const filteredPersonalFiles = computed(() => {
  let files = personalFiles.value

  if (personalFileSearch.value) {
    const keyword = personalFileSearch.value.toLowerCase()
    files = files.filter(file =>
        file.fileName.toLowerCase().includes(keyword) ||
        (file.originalFilename && file.originalFilename.toLowerCase().includes(keyword)) ||
        (file.sourceNoteTitle && file.sourceNoteTitle.toLowerCase().includes(keyword))
    )
  }

  if (personalFileFilter.value !== '' && personalFileFilter.value !== null) {
    files = files.filter(file => file.sourceType === personalFileFilter.value)
  }

  return files
})

// 方法
const searchKnowledgeBase = async () => {
  squarePagination.current = 1
  await loadSquareKnowledgeBases()
}

const loadSquareKnowledgeBases = async () => {
  try {
    const params = {
      keyword: searchKeyword.value,
      page: squarePagination.current,
      size: squarePagination.size
    }
    const response = await getKnowledgeBaseSquare(params)
    const data = response.data

    squareKnowledgeBases.value = data.data.data
    squarePagination.total = data.data.total
  } catch (error) {
    console.error('加载知识库广场失败:', error)
    ElMessage.error('加载知识库广场失败')
  }
}

const loadMyCreatedKnowledgeBases = async () => {
  try {
    const response = await getMyCreatedKnowledgeBase()
    const data = response.data
    createdKnowledgeBases.value = data.data
  } catch (error) {
    console.error('加载我创建的知识库失败:', error)
    ElMessage.error('加载我创建的知识库失败')
  }
}

const loadMyJoinedKnowledgeBases = async () => {
  try {
    const response = await getMyJoinedKnowledgeBase()
    const data = response.data
    joinedKnowledgeBases.value = data.data
  } catch (error) {
    console.error('加载我加入的知识库失败:', error)
    ElMessage.error('加载我加入的知识库失败')
  }
}

const viewKnowledgeBase = async (kb) => {
  try {
    const response = await getSharedKnowledgeBaseDetail(kb.id)
    const data = response.data
    currentKnowledgeBase.value = data.data
    sourceTab.value = activeTab.value
    activeTab.value = 'detail'
    await loadKnowledgeBaseFiles()
  } catch (error) {
    console.error('获取知识库详情失败:', error)
    ElMessage.error('获取知识库详情失败')
  }
}

const goBackToList = () => {
  activeTab.value = sourceTab.value
  currentKnowledgeBase.value = null
  kbChatMessages.value = []
  kbSessionId.value = ''
}

const joinKnowledgeBase = (kb) => {
  joinKnowledgeBaseData.value = kb
  joinForm.password = ''
  showJoinDialog.value = true
}

const handleJoinKnowledgeBase = async () => {
  try {
    if (joinKnowledgeBaseData.value.hasPassword) {
      await joinFormRef.value.validate()
    }

    const data = {
      knowledgeBaseId: joinKnowledgeBaseData.value.id,
      password: joinForm.password
    }

    const response = await joinKnowledgeBaseApi(data)
    showJoinDialog.value = false
    ElMessage.success(response.data.message)

    await loadSquareKnowledgeBases()
    await loadMyJoinedKnowledgeBases()

  } catch (error) {
    console.error('加入知识库失败:', error)
    ElMessage.error('加入知识库失败')
  }
}

const leaveKnowledgeBase = async (kb) => {
  try {
    await ElMessageBox.confirm('确定要退出这个知识库吗？', '提示', {
      type: 'warning'
    })

    const response =await leaveKnowledgeBaseApi(kb.id)
    ElMessage.success(response.data.message)

    await loadMyJoinedKnowledgeBases()

  } catch (error) {
    if (error !== 'cancel') {
      console.error('退出知识库失败:', error)
      ElMessage.error('退出知识库失败')
    }
  }
}

const openCreateDialog = async (mode = 'empty') => {
  resetForm()
  createMode.value = mode
  showCreateDialog.value = true

  if (mode === 'fromPersonal') {
    await loadPersonalFiles()
  }
}

const editKnowledgeBase = (kb) => {
  isEditing.value = true
  createMode.value = 'empty'
  selectedInitialFileIds.value = []
  knowledgeBaseForm.name = kb.name
  knowledgeBaseForm.description = kb.description
  knowledgeBaseForm.coverUrl = kb.coverUrl
  knowledgeBaseForm.isPublic = kb.isPublic
  knowledgeBaseForm.password = ''
  knowledgeBaseForm.id = kb.id
  showCreateDialog.value = true
}

const deleteKnowledgeBase = async (kb) => {
  try {
    await ElMessageBox.confirm('确定要删除这个知识库吗？删除后无法恢复。', '提示', {
      type: 'warning'
    })

    await deleteSharedKnowledgeBase(kb.id)
    ElMessage.success('删除知识库成功')

    await loadMyCreatedKnowledgeBases()

  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除知识库失败:', error)
      ElMessage.error('删除知识库失败')
    }
  }
}

const handleCreateOrUpdateKnowledgeBase = async () => {
  try {
    await knowledgeBaseFormRef.value.validate()

    const data = {
      name: knowledgeBaseForm.name,
      description: knowledgeBaseForm.description,
      coverUrl: knowledgeBaseForm.coverUrl,
      isPublic: knowledgeBaseForm.isPublic,
      password: knowledgeBaseForm.password
    }

    if (!isEditing.value && createMode.value === 'fromPersonal' && selectedInitialFileIds.value.length > 0) {
      data.initialFileIds = [...selectedInitialFileIds.value]
    }

    if (isEditing.value) {
      await updateSharedKnowledgeBase(knowledgeBaseForm.id, data)
      ElMessage.success('更新知识库成功')
    } else {
      await createSharedKnowledgeBase(data)
      ElMessage.success('创建知识库成功')
    }

    showCreateDialog.value = false
    resetForm()

    await loadMyCreatedKnowledgeBases()

  } catch (error) {
    console.error('操作失败:', error)
    ElMessage.error('操作失败')
  }
}

const resetForm = () => {
  knowledgeBaseForm.name = ''
  knowledgeBaseForm.description = ''
  knowledgeBaseForm.coverUrl = ''
  knowledgeBaseForm.isPublic = true
  knowledgeBaseForm.password = ''
  isEditing.value = false
  createMode.value = 'empty'
  selectedInitialFileIds.value = []
}

const loadKnowledgeBaseFiles = async () => {
  try {
    const params = {
      page: filePagination.current,
      size: filePagination.size,
      keyword: fileSearchKeyword.value
    }

    const response = await getSharedKnowledgeBaseFiles(currentKnowledgeBase.value.id, params)
    const data = response.data

    knowledgeBaseFiles.value = data.data.data || []
    filePagination.total = data.data.total || 0
  } catch (error) {
    console.error('加载文件列表失败:', error)
    ElMessage.error('加载文件列表失败')
    knowledgeBaseFiles.value = []
    filePagination.total = 0
  }
}

const searchFiles = () => {
  filePagination.current = 1
  loadKnowledgeBaseFiles()
}

const handleFileSizeChange = (size) => {
  filePagination.size = size
  filePagination.current = 1
  loadKnowledgeBaseFiles()
}

const handleFileCurrentChange = (current) => {
  filePagination.current = current
  loadKnowledgeBaseFiles()
}

const pushUploadTask = (file) => {
  const sourceFile = file?.raw || file
  const task = createUploadTask(sourceFile, { name: `知识库上传 · ${sourceFile?.name || '未命名文件'}` })
  uploadTasks.value.unshift(task)
  if (uploadTasks.value.length > 10) {
    uploadTasks.value = uploadTasks.value.slice(0, 10)
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

const ensureCategoryExists = async (categoryName) => {
  const normalizedName = typeof categoryName === 'string' ? categoryName.trim() : ''
  if (!normalizedName) return ''

  const existingCategory = kbCategories.value.find(cat => cat.name === normalizedName)
  if (existingCategory) {
    return normalizedName
  }

  const response = await createCategory({ name: normalizedName })
  const newCategory = response.data?.data || response.data
  if (newCategory?.id && !kbCategories.value.some(cat => cat.id === newCategory.id)) {
    kbCategories.value.push(newCategory)
  }
  return normalizedName
}

const handleFileSelect = (file) => {
  const extension = (file.name.split('.').pop() || '').toLowerCase()
  if (!SUPPORTED_UPLOAD_EXTENSIONS.includes(extension)) {
    ElMessage.error(`只支持 ${SUPPORTED_UPLOAD_LABEL} 格式`)
    return false
  }
  if ((file.size || 0) / 1024 / 1024 > MAX_UPLOAD_SIZE_MB) {
    ElMessage.error(`文件大小不能超过 ${MAX_UPLOAD_SIZE_MB}MB`)
    return false
  }
  uploadFileList.value = [...uploadFileList.value, file]
}

const handleRemoveFile = (file) => {
  const index = uploadFileList.value.findIndex(f => f.uid === file.uid)
  if (index > -1) {
    uploadFileList.value.splice(index, 1)
  }
}

const startEditCategory = (row) => {
  editingCategoryFileId.value = row.fileId
  editingCategoryValue.value = row.category || ''
}

const saveFileCategory = async (row) => {
  try {
    const categoryName = await ensureCategoryExists(editingCategoryValue.value)
    await updateKnowledgeBaseFileCategory(currentKnowledgeBase.value.id, row.fileId, categoryName || null)
    row.category = categoryName || null
    ElMessage.success('分类已更新')
  } catch (e) {
    ElMessage.error('更新分类失败')
  } finally {
    editingCategoryFileId.value = null
  }
}

const handleUploadFiles = async () => {
  try {
    const categoryName = await ensureCategoryExists(uploadCategory.value)
    const uploadPromises = uploadFileList.value.map(async (file) => {
      const uploadTask = pushUploadTask(file)
      try {
        await uploadToSharedKnowledgeBase(currentKnowledgeBase.value.id, file.raw, categoryName || undefined, {
          onUploadProgress: (progressEvent) => updateUploadTaskProgress(uploadTask, progressEvent)
        })
        finishUploadTask(uploadTask)
      } catch (error) {
        finishUploadTask(uploadTask, error)
        throw error
      }
    })

    await Promise.all(uploadPromises)

    showUploadDialog.value = false
    uploadFileList.value = []
    uploadCategory.value = ''
    ElMessage.success('文件上传成功')

    await loadKnowledgeBaseFiles()

  } catch (error) {
    console.error('上传文件失败:', error)
    ElMessage.error('上传文件失败')
  }
}

const loadPersonalFiles = async () => {
  try {
    const response = await getPersonalFileList()
    const data = response.data
    personalFiles.value = data.data
  } catch (error) {
    console.error('加载个人文件列表失败:', error)
    ElMessage.error('加载个人文件列表失败')
  }
}

const handleSelectionChange = (selection) => {
  selectedFiles.value = selection
}

const handleCopyFiles = async () => {
  try {
    const fileIds = selectedFiles.value.map(file => file.id)
    const data = { fileIds }

    const response = await copyFilesToSharedKnowledgeBase(currentKnowledgeBase.value.id, data)
    const result = response.data.data

    showCopyDialog.value = false
    selectedFiles.value = []

    ElMessage.success(`成功复制 ${result.successCount} 个文件，失败 ${result.failCount} 个文件`)

    await loadKnowledgeBaseFiles()

  } catch (error) {
    console.error('复制文件失败:', error)
    ElMessage.error('复制文件失败')
  }
}

const deleteFile = async (file) => {
  try {
    await ElMessageBox.confirm(
        `确定要删除文件"${file.originalFilename}"吗？删除后无法恢复。`,
        '删除确认',
        {
          type: 'warning',
          confirmButtonText: '确定删除',
          cancelButtonText: '取消'
        }
    )

    deletingFileId.value = file.fileId

    await deleteSharedKnowledgeBaseFile(currentKnowledgeBase.value.id, file.fileId)
    ElMessage.success('删除文件成功')

    await loadKnowledgeBaseFiles()

    if (currentKnowledgeBase.value) {
      currentKnowledgeBase.value.fileCount = Math.max(0, currentKnowledgeBase.value.fileCount - 1)
    }

  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除文件失败:', error)
      ElMessage.error('删除文件失败')
    }
  } finally {
    deletingFileId.value = null
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

// 重置预览对话框
const resetPreviewDialog = () => {
  previewDialog.fileUrl = ''
  previewDialog.fileType = ''
  previewDialog.textContent = ''
  previewDialog.markdownContent = ''
  previewDialog.title = ''
  previewDialog.metadata = null
}

const buildPreviewMetadata = (file) => ({
  fileName: file.originalFilename || file.fileName || '未命名文件',
  category: file.category || '未分类',
  fileSize: formatFileSize(file.fileSize || 0),
  sourceLabel: file.sourceType === 2 ? '笔记转换' : (file.sourceTypeName || '本地上传'),
  updateTime: formatTime(file.uploadTime || file.createTime),
  typeLabel: (file.fileExtension || file.fileType || '').toUpperCase() || '文件'
})

// 预览文件
const previewFile = async (file) => {
  if (!file.fileUrl) {
    ElMessage.warning('该文件暂不支持预览')
    return
  }

  const ext = (file.fileExtension || file.extension || file.fileType || (file.originalFilename || file.fileName || '').split('.').pop() || '').toLowerCase()

  const fileUrl = fixFileUrl(file.fileUrl)

  previewDialog.visible = true
  previewDialog.title = `预览: ${file.originalFilename || file.fileName || ''}`
  previewDialog.fileUrl = ''
  previewDialog.fileType = ''
  previewDialog.textContent = ''
  previewDialog.markdownContent = ''
  previewDialog.metadata = buildPreviewMetadata(file)

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

// AI对话相关方法
const sendKbMessage = async () => {
  if (!kbChatInput.value.trim() || isKbChatting.value) return

  const userMessage = {
    type: 'user',
    content: kbChatInput.value,
    time: new Date().toISOString()
  }

  kbChatMessages.value.push(userMessage)
  const currentMessage = kbChatInput.value
  kbChatInput.value = ''
  isKbChatting.value = true

  try {
    if (!kbSessionId.value) {
      kbSessionId.value = `kb_${currentKnowledgeBase.value.id}_${Date.now()}`
    }

    const token = userStore.token || sessionStorage.getItem('token') || localStorage.getItem('token')
    const response = await fetch('/api/shared-knowledge-base/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify({
        knowledgeBaseId: currentKnowledgeBase.value.id,
        message: currentMessage,
        memoryId: kbSessionId.value,
        categoryFilter: kbChatCategoryFilter.value || undefined
      })
    })

    if (!response.ok || !response.body) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

  const aiMessage = {
      type: 'ai',
      content: '',
      time: new Date().toISOString(),
      isStreaming: true,
      routeMeta: null
    }

    kbChatMessages.value.push(aiMessage)
    const currentAiMessage = kbChatMessages.value[kbChatMessages.value.length - 1]

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.trim() === '') continue

        if (line.startsWith('data:')) {
          let data = ''

          if (line.startsWith('data: ')) {
            data = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            data = line.slice(5).trim()
          }

          if (data === '[DONE]') {
            currentAiMessage.isStreaming = false
            isKbChatting.value = false
            return
          }

          if (data) {
            currentAiMessage.content += data.replace(/\\n/g, '\n')
            nextTick(() => {
              if (chatMessages.value) {
                chatMessages.value.scrollTop = chatMessages.value.scrollHeight
              }
            })
          }
        }
      }
    }

    nextTick(() => {
      if (chatMessages.value) {
        chatMessages.value.scrollTop = chatMessages.value.scrollHeight
      }
    })

  } catch (error) {
    console.error('AI对话失败:', error)
    const errorMessage = error?.message?.includes('401')
      ? '共享知识库AI助手鉴权失败，请重新登录后重试'
      : '共享知识库AI助手暂时不可用，请稍后重试'
    ElMessage.error(errorMessage)

    const lastMessage = kbChatMessages.value[kbChatMessages.value.length - 1]
    if (!lastMessage || lastMessage.type !== 'ai' || lastMessage.content) {
      kbChatMessages.value.push({
        type: 'ai',
        content: errorMessage,
        time: new Date().toISOString()
      })
    } else {
      lastMessage.content = errorMessage
    }
  } finally {
    isKbChatting.value = false
  }
}

const clearKbChat = () => {
  kbChatMessages.value = []
  kbSessionId.value = ''
}

const handleMouseDown = (e) => {
  if (e.target.closest('.ai-chat-header')) {
    isDragging.value = true
    dragStart.x = aiChatPosition.x
    dragStart.y = aiChatPosition.y
    dragStart.mouseX = e.clientX
    dragStart.mouseY = e.clientY

    document.addEventListener('mousemove', handleMouseMove)
    document.addEventListener('mouseup', handleMouseUp)
    e.preventDefault()
  }
}

const handleMouseMove = (e) => {
  if (!isDragging.value) return

  const deltaX = e.clientX - dragStart.mouseX
  const deltaY = e.clientY - dragStart.mouseY

  const newX = Math.max(0, Math.min(window.innerWidth - 400, dragStart.x + deltaX))
  const newY = Math.max(0, Math.min(window.innerHeight - 400, dragStart.y + deltaY))

  aiChatPosition.x = newX
  aiChatPosition.y = newY
}

const handleMouseUp = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
}

const extractReferenceLines = (content) => {
  if (!content) return []
  const marker = '参考来源'
  const index = content.lastIndexOf(marker)
  if (index === -1) return []
  return content
    .slice(index + marker.length)
    .split('\n')
    .map(line => line.replace(/^[:：\s-]+/, '').trim())
    .filter(Boolean)
}

const extractRetrievalMeta = (content) => {
  if (!content) return { routeLabel: '', routeReason: '' }
  const routeMatch = content.match(/检索策略：(.+)/)
  const reasonMatch = content.match(/策略原因：(.+)/)
  return {
    routeLabel: routeMatch?.[1]?.trim() || '',
    routeReason: reasonMatch?.[1]?.trim() || ''
  }
}

const extractSummaryLines = (content) => {
  if (!content) return []
  const strategyIndex = content.indexOf('检索策略：')
  const contextIndex = content.indexOf('可参考来源：')
  if (strategyIndex === -1 || contextIndex === -1 || contextIndex <= strategyIndex) {
    return []
  }
  return content
    .slice(strategyIndex, contextIndex)
    .split('\n')
    .map(line => line.trim())
    .filter(line => line && !line.startsWith('检索策略：') && !line.startsWith('策略原因：'))
    .slice(0, 3)
}

const formatMessage = (content) => {
  const displayContent = (() => {
    const referenceMarker = '参考来源'
    const referenceIndex = content?.lastIndexOf(referenceMarker) ?? -1
    const strategyIndex = content?.indexOf('检索策略：') ?? -1
    const contentWithoutReferences = referenceIndex === -1 ? content : content.slice(0, referenceIndex).trimEnd()
    return strategyIndex === -1 ? contentWithoutReferences : contentWithoutReferences.slice(contentWithoutReferences.indexOf('\n\n') + 2).trimStart()
  })()
  return displayContent
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/`(.*?)`/g, '<code>$1</code>')
      .replace(/\n/g, '<br>')
}

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatTime = (time) => {
  if (!time) {
    return ''
  }

  const date = new Date(time)
  if (isNaN(date.getTime())) {
    return ''
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const getFileColor = (fileType) => {
  const t = (fileType || '').toLowerCase()
  if (t === 'pdf') return '#ef4444'
  if (t === 'txt') return '#3b82f6'
  if (t === 'md') return '#10b981'
  return '#64748b'
}

const buildFileEntrySummary = (file) => {
  const summary = (file?.summary || '').replace(/\s+/g, ' ').trim()
  if (!summary) {
    return ''
  }
  return summary.length > 120 ? `${summary.slice(0, 120)}...` : summary
}

watch(activeTab, (newTab) => {
  if (newTab === 'square') {
    loadSquareKnowledgeBases()
  } else if (newTab === 'created') {
    loadMyCreatedKnowledgeBases()
  } else if (newTab === 'joined') {
    loadMyJoinedKnowledgeBases()
  }
})

watch(currentKnowledgeBase, (newKb) => {
  if (newKb) {
    kbChatMessages.value = []
    kbSessionId.value = ''
    showAiChat.value = false
    filePagination.current = 1
    filePagination.total = 0
    fileSearchKeyword.value = ''
  }
})

watch(showAiChat, (newVal) => {
  if (!newVal) {
    nextTick(() => {
      if (chatMessages.value) {
        chatMessages.value.scrollTop = 0
      }
    })
  } else {
    const maxX = window.innerWidth - 400
    const maxY = window.innerHeight - 400
    if (aiChatPosition.x > maxX) {
      aiChatPosition.x = Math.max(20, maxX)
    }
    if (aiChatPosition.y > maxY) {
      aiChatPosition.y = Math.max(80, maxY)
    }
  }
})

const handleResize = () => {
  const maxX = window.innerWidth - 400
  const maxY = window.innerHeight - 400
  if (aiChatPosition.x > maxX) {
    aiChatPosition.x = Math.max(20, maxX)
  }
  if (aiChatPosition.y > maxY) {
    aiChatPosition.y = Math.max(80, maxY)
  }
}

watch(showCopyDialog, (newVal) => {
  if (newVal) {
    loadPersonalFiles()
    personalFileSearch.value = ''
    personalFileFilter.value = ''
    selectedFiles.value = []
  }
})

watch(createMode, (newMode) => {
  if (showCreateDialog.value && !isEditing.value && newMode === 'fromPersonal') {
    loadPersonalFiles()
  }
})

watch(showMembersDialog, (newVal) => {
  if (newVal) {
    loadMembers()
  }
})

onMounted(async () => {
  loadSquareKnowledgeBases()
  window.addEventListener('resize', handleResize)

  aiChatPosition.x = Math.max(20, window.innerWidth - 420)
  aiChatPosition.y = 80

  try {
    const res = await getMyCategories()
    kbCategories.value = res.data?.data || res.data || []
  } catch (e) { /* 分类加载失败不影响主功能 */ }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (isDragging.value) {
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
  }
})

// 成员管理相关方法
const loadMembers = async () => {
  try {
    if (!currentKnowledgeBase.value) return

    const response = await getKnowledgeBaseMembers(currentKnowledgeBase.value.id)
    const data = response.data
    members.value = data.data || []
  } catch (error) {
    console.error('加载成员列表失败:', error)
    ElMessage.error('加载成员列表失败')
    members.value = []
  }
}

const deleteMember = async (member) => {
  try {
    await ElMessageBox.confirm(
        `确定要移除成员"${member.username}"吗？`,
        '移除确认',
        {
          type: 'warning',
          confirmButtonText: '确定移除',
          cancelButtonText: '取消'
        }
    )

    deletingMemberId.value = member.userId

    await deleteKnowledgeBaseMember(member.userId, currentKnowledgeBase.value.id)
    ElMessage.success('移除成员成功')

    await loadMembers()

    // 更新知识库成员数量
    if (currentKnowledgeBase.value) {
      currentKnowledgeBase.value.memberCount = Math.max(0, currentKnowledgeBase.value.memberCount - 1)
    }

  } catch (error) {
    if (error !== 'cancel') {
      console.error('移除成员失败:', error)
      ElMessage.error('移除成员失败')
    }
  } finally {
    deletingMemberId.value = null
  }
}
</script>

<style scoped>
.shared-knowledge-base {
  height: 100%;
  max-height: calc(100vh - 40px);
  min-height: 0;
  width: 100%;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 10%, #f093fb 50%, #f5576c 90%, #4facfe 100%);
  position: relative;
  margin: 20px;
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(139, 92, 246, 0.2);
}

.top-nav {
  flex: 1;
  min-height: 0;
  padding: 0;
  width: 100%;
  max-width: 100%;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.2);
  display: flex;
  flex-direction: column;
  height: 100%;
}

.knowledge-tabs {
  height: 100%;
  min-height: 0;
  width: 100%;
  max-width: 100%;
  display: flex;
  flex-direction: column;
}

.knowledge-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 30px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(248, 250, 252, 0.9) 100%);
  border-bottom: 1px solid rgba(139, 92, 246, 0.2);
  backdrop-filter: blur(10px);
  flex-shrink: 0;
}

.knowledge-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  width: 100%;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 50%, #e2e8f0 100%);
  display: flex;
  flex-direction: column;
}

.knowledge-tabs :deep(.el-tab-pane) {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 20px;
  box-sizing: border-box;
  max-width: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.knowledge-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  transition: all 0.3s ease;
  border-radius: 12px 12px 0 0;
  margin-right: 8px;
  padding: 12px 20px;
}

.knowledge-tabs :deep(.el-tabs__item.is-active) {
  color: #8b5cf6;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.1) 0%, rgba(59, 130, 246, 0.1) 100%);
  border-bottom: 3px solid #8b5cf6;
}

.knowledge-tabs :deep(.el-tabs__item:hover) {
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.05);
}

.square-container, .my-knowledge-base-container {
  height: 100%;
  width: 100%;
  max-width: 100%;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.knowledge-base-detail {
  height: 100%;
  width: 100%;
  max-width: none;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.search-section {
  margin-bottom: 30px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(248, 250, 252, 0.9) 100%);
  border-radius: 20px;
  padding: 30px;
  box-shadow: 0 15px 40px rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
  flex-shrink: 0;
}

.search-header {
  text-align: center;
  margin-bottom: 24px;
}

.search-header h3 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.02em;
}

.search-header p {
  margin: 0;
  color: #64748b;
  font-size: 16px;
  font-weight: 500;
}

.search-bar {
  display: flex;
  justify-content: center;
  gap: 12px;
  max-width: 600px;
  margin: 0 auto;
}

.search-input {
  flex: 1;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 16px;
  border: 2px solid rgba(139, 92, 246, 0.2);
  box-shadow: 0 6px 24px rgba(139, 92, 246, 0.1);
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
}

.search-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(139, 92, 246, 0.4);
  box-shadow: 0 8px 32px rgba(139, 92, 246, 0.2);
  transform: translateY(-1px);
}

.search-input :deep(.el-input__wrapper.is-focus) {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.2);
}

.search-bar .el-button {
  border-radius: 16px;
  padding: 10px 24px;
  font-weight: 600;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  border: none;
  box-shadow: 0 6px 24px rgba(139, 92, 246, 0.4);
  transition: all 0.3s ease;
}

.search-bar .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 36px rgba(139, 92, 246, 0.5);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  color: #2c3e50;
}

.knowledge-base-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
  overflow-y: auto;
  min-height: 0;
  max-height: calc(100vh - 400px);
  padding-right: 8px;
}

.knowledge-base-card {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-radius: 20px;
  box-shadow: 0 15px 40px rgba(139, 92, 246, 0.1);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  cursor: pointer;
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
  position: relative;
}

.knowledge-base-card:hover {
  transform: translateY(-4px) scale(1.02);
  box-shadow: 0 25px 60px rgba(139, 92, 246, 0.2);
  border-color: rgba(139, 92, 246, 0.3);
}

.knowledge-base-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.02) 0%, rgba(59, 130, 246, 0.02) 100%);
  pointer-events: none;
  z-index: 1;
}

.card-cover {
  position: relative;
  height: 160px;
  overflow: hidden;
  border-radius: 16px 16px 0 0;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.9) 0%, rgba(59, 130, 246, 0.9) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  backdrop-filter: blur(10px);
}

.knowledge-base-card:hover .card-overlay {
  opacity: 1;
}

.card-content {
  padding: 20px;
  position: relative;
  z-index: 2;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: -0.025em;
}

.card-description {
  color: #64748b;
  font-size: 14px;
  margin-bottom: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  line-height: 1.4;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #95a5a6;
}

.stats {
  display: flex;
  gap: 10px;
}

.stats span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.my-card {
  border-left: 4px solid #3b82f6;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.05) 0%, rgba(147, 197, 253, 0.05) 100%);
}

.my-card::before {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.03) 0%, rgba(147, 197, 253, 0.03) 100%);
}

.joined-card {
  border-left: 4px solid #10b981;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.05) 0%, rgba(52, 211, 153, 0.05) 100%);
}

.joined-card::before {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.03) 0%, rgba(52, 211, 153, 0.03) 100%);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  padding: 20px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-radius: 20px;
  box-shadow: 0 15px 40px rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
  flex-shrink: 0;
}

.kb-info {
  display: flex;
  gap: 16px;
}

.kb-cover {
  width: 120px;
  height: 120px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 6px 24px rgba(139, 92, 246, 0.2);
}

.kb-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.kb-meta h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: -0.025em;
}

.kb-meta p {
  margin: 0 0 12px 0;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
}

.kb-stats {
  display: flex;
  gap: 16px;
  align-items: center;
}

.kb-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.kb-content-container {
  flex: 1;
  display: flex;
  overflow: hidden;
  gap: 20px;
  min-height: 0;
}

.file-list-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-radius: 20px;
  box-shadow: 0 15px 40px rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
}

.file-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.file-count {
  color: #64748b;
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-table-wrapper {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.table-content {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.table-content :deep(.el-table) {
  border: none;
  height: 100%;
}

.table-content :deep(.el-table__body-wrapper) {
  overflow-y: auto;
  max-height: calc(100vh - 480px);
}

.table-content :deep(.el-table__header) {
  background: #f8fafc;
}

.table-content :deep(.el-table__header-wrapper) th {
  background: transparent;
  color: #374151;
  font-weight: 600;
  font-size: 13px;
  padding: 12px 8px;
}

.table-content :deep(.el-table__row) {
  transition: all 0.2s ease;
}

.table-content :deep(.el-table__row:hover) {
  background: #f1f5f9;
}

.table-content :deep(.el-table__row td) {
  border-bottom: 1px solid #f3f4f6;
  padding: 10px 8px;
}

.file-name-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.file-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.file-name {
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 0;
}

.file-entry-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.entry-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.1);
  white-space: nowrap;
}

.category-chip {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.1);
}

.file-summary {
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.file-original-name {
  font-size: 12px;
  color: #64748b;
}

.empty-state-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state {
  text-align: center;
  color: #64748b;
  padding: 40px;
}

.empty-state .el-icon {
  color: #94a3b8;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 8px 0;
  font-size: 16px;
}

.file-footer {
  padding: 12px 20px;
  border-top: 1px solid rgba(139, 92, 246, 0.1);
  flex-shrink: 0;
}

.pagination-wrapper, .stats-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-info, .file-stats-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 14px;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 30px;
  padding: 20px;
}

/* AI助手优化样式 */
.ai-chat-sidebar {
  position: fixed !important;
  width: 420px;
  height: 600px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.98) 100%);
  border-radius: 24px;
  box-shadow: 0 25px 80px rgba(139, 92, 246, 0.3);
  border: 2px solid rgba(139, 92, 246, 0.2);
  backdrop-filter: blur(30px);
  z-index: 1000;
  overflow: hidden;
  transform-origin: center center;
  transition: all 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.ai-chat-sidebar:hover {
  box-shadow: 0 35px 100px rgba(139, 92, 246, 0.4);
  transform: scale(1.02);
}

.ai-chat-header {
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  color: white;
  padding: 20px;
  cursor: move;
  border-radius: 20px 20px 0 0;
  box-shadow: 0 4px 20px rgba(139, 92, 246, 0.3);
  user-select: none;
}

.ai-chat-header.dragging {
  cursor: grabbing;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-icon {
  color: white;
  font-size: 20px;
}

.header-text h3 {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 700;
}

.header-text p {
  margin: 0;
  font-size: 12px;
  opacity: 0.9;
}

.drag-indicator {
  opacity: 0.7;
  cursor: move;
}

.close-btn {
  color: white !important;
  border: none !important;
  background: rgba(255, 255, 255, 0.2) !important;
  border-radius: 8px !important;
  transition: all 0.3s ease;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.3) !important;
  transform: scale(1.1);
}

.kb-chat-container {
  height: calc(100% - 80px);
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chat-status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: rgba(139, 92, 246, 0.05);
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
}

.status-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kb-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.chat-count {
  font-size: 12px;
  color: #64748b;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.indicator-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.status-indicator.online .indicator-dot {
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
}

.status-indicator.thinking .indicator-dot {
  background: #f59e0b;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 20px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
}

.welcome-message-modern {
  text-align: center;
  padding: 30px 20px;
}

.welcome-animation {
  position: relative;
  margin-bottom: 24px;
  display: inline-block;
}

.ai-avatar-large {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  position: relative;
  z-index: 2;
}

.pulse-rings {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.pulse-ring {
  width: 60px;
  height: 60px;
  border: 2px solid rgba(139, 92, 246, 0.3);
  border-radius: 50%;
  position: absolute;
  animation: pulse-ring 2s infinite;
}

.pulse-ring:nth-child(2) {
  animation-delay: 0.5s;
}

.pulse-ring:nth-child(3) {
  animation-delay: 1s;
}

@keyframes pulse-ring {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(2);
    opacity: 0;
  }
}

.welcome-content h4 {
  margin: 0 0 12px 0;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.welcome-content p {
  margin: 0 0 20px 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.features-list {
  display: flex;
  justify-content: space-around;
  margin-bottom: 24px;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.feature-item .el-icon {
  color: #8b5cf6;
  font-size: 20px;
}

.quick-questions-modern {
  background: rgba(255, 255, 255, 0.8);
  border-radius: 16px;
  padding: 20px;
  backdrop-filter: blur(10px);
}

.questions-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 12px;
}

.questions-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.question-card {
  background: rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.2);
  border-radius: 12px;
  padding: 12px;
  font-size: 12px;
  color: #1e293b;
  cursor: pointer;
  transition: all 0.3s ease;
}

.question-card:hover {
  background: rgba(139, 92, 246, 0.2);
  transform: translateY(-2px);
}

.message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.message.user-message {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 280px;
  flex-grow: 1;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
}

.message-route-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.route-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(139, 92, 246, 0.12);
  color: #7c3aed;
}

.route-reason {
  font-size: 12px;
  color: #64748b;
}

.message-summary-meta,
.message-sources {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sources-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

.summary-line {
  font-size: 12px;
  line-height: 1.6;
  color: #475569;
  background: rgba(148, 163, 184, 0.10);
  border-radius: 10px;
  padding: 8px 10px;
}

.source-chip {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  max-width: 100%;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #475569;
  background: rgba(59, 130, 246, 0.10);
}

.user-bubble {
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  color: white;
  margin-left: auto;
}

.ai-bubble {
  background: rgba(255, 255, 255, 0.9);
  color: #1e293b;
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(10px);
}

.message-time {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
  text-align: right;
}

.user-message .message-time {
  text-align: right;
}

.ai-message .message-time {
  text-align: left;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
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

.chat-input-container {
  padding: 16px 20px;
  border-top: 1px solid rgba(139, 92, 246, 0.1);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
}

.chat-input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-textarea :deep(.el-textarea__inner) {
  border-radius: 12px;
  border: 2px solid rgba(139, 92, 246, 0.1);
  padding: 12px;
  font-size: 14px;
  resize: none;
  transition: all 0.3s ease;
}

.chat-textarea :deep(.el-textarea__inner:focus) {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.1);
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modern-btn {
  border-radius: 12px;
  font-weight: 600;
  transition: all 0.3s ease;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.modern-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.ai-toggle-btn {
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  color: white !important;
  border: none !important;
}

.ai-toggle-btn:hover {
  background: linear-gradient(135deg, #7c3aed 0%, #2563eb 100%);
}

/* 预览对话框样式 */
.preview-container {
  width: 100%;
  height: 70vh;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
  overflow: auto;
  border-radius: 16px;
}

.text-preview-shell {
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

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.text-preview,
.markdown-preview {
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

.text-preview {
  font-size: 15px;
  font-family: "JetBrains Mono", "Consolas", "Microsoft YaHei", monospace;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3),
.markdown-preview :deep(h4) {
  color: #0f172a;
  line-height: 1.35;
  margin-top: 1.6em;
  margin-bottom: 0.7em;
}

.markdown-preview :deep(p),
.markdown-preview :deep(li),
.markdown-preview :deep(blockquote) {
  font-size: 15px;
}

.markdown-preview :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 10px 12px;
}

.markdown-preview :deep(pre) {
  padding: 12px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 6px;
}

.markdown-preview :deep(code) {
  padding: 2px 4px;
  background: #f5f7fa;
  border-radius: 4px;
}

.markdown-preview :deep(img) {
  max-width: 100%;
}

.preview-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 成员管理对话框样式 */
.members-dialog-content {
  padding: 0;
}

.members-header {
  padding: 20px;
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.05) 0%, rgba(59, 130, 246, 0.05) 100%);
  border-radius: 8px 8px 0 0;
}

.header-info h4 {
  margin: 0 0 12px 0;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: -0.025em;
}

.member-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
}

.member-stats .el-icon {
  color: #8b5cf6;
  font-size: 16px;
}

.members-list {
  padding: 0;
}

.members-list :deep(.el-table) {
  border: none;
}

.members-list :deep(.el-table__header) {
  background: #f8fafc;
}

.members-list :deep(.el-table__header-wrapper) th {
  background: transparent;
  color: #374151;
  font-weight: 600;
  font-size: 13px;
  padding: 12px 8px;
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
}

.members-list :deep(.el-table__row) {
  transition: all 0.2s ease;
}

.members-list :deep(.el-table__row:hover) {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.03) 0%, rgba(59, 130, 246, 0.03) 100%);
}

.members-list :deep(.el-table__row td) {
  border-bottom: 1px solid #f3f4f6;
  padding: 12px 8px;
}

.online-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.status-dot.online {
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
  animation: pulse-online 2s infinite;
}

.status-dot.offline {
  background: #94a3b8;
}

@keyframes pulse-online {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.2);
  }
}

.status-text {
  font-size: 12px;
  font-weight: 500;
}

.disabled-action {
  color: #cbd5e1;
  font-size: 12px;
}

/* 成员管理按钮样式优化 */
.modern-btn:has(.el-icon) {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 响应式优化 */
@media (max-width: 1200px) {
  .ai-chat-sidebar {
    width: 380px;
    height: 500px;
  }

  .knowledge-base-grid {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
  }
}

@media (max-width: 768px) {
  .ai-chat-sidebar {
    width: calc(100vw - 40px);
    height: calc(100vh - 40px);
    left: 20px !important;
    top: 20px !important;
  }

  .knowledge-base-grid {
    grid-template-columns: 1fr;
  }

  .kb-content-container {
    flex-direction: column;
  }

  .detail-header {
    flex-direction: column;
    gap: 16px;
  }

  .kb-info {
    flex-direction: column;
  }

  .kb-actions {
    width: 100%;
    justify-content: center;
  }
}
</style>
