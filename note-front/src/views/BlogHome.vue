<template>
  <div class="blog-home">
    <div class="reading-progress" :style="{ width: scrollProgress + '%' }"></div>
    <SakuraBackground />
    <div class="ambient ambient-a"></div>
    <div class="ambient ambient-b"></div>
    <div class="ambient ambient-c"></div>

    <header class="topbar">
      <div class="brand-block">
        <div class="brand-emblem">
          <span>H</span>
        </div>
        <div>
          <div class="brand-name">AEzer0's Blog</div>
          <div class="brand-sub">这里放着我的思考、灵感，还有一些舍不得丢掉的句子。</div>
        </div>
      </div>

      <div class="topbar-actions">
        <el-input
          v-model="keyword"
          placeholder="搜一搜文章、分类，或者你此刻想看的内容"
          clearable
          @clear="loadPosts"
          @keyup.enter="loadPosts"
          :prefix-icon="Search"
          class="search-input"
        />
        <router-link v-if="isLoggedIn" to="/notebook">
          <el-button class="soft-btn">工作台</el-button>
        </router-link>
        <router-link v-if="isDefaultAdmin" to="/blog/manage">
          <el-button class="glow-btn">进入管理台</el-button>
        </router-link>
      </div>
    </header>

    <section class="overview-strip">
      <article class="overview-card">
        <span>公开文章</span>
        <strong>{{ total }}</strong>
        <small>已经认真写完并放出来的内容</small>
      </article>
      <article class="overview-card">
        <span>分类目录</span>
        <strong>{{ categories.length }}</strong>
        <small>按兴趣分好的阅读入口</small>
      </article>
      <article class="overview-card">
        <span>标签索引</span>
        <strong>{{ tags.length }}</strong>
        <small>一些能顺藤摸瓜的小线索</small>
      </article>
      <article class="overview-card accent">
        <span>可见热度</span>
        <strong>{{ visibleViewCount }}</strong>
        <small>这些文字被路过的人看了这么多次</small>
      </article>
    </section>

    <section class="category-bridge">
      <div class="category-bridge-head">
        <div>
          <div class="control-label">分类漫游</div>
          <h2 class="bridge-title">先挑一个你今天想读的方向</h2>
        </div>
        <span class="bridge-meta">{{ categories.length }} 个话题入口</span>
      </div>

      <div class="category-pills">
        <button :class="['category-pill', { active: !selectedCategoryId }]" @click="selectCategory(null)">
          全部
        </button>
        <button
          v-for="cat in categories"
          :key="cat.id"
          :class="['category-pill', { active: selectedCategoryId === cat.id }]"
          @click="selectCategory(cat.id)"
        >
          {{ cat.name }}
        </button>
      </div>
    </section>

    <section class="showcase-grid">
      <article v-if="featuredPost" class="feature-stage" @click="goDetail(featuredPost.id)">
        <div class="feature-copy">
          <div class="feature-kicker">Featured Entry</div>
          <h1 class="feature-title">{{ featuredPost.title }}</h1>
          <p class="feature-summary">
            {{
              featuredPost.summary ||
              '如果你是第一次来，不妨先从这篇开始。它很适合作为进入这个博客的第一扇门。'
            }}
          </p>

          <div class="feature-meta">
            <span>{{ formatDate(featuredPost.publishTime) }}</span>
            <span v-if="featuredPost.categoryName">{{ featuredPost.categoryName }}</span>
            <span>阅读 {{ featuredPost.viewCount || 0 }}</span>
          </div>

          <div class="feature-actions">
            <button class="inline-action primary" @click.stop="goDetail(featuredPost.id)">阅读全文</button>
            <button class="inline-action" @click.stop="clearAllFilters">浏览全部文章</button>
          </div>
        </div>

        <div class="feature-visual">
          <img v-if="featuredPost.coverUrl" :src="featuredPost.coverUrl" class="feature-cover" alt="" />
          <div v-else class="feature-fallback">{{ getPostMark(featuredPost.categoryName) }}</div>
        </div>
      </article>

      <article v-else class="feature-stage empty">
        <div class="feature-copy">
          <div class="feature-kicker">Featured Entry</div>
          <h1 class="feature-title">这里很快会热闹起来</h1>
          <p class="feature-summary">第一篇公开文章还在路上。等它出现，这里就会成为整座博客的开场白。</p>
        </div>
      </article>

      <aside class="about-stack">
        <section class="aside-card about-card">
          <div class="aside-kicker">About</div>
          <h3 class="aside-title">写一点真实的东西，留给愿意读的人</h3>
          <p class="aside-desc">
            这里记录技术、想法、生活里的碎片，也收留一些深夜突然想明白的小事。更新未必勤快，但大多数时候，我会尽量写得认真一点。
          </p>

          <div class="about-stats">
            <div class="about-stat">
              <strong>{{ total }}</strong>
              <span>文章</span>
            </div>
            <div class="about-stat">
              <strong>{{ categories.length }}</strong>
              <span>分类</span>
            </div>
            <div class="about-stat">
              <strong>{{ tags.length }}</strong>
              <span>标签</span>
            </div>
          </div>

          <div class="publishing-note">
            <div class="aside-kicker">About This Place</div>
            <h4 class="publishing-title">这里没有标准答案，只有持续留下的想法</h4>
            <p class="publishing-desc">
              你会看到一些技术记录、一些经验总结，也会碰到偶尔跑偏的随笔。它们不一定都很郑重，但都是我认真留下来的东西。
            </p>

            <div class="publishing-actions">
              <button class="inline-action primary" @click="clearAllFilters">查看全部文章</button>
              <button v-if="featuredPost" class="inline-action" @click="goDetail(featuredPost.id)">先看推荐内容</button>
            </div>
          </div>
        </section>

        <section class="aside-card pulse-card">
          <div class="aside-kicker">Reading Pulse</div>
          <div class="pulse-row">
            <div>
              <h3 class="pulse-title">{{ selectedCategoryName || '全部分类' }}</h3>
              <p class="pulse-desc">这一页里一共有 {{ filteredPosts.length }} 篇内容，慢慢翻就好。</p>
            </div>
            <span class="pulse-badge">{{
              selectedTagIds.length ? `${selectedTagIds.length} 个标签筛选中` : '随便逛逛模式'
            }}</span>
          </div>
        </section>
      </aside>
    </section>

    <section v-if="tags.length" class="control-panel">
      <div class="control-block">
        <div class="control-row">
          <div class="control-label">标签筛选</div>
          <button v-if="hasFilters" class="tiny-action" @click="clearAllFilters">清空</button>
        </div>
        <div class="tag-pills">
          <button
            v-for="tag in topTags"
            :key="tag.id"
            :class="['tag-pill', { active: selectedTagIds.includes(tag.id) }]"
            @click="toggleTag(tag.id)"
          >
            # {{ tag.name }}
          </button>
        </div>
      </div>
    </section>

    <div class="content-layout">
      <main class="feed-column">
        <div class="section-head">
          <div>
            <div class="section-kicker">Fresh Notes</div>
            <h2 class="section-title">最近写下的内容</h2>
          </div>
          <div class="section-meta">
            <span>{{ filteredPosts.length }} 篇内容</span>
            <span>{{ selectedCategoryName || '全部分类' }}</span>
          </div>
        </div>

        <transition-group name="card-fade" tag="div" class="feed-list" v-loading="loading">
          <article
            v-for="post in feedPosts"
            :key="post.id"
            class="feed-card"
            @click="goDetail(post.id)"
          >
            <div class="feed-cover-box" v-if="post.coverUrl">
              <img :src="post.coverUrl" class="feed-cover" alt="" />
            </div>
            <div v-else class="feed-cover-box fallback">
              <span>{{ getPostMark(post.categoryName) }}</span>
            </div>

            <div class="feed-content">
              <div class="feed-topline">
                <span v-if="post.categoryName" class="feed-category">{{ post.categoryName }}</span>
                <span v-if="post.isTop" class="feed-badge">置顶</span>
                <span class="feed-date">{{ formatDate(post.publishTime) }}</span>
              </div>
              <h3 class="feed-title">{{ post.title }}</h3>
              <p class="feed-summary">{{ post.summary || '这篇没有提前剧透，适合直接点进去读。' }}</p>
              <div class="feed-footer">
                <div class="feed-tags" v-if="post.tags && post.tags.length">
                  <span v-for="tag in post.tags" :key="tag.id" class="feed-tag"># {{ tag.name }}</span>
                </div>
                <div class="feed-views">阅读 {{ post.viewCount || 0 }}</div>
              </div>
            </div>
          </article>
        </transition-group>

        <div v-if="!loading && filteredPosts.length === 0" class="empty-state">
          <div class="empty-mark">HALO</div>
          <p>这里暂时还很安静，等下一篇文章来打破沉默。</p>
        </div>

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
      </main>

      <aside class="aside-column">
        <section v-if="recentPosts.length" class="aside-card">
          <div class="aside-row">
            <h3 class="aside-title small">刚刚更新</h3>
          </div>
          <div class="mini-list">
            <button
              v-for="post in recentPosts"
              :key="post.id"
              class="mini-item"
              @click="goDetail(post.id)"
            >
              <span class="mini-item-title">{{ post.title }}</span>
              <span class="mini-item-date">{{ formatDate(post.publishTime) }}</span>
            </button>
          </div>
        </section>

        <section v-if="sidebarPostGroups.length" class="aside-card">
          <div class="aside-row">
            <h3 class="aside-title small">按分类找文章</h3>
          </div>
          <div class="mini-list">
            <button
              v-for="group in sidebarPostGroups"
              :key="group.key"
              class="mini-item compact"
              @click="group.key !== '__uncategorized__' ? selectCategory(group.key) : selectCategory(null)"
            >
              <span class="mini-item-title">{{ group.label }}</span>
              <span class="mini-item-date">{{ group.posts.length }}</span>
            </button>
          </div>
        </section>

        <section v-if="tags.length" class="aside-card">
          <div class="aside-row">
            <h3 class="aside-title small">标签云</h3>
            <button v-if="selectedTagIds.length" class="tiny-action" @click="clearTagFilter">清空</button>
          </div>
          <div class="tag-cloud">
            <span
              v-for="tag in tags"
              :key="tag.id"
              :class="['cloud-tag', { active: selectedTagIds.includes(tag.id) }]"
              @click="toggleTag(tag.id)"
            >
              {{ tag.name }}
            </span>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import SakuraBackground from '@/components/SakuraBackground.vue'
