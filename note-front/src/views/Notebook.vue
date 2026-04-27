<template>
  <div class="main-container">
    <!-- 顶部导航栏 -->
    <div class="top-nav">
      <div class="nav-left">
        <h2>我的笔记本</h2>
      </div>
      <div class="nav-right">
        <div class="user-info-nav">
          <el-avatar :size="32" :src="userStore.userInfo?.avatarUrl">
            {{ userStore.userInfo?.username?.charAt(0) }}
          </el-avatar>
          <span class="username">{{ userStore.userInfo?.username || userStore.userInfo?.phone }}</span>
          <el-dropdown @command="handleUserCommand">
            <el-icon class="user-menu"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="changePassword">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <!-- Tab页面 -->
    <div class="tab-container">
      <el-tabs v-model="activeTab" class="main-tabs">
        <!-- 笔记本Tab -->
        <el-tab-pane label="笔记本" name="notebook">
          <div class="notebook-container" :class="notebookContainerClass">
            <div class="left-dock">
            <div class="sidebar-wrapper" :class="sidebarWrapperClass">
              <button
                v-if="sidebarCollapsed && !sidebarFloatingOpen"
                class="floating-trigger sidebar-trigger"
                @click="openSidebarFloating"
                title="展开笔记本侧栏"
              >
                <el-icon><Expand /></el-icon>
                <span class="trigger-text">目录模块</span>
              </button>
              <!-- 左侧笔记本列表 -->
              <div class="sidebar" :class="{ collapsed: sidebarCollapsed, 'floating-open': sidebarFloatingOpen }">
                <div class="sidebar-toggle" @click="toggleSidebar">
                  <el-icon>
                    <Fold v-if="!sidebarCollapsed || sidebarFloatingOpen" />
                    <Expand v-else />
                  </el-icon>
                </div>

                <div class="notebook-section">
                  <div class="section-header">
                    <span>笔记本</span>
                    <el-button
                        type="text"
                        size="small"
                        @click="openCreateNotebookDialog"
                        title="新建笔记本"
                    >
                      <el-icon><Plus /></el-icon>
                    </el-button>
                  </div>

                  <div class="sidebar-panel">
                    <div class="panel-header" @click="filterPanelCollapsed = !filterPanelCollapsed">
                      <span>Filters</span>
                      <el-icon>
                        <ArrowDown v-if="!filterPanelCollapsed" />
                        <ArrowRight v-else />
                      </el-icon>
                    </div>
                    <div v-show="!filterPanelCollapsed" class="notebook-filter">
                    <div
                        class="filter-item"
                        :class="{ active: currentFilter === 'all' }"
                        @click="setFilter('all')"
                    >
                      <el-icon><Folder /></el-icon>
                      <span>全部</span>
                      <span class="count">{{ totalNoteCount }}</span>
                    </div>

                    <div
                        class="filter-item"
                        :class="{ active: currentFilter === 'recent' }"
                        @click="setFilter('recent')"
                    >
                      <el-icon><Clock /></el-icon>
                      <span>过去7天</span>
                      <span class="count">{{ recentNoteCount }}</span>
                    </div>
                    </div>
                  </div>

                  <div class="sidebar-panel">
                    <div class="panel-header" @click="notebookPanelCollapsed = !notebookPanelCollapsed">
                      <span>Notebooks</span>
                      <el-icon>
                        <ArrowDown v-if="!notebookPanelCollapsed" />
                        <ArrowRight v-else />
                      </el-icon>
                    </div>
                    <div v-show="!notebookPanelCollapsed" class="notebook-list">
                      <div
                        v-for="notebook in notebookStore.notebooks"
                        :key="notebook.id"
                        class="notebook-item"
                        :class="{ active: currentNotebook?.id === notebook.id }"
                        @click="selectNotebook(notebook)"
                    >
                      <div class="notebook-info">
                        <div class="notebook-name">{{ notebook.name }}</div>
                        <div class="notebook-count">{{ notebook.noteCount }} 篇笔记</div>
                        <el-tag v-if="notebook.categoryName" size="small" type="info" style="margin-top:2px">{{ notebook.categoryName }}</el-tag>
                      </div>
                      <el-dropdown @command="(command) => handleNotebookCommand(command, notebook)" @click.stop>
                        <el-icon class="notebook-menu" @click.stop><MoreFilled /></el-icon>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="edit">编辑</el-dropdown-item>
                            <el-dropdown-item command="delete">删除</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <!-- 中间笔记列表 -->
            <div class="note-list-wrapper" :class="noteListWrapperClass">
              <button
                v-if="noteListCollapsed && !noteListFloatingOpen"
                class="floating-trigger note-list-trigger"
                @click="openNoteListFloating"
                title="展开笔记列表"
              >
                <el-icon><Expand /></el-icon>
                <span class="trigger-text">笔记模块</span>
              </button>
              <div class="note-list" :class="{ collapsed: noteListCollapsed, 'floating-open': noteListFloatingOpen }">
              <div class="note-list-toggle" @click="toggleNoteList">
                <el-icon>
                  <Fold v-if="!noteListCollapsed || noteListFloatingOpen" />
                  <Expand v-else />
                </el-icon>
              </div>
              <div class="note-list-header">
                <div class="header-left">
                  <h3>{{ getListTitle() }}</h3>
                  <span class="note-count">{{ filteredNotes.length }} 篇笔记</span>
                </div>
                <div class="header-right">
                  <el-input
                      v-model="searchKeyword"
                      placeholder="搜索笔记..."
                      :prefix-icon="Search"
                      size="small"
                      clearable
                      style="width: 200px"
                  />
                  <el-button
                      type="success"
                      @click="triggerImportMd"
                      size="small"
                  >
                    <el-icon><Upload /></el-icon>
                    导入MD
                  </el-button>
                  <input
                      ref="mdFileInput"
                      type="file"
                      accept=".md"
                      style="display:none"
                      @change="handleImportMd"
                  />
                  <el-button
                      type="primary"
                      @click="handleCreateNote"
                      size="small"
                  >
                    <el-icon><Plus /></el-icon>
                    新建笔记
                  </el-button>
                </div>
              </div>

              <div class="note-list-content">
                <div
                    v-for="note in filteredNotes"
                    :key="note.id"
                    class="note-item"
                    :class="{ active: currentNote?.id === note.id }"
                    @click="selectNote(note)"
                >
                  <div class="note-header">
                    <div class="note-title">
                      <el-icon v-if="note.isPinned" class="pin-icon"><Star /></el-icon>
                      {{ note.title || '无标题' }}
                    </div>
                    <div class="note-time">{{ formatTime(note.updateTime) }}</div>
                  </div>

                  <div class="note-preview">
                    {{ getPreview(note.contentMd, note.title) }}
                  </div>

                  <div class="note-footer">
                    <div class="note-tags">
                      <el-tag
                          v-for="tag in note.tags"
                          :key="tag"
                          size="small"
                          type="info"
                      >
                        {{ tag }}
                      </el-tag>
                    </div>
                    <div class="note-stats">
                      <span>{{ note.wordCount }} 字</span>
                      <span>{{ note.viewCount }} 次阅读</span>
                    </div>
                  </div>
                </div>

                <div v-if="filteredNotes.length === 0" class="empty-state">
                  <el-icon size="64"><DocumentAdd /></el-icon>
                  <p>暂无笔记</p>
                  <p>点击"新建笔记"开始记录想法</p>
                </div>
              </div>
              </div>
            </div>
            </div>

            <!-- 右侧笔记编辑区域 -->
            <div class="note-editor" v-if="currentNote">
              <div class="editor-header">
                <el-input
                    v-model="currentNote.title"
                    placeholder="请输入笔记标题..."
                    size="large"
                    class="title-input"
                    @blur="autoSave"
                />
                <div class="editor-actions">
                  <el-button
                      @click="togglePin"
                      :type="currentNote.isPinned ? 'primary' : 'default'"
                      size="small"
                  >
                    <el-icon><Star /></el-icon>
                    {{ currentNote.isPinned ? '取消置顶' : '置顶' }}
                  </el-button>
                  <el-button @click="handleAddToKnowledgeBase" type="success" size="small">
                    <el-icon><DocumentAdd /></el-icon>
                    添加到知识库
                  </el-button>
                  <el-button @click="handleExportMd" type="info" size="small">
                    <el-icon><Download /></el-icon>
                    导出MD
                  </el-button>
                  <el-button @click="handleOptimizeFormat" type="warning" size="small" :loading="aiOptimizing">
                    <el-icon><MagicStick /></el-icon>
                    {{ aiOptimizing ? `${optimizeTaskMeta.stageLabel || 'AI优化中'} ${optimizeProgress}%` : 'AI优化格式' }}
                  </el-button>
                  <div
                    v-if="aiOptimizing"
                    style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:12px;color:#909399"
                  >
                    <span v-if="optimizeTaskMeta.totalChunks > 0">
                      {{ optimizeTaskMeta.completedChunks }}/{{ optimizeTaskMeta.totalChunks }} 段
                    </span>
                    <span>{{ optimizeTaskMeta.stageLabel || '处理中' }}</span>
                  </div>
                  <el-button v-if="isDefaultAdmin" @click="handleNoteToBlog" type="warning" size="small">
                    发布为博客
                  </el-button>
                  <el-button @click="saveNote" type="primary" size="small">
                    保存
                  </el-button>
                  <el-button @click="deleteCurrentNote" type="danger" size="small">
                    删除
                  </el-button>
                </div>
              </div>

              <div class="editor-content">
                <v-md-editor
                    v-model="currentNote.contentMd"
                    :height="editorHeight"
                    placeholder="开始写作..."
                    @change="onEditorChange"
                    @blur="autoSave"
                />
              </div>

              <div class="editor-footer">
                <div class="tags-section">
                  <span>标签：</span>
                  <el-tag
                      v-for="tag in currentNote.tags"
                      :key="tag"
                      closable
                      @close="removeTag(tag)"
                  >
                    {{ tag }}
                  </el-tag>
                  <el-input
                      v-if="showTagInput"
                      v-model="newTag"
                      @keyup.enter="addTag"
                      @blur="addTag"
                      size="small"
                      style="width: 100px"
                  />
                  <el-button
                      v-else
                      @click="showTagInput = true"
                      size="small"
                      type="text"
                  >
                    + 添加标签
                  </el-button>
                </div>

                <div class="note-info">
                  <span>字数：{{ currentNote.wordCount }}</span>
                  <span>最后编辑：{{ formatTime(currentNote.updateTime) }}</span>
                </div>
              </div>
            </div>

            <div v-else class="empty-editor">
              <el-icon size="64"><Edit /></el-icon>
              <p>选择一篇笔记开始编辑</p>
            </div>
          </div>
        </el-tab-pane>

        <!-- 知识库Tab -->
        <el-tab-pane label="知识库" name="documents">
          <DocumentManager />
        </el-tab-pane>

        <!-- AI对话Tab -->
        <el-tab-pane label="💬 AI对话" name="chat">
          <ChatDialog />
        </el-tab-pane>

        <!-- 共享知识库Tab - 已停用 -->
        <el-tab-pane label="共享知识库" name="sharedKnowledgeBase">
          <SharedKnowledgeBase />
        </el-tab-pane>
        <el-tab-pane v-if="isDefaultAdmin" label="📝 个人博客" name="blog">
          <BlogManager />
        </el-tab-pane>
        <el-tab-pane v-if="isDefaultAdmin" label="⚙️ AI设置" name="aiConfig">
          <AiConfigPanel />
        </el-tab-pane>
        <el-tab-pane style="display: none" >
        </el-tab-pane>
      </el-tabs>

    </div>

    <!-- 个人信息对话框 -->
    <el-dialog v-model="showProfileDialog" title="个人信息" width="520px">
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="90px">
        <el-form-item label="头像">
          <div class="profile-avatar-editor">
            <el-avatar :size="56" :src="profileForm.avatarUrl">
              {{ profileForm.username?.charAt(0) || 'U' }}
            </el-avatar>
            <el-upload
              accept="image/*"
              :show-file-list="false"
              :http-request="handleProfileAvatarUpload"
            >
              <el-button :loading="avatarUploading">上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input :model-value="userStore.userInfo?.phone || ''" disabled />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="profileForm.username" maxlength="50" show-word-limit placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="profileForm.realName" maxlength="50" show-word-limit placeholder="可选" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="profileForm.gender" placeholder="请选择" style="width: 100%">
            <el-option label="未设置" :value="0" />
            <el-option label="男" :value="1" />
            <el-option label="女" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="生日" prop="birthDate">
          <el-date-picker
            v-model="profileForm.birthDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择生日"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="工龄" prop="workYears">
          <el-input-number v-model="profileForm.workYears" :min="0" :max="60" />
        </el-form-item>
        <el-form-item label="学历" prop="education">
          <el-select v-model="profileForm.education" clearable placeholder="请选择" style="width: 100%">
            <el-option label="高中" :value="1" />
            <el-option label="专科" :value="2" />
            <el-option label="本科" :value="3" />
            <el-option label="硕士" :value="4" />
            <el-option label="博士" :value="5" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProfileDialog = false">取消</el-button>
        <el-button type="primary" :loading="profileSubmitting" @click="handleSaveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="showChangePassword" title="修改密码" width="400px">
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6-32位，包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showChangePassword = false">取消</el-button>
        <el-button type="primary" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>

    <!-- 创建笔记本对话框 -->
    <el-dialog
        v-model="showCreateNotebook"
        :title="notebookDialogTitle"
        width="400px"
        @closed="resetNotebookDialog"
    >
      <el-form :model="notebookForm" :rules="notebookRules" ref="notebookFormRef">
        <el-form-item label="笔记本名称" prop="name">
          <el-input v-model="notebookForm.name" placeholder="请输入笔记本名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
              v-model="notebookForm.description"
              type="textarea"
              :rows="3"
              placeholder="请输入描述"
          />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="notebookForm.categoryId" placeholder="选择已有分类" clearable filterable style="width:100%">
            <el-option v-for="cat in blogCategories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
          <el-input
            v-model="notebookNewCategoryName"
            placeholder="或输入新分类名称，提交时自动创建"
            clearable
            style="margin-top:8px"
          />
          <div style="font-size:12px;color:#999;margin-top:2px">已有分类从下拉选择，新分类在输入框里填写后会自动写入数据库</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateNotebook = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitNotebook">{{ notebookDialogConfirmText }}</el-button>
      </template>
    </el-dialog>

    <!-- 博客编辑弹窗 -->
    <BlogEditorDialog
        v-model="showBlogEditor"
        :note-title="blogEditorNoteTitle"
        :note-content="blogEditorNoteContent"
        @saved="showBlogEditor = false"
    />

    <UploadTaskPanel :tasks="uploadTasks" title="上传任务" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Plus,
  Folder,
  Clock,
  MoreFilled,
  Search,
  Star,
  DocumentAdd,
  Edit,
  Fold,
  Expand,
  ArrowRight,
  ArrowDown,
  Download,
  Upload,
  MagicStick
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore, useNotebookStore } from '@/store'
import {
  getNotebooks,
  createNotebook,
  updateNotebook,
  deleteNotebook,
  getNotes,
  getNote,
  createNote as createNoteApi,
  updateNote,
  deleteNote,
  toggleNotePin,
  addNoteToKnowledgeBase,
  importNoteFromMarkdown,
  exportNoteToMarkdown,
  submitOptimizeTask,
  getOptimizeTask,
  deleteOptimizeTask,
  logout,
  changePassword,
  uploadFile,
  updateUserInfo
} from '@/api'
import { getMyCategories, createCategory } from '@/api/blog'
import DocumentManager from '@/components/DocumentManager.vue'
import ChatDialog from '@/components/ChatDialog.vue'
import SharedKnowledgeBase from '@/components/SharedKnowledgeBase.vue'
import BlogManager from '@/components/BlogManager.vue'
import BlogEditorDialog from '@/components/BlogEditorDialog.vue'
import AiConfigPanel from '@/components/AiConfigPanel.vue'
import UploadTaskPanel from '@/components/UploadTaskPanel.vue'
import {
  MAX_UPLOAD_SIZE_MB,
  createUploadTask,
  updateUploadTaskProgress,
  markUploadTaskSuccess,
  markUploadTaskError,
  scheduleUploadTaskCleanup
} from '@/utils/uploadProgress'

