<template>
  <div class="blog-manager-shell">
    <div class="ambient ambient-a"></div>
    <div class="ambient ambient-b"></div>

    <section class="manager-hero">
      <div class="hero-copy">
        <div class="manager-kicker">Writing Corner</div>
        <h3>我的写作后台</h3>
        <p>
          这里放着最近写下的内容，也留着一些还没完全想好的草稿。
          我希望写东西的时候不用频繁跳来跳去，所以把编辑入口尽量收在同一个地方，思路也能更连贯一点。
        </p>

        <div class="hero-actions">
          <router-link to="/blog/manage">
            <el-button class="primary-btn">打开完整控制台</el-button>
          </router-link>
          <el-button class="ghost-btn" @click="openEditorDialog()">写新文章</el-button>
          <router-link to="/blog">
            <el-button class="ghost-btn">预览首页</el-button>
          </router-link>
        </div>
      </div>

      <div class="hero-signal">
        <div class="signal-chip">RECENT WRITING STATUS</div>
        <div class="signal-main">
          <span>最近更新</span>
          <strong>{{ latestUpdateText }}</strong>
        </div>
        <div class="signal-grid">
          <article class="signal-card">
            <span>发布率</span>
            <strong>{{ publishedRatio }}</strong>
          </article>
          <article class="signal-card">
            <span>可见分类</span>
            <strong>{{ stats.categoryCount }}</strong>
          </article>
        </div>
      </div>
    </section>

    <section class="manager-stats">
      <article class="mini-stat">
        <span>文章总数</span>
        <strong>{{ stats.total }}</strong>
        <small>这些都是我留下来的内容</small>
      </article>
      <article class="mini-stat">
        <span>已发布</span>
        <strong>{{ stats.published }}</strong>
        <small>已经公开给别人看的部分</small>
      </article>
      <article class="mini-stat">
        <span>草稿</span>
        <strong>{{ stats.draft }}</strong>
        <small>还在慢慢打磨的那些想法</small>
      </article>
      <article class="mini-stat accent">
        <span>最近列表阅读</span>
        <strong>{{ visibleReads }}</strong>
        <small>这几篇最近被看过这么多次</small>
      </article>
    </section>

    <section class="manager-grid">
      <div class="story-board" v-loading="loading">
        <div class="section-head">
          <div>
            <div class="manager-kicker">Recent Writing</div>
            <h4>最近文章</h4>
          </div>
          <span class="section-meta">{{ posts.length }} 篇最近内容</span>
        </div>

        <div v-if="posts.length" class="story-list">
          <article v-for="post in posts" :key="post.id" class="story-item">
            <div class="story-top">
              <div class="story-badges">
                <span :class="['status-badge', post.status === 1 ? 'published' : 'draft']">
                  {{ post.status === 1 ? '已发布' : '草稿' }}
                </span>
                <span v-if="post.categoryName" class="soft-badge">{{ post.categoryName }}</span>
              </div>
              <span class="story-date">{{ formatDate(post.updateTime) }}</span>
            </div>

            <h5>{{ post.title || '还没起名字的文章' }}</h5>
            <p>{{ post.summary || '这篇先留一个位置，完整内容可以在编辑窗口里慢慢补完。' }}</p>

            <div class="story-footer">
              <div class="story-meta">
                <span>阅读 {{ post.viewCount || 0 }}</span>
                <span v-if="post.tags && post.tags.length">{{ post.tags.length }} 个标签</span>
              </div>
              <div class="story-actions">
                <button class="story-btn" @click="openEditorDialog(post.id)">编辑</button>
                <button
                  v-if="post.status === 0"
                  class="story-btn primary"
                  @click="handlePublish(post.id)"
                >
                  发布
                </button>
                <button
                  v-else
                  class="story-btn warm"
                  @click="handleUnpublish(post.id)"
                >
                  撤回
                </button>
              </div>
            </div>
          </article>
        </div>

        <div v-else class="empty-state">
          <div class="empty-mark">HALO</div>
          <p>这里暂时还没有内容，下一篇文章会从这里开始。</p>
        </div>
      </div>

      <aside class="side-deck">
        <section class="deck-card">
          <div class="section-head compact">
            <div>
              <div class="manager-kicker">Quick Routes</div>
              <h4>快捷入口</h4>
            </div>
          </div>
          <div class="quick-links">
            <router-link class="quick-link primary" to="/blog/manage">博客控制台</router-link>
            <button class="quick-link" @click="openEditorDialog()">新建文章</button>
            <router-link class="quick-link" to="/blog">博客首页</router-link>
          </div>
        </section>

        <section class="deck-card">
          <div class="section-head compact">
            <div>
              <div class="manager-kicker">Category Notes</div>
              <h4>分类概览</h4>
            </div>
          </div>
          <div class="category-cloud">
            <span v-for="category in topCategories" :key="category.id" class="category-chip">
              {{ category.name }}
            </span>
            <span v-if="!topCategories.length" class="category-chip muted">暂时还没整理分类</span>
          </div>
        </section>

        <section class="deck-card">
          <div class="section-head compact">
            <div>
              <div class="manager-kicker">A Small Note</div>
              <h4>后台说明</h4>
            </div>
          </div>
          <p class="deck-note">
            这里更适合快速写作、顺手修改和整理最近的内容。
            如果要做更完整的管理，比如批量筛选、删除或者统一调整状态，我一般会去
            <code>/blog/manage</code> 里处理。
          </p>
        </section>
      </aside>
    </section>

    <BlogEditorDialog
      v-model="showEditorWindow"
      :post-id="editingPostId"
      @saved="handleEditorSaved"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyBlogPosts, publishBlogPost, unpublishBlogPost, getMyCategories } from '@/api/blog'