import { getPublicBlogPosts, getMyTags, getPublicCategories } from '@/api/blog'
import { useUserStore } from '@/store'

const router = useRouter()
const userStore = useUserStore()

const isLoggedIn = computed(() => userStore.isLoggedIn)
const isDefaultAdmin = computed(() => userStore.isDefaultAdmin)

const loading = ref(false)
const posts = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const selectedCategoryId = ref(null)
const selectedTagIds = ref([])
const categories = ref([])
const tags = ref([])
const scrollProgress = ref(0)

let scrollHandler = null

const filteredPosts = computed(() => {
  if (selectedTagIds.value.length <= 1) return posts.value

  return posts.value.filter((post) => {
    const postTagIds = (post.tags || []).map((tag) => tag.id)
    return selectedTagIds.value.every((id) => postTagIds.includes(id))
  })
})

const featuredPost = computed(() => {
  if (currentPage.value !== 1) return null
  return filteredPosts.value.find((post) => post.isTop) || filteredPosts.value[0] || null
})

const feedPosts = computed(() => {
  if (!featuredPost.value) return filteredPosts.value
  return filteredPosts.value.filter((post) => post.id !== featuredPost.value.id)
})

const recentPosts = computed(() => {
  return [...posts.value]
    .sort((a, b) => new Date(b.publishTime || 0) - new Date(a.publishTime || 0))
    .slice(0, 5)
})

