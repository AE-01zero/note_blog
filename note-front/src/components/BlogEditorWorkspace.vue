<template>
  <div :class="['editor-workspace', `variant-${variant}`]" v-loading="loading">
    <div v-if="showHeader" class="workspace-header">
      <div>
        <div class="workspace-kicker">{{ isEdit ? 'Edit Post' : 'Write Post' }}</div>
        <h2>{{ isEdit ? '编辑文章' : '写新文章' }}</h2>
        <p>{{ headerDescription }}</p>
      </div>
      <div class="workspace-actions">
        <el-button class="ghost-btn" @click="emit('cancel')">{{ returnText }}</el-button>
        <el-button @click="handleSave(0)">保存草稿</el-button>
        <el-button type="primary" @click="handleSave(1)">发布文章</el-button>
      </div>
    </div>

    <div class="workspace-body">
      <section class="editor-main">
        <div class="field-card title-card">
          <div class="field-label">文章标题</div>
          <el-input
            v-model="form.title"
            placeholder="请输入文章标题"
            size="large"
            class="title-input"
          />
        </div>

        <div class="field-card">
          <div class="field-label">摘要</div>
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="3"
            placeholder="可以写一句简短摘要，作为列表和首页的预览文案"
          />
        </div>

        <div class="field-card editor-card">
          <div class="field-label">Markdown 正文</div>
          <v-md-editor
            v-model="form.contentMd"
            :height="editorHeight"
            placeholder="开始写作..."
          />
        </div>
      </section>

      <aside class="editor-sidebar">
        <section class="side-card">
          <div class="side-title">封面</div>
          <el-input v-model="form.coverUrl" placeholder="封面图片 URL" />
          <el-image v-if="form.coverUrl" :src="form.coverUrl" fit="cover" class="cover-preview" />
        </section>

        <section class="side-card">
          <div class="side-title">分类</div>
          <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
          <div class="inline-create">
            <el-input v-model="newCategoryName" placeholder="新分类" size="small" style="flex: 1" />
            <el-button size="small" @click="handleCreateCategory">添加</el-button>
          </div>
        </section>

        <section class="side-card">
          <div class="side-title">标签</div>
          <div class="tag-select">
            <el-tag
              v-for="tag in allTags"
              :key="tag.id"
              :effect="form.tagIds.includes(tag.id) ? 'dark' : 'plain'"
              size="small"
              class="clickable-tag"
              @click="toggleTag(tag.id)"
            >
              {{ tag.name }}
            </el-tag>
          </div>
          <div class="inline-create">
            <el-input v-model="newTagName" placeholder="新标签" size="small" style="flex: 1" />
            <el-button size="small" @click="handleCreateTag">添加</el-button>
          </div>
        </section>

        <section class="side-card">
          <div class="side-title">选项</div>
          <el-checkbox v-model="form.isTop">置顶文章</el-checkbox>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createBlogPost,
  updateBlogPost,
  getMyBlogPostDetail,
  publishBlogPost,
  getMyCategories,
  createCategory,
  getMyTags,
  createTag
} from '@/api/blog'
import { getNote } from '@/api/index'

const props = defineProps({
  postId: { type: [String, Number], default: null },
  noteId: { type: [String, Number], default: null },
  noteTitle: { type: String, default: '' },
  noteContent: { type: String, default: '' },
  variant: { type: String, default: 'floating' },
  showHeader: { type: Boolean, default: true },
  returnText: { type: String, default: '返回' },
  editorHeight: { type: String, default: '560px' }
})

const emit = defineEmits(['saved', 'cancel'])

const loading = ref(false)
const categories = ref([])
const allTags = ref([])
const newCategoryName = ref('')
const newTagName = ref('')

const form = reactive({
  title: '',
  summary: '',
  contentMd: '',
  coverUrl: '',
  categoryId: null,
  tagIds: [],
  isTop: false
})

const isEdit = computed(() => Boolean(props.postId))