const router = useRouter()
const userStore = useUserStore()
const notebookStore = useNotebookStore()
const isDefaultAdmin = computed(() => userStore.isDefaultAdmin)

// 新增Tab相关数据
const activeTab = ref('notebook')

// 响应式数据
const currentFilter = ref('all')
const currentNotebook = ref(null)
const currentNote = ref(null)
const searchKeyword = ref('')
const showCreateNotebook = ref(false)
const editingNotebookId = ref(null)
const showTagInput = ref(false)
const newTag = ref('')
const sidebarCollapsed = ref(false)
const noteListCollapsed = ref(false)
const sidebarFloatingOpen = ref(false)
const noteListFloatingOpen = ref(false)
const filterPanelCollapsed = ref(false)
const notebookPanelCollapsed = ref(false)
const showBlogEditor = ref(false)
const blogEditorNoteTitle = ref('')
const blogEditorNoteContent = ref('')
const aiOptimizing = ref(false)
const optimizeTaskId = ref('')
const optimizeTimer = ref(null)
const mdFileInput = ref(null)
const uploadTasks = ref([])

// 个人信息
const showProfileDialog = ref(false)
const profileSubmitting = ref(false)
const avatarUploading = ref(false)
const profileFormRef = ref()
const profileForm = reactive({
  username: '',
  avatarUrl: '',
  realName: '',
  gender: 0,
  birthDate: '',
  workYears: 0,
  education: null
})
const profileRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 1, max: 50, message: '用户名长度需在1到50位之间', trigger: 'blur' }
  ],
  realName: [{ max: 50, message: '真实姓名最多50字符', trigger: 'blur' }]
}