const sidebarPostGroups = computed(() => {
  const uncategorizedKey = '__uncategorized__'
  const groups = new Map()

  filteredPosts.value.forEach((post) => {
    const key = post.categoryId ?? uncategorizedKey
    const label = post.categoryName || '未分类'

    if (!groups.has(key)) {
      groups.set(key, { key, label, posts: [] })
    }

    groups.get(key).posts.push(post)
  })

  return Array.from(groups.values())
    .map((group) => ({
      ...group,
      posts: [...group.posts].sort((a, b) => new Date(b.publishTime || 0) - new Date(a.publishTime || 0))
    }))
    .sort((a, b) => {
      if (a.key === uncategorizedKey) return 1
      if (b.key === uncategorizedKey) return -1
      return a.label.localeCompare(b.label, 'zh-Hans-CN')
    })
})

const visibleViewCount = computed(() => {
  return filteredPosts.value.reduce((sum, post) => sum + (post.viewCount || 0), 0)
})

const selectedCategoryName = computed(() => {
  return categories.value.find((cat) => cat.id === selectedCategoryId.value)?.name || ''
})

const topTags = computed(() => tags.value.slice(0, 12))

const hasFilters = computed(() => {
  return Boolean(selectedCategoryId.value || selectedTagIds.value.length || keyword.value)
})