import BlogEditorDialog from '@/components/BlogEditorDialog.vue'

const loading = ref(false)
const posts = ref([])
const categories = ref([])
const showEditorWindow = ref(false)
const editingPostId = ref(null)

const stats = reactive({
  total: 0,
  published: 0,
  draft: 0,
  categoryCount: 0
})

const latestUpdateText = computed(() => {
  const latest = posts.value[0]?.updateTime
  return latest ? `${formatDate(latest)} 更新` : '最近还没有新内容'
})

const publishedRatio = computed(() => {
  if (!stats.total) return '0%'
  return `${Math.round((stats.published / stats.total) * 100)}%`
})

const visibleReads = computed(() => {
  return posts.value.reduce((sum, item) => sum + (item.viewCount || 0), 0)
})

const topCategories = computed(() => categories.value.slice(0, 6))

const loadPosts = async () => {
  loading.value = true
  try {
    const res = await getMyBlogPosts({ page: 1, size: 4 })
    const data = res.data.data
    posts.value = data.data || data.records || []
    stats.total = data.total || 0
  } catch (error) {
    posts.value = []
    stats.total = 0
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const [publishedRes, draftRes, categoryRes] = await Promise.allSettled([
      getMyBlogPosts({ page: 1, size: 1, status: 1 }),
      getMyBlogPosts({ page: 1, size: 1, status: 0 }),
      getMyCategories()
    ])

    stats.published = publishedRes.status === 'fulfilled' ? (publishedRes.value.data.data?.total || 0) : 0
    stats.draft = draftRes.status === 'fulfilled' ? (draftRes.value.data.data?.total || 0) : 0
    categories.value = categoryRes.status === 'fulfilled' ? (categoryRes.value.data.data || []) : []
    stats.categoryCount = categories.value.length
  } catch (error) {
    stats.published = 0
    stats.draft = 0
    categories.value = []
    stats.categoryCount = 0
  }
}

const refreshAll = async () => {
  await Promise.all([loadPosts(), loadStats()])
}

