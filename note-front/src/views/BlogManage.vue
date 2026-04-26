<template>
  <div class="blog-console">
    <SakuraBackground />
    <div class="console-haze haze-a"></div>
    <div class="console-haze haze-b"></div>

    <aside class="console-rail">
      <div class="rail-brand">
        <div class="rail-logo">H</div>
        <div>
          <div class="rail-name">My Blog Desk</div>
          <div class="rail-sub">写作、整理和发布都在这里</div>
        </div>
      </div>

      <section class="rail-card mode-card">
        <div class="rail-label">Writing Mode</div>
        <h2>{{ isComposeMode ? (composePostId ? '编辑文章' : '写作中') : '文章后台' }}</h2>
        <p>
          {{ isComposeMode
            ? '写作区直接留在后台里，不用来回跳页面。思路如果正顺着往下走，这样会更舒服一点。'
            : '这里用来整理文章、看看发布状态，也顺手照顾一下那些还没写完的草稿。' }}
        </p>
      </section>

      <nav class="rail-nav">
        <button
          :class="['rail-link', { active: !isComposeMode && statusFilter === null }]"
          @click="goList(null)"
        >
          <span>全部文章</span>
          <strong>{{ dashboard.total }}</strong>
        </button>
        <button
          :class="['rail-link', { active: !isComposeMode && statusFilter === 1 }]"
          @click="goList(1)"
        >
          <span>已发布</span>
          <strong>{{ dashboard.published }}</strong>
        </button>
        <button
          :class="['rail-link', { active: !isComposeMode && statusFilter === 0 }]"
          @click="goList(0)"
        >
          <span>草稿箱</span>
          <strong>{{ dashboard.draft }}</strong>
        </button>
        <button :class="['rail-link', { compose: isComposeMode }]" @click="openCompose()">
          <span>{{ isComposeMode ? '继续写作' : '写新文章' }}</span>
          <strong>{{ isComposeMode ? 'ON' : '+' }}</strong>
        </button>
      </nav>

      <section class="rail-card quick-card">
        <div class="rail-label">Quick Routes</div>
        <button class="quick-link primary" @click="openCompose()">新建文章</button>
        <button v-if="isComposeMode" class="quick-link" @click="closeCompose()">返回文章列表</button>
        <router-link class="quick-link" to="/blog">博客首页</router-link>
        <router-link class="quick-link" to="/notebook">返回工作台</router-link>
      </section>

      <section class="rail-card note-card">
        <div class="rail-label">Latest Update</div>
        <div class="note-value">{{ latestUpdateText }}</div>
      </section>
    </aside>

    <main class="console-main">
      <section class="console-hero">
        <div>
          <div class="hero-label">{{ isComposeMode ? 'Writing Space' : 'Post Overview' }}</div>
          <h1>{{ isComposeMode ? (composePostId ? '在这里继续改这篇文章' : '开始写一篇新的文章') : '我的博客后台' }}</h1>
          <p>
            {{ isComposeMode
              ? '这一页就是写作本身。想法还热着的时候，直接在这里继续写，会比切来切去更不容易断掉。'
              : '平时写下来的内容、已经发出去的文章，还有暂时搁着的草稿，都会在这里慢慢堆起来。' }}
          </p>
        </div>

        <div class="console-actions">
          <el-input
            v-if="!isComposeMode"
            v-model="keyword"
            placeholder="搜一搜标题、摘要，或者你记得的某个关键词"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
            class="console-search"
          />
          <el-button class="action-glow" @click="openCompose()">
            {{ isComposeMode ? '新建另一篇' : '写文章' }}
          </el-button>
          <el-button class="action-soft" @click="isComposeMode ? closeCompose() : refreshAll()">
            {{ isComposeMode ? '返回列表' : '刷新内容' }}
          </el-button>
        </div>
      </section>

      <template v-if="!isComposeMode">
        <section class="stats-panel">
          <article class="dashboard-card">
            <span>文章总数</span>
            <strong>{{ dashboard.total }}</strong>
            <small>这些都是我写下来并留下的内容</small>
          </article>
          <article class="dashboard-card">
            <span>已发布</span>
            <strong>{{ dashboard.published }}</strong>
            <small>已经公开给别人看的部分</small>
          </article>
          <article class="dashboard-card">
            <span>草稿数</span>
            <strong>{{ dashboard.draft }}</strong>
            <small>还在慢慢修改和打磨的内容</small>
          </article>
          <article class="dashboard-card accent">
            <span>分类数</span>
            <strong>{{ dashboard.categoryCount }}</strong>
            <small>这些文章目前被分成了这些方向</small>
          </article>
        </section>

        <section class="toolbar-panel">
          <div class="toolbar-group">
            <button :class="['toolbar-pill', { active: statusFilter === null }]" @click="goList(null)">全部</button>
            <button :class="['toolbar-pill', { active: statusFilter === 1 }]" @click="goList(1)">已发布</button>
            <button :class="['toolbar-pill', { active: statusFilter === 0 }]" @click="goList(0)">草稿</button>
          </div>
          <div class="toolbar-meta">
            <span>当前筛选: {{ activeStatusText }}</span>
            <span>本页累计阅读: {{ visibleViewCount }}</span>
            <button class="refresh-link" @click="refreshAll">刷新</button>
          </div>
        </section>

        <section class="table-shell">
          <div class="table-head">
            <div>
              <div class="hero-label">Recent Archive</div>
              <h2>文章列表</h2>
              <p>这里放着最近的内容，也能顺手管理它们的状态、标签和发布时间。</p>
            </div>
            <div class="table-summary">
              <span>{{ posts.length }} 篇显示中</span>
              <span>{{ latestUpdateText }}</span>
            </div>
          </div>

          <el-table :data="posts" v-loading="loading" stripe style="width: 100%">
            <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="categoryName" label="分类" width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.categoryName || '-' }}</template>
            </el-table-column>
            <el-table-column label="标签" width="220">
              <template #default="{ row }">
                <span v-for="tag in row.tags || []" :key="tag.id" class="tag-chip">{{ tag.name }}</span>
                <span v-if="!row.tags || !row.tags.length" class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <span :class="['status-chip', row.status === 1 ? 'published' : 'draft']">
                  {{ row.status === 1 ? '已发布' : '草稿' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="viewCount" label="阅读" width="80" align="center" />
            <el-table-column label="置顶" width="80" align="center">
              <template #default="{ row }">{{ row.isTop ? '是' : '-' }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="140">
              <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openCompose(row.id)">编辑</el-button>
                <el-button v-if="row.status === 0" size="small" type="success" @click="handlePublish(row.id)">发布</el-button>
                <el-button v-else size="small" type="warning" @click="handleUnpublish(row.id)">撤回</el-button>
                <el-popconfirm title="确认删除这篇文章？" @confirm="handleDelete(row.id)">
                  <template #reference>
                    <el-button size="small" type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <div class="pagination" v-if="total > pageSize">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :total="total"
            :page-size="pageSize"
            :current-page="currentPage"
            @current-change="handlePageChange"
          />
        </div>
      </template>

      <section v-else class="editor-console-shell">
        <div class="editor-shell-head">
          <div>
            <div class="hero-label">Writing Desk</div>
            <h2>{{ composePostId ? '编辑文章' : '写新文章' }}</h2>
            <p>写作区直接留在这里，想写就写，写完再回到列表，不用把思路中途打断。</p>
          </div>
          <button class="ghost-text" @click="closeCompose">返回文章列表</button>
        </div>

        <BlogEditorWorkspace
          variant="console"
          :post-id="composePostId"
          :note-id="composeNoteId"
          editor-height="66vh"
          return-text="返回文章列表"
          @saved="handleEditorSaved"
          @cancel="closeCompose"
        />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyBlogPosts, publishBlogPost, unpublishBlogPost, deleteBlogPost, getMyCategories } from '@/api/blog'
import BlogEditorWorkspace from '@/components/BlogEditorWorkspace.vue'
import SakuraBackground from '@/components/SakuraBackground.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const posts = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const statusFilter = ref(null)

const dashboard = reactive({
  total: 0,
  published: 0,
  draft: 0,
  categoryCount: 0
})

const composePostId = computed(() => route.query.editId || null)
const composeNoteId = computed(() => route.query.noteId || null)
const isComposeMode = computed(() => route.query.compose === '1' || Boolean(route.query.editId) || Boolean(route.query.noteId))

const activeStatusText = computed(() => {
  if (statusFilter.value === 1) return '已发布'
  if (statusFilter.value === 0) return '草稿'
  return '全部'
})

const latestUpdateText = computed(() => {
  const latest = posts.value[0]?.updateTime
  return latest ? `${formatDate(latest)} 更新` : '最近还没有新内容'
})

const visibleViewCount = computed(() => {
  return posts.value.reduce((sum, item) => sum + (item.viewCount || 0), 0)
})

const buildParams = () => {
  const params = {
    page: currentPage.value,
    size: pageSize.value
  }

  if (keyword.value) params.keyword = keyword.value
  if (statusFilter.value !== null && statusFilter.value !== '') params.status = statusFilter.value

  return params
}

const loadPosts = async () => {
  loading.value = true
  try {
    const res = await getMyBlogPosts(buildParams())
    const data = res.data.data
    posts.value = data.data || data.records || []
    total.value = data.total || 0
  } catch (error) {
    posts.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  try {
    const [allRes, publishedRes, draftRes, categoryRes] = await Promise.allSettled([
      getMyBlogPosts({ page: 1, size: 1 }),
      getMyBlogPosts({ page: 1, size: 1, status: 1 }),
      getMyBlogPosts({ page: 1, size: 1, status: 0 }),
      getMyCategories()
    ])

    dashboard.total = allRes.status === 'fulfilled' ? (allRes.value.data.data?.total || 0) : 0
    dashboard.published = publishedRes.status === 'fulfilled' ? (publishedRes.value.data.data?.total || 0) : 0
    dashboard.draft = draftRes.status === 'fulfilled' ? (draftRes.value.data.data?.total || 0) : 0
    dashboard.categoryCount = categoryRes.status === 'fulfilled' ? ((categoryRes.value.data.data || []).length) : 0
  } catch (error) {
    dashboard.total = 0
    dashboard.published = 0
    dashboard.draft = 0
    dashboard.categoryCount = 0
  }
}

const refreshAll = async () => {
  await Promise.all([loadPosts(), loadDashboard()])
}

const handleSearch = () => {
  currentPage.value = 1
  loadPosts()
}

const openCompose = (postId = null) => {
  const query = { compose: '1' }
  if (postId) query.editId = String(postId)
  router.replace({ path: '/blog/manage', query })
}

const closeCompose = () => {
  router.replace({ path: '/blog/manage' })
}

const goList = (status) => {
  statusFilter.value = status
  currentPage.value = 1
  if (isComposeMode.value) {
    closeCompose()
  }
  loadPosts()
}

const handleEditorSaved = async () => {
  await refreshAll()
  closeCompose()
}

const handlePublish = async (id) => {
  await publishBlogPost(id)
  ElMessage.success('发布成功')
  refreshAll()
}

const handleUnpublish = async (id) => {
  await unpublishBlogPost(id)
  ElMessage.success('已撤回')
  refreshAll()
}

const handleDelete = async (id) => {
  await deleteBlogPost(id)
  ElMessage.success('已删除')
  refreshAll()
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadPosts()
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.blog-console {
  --ink: #f7fbff;
  --ink-soft: rgba(247, 251, 255, 0.88);
  --ink-muted: rgba(235, 243, 255, 0.72);
  --card: rgba(23, 32, 54, 0.74);
  --card-soft: rgba(28, 39, 64, 0.56);
  --cyan: #83d8ea;
  --blue: #7f97ff;
  --pink: #f6b7d8;
  --amber: #ffd59c;
  min-height: 100vh;
  height: 100vh;
  height: 100dvh;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 20px;
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  padding: 22px;
  background:
    radial-gradient(circle at top left, rgba(127, 151, 255, 0.22), transparent 18%),
    radial-gradient(circle at bottom right, rgba(246, 183, 216, 0.18), transparent 20%),
    linear-gradient(180deg, #11182a 0%, #161d30 52%, #131928 100%);
  color: var(--ink);
  font-family: "Avenir Next", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

.console-haze {
  position: fixed;
  width: 280px;
  height: 280px;
  filter: blur(54px);
  pointer-events: none;
  opacity: 0.6;
}

.haze-a {
  top: 120px;
  right: 6%;
  background: rgba(131, 216, 234, 0.16);
}

.haze-b {
  bottom: 110px;
  left: 12%;
  background: rgba(246, 183, 216, 0.14);
}

.console-rail,
.console-main {
  position: relative;
  z-index: 1;
}

.console-rail {
  position: sticky;
  top: 0;
  height: calc(100vh - 44px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rail-brand,
.rail-card,
.rail-nav,
.console-hero,
.toolbar-panel,
.table-shell,
.editor-console-shell {
  border: 1px solid rgba(140, 171, 255, 0.14);
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(18px);
}

.rail-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.04);
}

.rail-logo {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--blue), var(--cyan));
  color: white;
  font-size: 22px;
  font-weight: 700;
}

.rail-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--ink);
}

.rail-sub,
.rail-label,
.hero-label {
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(247, 251, 255, 0.78);
}

.rail-card {
  padding: 18px;
  border-radius: 24px;
  background: var(--card);
}

.mode-card h2,
.console-hero h1,
.table-head h2,
.editor-shell-head h2 {
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.mode-card h2 {
  margin: 10px 0;
  font-size: 28px;
}

.mode-card p,
.console-hero p,
.table-head p,
.editor-shell-head p {
  margin: 0;
  color: var(--ink-soft);
  line-height: 1.8;
}

.rail-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.03);
}