const setupScrollProgress = () => {
  scrollHandler = () => {
    const scrollTop = window.scrollY
    const docHeight = document.documentElement.scrollHeight - window.innerHeight
    scrollProgress.value = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0
  }

  window.addEventListener('scroll', scrollHandler, { passive: true })
}

const loadPosts = async () => {
  loading.value = true

  try {
    const params = { page: currentPage.value, size: pageSize.value }

    if (keyword.value) params.keyword = keyword.value
    if (selectedCategoryId.value) params.categoryId = selectedCategoryId.value
    if (selectedTagIds.value.length) params.tagId = selectedTagIds.value[0]

    const res = await getPublicBlogPosts(params)
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

const loadSidebarData = async () => {
  try {
    const [categoryRes, tagRes] = await Promise.allSettled([getPublicCategories(), getMyTags()])
    if (categoryRes.status === 'fulfilled') categories.value = categoryRes.value.data.data || []
    if (tagRes.status === 'fulfilled') tags.value = tagRes.value.data.data || []
  } catch (error) {
    // optional sidebar data
  }
}

const selectCategory = (id) => {
  selectedCategoryId.value = id
  currentPage.value = 1
  loadPosts()
}

const toggleTag = (id) => {
  const index = selectedTagIds.value.indexOf(id)

  if (index >= 0) {
    selectedTagIds.value.splice(index, 1)
  } else {
    selectedTagIds.value.push(id)
  }

  currentPage.value = 1
  loadPosts()
}

const clearTagFilter = () => {
  selectedTagIds.value = []
  currentPage.value = 1
  loadPosts()
}

const clearAllFilters = () => {
  selectedCategoryId.value = null
  selectedTagIds.value = []
  keyword.value = ''
  currentPage.value = 1
  loadPosts()
}

const getPostMark = (categoryName) => {
  const text = (categoryName || '').trim()
  return text ? text.charAt(0) : 'B'
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadPosts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const goDetail = (id) => {
  router.push(`/blog/${id}`)
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''

  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      // ignore invalid session on public page
    }
  }

  loadPosts()
  loadSidebarData()
  setupScrollProgress()
})

onUnmounted(() => {
  if (scrollHandler) window.removeEventListener('scroll', scrollHandler)
})
</script>