// 修改密码
const showChangePassword = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度需在6到32位之间', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }]
}

const blogCategories = ref([])
const notebookNewCategoryName = ref('')

// 表单数据
const notebookForm = reactive({
  name: '',
  description: '',
  categoryId: null
})

const notebookRules = {
  name: [
    { required: true, message: '请输入笔记本名称', trigger: 'blur' }
  ]
}

const notebookFormRef = ref()
const notebookDialogTitle = computed(() => editingNotebookId.value ? '编辑笔记本' : '创建笔记本')
const notebookDialogConfirmText = computed(() => editingNotebookId.value ? '保存' : '确定')

// 计算属性
const totalNoteCount = computed(() => {
  return notebookStore.notes.length
})

const recentNoteCount = computed(() => {
  const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
  return notebookStore.notes.filter(note =>
      new Date(note.updateTime).getTime() > sevenDaysAgo
  ).length
})

const filteredNotes = computed(() => {
  let notes = notebookStore.notes

  // 根据当前过滤器过滤
  if (currentFilter.value === 'recent') {
    const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
    notes = notes.filter(note =>
        new Date(note.updateTime).getTime() > sevenDaysAgo
    )
  }

  // 根据搜索关键词过滤
  if (searchKeyword.value) {
    notes = notes.filter(note =>
        note.title.includes(searchKeyword.value) ||
        note.contentMd.includes(searchKeyword.value)
    )
  }

  // 置顶笔记排序
  return notes.sort((a, b) => {
    if (a.isPinned && !b.isPinned) return -1
    if (!a.isPinned && b.isPinned) return 1
    return new Date(b.updateTime) - new Date(a.updateTime)
  })
})

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1366)
const isMobile = ref(windowWidth.value <= 768)
const shouldSingleFloating = computed(() => isMobile.value)
const desktopDirectExpand = computed(() => !isMobile.value)
const dockHorizontal = computed(() => !isMobile.value && !sidebarCollapsed.value && !noteListCollapsed.value)
const notebookContainerClass = computed(() => ({
  'dock-horizontal': dockHorizontal.value,
  'dock-vertical': !dockHorizontal.value
}))
const sidebarWrapperClass = computed(() => ({
  'is-collapsed': sidebarCollapsed.value,
  'is-floating-active': sidebarCollapsed.value && sidebarFloatingOpen.value
}))
const noteListWrapperClass = computed(() => ({
  'is-collapsed': noteListCollapsed.value,
  'is-floating-active': noteListCollapsed.value && noteListFloatingOpen.value
}))
const editorHeight = computed(() => (isMobile.value ? '58vh' : '500px'))

const expandSidebarInMobile = () => {
  sidebarCollapsed.value = false
  sidebarFloatingOpen.value = false
  noteListCollapsed.value = true
  noteListFloatingOpen.value = false
}

const expandNoteListInMobile = () => {
  noteListCollapsed.value = false
  noteListFloatingOpen.value = false
  sidebarCollapsed.value = true
  sidebarFloatingOpen.value = false
}

const resetNotebookForm = () => {
  notebookForm.name = ''
  notebookForm.description = ''
  notebookForm.categoryId = null
  notebookNewCategoryName.value = ''
  notebookFormRef.value?.clearValidate?.()
}

const resetNotebookDialog = () => {
  editingNotebookId.value = null
  resetNotebookForm()
}

const openCreateNotebookDialog = async () => {
  resetNotebookDialog()
  showCreateNotebook.value = true
  await nextTick()
  notebookFormRef.value?.clearValidate?.()
}

const openEditNotebookDialog = async (notebook) => {
  editingNotebookId.value = notebook.id
  notebookForm.name = notebook.name || ''
  notebookForm.description = notebook.description || ''
  notebookForm.categoryId = notebook.categoryId ?? null
  notebookNewCategoryName.value = ''
  showCreateNotebook.value = true
  await nextTick()
  notebookFormRef.value?.clearValidate?.()
}

const loadNotebookCategories = async () => {
  const catRes = await getMyCategories()
  blogCategories.value = catRes.data?.data || catRes.data || []
}

const pushUploadTask = (file, name) => {
  const task = createUploadTask(file, { name })
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

const resolveNotebookCategoryId = async (selectedCategoryId, rawCategoryName) => {
  const categoryName = typeof rawCategoryName === 'string' ? rawCategoryName.trim() : ''
  if (!categoryName) {
    return selectedCategoryId ?? null
  }

  const existingCategory = blogCategories.value.find(cat => cat.name === categoryName)
  if (existingCategory) {
    return existingCategory.id
  }

  const catRes = await createCategory({ name: categoryName })
  const newCat = catRes.data?.data || catRes.data
  await loadNotebookCategories()
  return newCat.id
}

// 方法
const setFilter = (filter) => {
  currentFilter.value = filter
  currentNotebook.value = null
  notebookStore.setCurrentNotebook(null)
  currentNote.value = null
  notebookStore.setCurrentNote(null)
  loadNotes()
}

const selectNotebook = (notebook) => {
  currentNotebook.value = notebook
  currentFilter.value = 'notebook'
  notebookStore.setCurrentNotebook(notebook)
  if (shouldSingleFloating.value) {
    sidebarFloatingOpen.value = false
  }
  loadNotes()
}

const selectNote = async (note) => {
  if (currentNote.value && currentNote.value.id === note.id) return
  if (shouldSingleFloating.value) {
    noteListFloatingOpen.value = false
  }

  try {
    // 获取完整的笔记详情
    const response = await getNote(note.id)
    const data = response.data

    // 设置当前笔记为完整数据
    // 根据API返回结构调整数据访问
    const noteData = data.data || data
    currentNote.value = { ...noteData }
    notebookStore.setCurrentNote(noteData)

    // 增加阅读次数（静默更新，不影响用户体验）
    try {
      await updateNote({
        noteId: note.id,
        title: noteData.title,
        contentMd: noteData.contentMd,
        status: noteData.status,
        isPinned: noteData.isPinned,
        tags: noteData.tags,
        viewCount: (noteData.viewCount || 0) + 1
      })
    } catch (error) {
      console.error('更新阅读次数失败:', error)
    }

  } catch (error) {
    console.error('获取笔记详情失败:', error)
    // 如果获取详情失败，使用列表中的基础信息
    currentNote.value = { ...note }
    notebookStore.setCurrentNote(note)
    ElMessage.error('获取笔记详情失败')
  }
}

const getListTitle = () => {
  if (currentFilter.value === 'all') return '全部笔记'
  if (currentFilter.value === 'recent') return '最近笔记'
  if (currentNotebook.value) return currentNotebook.value.name
  return '笔记列表'
}

const formatTime = (time) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`

  return date.toLocaleDateString()
}

const getPreview = (content, title) => {
  if (!content || content.trim() === '') {
    return title ? `${title}...` : '点击开始编辑'
  }

  // 清理markdown语法并提取纯文本
  const cleanContent = content
      .replace(/[#*`_~\[\]()]/g, '') // 移除markdown标记
      .replace(/\n+/g, ' ') // 换行符替换为空格
      .replace(/\s+/g, ' ') // 多个空格合并为一个
      .trim()

  if (!cleanContent) {
    return title ? `${title}...` : '点击开始编辑'
  }

  return cleanContent.length > 100 ?
      cleanContent.substring(0, 100) + '...' :
      cleanContent
}

