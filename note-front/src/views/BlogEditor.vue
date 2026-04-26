<template>
  <div class="blog-editor">
    <div class="editor-header">
      <h1>{{ isEdit ? '编辑文章' : '写文章' }}</h1>
      <div class="header-actions">
        <el-button @click="$router.push('/blog/manage')">返回管理</el-button>
        <el-button @click="handleSave(0)">保存草稿</el-button>
        <el-button type="primary" @click="handleSave(1)">发布</el-button>
      </div>
    </div>

    <div class="editor-body" v-loading="loading">
      <div class="editor-main">
        <el-input v-model="form.title" placeholder="请输入文章标题" size="large" class="title-input" />

        <el-input v-model="form.summary" type="textarea" :rows="2" placeholder="文章摘要（可选）" class="summary-input" />

        <div class="md-editor-wrapper">
          <el-input v-model="form.contentMd" type="textarea" :rows="20" placeholder="请输入 Markdown 内容..." class="content-input" />
        </div>
      </div>

      <div class="editor-sidebar">
        <div class="sidebar-card">
          <h3>封面图</h3>
          <el-input v-model="form.coverUrl" placeholder="封面图片 URL" />
          <el-image v-if="form.coverUrl" :src="form.coverUrl" fit="cover" class="cover-preview" />
        </div>

        <div class="sidebar-card">
          <h3>分类</h3>
          <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
          <div class="inline-create">
            <el-input v-model="newCategoryName" placeholder="新分类" size="small" style="flex:1" />
            <el-button size="small" @click="handleCreateCategory">添加</el-button>
          </div>
        </div>

        <div class="sidebar-card">
          <h3>标签</h3>
          <div class="tag-select">
            <el-tag v-for="tag in allTags" :key="tag.id" :effect="form.tagIds.includes(tag.id) ? 'dark' : 'plain'" @click="toggleTag(tag.id)" style="cursor:pointer; margin: 4px">
              {{ tag.name }}
            </el-tag>
          </div>
          <div class="inline-create">
            <el-input v-model="newTagName" placeholder="新标签" size="small" style="flex:1" />
            <el-button size="small" @click="handleCreateTag">添加</el-button>
          </div>
        </div>

        <div class="sidebar-card">
          <h3>选项</h3>
          <el-checkbox v-model="form.isTop">置顶</el-checkbox>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createBlogPost, updateBlogPost, getMyBlogPostDetail, publishBlogPost,
  noteToBlog,
  getMyCategories, createCategory,
  getMyTags, createTag
} from '@/api/blog'
import { getNote } from '@/api/index'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)

const form = reactive({
  title: '',
  summary: '',
  contentMd: '',
  coverUrl: '',
  categoryId: null,
  tagIds: [],
  isTop: false
})

const categories = ref([])
const allTags = ref([])
const newCategoryName = ref('')
const newTagName = ref('')

const loadPost = async () => {
  if (!route.params.id) return
  loading.value = true
  try {
    const res = await getMyBlogPostDetail(route.params.id)
    const post = res.data.data
    form.title = post.title || ''
    form.summary = post.summary || ''
    form.contentMd = post.contentMd || ''
    form.coverUrl = post.coverUrl || ''
    form.categoryId = post.categoryId || null
    form.tagIds = (post.tags || []).map(t => t.id)
    form.isTop = post.isTop || false
  } catch {
    ElMessage.error('加载文章失败')
  } finally {
    loading.value = false
  }
}

const loadFromNote = async () => {
  const noteId = route.query.noteId
  if (!noteId) return
  loading.value = true
  try {
    const res = await getNote(noteId)
    const note = res.data.data
    form.title = note.title || ''
    form.contentMd = note.contentMd || ''
  } catch {
    ElMessage.error('加载笔记失败')
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    const res = await getMyCategories()
    categories.value = res.data.data || []
  } catch { /* ignore */ }
}

const loadTags = async () => {
  try {
    const res = await getMyTags()
    allTags.value = res.data.data || []
  } catch { /* ignore */ }
}

const toggleTag = (tagId) => {
  const idx = form.tagIds.indexOf(tagId)
  if (idx >= 0) {
    form.tagIds.splice(idx, 1)
  } else {
    form.tagIds.push(tagId)
  }
}

const handleCreateCategory = async () => {
  if (!newCategoryName.value.trim()) return
  try {
    await createCategory({ name: newCategoryName.value.trim() })
    newCategoryName.value = ''
    await loadCategories()
    ElMessage.success('分类已创建')
  } catch {
    ElMessage.error('创建分类失败')
  }
}

const handleCreateTag = async () => {
  if (!newTagName.value.trim()) return
  try {
    const res = await createTag({ name: newTagName.value.trim() })
    const newTag = res.data.data
    newTagName.value = ''
    await loadTags()
    form.tagIds.push(newTag.id)
    ElMessage.success('标签已创建')
  } catch {
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
    const data = {
      title: form.title,
      summary: form.summary,
      contentMd: form.contentMd,
      coverUrl: form.coverUrl,
      categoryId: form.categoryId,
      tagIds: form.tagIds,
      isTop: form.isTop
    }

    let postId = route.params.id
    if (isEdit.value) {
      await updateBlogPost(postId, data)
    } else {
      const res = await createBlogPost(data)
      postId = res.data.data.id
    }

    if (publishStatus === 1) {
      await publishBlogPost(postId)
      ElMessage.success('发布成功')
    } else {
      ElMessage.success('已保存')
    }
    router.push('/blog/manage')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCategories()
  loadTags()
  if (route.params.id) {
    loadPost()
  } else if (route.query.noteId) {
    loadFromNote()
  }
})
</script>

<style scoped>
.blog-editor {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.editor-header h1 {
  font-size: 24px;
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 10px;
}
.editor-body {
  display: flex;
  gap: 20px;
}
.editor-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.title-input :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 600;
}
.content-input :deep(.el-textarea__inner) {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
}
.editor-sidebar {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.sidebar-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.sidebar-card h3 {
  margin: 0 0 10px;
  font-size: 14px;
  color: #374151;
}
.cover-preview {
  width: 100%;
  height: 120px;
  border-radius: 6px;
  margin-top: 8px;
}
.tag-select {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.inline-create {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .editor-body { flex-direction: column; }
  .editor-sidebar { width: 100%; }
}
</style>