<style scoped>
.blog-home {
  --surface: rgba(255, 255, 255, 0.76);
  --surface-strong: rgba(255, 255, 255, 0.88);
  --border: rgba(175, 193, 232, 0.22);
  --text-main: #24304b;
  --text-soft: #69748f;
  --brand-1: #6d8cff;
  --brand-2: #83d8ea;
  --brand-3: #ffc0d6;
  --shadow: 0 18px 52px rgba(84, 100, 154, 0.12);
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  overflow-x: hidden;
  padding: 22px;
  color: var(--text-main);
  background:
    radial-gradient(circle at 8% 10%, rgba(255, 192, 214, 0.26), transparent 18%),
    radial-gradient(circle at 88% 16%, rgba(131, 216, 234, 0.25), transparent 18%),
    radial-gradient(circle at 76% 76%, rgba(255, 217, 154, 0.2), transparent 24%),
    linear-gradient(180deg, #f7f7ff 0%, #fffaf7 42%, #f6f8ff 100%);
  font-family: "Avenir Next", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

.reading-progress {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 999;
  height: 3px;
  background: linear-gradient(90deg, var(--brand-1), var(--brand-2), var(--brand-3));
  transition: width 0.15s ease;
}

.ambient {
  position: fixed;
  pointer-events: none;
  filter: blur(54px);
  opacity: 0.6;
}

.ambient-a {
  top: 120px;
  right: 8%;
  width: 220px;
  height: 220px;
  background: rgba(131, 216, 234, 0.16);
}

.ambient-b {
  top: 380px;
  left: 6%;
  width: 260px;
  height: 260px;
  background: rgba(255, 192, 214, 0.16);
}

.ambient-c {
  bottom: 120px;
  right: 18%;
  width: 240px;
  height: 240px;
  background: rgba(255, 217, 154, 0.14);
}

.topbar,
.overview-strip,
.category-bridge,
.showcase-grid,
.control-panel,
.content-layout {
  position: relative;
  z-index: 1;
  max-width: 1260px;
  margin: 0 auto 18px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  padding: 18px 20px;
  border-radius: 28px;
  background: var(--surface);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow);
  backdrop-filter: blur(18px);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-emblem {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, rgba(109, 140, 255, 0.95), rgba(131, 216, 234, 0.92));
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  box-shadow: 0 10px 24px rgba(109, 140, 255, 0.24);
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
}

.brand-sub,
.feature-kicker,
.aside-kicker,
.section-kicker,
.control-label {
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #7b86a6;
}

.topbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.search-input {
  width: 320px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 999px !important;
  background: rgba(255, 255, 255, 0.86) !important;
  box-shadow: none !important;
  border: 1px solid rgba(169, 185, 255, 0.22) !important;
}

.soft-btn,
.glow-btn {
  border-radius: 999px !important;
  padding: 0 18px !important;
  font-weight: 600 !important;
}

.soft-btn {
  background: rgba(255, 255, 255, 0.78) !important;
  border: 1px solid rgba(169, 185, 255, 0.24) !important;
  color: var(--text-main) !important;
}

.glow-btn {
  background: linear-gradient(135deg, #6d8cff, #83d8ea) !important;
  border: none !important;
  color: #fff !important;
}

.overview-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.overview-card,
.feature-stage,
.aside-card,
.control-panel,
.feed-column {
  background: var(--surface);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow);
}

.overview-card {
  padding: 18px 20px;
  border-radius: 24px;
}

.overview-card span,
.overview-card strong,
.overview-card small {
  display: block;
}

.overview-card span,
.overview-card small {
  color: var(--text-soft);
}

.overview-card strong {
  margin: 10px 0 6px;
  font-size: 34px;
}

.overview-card.accent {
  background: linear-gradient(135deg, rgba(109, 140, 255, 0.9), rgba(131, 216, 234, 0.84));
  color: #fff;
}

.overview-card.accent span,
.overview-card.accent small {
  color: rgba(255, 255, 255, 0.9);
}

.category-bridge {
  padding: 18px 20px;
  border-radius: 28px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.84), rgba(247, 249, 255, 0.72)),
    var(--surface);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow);
}