.rail-link {
  border: none;
  border-radius: 16px;
  background: transparent;
  color: var(--ink);
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}

.rail-link span {
  color: var(--ink-soft);
}

.rail-link strong {
  color: var(--amber);
}

.rail-link.active,
.rail-link:hover,
.rail-link.compose {
  background: linear-gradient(135deg, rgba(127, 151, 255, 0.18), rgba(131, 216, 234, 0.12));
}

.quick-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-link {
  width: 100%;
  padding: 12px 14px;
  border-radius: 16px;
  text-decoration: none;
  text-align: left;
  color: var(--ink);
  background: rgba(255, 255, 255, 0.08);
}

.quick-link.primary {
  background: linear-gradient(135deg, rgba(127, 151, 255, 0.26), rgba(246, 183, 216, 0.22));
}

.note-value {
  margin-top: 10px;
  color: var(--ink);
}

.console-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
  min-height: 0;
}

.console-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  padding: 24px 26px;
  border-radius: 30px;
  background: linear-gradient(135deg, rgba(23, 32, 54, 0.92), rgba(31, 45, 76, 0.84));
}

.console-hero h1 {
  margin: 10px 0;
  font-size: 38px;
}

.console-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.console-search {
  width: 300px;
}

.console-search :deep(.el-input__wrapper) {
  border-radius: 999px !important;
  background: rgba(255, 255, 255, 0.14) !important;
  border: 1px solid rgba(163, 191, 255, 0.16) !important;
  box-shadow: none !important;
}