const toggleSidebar = () => {
  if (isMobile.value) {
    if (!sidebarCollapsed.value) {
      sidebarCollapsed.value = true
      sidebarFloatingOpen.value = false
      return
    }
    expandSidebarInMobile()
    return
  }

  if (!sidebarCollapsed.value) {
    sidebarCollapsed.value = true
    sidebarFloatingOpen.value = false
    return
  }
  if (desktopDirectExpand.value) {
    sidebarCollapsed.value = false
    sidebarFloatingOpen.value = false
    return
  }
  if (sidebarFloatingOpen.value) {
    sidebarCollapsed.value = false
    sidebarFloatingOpen.value = false
    return
  }
  sidebarFloatingOpen.value = true
  if (shouldSingleFloating.value) {
    noteListFloatingOpen.value = false
  }
}

const toggleNoteList = () => {
  if (isMobile.value) {
    if (!noteListCollapsed.value) {
      noteListCollapsed.value = true
      noteListFloatingOpen.value = false
      return
    }
    expandNoteListInMobile()
    return
  }

  if (!noteListCollapsed.value) {
    noteListCollapsed.value = true
    noteListFloatingOpen.value = false
    return
  }
  if (desktopDirectExpand.value) {
    noteListCollapsed.value = false
    noteListFloatingOpen.value = false
    return
  }
  if (noteListFloatingOpen.value) {
    noteListCollapsed.value = false
    noteListFloatingOpen.value = false
    return
  }
  noteListFloatingOpen.value = true
  if (shouldSingleFloating.value) {
    sidebarFloatingOpen.value = false
  }
}

const openSidebarFloating = () => {
  if (isMobile.value) {
    expandSidebarInMobile()
    return
  }
  if (desktopDirectExpand.value) {
    sidebarCollapsed.value = false
    sidebarFloatingOpen.value = false
    return
  }
  sidebarFloatingOpen.value = true
  if (shouldSingleFloating.value) {
    noteListFloatingOpen.value = false
  }
}

const openNoteListFloating = () => {
  if (isMobile.value) {
    expandNoteListInMobile()
    return
  }
  if (desktopDirectExpand.value) {
    noteListCollapsed.value = false
    noteListFloatingOpen.value = false
    return
  }
  noteListFloatingOpen.value = true
  if (shouldSingleFloating.value) {
    sidebarFloatingOpen.value = false
  }
}

const handleWindowResize = () => {
  const prevMobile = isMobile.value
  windowWidth.value = window.innerWidth
  isMobile.value = windowWidth.value <= 768
  if (!prevMobile && isMobile.value) {
    // 进入窄屏默认双收缩并列
    sidebarCollapsed.value = true
    noteListCollapsed.value = true
    sidebarFloatingOpen.value = false
    noteListFloatingOpen.value = false
    return
  }
  if (isMobile.value && !sidebarCollapsed.value && !noteListCollapsed.value) {
    // 窄屏仅允许单开
    noteListCollapsed.value = true
    noteListFloatingOpen.value = false
  }
  if (!isMobile.value) {
    sidebarFloatingOpen.value = false
    noteListFloatingOpen.value = false
  }
  if (shouldSingleFloating.value && sidebarFloatingOpen.value && noteListFloatingOpen.value) {
    noteListFloatingOpen.value = false
  }
}

const onEditorChange = (text, html) => {
  if (currentNote.value) {
    currentNote.value.contentMd = text
    currentNote.value.contentHtml = html
    debouncedAutoSave()
  }
}

const handleCreateNote = async () => {
  // 如果没有选择笔记本，自动创建一个默认笔记本
  if (!currentNotebook.value && notebookStore.notebooks.length === 0) {
    try {
      const defaultNotebook = {
        name: '我的笔记本',
        description: '默认笔记本'
      }
      const response = await createNotebook(defaultNotebook)
      const data = response.data
      const notebook = data.data || data
      notebookStore.addNotebook(notebook)
      currentNotebook.value = notebook
      notebookStore.setCurrentNotebook(notebook)
    } catch (error) {
      ElMessage.error('创建默认笔记本失败')
      return
    }
  } else if (!currentNotebook.value && notebookStore.notebooks.length > 0) {
    // 如果有笔记本但没有选择，默认选择第一个
    currentNotebook.value = notebookStore.notebooks[0]
    notebookStore.setCurrentNotebook(currentNotebook.value)
  }

  try {
    const noteData = {
      notebookId: currentNotebook.value.id,
      title: '新建笔记',
      contentMd: '# 新建笔记\n\n开始记录你的想法...',
      status: 1,
      isPinned: false,
      tags: []
    }

    const response = await createNoteApi(noteData)
    const data = response.data

    // 添加到store并设置为当前笔记
    const createdNote = data.data || data
    notebookStore.addNote(createdNote)
    currentNote.value = { ...createdNote }
    notebookStore.setCurrentNote(createdNote)

    // 重新加载笔记列表以确保同步
    await loadNotes()

    ElMessage.success('笔记创建成功')

  } catch (error) {
    console.error('创建笔记失败:', error)
    ElMessage.error('创建笔记失败')
  }
}

const saveNote = async () => {
  if (!currentNote.value) return

  try {
    const noteData = {
      noteId: currentNote.value.id,
      title: currentNote.value.title || '无标题',
      contentMd: currentNote.value.contentMd || '',
      tags: currentNote.value.tags || [],
      status: 1,
      isPinned: currentNote.value.isPinned || false
    }

    const response = await updateNote(noteData)
    const data = response.data

    // 更新当前笔记和store中的数据
    const updatedNote = data.data || data
    Object.assign(currentNote.value, updatedNote)
    notebookStore.updateNote(currentNote.value.id, updatedNote)

    // 重新加载笔记列表以确保同步
    await loadNotes()

    ElMessage.success('保存成功')

  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  }
}

const autoSave = async () => {
  if (!currentNote.value || !currentNote.value.id) return

  try {
    const noteData = {
      noteId: currentNote.value.id,
      title: currentNote.value.title || '无标题',
      contentMd: currentNote.value.contentMd || '',
      tags: currentNote.value.tags || [],
      status: 1,
      isPinned: currentNote.value.isPinned || false
    }

    const response = await updateNote(noteData)
    const data = response.data

    // 更新当前笔记的数据
    const updatedNote = data.data || data
    Object.assign(currentNote.value, updatedNote)
    notebookStore.updateNote(currentNote.value.id, updatedNote)

    console.log('自动保存成功')
  } catch (error) {
    console.error('自动保存失败:', error)
  }
}