.category-bridge-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.bridge-title {
  margin: 8px 0 0;
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
  font-size: 26px;
  letter-spacing: -0.02em;
}

.bridge-meta {
  color: #7b86a6;
  font-size: 12px;
}

.showcase-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 360px;
  gap: 18px;
  align-items: start;
}

.feature-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  padding: 22px;
  border-radius: 32px;
  cursor: pointer;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.86), rgba(247, 249, 255, 0.7)),
    var(--surface);
}

.feature-stage.empty {
  grid-template-columns: 1fr;
  cursor: default;
}

.feature-title,
.aside-title,
.section-title,
.publishing-title {
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
  letter-spacing: -0.02em;
}

.feature-title {
  margin: 12px 0;
  font-size: 42px;
  line-height: 1.14;
}

.feature-summary,
.aside-desc,
.publishing-desc,
.pulse-desc,
.feed-summary {
  color: var(--text-soft);
  line-height: 1.78;
}

.feature-meta,
.feature-actions,
.about-stats,
.publishing-actions,
.feature-topline,
.feed-topline,
.feed-footer,
.aside-row,
.pulse-row {
  display: flex;
  flex-wrap: wrap;
}

.feature-meta {
  gap: 10px;
  font-size: 12px;
  color: #7b86a6;
}

.feature-actions {
  gap: 12px;
  margin-top: 24px;
}

.inline-action {
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.74);
  color: var(--text-main);
  cursor: pointer;
  font-weight: 600;
}

.inline-action.primary {
  background: linear-gradient(135deg, rgba(109, 140, 255, 0.16), rgba(255, 192, 214, 0.2));
  color: #4964cf;
}

.feature-visual {
  min-height: 360px;
  border-radius: 28px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(109, 140, 255, 0.14), rgba(255, 192, 214, 0.18));
}

.feature-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.feature-fallback {
  min-height: 360px;
  display: grid;
  place-items: center;
  color: #6982d2;
  font-size: 72px;
  font-weight: 700;
}

.about-stack,
.aside-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.aside-card {
  padding: 20px;
  border-radius: 28px;
}

.aside-title {
  margin: 10px 0 12px;
  font-size: 24px;
}

.aside-title.small {
  margin: 0;
  font-size: 18px;
}

.about-stats {
  gap: 10px;
  margin-top: 18px;
}

.about-stat {
  flex: 1 1 calc(33.33% - 8px);
  min-width: 82px;
  padding: 14px 10px;
  border-radius: 20px;
  text-align: center;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid var(--border);
}

.about-stat strong {
  display: block;
  font-size: 24px;
}

.about-stat span {
  color: var(--text-soft);
  font-size: 13px;
}

.publishing-note {
  margin-top: 18px;
  padding: 18px;
  border-radius: 22px;
  background:
    linear-gradient(135deg, rgba(109, 140, 255, 0.08), rgba(255, 192, 214, 0.12)),
    rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(169, 185, 255, 0.16);
}

.publishing-title {
  margin: 8px 0 10px;
  font-size: 22px;
  line-height: 1.35;
}

.publishing-actions {
  gap: 10px;
  margin-top: 16px;
}

.pulse-card {
  background:
    linear-gradient(135deg, rgba(109, 140, 255, 0.1), rgba(131, 216, 234, 0.1)),
    var(--surface);
}