const openEditorDialog = (postId = null) => {
  editingPostId.value = postId
  showEditorWindow.value = true
}

const handleEditorSaved = async () => {
  showEditorWindow.value = false
  editingPostId.value = null
  await refreshAll()
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

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

watch(showEditorWindow, (value) => {
  if (!value) {
    editingPostId.value = null
  }
})

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.blog-manager-shell {
  --surface: rgba(255, 255, 255, 0.78);
  --surface-strong: rgba(255, 255, 255, 0.88);
  --border: rgba(171, 191, 236, 0.24);
  --text-main: #22314f;
  --text-soft: #6e7c98;
  --brand-blue: #6f8fff;
  --brand-cyan: #88dcea;
  --brand-pink: #ffc0d7;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  min-height: 100%;
  color: var(--text-main);
  background:
    radial-gradient(circle at 10% 0%, rgba(255, 192, 215, 0.18), transparent 24%),
    radial-gradient(circle at 90% 16%, rgba(136, 220, 234, 0.2), transparent 22%),
    linear-gradient(180deg, #f8f8ff 0%, #fff9f7 48%, #f6f9ff 100%);
}

.ambient {
  position: absolute;
  pointer-events: none;
  filter: blur(58px);
  opacity: 0.68;
}

.ambient-a {
  top: 36px;
  right: 42px;
  width: 220px;
  height: 220px;
  background: rgba(136, 220, 234, 0.18);
}

.ambient-b {
  bottom: 18px;
  left: 6%;
  width: 240px;
  height: 240px;
  background: rgba(255, 192, 215, 0.16);
}

.manager-hero,
.mini-stat,
.story-board,
.deck-card {
  position: relative;
  z-index: 1;
  border: 1px solid var(--border);
  box-shadow: 0 18px 48px rgba(83, 99, 149, 0.12);
  backdrop-filter: blur(18px);
}

.manager-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.7fr);
  gap: 18px;
  padding: 28px;
  border-radius: 30px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.88), rgba(249, 251, 255, 0.72));
}

.manager-kicker {
  margin-bottom: 10px;
  color: #7b88a7;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
}

.hero-copy h3,
.section-head h4 {
  margin: 0 0 10px;
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
  letter-spacing: -0.02em;
}

.hero-copy h3 {
  font-size: 34px;
}

.hero-copy p,
.deck-note,
.story-item p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.primary-btn,
.ghost-btn {
  border-radius: 999px !important;
  padding: 0 18px !important;
  font-weight: 600 !important;
}

.primary-btn {
  background: linear-gradient(135deg, var(--brand-blue), var(--brand-cyan)) !important;
  border: none !important;
  color: #fff !important;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.82) !important;
  border: 1px solid rgba(171, 191, 236, 0.22) !important;
  color: var(--text-main) !important;
}

.hero-signal {
  padding: 20px;
  border-radius: 26px;
  background:
    linear-gradient(160deg, rgba(110, 143, 255, 0.12), rgba(255, 255, 255, 0.65)),
    rgba(255, 255, 255, 0.72);
}

.signal-chip,
.section-meta,
.story-date,
.story-meta {
  font-size: 12px;
  color: #7b88a7;
}

.signal-chip {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(111, 143, 255, 0.1);
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
}

.signal-main {
  margin: 18px 0 14px;
}

.signal-main span,
.signal-card span,
.mini-stat span {
  display: block;
  font-size: 13px;
  color: var(--text-soft);
}

.signal-main strong,
.signal-card strong,
.mini-stat strong {
  display: block;
  margin-top: 8px;
}

.signal-main strong {
  font-size: 28px;
  line-height: 1.3;
}

.signal-grid,
.manager-stats {
  display: grid;
  gap: 14px;
}

.signal-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.signal-card {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.68);
}

.signal-card strong {
  font-size: 24px;
}