const headerDescription = computed(() => {
  if (props.postId) return '保留原有文章字段和发布逻辑，在当前界面里直接完成编辑。'
  if (props.noteId || props.noteTitle || props.noteContent) return '已带入笔记内容，补充摘要、分类和标签后即可发布。'
  return '内容字段不变，只把写作体验切到更顺滑的内嵌工作区。'
})

const resetForm = () => {
  form.title = ''
  form.summary = ''
  form.contentMd = ''
  form.coverUrl = ''
  form.categoryId = null
  form.tagIds = []
  form.isTop = false
  newCategoryName.value = ''
  newTagName.value = ''
}

const loadCategories = async () => {
  try {
    const res = await getMyCategories()
    categories.value = res.data.data || []
  } catch (error) {
    categories.value = []
  }
}

const loadTags = async () => {
  try {
    const res = await getMyTags()
    allTags.value = res.data.data || []
  } catch (error) {
    allTags.value = []
  }
}

const loadPost = async (postId) => {
  const res = await getMyBlogPostDetail(postId)
  const post = res.data.data || {}
  form.title = post.title || ''
  form.summary = post.summary || ''
  form.contentMd = post.contentMd || ''
  form.coverUrl = post.coverUrl || ''
  form.categoryId = post.categoryId || null
  form.tagIds = (post.tags || []).map((tag) => tag.id)
  form.isTop = Boolean(post.isTop)
}

const loadNoteSource = async (noteId) => {
  const res = await getNote(noteId)
  const note = res.data.data || {}
  form.title = note.title || ''
  form.contentMd = note.contentMd || ''
}

const initialize = async () => {
  resetForm()
  loading.value = true

  try {
    await Promise.all([loadCategories(), loadTags()])

    if (props.postId) {
      await loadPost(props.postId)
      return
    }

    if (props.noteId) {
      await loadNoteSource(props.noteId)
      return
    }

    form.title = props.noteTitle || ''
    form.contentMd = props.noteContent || ''
  } catch (error) {
    ElMessage.error(props.postId ? '加载文章失败' : '加载写作数据失败')
  } finally {
    loading.value = false
  }
}

const toggleTag = (tagId) => {
  const index = form.tagIds.indexOf(tagId)
  if (index >= 0) {
    form.tagIds.splice(index, 1)
  } else {
    form.tagIds.push(tagId)
  }
}

const handleCreateCategory = async () => {
  const name = newCategoryName.value.trim()
  if (!name) return

  try {
    const res = await createCategory({ name })
    const created = res.data.data
    newCategoryName.value = ''
    await loadCategories()
    if (created?.id) form.categoryId = created.id
    ElMessage.success('分类已创建')
  } catch (error) {
    ElMessage.error('创建分类失败')
  }
}

const handleCreateTag = async () => {
  const name = newTagName.value.trim()
  if (!name) return

  try {
    const res = await createTag({ name })
    const newTag = res.data.data
    newTagName.value = ''
    await loadTags()
    if (newTag?.id && !form.tagIds.includes(newTag.id)) {
      form.tagIds.push(newTag.id)
    }
    ElMessage.success('标签已创建')
  } catch (error) {
    ElMessage.error('创建标签失败')
  }
}

const handleSave = async (publishStatus) => {
  if (!form.title.trim()) {
    ElMessage.warning('请输入文章标题')
    return
  }

  loading.value = true

  try {
    const payload = {
      title: form.title,
      summary: form.summary,
      contentMd: form.contentMd,
      coverUrl: form.coverUrl,
      categoryId: form.categoryId,
      tagIds: form.tagIds,
      isTop: form.isTop
    }

    let postId = props.postId

    if (postId) {
      await updateBlogPost(postId, payload)
    } else {
      const res = await createBlogPost(payload)
      postId = res.data.data?.id
    }

    if (publishStatus === 1 && postId) {
      await publishBlogPost(postId)
      ElMessage.success('发布成功')
    } else {
      ElMessage.success('草稿已保存')
    }

    emit('saved', { postId, publishStatus })
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.postId, props.noteId],
  () => {
    initialize()
  },
  { immediate: true }
)
</script>