.pulse-row {
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.pulse-title {
  margin: 8px 0;
  font-size: 24px;
}

.pulse-badge {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  color: #6476b5;
  font-size: 12px;
  white-space: nowrap;
}

.control-panel {
  padding: 18px 20px;
  border-radius: 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.control-row,
.category-pills,
.tag-pills,
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.control-row {
  justify-content: space-between;
}

.category-pill,
.tag-pill,
.cloud-tag {
  border: 1px solid rgba(169, 185, 255, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  cursor: pointer;
  transition: 0.24s ease;
}

.category-pill {
  padding: 10px 14px;
}

.tag-pill,
.cloud-tag {
  padding: 8px 12px;
  font-size: 13px;
}

.category-pill.active,
.category-pill:hover,
.tag-pill.active,
.tag-pill:hover,
.cloud-tag.active,
.cloud-tag:hover {
  background: linear-gradient(135deg, rgba(109, 140, 255, 0.92), rgba(131, 216, 234, 0.82));
  color: #fff;
  border-color: transparent;
}

.tiny-action {
  border: none;
  background: none;
  color: #e188aa;
  cursor: pointer;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
}

.feed-column {
  padding: 20px;
  border-radius: 28px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 16px;
}

.section-title {
  margin: 8px 0 0;
  font-size: 30px;
}

.section-meta,
.feed-date,
.feed-views,
.mini-item-date {
  color: #7b86a6;
  font-size: 12px;
}

.section-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.feed-card {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 18px;
  padding: 18px;
  border-radius: 24px;
  margin-bottom: 16px;
  background: var(--surface-strong);
  border: 1px solid rgba(169, 185, 255, 0.14);
  cursor: pointer;
}

.feed-cover-box {
  min-height: 170px;
  border-radius: 22px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(109, 140, 255, 0.14), rgba(255, 192, 214, 0.16));
}

.feed-cover-box.fallback {
  display: grid;
  place-items: center;
  font-size: 46px;
  color: #7283d8;
  font-weight: 700;
}

.feed-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.feed-topline {
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.feed-category,
.feed-badge,
.feed-tag {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
}

.feed-category {
  background: rgba(109, 140, 255, 0.08);
  color: #5f78d7;
}

.feed-badge {
  background: rgba(255, 192, 214, 0.16);
  color: #d97198;
}

.feed-date {
  margin-left: auto;
}

.feed-title {
  margin: 0 0 10px;
  font-size: 24px;
}

.feed-footer {
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.feed-tags,
.mini-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.feed-tag {
  background: rgba(131, 216, 234, 0.12);
  color: #4c87a0;
}

.mini-list {
  flex-direction: column;
}

.mini-item {
  border: 1px solid rgba(169, 185, 255, 0.12);
  background: rgba(255, 255, 255, 0.78);
  border-radius: 18px;
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  text-align: left;
}

.mini-item-title {
  color: var(--text-main);
}

.empty-state {
  padding: 54px 0 26px;
  text-align: center;
  color: var(--text-soft);
}

.empty-mark {
  width: 80px;
  height: 80px;
  margin: 0 auto 14px;
  border-radius: 26px;
  display: grid;
  place-items: center;
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  background: linear-gradient(135deg, rgba(109, 140, 255, 0.16), rgba(255, 192, 214, 0.18));
  color: #5d78db;
  font-weight: 700;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.card-fade-enter-active {
  transition: all 0.34s ease;
}

.card-fade-leave-active {
  transition: all 0.2s ease;
}

.card-fade-enter-from,
.card-fade-leave-to {
  opacity: 0;
  transform: translateY(18px);
}

@media (max-width: 1120px) {
  .overview-strip,
  .showcase-grid,
  .content-layout {
    grid-template-columns: 1fr;
  }

  .feature-stage {
    grid-template-columns: 1fr;
  }

  .feature-visual {
    min-height: 280px;
  }

  .feature-fallback {
    min-height: 280px;
  }
}

@media (max-width: 760px) {
  .blog-home {
    padding: 12px;
  }

  .topbar,
  .category-bridge-head,
  .section-head,
  .pulse-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .search-input {
    width: 100%;
  }

  .feature-title {
    font-size: 32px;
  }

  .feed-card {
    grid-template-columns: 1fr;
  }

  .feed-date {
    margin-left: 0;
  }
}
</style>