const togglePin = async () => {
  if (!currentNote.value) return

  try {
    await toggleNotePin(currentNote.value.id)
    currentNote.value.isPinned = !currentNote.value.isPinned

    // 更新笔记的置顶状态
    await updateNote({
      noteId: currentNote.value.id,
      title: currentNote.value.title,
      contentMd: currentNote.value.contentMd,
      status: currentNote.value.status,
      isPinned: currentNote.value.isPinned,
      tags: currentNote.value.tags
    })

    notebookStore.updateNote(currentNote.value.id, { isPinned: currentNote.value.isPinned })

    // 重新加载笔记列表以确保同步
    await loadNotes()

    ElMessage.success(currentNote.value.isPinned ? '置顶成功' : '取消置顶成功')

  } catch (error) {
    console.error('置顶操作失败:', error)
    ElMessage.error('置顶操作失败')
  }
}

const deleteCurrentNote = async () => {
  if (!currentNote.value) return

  try {
    await ElMessageBox.confirm('确定删除这篇笔记吗？', '提示', {
      type: 'warning'
    })

    await deleteNote(currentNote.value.id)

    notebookStore.removeNote(currentNote.value.id)
    currentNote.value = null

    ElMessage.success('删除成功')

  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

const addTag = () => {
  if (!newTag.value.trim()) {
    showTagInput.value = false
    return
  }

  if (!currentNote.value.tags.includes(newTag.value)) {
    currentNote.value.tags.push(newTag.value)
  }

  newTag.value = ''
  showTagInput.value = false
}

const removeTag = (tag) => {
  const index = currentNote.value.tags.indexOf(tag)
  if (index > -1) {
    currentNote.value.tags.splice(index, 1)
  }
}

const handleAddToKnowledgeBase = async () => {
  if (!currentNote.value || !currentNote.value.id) {
    ElMessage.warning('请先选择一篇笔记')
    return
  }

  try {
    const response = await addNoteToKnowledgeBase(currentNote.value.id)
    const data = response.data

    if (data.code === 0) {
      const result = data.data
      ElMessage.success(`笔记“${result.noteTitle}”已成功添加到个人知识库`)

      // 显示处理结果详情
      setTimeout(() => {
        ElMessage.info(`文件: ${result.fileName}，大小: ${(result.fileSize / 1024 / 1024).toFixed(2)}MB，文档数: ${result.processResult.documentCount}`)
      }, 1000)
    } else {
      ElMessage.error(data.message || '添加到知识库失败')
    }

  } catch (error) {
    console.error('添加到知识库失败:', error)
    ElMessage.error('添加到知识库失败')
  }
}

const handleNoteToBlog = () => {
  if (!isDefaultAdmin.value) {
    ElMessage.error('仅默认管理员可以使用博客发布功能')
    return
  }
  if (!currentNote.value || !currentNote.value.id) {
    ElMessage.warning('请先选择一篇笔记')
    return
  }
  blogEditorNoteTitle.value = currentNote.value.title || ''
  blogEditorNoteContent.value = currentNote.value.contentMd || ''
  showBlogEditor.value = true
}

const triggerImportMd = () => {
  if (!currentNotebook.value && notebookStore.notebooks.length === 0) {
    ElMessage.warning('请先创建或选择一个笔记本')
    return
  }
  mdFileInput.value.click()
}

const handleImportMd = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.md')) {
    ElMessage.error('仅支持导入 .md 文件')
    event.target.value = ''
    return
  }
  if (file.size > MAX_UPLOAD_SIZE_MB * 1024 * 1024) {
    ElMessage.error(`文件大小不能超过 ${MAX_UPLOAD_SIZE_MB}MB`)
    event.target.value = ''
    return
  }
  // 确保有选中的笔记本
  if (!currentNotebook.value) {
    currentNotebook.value = notebookStore.notebooks[0]
    notebookStore.setCurrentNotebook(currentNotebook.value)
  }
  const uploadTask = pushUploadTask(file, `Markdown 导入 · ${file.name}`)
  try {
    const response = await importNoteFromMarkdown(currentNotebook.value.id, file, {
      onUploadProgress: (progressEvent) => updateUploadTaskProgress(uploadTask, progressEvent)
    })
    const data = response.data
    const createdNote = data.data || data
    notebookStore.addNote(createdNote)
    currentNote.value = { ...createdNote }
    notebookStore.setCurrentNote(createdNote)
    await loadNotes()
    finishUploadTask(uploadTask)
    ElMessage.success('MD文件导入成功')
  } catch (error) {
    console.error('导入MD文件失败:', error)
    finishUploadTask(uploadTask, error)
    ElMessage.error('导入MD文件失败')
  } finally {
    event.target.value = ''
  }
}