.console-search :deep(.el-input__inner),
.console-search :deep(.el-input__prefix-inner) {
  color: var(--ink) !important;
}

.action-glow,
.action-soft {
  border-radius: 999px !important;
}

.action-glow {
  background: linear-gradient(135deg, rgba(111, 143, 255, 0.92), rgba(131, 216, 234, 0.82)) !important;
  color: #fff !important;
  border: none !important;
}

.action-soft {
  background: rgba(255, 255, 255, 0.1) !important;
  border: 1px solid rgba(163, 191, 255, 0.16) !important;
  color: var(--ink) !important;
}

.stats-panel {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.dashboard-card {
  padding: 18px 20px;
  border-radius: 24px;
  background: var(--card-soft);
}

.dashboard-card span,
.dashboard-card strong,
.dashboard-card small {
  display: block;
}

.dashboard-card span {
  color: var(--ink-soft);
}

.dashboard-card strong {
  font-size: 34px;
  margin: 8px 0;
  color: var(--ink);
}

.dashboard-card small {
  color: var(--ink-muted);
}

.dashboard-card.accent {
  background: linear-gradient(135deg, rgba(127, 151, 255, 0.72), rgba(246, 183, 216, 0.5));
}

.toolbar-panel {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px 18px;
  border-radius: 24px;
  background: var(--card);
}

.toolbar-group,
.toolbar-meta,
.table-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.toolbar-pill {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--ink-soft);
  cursor: pointer;
}