.manager-stats {
  position: relative;
  z-index: 1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.mini-stat {
  padding: 18px 20px;
  border-radius: 24px;
  background: var(--surface);
}

.mini-stat strong {
  font-size: 34px;
  margin: 10px 0 6px;
}

.mini-stat small {
  display: block;
  color: var(--text-soft);
  line-height: 1.6;
}

.mini-stat.accent {
  background: linear-gradient(135deg, rgba(111, 143, 255, 0.9), rgba(255, 192, 215, 0.78));
  color: #fff;
}

.mini-stat.accent span,
.mini-stat.accent small {
  color: rgba(255, 255, 255, 0.88);
}

.manager-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
}

.story-board,
.deck-card {
  border-radius: 28px;
  background: var(--surface);
}

.story-board {
  padding: 22px;
}

.side-deck {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.deck-card {
  padding: 18px;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-head.compact {
  margin-bottom: 12px;
}

.section-head h4 {
  font-size: 24px;
}

.story-list {
  display: grid;
  gap: 14px;
}

.story-item {
  padding: 18px;
  border-radius: 24px;
  background: var(--surface-strong);
  border: 1px solid rgba(171, 191, 236, 0.18);
}

.story-top,
.story-badges,
.story-footer,
.story-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.story-top,
.story-footer {
  justify-content: space-between;
  align-items: center;
}

.story-item h5 {
  margin: 12px 0 8px;
  font-size: 22px;
  line-height: 1.35;
}

.story-item p {
  margin-bottom: 14px;
}

.story-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.status-badge,
.soft-badge,
.category-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
}

.status-badge,
.soft-badge {
  padding: 5px 10px;
  font-size: 12px;
}

.status-badge.published {
  background: rgba(109, 214, 159, 0.16);
  color: #1c8b5e;
}

.status-badge.draft {
  background: rgba(255, 220, 168, 0.26);
  color: #b6791d;
}

.soft-badge {
  background: rgba(111, 143, 255, 0.1);
  color: #5d76d8;
}

.story-btn {
  border: none;
  border-radius: 999px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--text-main);
  font-weight: 600;
  cursor: pointer;
}

.story-btn.primary {
  background: linear-gradient(135deg, var(--brand-blue), var(--brand-cyan));
  color: #fff;
}

.story-btn.warm {
  background: linear-gradient(135deg, rgba(255, 191, 215, 0.9), rgba(255, 220, 168, 0.92));
  color: #834d69;
}

.quick-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-link {
  display: block;
  width: 100%;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(171, 191, 236, 0.18);
  text-decoration: none;
  color: var(--text-main);
  font-weight: 600;
  text-align: left;
}

.quick-link.primary {
  background: linear-gradient(135deg, rgba(111, 143, 255, 0.9), rgba(136, 220, 234, 0.76));
  color: #fff;
}

.category-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.category-chip {
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(171, 191, 236, 0.18);
  color: #5d76d8;
  font-size: 13px;
}

.category-chip.muted {
  color: var(--text-soft);
}

.deck-note code {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(111, 143, 255, 0.12);
  color: #5570d8;
}

.empty-state {
  padding: 44px 0 18px;
  text-align: center;
  color: var(--text-soft);
}

.empty-mark {
  width: 78px;
  height: 78px;
  margin: 0 auto 14px;
  border-radius: 24px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, rgba(111, 143, 255, 0.16), rgba(255, 192, 215, 0.18));
  color: #5f79dc;
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .manager-hero,
  .manager-grid {
    grid-template-columns: 1fr;
  }

  .manager-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .blog-manager-shell {
    padding: 14px;
  }

  .manager-hero,
  .story-board,
  .deck-card {
    padding: 18px;
  }

  .hero-copy h3 {
    font-size: 28px;
  }

  .signal-grid,
  .manager-stats {
    grid-template-columns: 1fr;
  }

  .section-head,
  .story-top,
  .story-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>