const handleExportMd = async () => {
  if (!currentNote.value || !currentNote.value.id) {
    ElMessage.warning('请先选择一篇笔记')
    return
  }
  try {
    const response = await exportNoteToMarkdown(currentNote.value.id)
    const blob = new Blob([response.data], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = (currentNote.value.title || 'note') + '.md'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出MD失败:', error)
    ElMessage.error('导出MD失败')
  }
}

const optimizeProgress = ref(0)
const optimizeTaskMeta = reactive({
  totalChunks: 0,
  completedChunks: 0,
  stage: 'pending',
  stageLabel: '等待开始'
})

const clearOptimizePolling = () => {
  if (optimizeTimer.value) {
    clearTimeout(optimizeTimer.value)
    optimizeTimer.value = null
  }
}

const handleOptimizeFormat = async () => {
  if (!currentNote.value || !currentNote.value.id) {
    ElMessage.warning('请先选择一篇笔记')
    return
  }
  try {
    await ElMessageBox.confirm('AI将优化当前笔记的格式，原内容将被替换，是否继续？', '提示', { type: 'warning' })
    clearOptimizePolling()
    aiOptimizing.value = true
    optimizeTaskId.value = ''
    optimizeProgress.value = 0
    optimizeTaskMeta.totalChunks = 0
    optimizeTaskMeta.completedChunks = 0
    optimizeTaskMeta.stage = 'pending'
    optimizeTaskMeta.stageLabel = '等待开始'
    ElMessage.info('AI优化任务已提交，正在处理中...')

    const noteId = currentNote.value.id

    // 1. 提交任务获取taskId
    const submitRes = await submitOptimizeTask(noteId)
    const taskId = submitRes.data?.data
    if (!taskId || typeof taskId !== 'string') {
      throw new Error('提交优化任务失败，未获取到任务ID')
    }
    optimizeTaskId.value = taskId

    // 2. 开始轮询
    startOptimizePolling(taskId, noteId)
  } catch (error) {
    if (error !== 'cancel') {
      console.error('AI优化失败:', error)
      ElMessage.error('AI优化失败: ' + (error.message || '未知错误'))
    }
    clearOptimizePolling()
    optimizeTaskId.value = ''
    aiOptimizing.value = false
  }
}

const startOptimizePolling = (taskId, noteId) => {
  clearOptimizePolling()

  const deadline = Date.now() + 300000 // 5分钟超时

  const poll = async () => {
    if (optimizeTaskId.value !== taskId) {
      return
    }

    if (Date.now() > deadline) {
      clearOptimizePolling()
      optimizeTaskId.value = ''
      aiOptimizing.value = false
      ElMessage.error('AI优化超时，请稍后重试')
      return
    }

    try {
      const res = await getOptimizeTask(taskId)
      const data = res.data?.data
      if (!data || typeof data.status !== 'string') {
        clearOptimizePolling()
        optimizeTaskId.value = ''
        aiOptimizing.value = false
        ElMessage.error('任务状态异常')
        return
      }

      optimizeProgress.value = Number(data.progress || 0)
      optimizeTaskMeta.totalChunks = Number(data.totalChunks || 0)
      optimizeTaskMeta.completedChunks = Number(data.completedChunks || 0)
      optimizeTaskMeta.stage = data.stage || 'running'
      optimizeTaskMeta.stageLabel = data.stageLabel || 'AI优化中'

      if (data.status === 'pending' || data.status === 'running') {
        optimizeTimer.value = setTimeout(() => {
          void poll()
        }, 2000)
        return
      }

      // done 或 error，停止轮询
      clearOptimizePolling()
      optimizeTaskId.value = ''
      aiOptimizing.value = false

      if (data.status === 'done') {
        optimizeProgress.value = 100
        if (currentNote.value && currentNote.value.id === noteId) {
          const response = await getNote(noteId)
          const noteData = response.data?.data || response.data
          currentNote.value = { ...noteData }
          notebookStore.setCurrentNote(noteData)
          await loadNotes()
          ElMessage.success('AI格式优化完成')
        } else {
          ElMessage.warning('AI优化完成，但当前笔记已切换，请重新打开笔记查看最新内容')
        }
      } else if (data.status === 'error') {
        ElMessage.error('AI优化失败: ' + (data.error || '未知错误'))
      }

      // 3. 结果拿到后删除任务
      try { await deleteOptimizeTask(taskId) } catch (e) { /* 忽略清理失败 */ }

    } catch (e) {
      // 任务不存在（可能已过期）时停止轮询
      clearOptimizePolling()
      optimizeTaskId.value = ''
      aiOptimizing.value = false
      ElMessage.error('AI优化失败: ' + (e.message || '未知错误'))
    }
  }

  void poll()
}

const handleSubmitNotebook = async () => {
  if (!notebookFormRef.value) return

  try {
    await notebookFormRef.value.validate()
    const notebookId = editingNotebookId.value
    const actionText = notebookId ? '更新' : '创建'
    const payload = {
      name: notebookForm.name.trim(),
      description: notebookForm.description?.trim() || '',
      categoryId: await resolveNotebookCategoryId(notebookForm.categoryId, notebookNewCategoryName.value)
    }

    if (notebookId) {
      const response = await updateNotebook(notebookId, payload)
      const data = response.data
      const updatedNotebook = data.data || data

      notebookStore.updateNotebook(notebookId, updatedNotebook)
      if (currentNotebook.value?.id === notebookId) {
        currentNotebook.value = { ...currentNotebook.value, ...updatedNotebook }
        notebookStore.setCurrentNotebook(currentNotebook.value)
      }

      showCreateNotebook.value = false
      ElMessage.success('笔记本更新成功')
      return
    }

    const response = await createNotebook(payload)
    const data = response.data

    const notebookData = data.data || data
    notebookStore.addNotebook(notebookData)
    currentNotebook.value = notebookData
    notebookStore.setCurrentNotebook(notebookData)
    currentFilter.value = 'notebook'

    await loadNotes()

    showCreateNotebook.value = false
    ElMessage.success('笔记本创建成功')

  } catch (error) {
    if (error !== false) {
      const actionText = editingNotebookId.value ? '更新' : '创建'
      console.error(`${actionText}笔记本失败:`, error)
      ElMessage.error(`${actionText}笔记本失败`)
    }
  }
}

const handleNotebookCommand = async (command, notebook) => {
  if (command === 'edit') {
    await openEditNotebookDialog(notebook)
    return
  }

  if (command === 'delete') {
    try {
      await ElMessageBox.confirm('确定删除这个笔记本吗？', '提示', {
        type: 'warning'
      })

      await deleteNotebook(notebook.id)

      notebookStore.removeNotebook(notebook.id)

      if (currentNotebook.value?.id === notebook.id) {
        const nextNotebook = notebookStore.notebooks[0] || null
        currentNotebook.value = nextNotebook
        notebookStore.setCurrentNotebook(nextNotebook)
        currentFilter.value = nextNotebook ? 'notebook' : 'all'
      }

      if (currentNote.value?.notebookId === notebook.id || currentNotebook.value == null) {
        currentNote.value = null
        notebookStore.setCurrentNote(null)
      }

      await loadNotes()

      ElMessage.success('删除成功')

    } catch (error) {
      if (error !== 'cancel') {
        console.error('删除失败:', error)
      }
    }
  }
}

const normalizeBirthDate = (value) => {
  if (!value) return ''
  if (typeof value === 'string') return value.slice(0, 10)
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${y}-${m}-${day}`
}

const fillProfileForm = () => {
  const info = userStore.userInfo || {}
  profileForm.username = info.username || ''
  profileForm.avatarUrl = info.avatarUrl || ''
  profileForm.realName = info.realName || ''
  profileForm.gender = info.gender ?? 0
  profileForm.birthDate = normalizeBirthDate(info.birthDate)
  profileForm.workYears = info.workYears ?? 0
  profileForm.education = info.education ?? null
}

const handleProfileAvatarUpload = async (options) => {
  const file = options.file
  if (!file) return
  if (!file.type?.startsWith('image/')) {
    ElMessage.error('仅支持上传图片')
    options.onError?.(new Error('invalid file type'))
    return
  }
  if (file.size > MAX_UPLOAD_SIZE_MB * 1024 * 1024) {
    ElMessage.error(`图片大小不能超过${MAX_UPLOAD_SIZE_MB}MB`)
    options.onError?.(new Error('file too large'))
    return
  }

  const uploadTask = pushUploadTask(file, `头像上传 · ${file.name}`)
  avatarUploading.value = true
  try {
    const response = await uploadFile(file, {
      onUploadProgress: (progressEvent) => updateUploadTaskProgress(uploadTask, progressEvent)
    })
    const fileInfo = response.data?.data
    if (!fileInfo?.fileUrl) {
      throw new Error('上传结果缺少文件地址')
    }
    profileForm.avatarUrl = fileInfo.fileUrl
    finishUploadTask(uploadTask)
    ElMessage.success('头像上传成功')
    options.onSuccess?.(fileInfo)
  } catch (error) {
    console.error('头像上传失败:', error)
    finishUploadTask(uploadTask, error)
    ElMessage.error('头像上传失败')
    options.onError?.(error)
  } finally {
    avatarUploading.value = false
  }
}

const handleSaveProfile = async () => {
  if (!profileFormRef.value) return
  try {
    await profileFormRef.value.validate()
    profileSubmitting.value = true
    const payload = {
      username: profileForm.username?.trim(),
      avatarUrl: profileForm.avatarUrl?.trim() || '',
      realName: profileForm.realName?.trim() || '',
      gender: profileForm.gender ?? 0,
      birthDate: profileForm.birthDate || '',
      workYears: profileForm.workYears ?? 0,
      education: profileForm.education ?? null
    }
    await updateUserInfo(payload)
    await userStore.fetchUserInfo()
    showProfileDialog.value = false
    ElMessage.success('个人信息已更新')
  } catch (error) {
    if (error !== false) {
      console.error('更新个人信息失败:', error)
      ElMessage.error('更新个人信息失败')
    }
  } finally {
    profileSubmitting.value = false
  }
}

const handleUserCommand = async (command) => {
  if (command === 'profile') {
    fillProfileForm()
    showProfileDialog.value = true
  } else if (command === 'changePassword') {
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    showChangePassword.value = true
  } else if (command === 'logout') {
    try {
      await logout()
      userStore.logout()
      router.push('/login')
      ElMessage.success('退出成功')
    } catch (error) {
      console.error('退出失败:', error)
    }
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      ElMessage.error('两次输入的新密码不一致')
      return
    }
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    showChangePassword.value = false
    userStore.logout()
    router.push('/login')
  } catch (error) {
    if (error !== false) {
      console.error('修改密码失败:', error)
    }
  }
}

const loadNotebooks = async () => {
  try {
    const response = await getNotebooks()
    const data = response.data
    // notebooks API返回的是 {data: [...], code: 0, message: "成功"}
    notebookStore.setNotebooks(data.data)
  } catch (error) {
    console.error('加载笔记本失败:', error)
  }
}

const loadNotes = async () => {
  try {
    const params = {}
    if (currentNotebook.value && currentFilter.value === 'notebook') {
      params.notebookId = currentNotebook.value.id
    }

    const response = await getNotes(params)
    const data = response.data
    console.log('获取笔记数据:', data) // 添加调试日志

    // notes API返回的是 {data: {data: [...], total: 2}, code: 0, message: "成功"}
    let notes = []
    if (data && data.data && Array.isArray(data.data.data)) {
      notes = data.data.data
    }

    // 确保每个笔记都有必要字段
    notes = notes.map(note => {
      const processedNote = {
        ...note,
        tags: note.tags || [],
        contentMd: note.contentMd || note.content || '', // 尝试多个可能的字段名
        title: note.title || '无标题',
        isPinned: note.isPinned || false,
        viewCount: note.viewCount || 0,
        wordCount: note.wordCount || (note.contentMd ? note.contentMd.length : 0)
      }

      console.log('处理后的笔记:', processedNote) // 添加调试日志
      return processedNote
    })

    notebookStore.setNotes(notes)
  } catch (error) {
    console.error('加载笔记失败:', error)
    notebookStore.setNotes([])
  }
}

// 防抖自动保存
let autoSaveTimer = null

const debouncedAutoSave = () => {
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
  }
  autoSaveTimer = setTimeout(() => {
    autoSave()
  }, 2000) // 2秒后自动保存
}

// 监听
watch(currentNote, (newNote) => {
  if (newNote) {
    // 计算字数
    newNote.wordCount = (newNote.contentMd || '').length
  }
}, { deep: true })

// 监听笔记内容变化，触发自动保存
watch(() => currentNote.value?.contentMd, (newContent, oldContent) => {
  if (currentNote.value && newContent !== oldContent && currentNote.value.id) {
    debouncedAutoSave()
  }
})

// 监听笔记标题变化，触发自动保存
watch(() => currentNote.value?.title, (newTitle, oldTitle) => {
  if (currentNote.value && newTitle !== oldTitle && currentNote.value.id) {
    debouncedAutoSave()
  }
})

onMounted(async () => {
  handleWindowResize()
  window.addEventListener('resize', handleWindowResize, { passive: true })
  if (isMobile.value) {
    sidebarCollapsed.value = true
    noteListCollapsed.value = true
  }
  try {
    // 加载分类
    await loadNotebookCategories()

    // 先加载笔记本
    await loadNotebooks()

    // 如果有笔记本，默认选择第一个
    if (notebookStore.notebooks.length > 0 && !currentNotebook.value) {
      currentNotebook.value = notebookStore.notebooks[0]
      notebookStore.setCurrentNotebook(currentNotebook.value)
      currentFilter.value = 'notebook'
    }

    // 再加载笔记
    await loadNotes()
  } catch (error) {
    console.error('初始化数据失败:', error)
    ElMessage.error('加载数据失败')
  }
})

onUnmounted(() => {
  // 清理自动保存定时器
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
  }
  clearOptimizePolling()
  window.removeEventListener('resize', handleWindowResize)
})
</script>

<style scoped>
.main-container {
  height: 100vh;
  max-height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #0f0c29 0%, #24243e 25%, #313862 50%, #4a5568 75%, #2d3748 100%);
}

.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(139, 92, 246, 0.2);
  flex-shrink: 0;
  box-shadow: 0 4px 20px rgba(139, 92, 246, 0.1);
}

.nav-left h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.02em;
}

.nav-right {
  display: flex;
  align-items: center;
}

.user-info-nav {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  font-weight: 600;
  color: #1e293b;
  font-size: 14px;
}

.user-menu {
  cursor: pointer;
  color: #64748b;
  transition: all 0.3s ease;
  padding: 4px;
  border-radius: 8px;
}

.user-menu:hover {
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.1);
}

.profile-avatar-editor {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tab-container {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
  margin: 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.main-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.main-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 24px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(248, 250, 252, 0.9) 100%);
  border-bottom: 1px solid rgba(139, 92, 246, 0.2);
  backdrop-filter: blur(10px);
  flex-shrink: 0;
  border-radius: 16px 16px 0 0;
}

.main-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 50%, #e2e8f0 100%);
  border-radius: 0 0 16px 16px;
}

.main-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
  padding: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.main-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  transition: all 0.3s ease;
  border-radius: 12px 12px 0 0;
  margin-right: 8px;
  padding: 14px 20px;
}

.main-tabs :deep(.el-tabs__item.is-active) {
  color: #8b5cf6;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.1) 0%, rgba(59, 130, 246, 0.1) 100%);
  border-bottom: 3px solid #8b5cf6;
}

.main-tabs :deep(.el-tabs__item:hover) {
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.05);
}

.notebook-container {
  height: 100%;
  min-height: 0;
  display: flex;
  overflow: hidden;
  gap: 20px;
  padding: 20px 30px;
  --floating-sidebar-width: 280px;
  --floating-note-width: 320px;
}

.left-dock {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  gap: 20px;
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  position: relative;
  z-index: 10;
}

.notebook-container.dock-horizontal .left-dock {
  flex-direction: row;
  gap: 20px;
}

.notebook-container.dock-vertical .left-dock {
  flex-direction: column;
  gap: 12px;
}

.sidebar-wrapper {
  width: var(--floating-sidebar-width);
  position: relative;
  display: flex;
  overflow: visible;
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  transition: width 0.28s ease;
}

.sidebar-wrapper.is-collapsed {
  width: 52px;
}

.sidebar-wrapper.is-floating-active {
  width: var(--floating-sidebar-width);
  z-index: 40;
}

.sidebar-wrapper.is-collapsed:not(.is-floating-active) {
  min-height: 58px;
  height: 58px;
}

.note-list-wrapper {
  width: var(--floating-note-width);
  position: relative;
  display: flex;
  overflow: visible;
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  transition: width 0.28s ease;
}

.note-list-wrapper.is-collapsed {
  width: 52px;
}

.note-list-wrapper.is-floating-active {
  width: var(--floating-note-width);
  z-index: 40;
}

.note-list-wrapper.is-collapsed:not(.is-floating-active) {
  min-height: 58px;
  height: 58px;
}

.floating-trigger {
  min-width: 42px;
  width: auto;
  height: 42px;
  padding: 0 12px;
  border: none;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.22);
  position: absolute;
  top: 16px;
  z-index: 360;
}

.sidebar-trigger {
  left: 4px;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
}

.note-list-trigger {
  left: 4px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.trigger-text {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.sidebar {
  width: 280px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
  transition: all 0.3s ease;
  flex-shrink: 0;
  overflow: visible;
  display: flex;
  flex-direction: column;
  position: relative;
  min-height: 0;
  height: 100%;
  max-height: 100%;
}

.sidebar.collapsed {
  width: 0;
  min-width: 0;
  height: 0;
  min-height: 0;
  opacity: 0;
  border: 0;
  box-shadow: none;
  pointer-events: none;
  overflow: hidden;
}

.sidebar.collapsed.floating-open {
  width: 100%;
  min-width: 100%;
  opacity: 1;
  border: 1px solid rgba(139, 92, 246, 0.2);
  box-shadow: 0 12px 36px rgba(15, 23, 42, 0.18);
  pointer-events: auto;
  position: relative;
  z-index: 1;
}

.sidebar-toggle {
  position: absolute;
  top: 20px;
  right: -25px;
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  cursor: pointer;
  z-index: 200;
  box-shadow: 0 6px 20px rgba(139, 92, 246, 0.4);
  transition: all 0.3s ease;
  border: 2px solid rgba(255, 255, 255, 0.9);
}

.sidebar-toggle:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(139, 92, 246, 0.4);
}

.notebook-section {
  padding: 20px;
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.sidebar-panel {
  margin-bottom: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sidebar-panel:last-of-type {
  flex: 1;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 10px;
  background: rgba(139, 92, 246, 0.08);
  color: #4c1d95;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.notebook-filter {
  margin-top: 8px;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  color: #64748b;
  margin-bottom: 4px;
}

.filter-item:hover {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}

.filter-item.active {
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3);
}

.count {
  margin-left: auto;
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
}

.notebook-list {
  flex: 1;
  overflow-y: auto;
  margin-top: 8px;
  min-height: 0;
}

.notebook-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 4px;
  border: 1px solid transparent;
}

.notebook-item:hover {
  background: rgba(139, 92, 246, 0.1);
  border-color: rgba(139, 92, 246, 0.2);
  transform: translateX(4px);
}

.notebook-item.active {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.2) 0%, rgba(59, 130, 246, 0.2) 100%);
  border-color: rgba(139, 92, 246, 0.3);
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.2);
}

.notebook-info {
  flex: 1;
}

.notebook-name {
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 2px;
  font-size: 14px;
}

.notebook-count {
  font-size: 12px;
  color: #64748b;
}

.notebook-menu {
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.notebook-menu:hover {
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.1);
}

.note-list {
  width: 320px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
  transition: all 0.3s ease;
  flex-shrink: 0;
  overflow: visible;
  display: flex;
  flex-direction: column;
  position: relative;
  min-height: 0;
  height: 100%;
  max-height: 100%;
}

.note-list.collapsed {
  width: 0;
  min-width: 0;
  height: 0;
  min-height: 0;
  opacity: 0;
  border: 0;
  box-shadow: none;
  pointer-events: none;
  overflow: hidden;
}

.note-list.collapsed.floating-open {
  width: 100%;
  min-width: 100%;
  opacity: 1;
  border: 1px solid rgba(16, 185, 129, 0.2);
  box-shadow: 0 12px 36px rgba(15, 23, 42, 0.18);
  pointer-events: auto;
  position: relative;
  z-index: 1;
}

.note-list-toggle {
  position: absolute;
  top: 20px;
  right: -25px;
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  cursor: pointer;
  z-index: 200;
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.4);
  transition: all 0.3s ease;
  border: 2px solid rgba(255, 255, 255, 0.9);
}

.note-list-toggle:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(16, 185, 129, 0.4);
}

.note-list-header {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px;
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 0;
}

.header-left h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.note-count {
  color: #64748b;
  font-size: 12px;
  background: rgba(139, 92, 246, 0.1);
  padding: 4px 8px;
  border-radius: 8px;
  font-weight: 600;
}

.header-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.note-list-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
}

.note-item {
  padding: 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 8px;
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.5);
}

.note-item:hover {
  background: rgba(139, 92, 246, 0.1);
  border-color: rgba(139, 92, 246, 0.2);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.1);
}

.note-item.active {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.2) 0%, rgba(59, 130, 246, 0.2) 100%);
  border-color: rgba(139, 92, 246, 0.3);
  box-shadow: 0 6px 16px rgba(139, 92, 246, 0.2);
}

.note-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.note-title {
  font-weight: 600;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.4;
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}

.pin-icon {
  color: #f59e0b;
  font-size: 12px;
}

.note-time {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
}

.note-preview {
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.note-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.note-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.note-stats {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: #94a3b8;
}

.note-editor {
  flex: 1;
  min-height: 0;
  position: relative;
  z-index: 1;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.editor-header {
  padding: 20px;
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
  flex-shrink: 0;
}

.title-input {
  margin-bottom: 16px;
}

.title-input :deep(.el-input__wrapper) {
  border-radius: 12px;
  border: 2px solid rgba(139, 92, 246, 0.1);
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.05);
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.9);
}

.title-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(139, 92, 246, 0.3);
  box-shadow: 0 6px 16px rgba(139, 92, 246, 0.1);
}

.title-input :deep(.el-input__wrapper.is-focus) {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.1);
}

.editor-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.editor-content {
  flex: 1;
  padding: 20px;
  overflow: auto;
}

.editor-content :deep(.v-md-editor) {
  border-radius: 12px;
  border: 2px solid rgba(139, 92, 246, 0.1);
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.05);
  overflow: hidden;
}

.editor-footer {
  padding: 20px;
  border-top: 1px solid rgba(139, 92, 246, 0.1);
  flex-shrink: 0;
}

.tags-section {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.note-info {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #64748b;
}

.empty-editor {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.1);
  backdrop-filter: blur(20px);
  color: #64748b;
  text-align: center;
}

.empty-editor .el-icon {
  color: #94a3b8;
  margin-bottom: 16px;
}

.empty-editor p {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
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

.sidebar.collapsed:not(.floating-open) .panel-header span,
.sidebar.collapsed:not(.floating-open) .notebook-filter,
.sidebar.collapsed:not(.floating-open) .notebook-list {
  display: none;
}

.sidebar.collapsed:not(.floating-open) .panel-header {
  justify-content: center;
}

.note-list.collapsed:not(.floating-open) .note-list-header,
.note-list.collapsed:not(.floating-open) .note-list-content {
  display: none;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .notebook-container {
    gap: 16px;
    padding: 16px 25px;
    --floating-sidebar-width: 240px;
    --floating-note-width: 280px;
  }

  .left-dock {
    gap: 16px;
  }

  .sidebar {
    width: 240px;
  }

  .note-list {
    width: 280px;
  }

  .sidebar.collapsed.floating-open {
    width: 240px;
    min-width: 240px;
  }

  .note-list.collapsed.floating-open {
    width: 280px;
    min-width: 280px;
  }
}

@media (max-width: 992px) {
  .notebook-container {
    gap: 12px;
    padding: 14px 16px;
    --floating-sidebar-width: 220px;
    --floating-note-width: 260px;
  }

  .left-dock {
    gap: 12px;
  }

  .sidebar {
    width: 220px;
  }

  .note-list {
    width: 260px;
  }

  .sidebar.collapsed.floating-open {
    width: 220px;
    min-width: 220px;
  }

  .note-list.collapsed.floating-open {
    width: 260px;
    min-width: 260px;
  }

  .editor-content {
    min-height: 340px;
  }
}

@media (max-width: 768px) {
  .top-nav {
    flex-direction: column;
    gap: 12px;
    padding: 12px;
  }

  .tab-container {
    margin: 10px;
  }

  .notebook-container {
    flex-direction: column;
    gap: 12px;
    padding: 12px 14px 24px;
    height: auto;
    min-height: 100%;
    overflow-y: auto;
  }

  .left-dock {
    width: 100%;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 10px;
    align-items: start;
    height: auto;
  }

  .sidebar-wrapper,
  .note-list-wrapper {
    width: 100%;
    min-width: 0;
    height: auto;
  }

  .sidebar-wrapper:not(.is-collapsed),
  .note-list-wrapper:not(.is-collapsed),
  .sidebar-wrapper.is-floating-active,
  .note-list-wrapper.is-floating-active {
    grid-column: 1 / -1;
  }

  .sidebar-wrapper.is-collapsed,
  .note-list-wrapper.is-collapsed {
    width: 100%;
    min-height: 58px;
    height: 58px;
  }

  .sidebar,
  .note-list {
    width: 100%;
    height: auto;
    max-height: 260px;
    position: relative;
    overflow: hidden;
  }

  .sidebar.collapsed.floating-open,
  .note-list.collapsed.floating-open {
    position: relative;
    left: auto;
    right: auto;
    width: 100%;
    min-width: 100%;
    max-height: 56vh;
    z-index: 1;
  }

  .floating-trigger {
    position: absolute;
    left: 8px;
    top: 8px;
    z-index: 120;
  }

  .sidebar-trigger {
    top: 8px;
  }

  .note-list-trigger {
    top: 8px;
  }

  .sidebar-toggle,
  .note-list-toggle {
    right: -20px;
    z-index: 300;
  }

  .note-editor,
  .empty-editor {
    min-height: 460px;
  }
}

/* 动画效果 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.notebook-item, .note-item {
  animation: fadeIn 0.3s ease;
}

/* 按钮悬停效果 */
.el-button {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
</style>