.toolbar-pill.active,
.toolbar-pill:hover {
  background: linear-gradient(135deg, rgba(127, 151, 255, 0.32), rgba(131, 216, 234, 0.22));
}

.toolbar-meta,
.table-summary {
  color: var(--ink-soft);
  font-size: 13px;
}

.refresh-link,
.ghost-text {
  border: none;
  background: none;
  color: var(--pink);
  cursor: pointer;
}

.table-shell,
.editor-console-shell {
  padding: 14px;
  border-radius: 28px;
  background: var(--card);
  min-width: 0;
}

.table-head,
.editor-shell-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-end;
  padding: 8px 8px 18px;
}

.table-head h2,
.editor-shell-head h2 {
  margin: 10px 0;
  font-size: 28px;
}

.table-shell :deep(.el-table),
.table-shell :deep(.el-table__inner-wrapper),
.table-shell :deep(.el-table__header-wrapper),
.table-shell :deep(.el-table__body-wrapper),
.table-shell :deep(.el-scrollbar__view),
.table-shell :deep(table),
.table-shell :deep(tr),
.table-shell :deep(th.el-table__cell),
.table-shell :deep(td.el-table__cell) {
  background: transparent !important;
}

.table-shell :deep(.el-table) {
  color: var(--ink) !important;
}