<style scoped>
.editor-workspace {
  --surface: rgba(255, 255, 255, 0.92);
  --surface-strong: rgba(255, 255, 255, 0.98);
  --surface-soft: rgba(246, 249, 255, 0.86);
  --border: rgba(173, 190, 234, 0.22);
  --text-main: #25314b;
  --text-soft: #6b7894;
  --brand-1: #6f8fff;
  --brand-2: #87dbea;
  --brand-3: #ffc1d8;
  color: var(--text-main);
  min-width: 0;
}

.editor-workspace.variant-console {
  --surface: rgba(25, 35, 58, 0.78);
  --surface-strong: rgba(31, 43, 70, 0.9);
  --surface-soft: rgba(20, 29, 49, 0.76);
  --border: rgba(149, 179, 255, 0.14);
  --text-main: #f4f8ff;
  --text-soft: rgba(232, 241, 255, 0.76);
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;
  padding: 20px 22px;
  border-radius: 28px;
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: 0 18px 48px rgba(83, 99, 149, 0.12);
  backdrop-filter: blur(18px);
}

.workspace-kicker,
.field-label,
.side-title {
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-soft);
}

.workspace-header h2 {
  margin: 10px 0 8px;
  font-size: 30px;
  letter-spacing: -0.02em;
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
  color: var(--text-main);
}

.workspace-header p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.75;
}

.workspace-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.14) !important;
  border: 1px solid var(--border) !important;
  color: var(--text-main) !important;
}

.workspace-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  min-width: 0;
  min-height: 0;
  align-items: start;
}

.editor-main,
.editor-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 0;
}

.field-card,
.side-card {
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: 0 18px 48px rgba(83, 99, 149, 0.12);
  backdrop-filter: blur(18px);
}

.field-card {
  padding: 18px;
  border-radius: 26px;
}

.title-card {
  padding-bottom: 14px;
}

.title-input {
  margin-top: 10px;
}

.title-input :deep(.el-input__wrapper) {
  min-height: 56px;
}

.title-input :deep(.el-input__inner) {
  font-size: 22px;
  font-weight: 700;
}

.field-card :deep(.el-textarea__inner) {
  line-height: 1.75;
}

.editor-card {
  overflow: hidden;
  min-height: 0;
}

.editor-card :deep(.v-md-editor) {
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(173, 190, 234, 0.18);
  max-width: 100%;
}

.editor-card :deep(.v-md-editor__main),
.editor-card :deep(.v-md-editor__editor-wrapper),
.editor-card :deep(.scrollbar),
.editor-card :deep(.scrollbar__wrap) {
  min-height: 0;
}

.editor-card :deep(.v-md-textarea-editor textarea) {
  overflow-y: auto;
}

.editor-card :deep(.v-md-editor__toolbar),
.editor-card :deep(.v-md-editor__left-area),
.editor-card :deep(.v-md-editor__preview) {
  background: rgba(255, 255, 255, 0.98);
}

.editor-workspace.variant-console .editor-card :deep(.v-md-editor__toolbar),
.editor-workspace.variant-console .editor-card :deep(.v-md-editor__left-area),
.editor-workspace.variant-console .editor-card :deep(.v-md-editor__preview) {
  background: rgba(248, 250, 255, 0.98);
}

.side-card {
  padding: 16px;
  border-radius: 22px;
}

.side-title {
  margin-bottom: 10px;
}

.cover-preview {
  width: 100%;
  height: 150px;
  margin-top: 10px;
  border-radius: 16px;
  overflow: hidden;
}

.inline-create {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.tag-select {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.clickable-tag {
  cursor: pointer;
}

@media (max-width: 1080px) {
  .workspace-body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .workspace-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .workspace-header h2 {
    font-size: 26px;
  }
}
</style>