.table-shell :deep(.el-table th.el-table__cell) {
  color: var(--ink-soft) !important;
  font-weight: 700 !important;
  border-bottom-color: rgba(163, 191, 255, 0.16) !important;
}

.table-shell :deep(.el-table td.el-table__cell) {
  color: var(--ink) !important;
  border-bottom-color: rgba(163, 191, 255, 0.1) !important;
}

.table-shell :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: rgba(127, 151, 255, 0.12) !important;
}

.table-shell :deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: rgba(255, 255, 255, 0.03) !important;
}

.table-shell :deep(.el-table__fixed-right::before),
.table-shell :deep(.el-table__inner-wrapper::before) {
  background-color: rgba(163, 191, 255, 0.14) !important;
}

.tag-chip {
  display: inline-block;
  margin: 2px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(131, 216, 234, 0.12);
  color: #9ce7f3;
  font-size: 12px;
}

.status-chip {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-chip.published {
  background: rgba(34, 197, 94, 0.16);
  color: #8df0af;
}

.status-chip.draft {
  background: rgba(255, 213, 156, 0.16);
  color: #ffd59c;
}

.text-muted {
  color: var(--ink-muted);
}

.pagination {
  display: flex;
  justify-content: center;
}

@media (max-width: 1220px) {
  .blog-console {
    grid-template-columns: 1fr;
  }

  .console-rail {
    position: static;
    height: auto;
  }
}

@media (max-width: 900px) {
  .console-hero,
  .toolbar-panel,
  .table-head,
  .editor-shell-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .stats-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .console-search {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .blog-console {
    padding: 12px;
  }

  .stats-panel {
    grid-template-columns: 1fr;
  }

  .console-hero h1 {
    font-size: 30px;
  }
}
</style>
