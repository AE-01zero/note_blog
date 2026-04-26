<template>
  <div class="blog-home">
    <SakuraBackground />
    <div class="reading-progress" :style="{ width: `${scrollProgress}%` }"></div>

    <header class="topbar">
      <div class="brand-block">
        <span class="panel-kicker">SIGNAL LANDING</span>
        <h1 class="brand-name">AEzer0 的博客</h1>
        <p class="brand-sub">
          记录代码、系统、阅读与值得反复回看的想法。
        </p>
        <p class="brand-note">
          这里更像一个安静的个人阅读入口，适合慢慢看，而不是匆匆扫过。
        </p>
      </div>

      <div class="topbar-actions">
        <el-input
          v-model="keyword"
          placeholder="搜一篇文章、分类、标签或关键词"
          clearable
          @clear="loadPosts"
          @keyup.enter="loadPosts"
          :prefix-icon="Search"
          class="search-input"
        />
        <router-link v-if="isLoggedIn" to="/notebook">
          <el-button class="soft-btn">进入工作台</el-button>
        </router-link>
        <router-link v-if="isDefaultAdmin" to="/blog/manage">
          <el-button class="glow-btn">管理博客</el-button>
        </router-link>
      </div>
    </header>

    <section class="overview-strip">
      <article class="overview-card">
        <div class="overview-head">
          <span class="overview-mark">DAY</span>
          <span class="overview-label">今日文章</span>
        </div>
        <strong>{{ todayPostsCount }}</strong>
        <small>当前筛选结果里今天新增的阅读内容。</small>
      </article>

      <article class="overview-card">
        <div class="overview-head">
          <span class="overview-mark">TXT</span>
          <span class="overview-label">公开文章</span>
        </div>
        <strong>{{ total }}</strong>
        <small>已经整理好、可以直接开始阅读的公开内容。</small>
      </article>

      <article class="overview-card">
        <div class="overview-head">
          <span class="overview-mark">TAG</span>
          <span class="overview-label">标签索引</span>
        </div>
        <strong>{{ tags.length }}</strong>
        <small>帮助你快速串联相关文章的阅读线索。</small>
      </article>

      <article class="overview-card accent">
        <div class="overview-head">
          <span class="overview-mark">PULSE</span>
          <span class="overview-label">灵感坐标</span>
        </div>
        <strong>{{ visibleViewCount }}</strong>
        <small>当前页面里这些文章累计收获的阅读次数。</small>
      </article>
    </section>

    <section class="category-bridge">
      <div class="category-bridge-head">
        <div>
          <div class="control-label">阅读方向</div>
          <h2 class="bridge-title">今天想从哪里开始？</h2>
          <p class="bridge-sub">
            选择一个方向，快速进入你的阅读状态。
          </p>
        </div>
        <span class="bridge-meta">共 {{ categories.length }} 个主题入口</span>
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
          <div class="feature-topline">
            <span class="panel-kicker">首页推荐</span>
            <span class="hero-badge">今日主阅读入口</span>
            <span v-if="featuredPost.isTop" class="hero-badge primary">置顶</span>
            <span v-if="featuredPost.categoryName" class="hero-badge muted">{{ featuredPost.categoryName }}</span>
          </div>

          <h2 class="feature-title">{{ featuredPost.title }}</h2>
          <p class="feature-summary">{{ featureSummary }}</p>

          <div class="feature-meta">
            <span>{{ formatDate(featuredPost.publishTime) }}</span>
            <span>预计 {{ featuredReadMinutes }} 分钟读完</span>
            <span>{{ formatCompactNumber(featuredWordCount) }} 字</span>
            <span>{{ featuredPost.viewCount || 0 }} 次阅读</span>
          </div>

          <div class="feature-signal-grid">
            <article class="signal-card">
              <span class="signal-label">当前阅读范围</span>
              <strong class="signal-value">{{ selectedCategoryName || '全部' }}</strong>
              <small class="signal-hint">你当前正在浏览的阅读方向。</small>
            </article>
            <article class="signal-card">
              <span class="signal-label">今日更新</span>
              <strong class="signal-value">{{ todayPostsCount }}</strong>
              <small class="signal-hint">当前页数据里今天发布的文章数量。</small>
            </article>
            <article class="signal-card">
              <span class="signal-label">后续文章</span>
              <strong class="signal-value">{{ feedPosts.length }}</strong>
              <small class="signal-hint">推荐文章下方还有多少篇可继续阅读。</small>
            </article>
          </div>

          <div class="feature-actions">
            <button class="inline-action primary" @click.stop="goDetail(featuredPost.id)">阅读全文</button>
            <button class="inline-action" @click.stop="scrollToFeed">继续查看其余文章</button>
          </div>
        </div>

        <div class="feature-side">
          <div class="feature-visual">
            <img v-if="featuredPost.coverUrl" :src="featuredPost.coverUrl" class="feature-cover" alt="" />
            <div v-else class="feature-fallback">
              <span class="cover-badge">{{ featuredPost.categoryName || '博客' }}</span>
              <strong class="cover-mark">{{ getPostMark(featuredPost.categoryName || featuredPost.title) }}</strong>
              <p>{{ featureSummary }}</p>
            </div>
          </div>

          <div class="feature-outline">
            <div class="outline-head">
              <div>
                <span class="panel-kicker">阅读指引</span>
                <h3>可以这样浏览这个页面</h3>
              </div>
              <span class="outline-count">{{ filteredPosts.length }} 篇文章</span>
            </div>
            <div class="outline-item">
              <span class="outline-level">01</span>
              <span class="outline-text">先从推荐文章开始，快速进入当前主题。</span>
            </div>
            <div class="outline-item" v-if="feedPosts.length">
              <span class="outline-level">02</span>
              <span class="outline-text">再继续往下看另外 {{ feedPosts.length }} 篇相关文章。</span>
            </div>
            <div class="outline-item">
              <span class="outline-level">03</span>
              <span class="outline-text">右侧栏还能帮助你查看最近更新、分类入口和标签归档。</span>
            </div>
          </div>
        </div>
      </article>

      <article v-else class="feature-stage empty">
        <div class="feature-copy">
          <div class="panel-kicker">首页推荐</div>
          <h2 class="feature-title">首页正在等待下一篇公开文章。</h2>
          <p class="feature-summary">
            下一篇公开内容发布后，会自动出现在这里作为新的阅读入口。
          </p>
        </div>
      </article>

      <aside class="blog-sidebar">
        <section class="aside-card about-card">
          <div class="aside-kicker">关于这里</div>
          <h3 class="aside-title">写一些真实的东西，留给愿意慢慢读的人。</h3>
          <p class="aside-desc">
            这里会放技术笔记、阶段性总结，以及那些值得回头再看的思考片段。
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
            <div class="aside-kicker">这个空间</div>
            <h4 class="publishing-title">没有标准答案，只有持续生长的想法。</h4>
            <p class="publishing-desc">
              有些文章更偏工程实践，有些更像缓慢整理后的个人思考，但都会尽量保持清晰、耐读。
            </p>

            <div class="publishing-actions">
              <button class="inline-action primary" @click="clearAllFilters">查看全部文章</button>
              <button v-if="featuredPost" class="inline-action" @click="goDetail(featuredPost.id)">阅读推荐文章</button>
            </div>
          </div>
        </section>

        <section class="aside-card pulse-card">
          <div class="aside-kicker">阅读状态</div>
          <div class="pulse-row">
            <div>
              <h3 class="pulse-title">{{ selectedCategoryName || '全部分类' }}</h3>
              <p class="pulse-desc">当前阅读状态下可见 {{ filteredPosts.length }} 篇文章。</p>
            </div>
            <span class="pulse-badge">
              {{ selectedTagIds.length ? `已选 ${selectedTagIds.length} 个标签` : '开放浏览模式' }}
            </span>
          </div>
        </section>
        <!--
        <section v-if="recentPosts.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">鏈€杩戞洿鏂?/h3>
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

        <section v-if="sidebarPostGroups.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">鎸夊垎绫绘祻瑙?/h3>
          </div>
          <div class="mini-list">
            <button
              v-for="group in sidebarPostGroups"
              :key="group.key"
              class="mini-item compact"
              @click="group.key !== uncategorizedKey ? selectCategory(group.key) : selectCategory(null)"
            >
              <span class="mini-item-title">{{ group.label }}</span>
              <span class="mini-item-date">{{ group.posts.length }}</span>
            </button>
          </div>
        </section>

        <section v-if="tags.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">鏍囩褰掓。</h3>
            <button v-if="selectedTagIds.length" class="tiny-action" @click="clearTagFilter">娓呯┖</button>
          </div>
          <div class="tag-cloud">
            <button
              v-for="tag in tags"
              :key="tag.id"
              type="button"
              :class="['cloud-tag', { active: selectedTagIds.includes(tag.id) }]"
              @click="toggleTag(tag.id)"
            >
              {{ tag.name }}
            </button>
          </div>
        </section>
        -->

        <section v-if="recentPosts.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">最近更新</h3>
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

        <section v-if="sidebarPostGroups.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">按分类浏览</h3>
          </div>
          <div class="mini-list">
            <button
              v-for="group in sidebarPostGroups"
              :key="group.key"
              class="mini-item compact"
              @click="group.key !== uncategorizedKey ? selectCategory(group.key) : selectCategory(null)"
            >
              <span class="mini-item-title">{{ group.label }}</span>
              <span class="mini-item-date">{{ group.posts.length }}</span>
            </button>
          </div>
        </section>

        <section v-if="tags.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">标签归档</h3>
            <button v-if="selectedTagIds.length" class="tiny-action" @click="clearTagFilter">清空</button>
          </div>
          <div class="tag-cloud">
            <button
              v-for="tag in tags"
              :key="tag.id"
              type="button"
              :class="['cloud-tag', { active: selectedTagIds.includes(tag.id) }]"
              @click="toggleTag(tag.id)"
            >
              {{ tag.name }}
            </button>
          </div>
        </section>
      </aside>
    </section>

    <section v-if="tags.length" class="control-panel">
      <div class="control-row">
        <div>
          <div class="control-label">标签筛选</div>
          <p class="control-sub">用更细一点的标签，帮你缩小阅读范围，但不会打断整体浏览节奏。</p>
        </div>
        <button v-if="hasFilters" class="tiny-action" @click="clearAllFilters">清空筛选</button>
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
    </section>

    <div class="content-layout">
      <main class="feed-column" ref="feedSectionRef">
        <section v-if="tags.length" class="control-panel embedded-control-panel">
          <div class="control-row">
            <div>
              <div class="control-label">标签筛选</div>
              <p class="control-sub">用更细一点的标签，帮你缩小阅读范围，但不会打断整体浏览节奏。</p>
            </div>
            <button v-if="hasFilters" class="tiny-action" @click="clearAllFilters">清空筛选</button>
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
        </section>

        <div class="section-head">
          <div>
            <div class="section-kicker">最近写下的内容</div>
            <h2 class="section-title">最近文章，按更清晰的阅读流排列</h2>
          </div>
          <div class="section-meta">
            <span>{{ filteredPosts.length }} 篇文章</span>
            <span>{{ selectedCategoryName || '全部分类' }}</span>
            <span v-if="featuredPost">推荐文章已在上方展示</span>
          </div>
        </div>

        <div v-if="featuredPost" class="feed-intro-card">
          <span class="panel-kicker">文章流</span>
          <h3>继续阅读剩下的内容</h3>
          <p>
            上方推荐文章是主入口，下面则按更清晰的顺序展示其余公开文章。
          </p>
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
              <span>{{ getPostMark(post.categoryName || post.title) }}</span>
            </div>

            <div class="feed-content">
              <div class="feed-topline">
                <span v-if="post.categoryName" class="feed-category">{{ post.categoryName }}</span>
                <span v-if="post.isTop" class="feed-badge">置顶</span>
                <span class="feed-date">{{ formatDate(post.publishTime) }}</span>
              </div>
              <h3 class="feed-title">{{ post.title }}</h3>
              <p class="feed-summary">{{ post.summary || fallbackSummary(post) }}</p>
              <div class="feed-footer">
                <div class="feed-tags" v-if="post.tags && post.tags.length">
                  <span v-for="tag in post.tags" :key="tag.id" class="feed-tag"># {{ tag.name }}</span>
                </div>
                <div class="feed-views">{{ estimateReadMinutes(post) }} 分钟 / {{ post.viewCount || 0 }} 次阅读</div>
              </div>
            </div>
          </article>
        </transition-group>

        <div v-if="!loading && filteredPosts.length === 0" class="empty-state">
          <div class="empty-mark">CALM</div>
          <p>当前筛选条件下还没有公开文章，试试放宽筛选继续浏览。</p>
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
        <section v-if="recentPosts.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">最近更新</h3>
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

        <section v-if="sidebarPostGroups.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">按分类浏览</h3>
          </div>
          <div class="mini-list">
            <button
              v-for="group in sidebarPostGroups"
              :key="group.key"
              class="mini-item compact"
              @click="group.key !== uncategorizedKey ? selectCategory(group.key) : selectCategory(null)"
            >
              <span class="mini-item-title">{{ group.label }}</span>
              <span class="mini-item-date">{{ group.posts.length }}</span>
            </button>
          </div>
        </section>

        <section v-if="tags.length" class="aside-card compact-card">
          <div class="aside-row">
            <h3 class="aside-title small">标签归档</h3>
            <button v-if="selectedTagIds.length" class="tiny-action" @click="clearTagFilter">清空</button>
          </div>
          <div class="tag-cloud">
            <button
              v-for="tag in tags"
              :key="tag.id"
              type="button"
              :class="['cloud-tag', { active: selectedTagIds.includes(tag.id) }]"
              @click="toggleTag(tag.id)"
            >
              {{ tag.name }}
            </button>
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

const uncategorizedKey = '__uncategorized__'

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
const feedSectionRef = ref(null)

let scrollHandler = null

const cleanMarkdownText = (content = '') => content
  .replace(/```[\s\S]*?```/g, ' ')
  .replace(/`[^`]*`/g, ' ')
  .replace(/!\[[^\]]*\]\([^)]+\)/g, ' ')
  .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
  .replace(/^#{1,6}\s+/gm, '')
  .replace(/^>\s?/gm, '')
  .replace(/[*_~|-]/g, ' ')
  .replace(/\d+\.\s+/g, ' ')
  .replace(/\n+/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

const estimateReadMinutes = (post) => {
  const words = cleanMarkdownText(post?.contentMd || '').length
  return Math.max(1, Math.ceil(words / 420))
}

const fallbackSummary = (post) => {
  const plain = cleanMarkdownText(post?.contentMd || '')
  if (!plain) return '打开文章即可继续阅读全文。'
  return plain.length > 110 ? `${plain.slice(0, 110)}...` : plain
}

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

const featureSummary = computed(() => {
  const featured = featuredPost.value
  if (featured?.summary?.trim()) return featured.summary.trim()
  const plain = cleanMarkdownText(featured?.contentMd || '')
  return plain.length > 140 ? `${plain.slice(0, 140)}...` : (plain || '打开这篇推荐文章，进入今天最适合开始阅读的主线。')
})

const featuredWordCount = computed(() => cleanMarkdownText(featuredPost.value?.contentMd || '').length)
const featuredReadMinutes = computed(() => Math.max(1, Math.ceil(featuredWordCount.value / 420)))

const feedPosts = computed(() => {
  const featured = featuredPost.value
  if (!featured) return filteredPosts.value
  return filteredPosts.value.filter((post) => post.id !== featured.id)
})

const selectedCategoryName = computed(() => {
  return categories.value.find((cat) => cat.id === selectedCategoryId.value)?.name || ''
})

const topTags = computed(() => tags.value.slice(0, 12))
const hasFilters = computed(() => Boolean(selectedCategoryId.value || selectedTagIds.value.length || keyword.value))

const recentPosts = computed(() => {
  return [...posts.value]
    .sort((a, b) => new Date(b.publishTime || 0) - new Date(a.publishTime || 0))
    .slice(0, 5)
})

const sidebarPostGroups = computed(() => {
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
  return filteredPosts.value.reduce((sum, post) => sum + Number(post.viewCount || 0), 0)
})

const todayPostsCount = computed(() => {
  const today = new Date().toDateString()
  return filteredPosts.value.filter((post) => {
    if (!post.publishTime) return false
    return new Date(post.publishTime).toDateString() === today
  }).length
})

const setupScrollProgress = () => {
  scrollHandler = () => {
    const scrollTop = window.scrollY
    const docHeight = document.documentElement.scrollHeight - window.innerHeight
    scrollProgress.value = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0
  }

  window.addEventListener('scroll', scrollHandler, { passive: true })
  scrollHandler()
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
  } catch {
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
  } catch {
    categories.value = []
    tags.value = []
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

const getPostMark = (text = '') => {
  return (`${text}`.trim() || 'BL').slice(0, 2).toUpperCase()
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadPosts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const scrollToFeed = () => {
  if (!feedSectionRef.value) return
  const top = window.scrollY + feedSectionRef.value.getBoundingClientRect().top - 96
  window.scrollTo({ top, behavior: 'smooth' })
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

const formatCompactNumber = (value) => {
  const numeric = Number(value || 0)
  if (numeric >= 10000) return `${(numeric / 10000).toFixed(1).replace(/\.0$/, '')}w`
  if (numeric >= 1000) return `${(numeric / 1000).toFixed(1).replace(/\.0$/, '')}k`
  return `${numeric}`
}

onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
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
  --surface: rgba(255, 255, 255, 0.68);
  --surface-strong: rgba(255, 255, 255, 0.78);
  --surface-muted: rgba(252, 248, 247, 0.7);
  --border: rgba(232, 220, 223, 0.56);
  --border-soft: rgba(232, 220, 223, 0.42);
  --text-main: #1f2a44;
  --text-soft: #5f6779;
  --text-mute: #7f8798;
  --brand-1: #ef9ab6;
  --brand-2: #f3c49a;
  --brand-3: #9cbddc;
  --shadow: 0 14px 30px rgba(40, 52, 73, 0.07);
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  overflow-x: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 12px 18px;
  max-width: 1358px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  color: var(--text-main);
  background:
    radial-gradient(circle at 18% 10%, rgba(248, 228, 234, 0.12), transparent 18%),
    radial-gradient(circle at 82% 12%, rgba(221, 233, 244, 0.12), transparent 20%),
    linear-gradient(180deg, rgba(255, 249, 248, 0.82) 0%, rgba(255, 250, 249, 0.78) 56%, rgba(252, 253, 255, 0.8) 100%);
}

.blog-home::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(225, 226, 235, 0.18) 1px, transparent 1px),
    linear-gradient(90deg, rgba(225, 226, 235, 0.18) 1px, transparent 1px);
  background-size: 36px 36px;
  opacity: 0.14;
}

.reading-progress {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 999;
  height: 4px;
  border-radius: 0 999px 999px 0;
  background: linear-gradient(90deg, var(--brand-1), var(--brand-2), var(--brand-3));
  box-shadow: 0 0 18px rgba(239, 154, 182, 0.35);
  transition: width 0.15s ease;
}

.topbar,
.overview-strip,
.category-bridge,
.feature-stage,
.feed-column,
.blog-sidebar {
  position: relative;
  z-index: 1;
  margin: 0;
}

.topbar,
.overview-strip,
.category-bridge {
  grid-column: 1 / -1;
}

.feature-stage,
.feed-column {
  grid-column: 1;
}

.blog-sidebar {
  grid-column: 2;
}

.showcase-grid,
.content-layout {
  display: contents;
}

.blog-home > .control-panel {
  display: none;
}

.aside-column {
  display: none;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  padding: 26px 28px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.64);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  backdrop-filter: blur(22px) saturate(135%);
}

.brand-block {
  max-width: 640px;
}

.panel-kicker,
.aside-kicker,
.section-kicker,
.control-label,
.overview-label {
  display: inline-block;
  margin-bottom: 6px;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #d585a1;
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
}

.brand-name,
.feature-title,
.aside-title,
.section-title,
.publishing-title,
.bridge-title,
.feed-title,
.feature-outline h3,
.feed-intro-card h3 {
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
  letter-spacing: -0.03em;
}

.brand-name {
  margin: 0;
  font-size: clamp(30px, 4vw, 42px);
  line-height: 1.04;
  font-weight: 800;
  color: #22304a;
}

.brand-sub {
  margin: 10px 0 0;
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-soft);
}

.brand-note {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-mute);
}

.topbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.search-input {
  width: 340px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 999px !important;
  background: rgba(250, 251, 253, 0.96) !important;
  box-shadow: none !important;
  border: 1px solid rgba(218, 225, 236, 0.96) !important;
  transition: border-color 0.22s ease, box-shadow 0.22s ease, transform 0.22s ease;
}

.search-input :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(151, 185, 220, 0.92) !important;
  box-shadow: 0 0 0 4px rgba(191, 214, 234, 0.2) !important;
}

.soft-btn,
.glow-btn {
  border-radius: 999px !important;
  padding: 0 18px !important;
  font-weight: 600 !important;
}

.soft-btn {
  background: rgba(255, 255, 255, 0.88) !important;
  border: 1px solid var(--border) !important;
  color: var(--text-main) !important;
}

.glow-btn {
  background: linear-gradient(135deg, #f3c1d1, #f1d7b8) !important;
  border: none !important;
  color: #6b4860 !important;
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
.feed-column,
.feed-intro-card {
  background: var(--surface);
  backdrop-filter: blur(22px) saturate(135%);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow);
}

.overview-card {
  padding: 18px 20px;
  border-radius: 22px;
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

.overview-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 42px rgba(40, 52, 73, 0.1);
}

.overview-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.overview-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #6b4860;
  background: linear-gradient(135deg, rgba(242, 217, 226, 0.9), rgba(241, 233, 214, 0.92));
}

.overview-card strong {
  display: block;
  margin: 12px 0 6px;
  font-size: 34px;
  line-height: 1.05;
  color: #24324b;
}

.overview-card small {
  display: block;
  line-height: 1.65;
  color: var(--text-soft);
}

.overview-card.accent {
  background: linear-gradient(135deg, rgba(246, 221, 229, 0.74), rgba(247, 236, 220, 0.68));
}

.overview-card.accent .overview-mark {
  background: rgba(255, 255, 255, 0.74);
}

.category-bridge {
  padding: 20px 22px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  backdrop-filter: blur(22px) saturate(135%);
}

.category-bridge-head,
.section-head,
.pulse-row,
.feature-meta,
.feature-actions,
.about-stats,
.publishing-actions,
.feed-topline,
.feed-footer,
.aside-row,
.control-row {
  display: flex;
  flex-wrap: wrap;
}

.category-bridge-head {
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.bridge-title {
  margin: 6px 0 0;
  font-size: clamp(28px, 3.2vw, 34px);
  color: #22304a;
}

.bridge-sub {
  margin: 10px 0 0;
  max-width: 620px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-soft);
}

.bridge-meta,
.section-meta,
.feed-date,
.feed-views,
.mini-item-date,
.feature-meta,
.outline-count {
  color: var(--text-mute);
  font-size: 12px;
}

.category-pills,
.tag-pills,
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.category-pill,
.tag-pill,
.cloud-tag {
  border: 1px solid rgba(219, 225, 236, 0.92);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  cursor: pointer;
  transition: background 0.22s ease, color 0.22s ease, border-color 0.22s ease, transform 0.22s ease;
}

.category-pill {
  padding: 10px 16px;
  color: var(--text-main);
}

.tag-pill,
.cloud-tag {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-main);
}

.category-pill.active,
.category-pill:hover,
.tag-pill.active,
.tag-pill:hover,
.cloud-tag.active,
.cloud-tag:hover {
  background: linear-gradient(135deg, rgba(239, 170, 192, 0.96), rgba(158, 191, 223, 0.88));
  color: #fff;
  border-color: transparent;
  transform: translateY(-1px);
}

.showcase-grid {
  display: contents;
}

.feature-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 22px;
  padding: 30px;
  border-radius: 32px;
  cursor: pointer;
  background:
    radial-gradient(circle at 0% 0%, rgba(248, 231, 236, 0.36), transparent 24%),
    radial-gradient(circle at 100% 0%, rgba(236, 241, 248, 0.34), transparent 24%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.74), rgba(251, 248, 246, 0.68));
  transition: transform 0.26s ease, box-shadow 0.26s ease;
}

.feature-stage:hover {
  transform: translateY(-2px);
  box-shadow: 0 24px 48px rgba(40, 52, 73, 0.12);
}

.feature-stage.empty {
  grid-template-columns: 1fr;
  cursor: default;
}

.feature-copy,
.feature-side {
  display: flex;
  flex-direction: column;
}

.feature-topline {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.feature-title {
  margin: 14px 0 16px;
  font-size: clamp(36px, 4vw, 50px);
  line-height: 1.06;
  font-weight: 800;
  color: #22304a;
}

.feature-summary,
.aside-desc,
.publishing-desc,
.pulse-desc,
.feed-summary,
.feed-intro-card p,
.feature-fallback p {
  color: var(--text-soft);
  line-height: 1.86;
}

.hero-badge,
.feed-category,
.feed-badge,
.feed-tag,
.pulse-badge,
.outline-level,
.cover-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.hero-badge {
  padding: 6px 12px;
  border: 1px solid rgba(231, 219, 223, 0.96);
  background: rgba(255, 255, 255, 0.88);
  color: #6f6070;
}

.hero-badge.primary {
  background: linear-gradient(135deg, #f2bfd0, #f1d7b8);
  color: #6b4860;
}

.hero-badge.muted {
  color: #4b6e90;
  border-color: rgba(206, 222, 240, 0.96);
  background: rgba(244, 248, 252, 0.92);
}

.feature-meta {
  gap: 10px 14px;
  margin-bottom: 4px;
}

.feature-signal-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 22px;
}

.signal-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(252, 250, 249, 0.62);
  border: 1px solid rgba(233, 228, 231, 0.66);
}

.signal-label,
.signal-hint {
  display: block;
}

.signal-label {
  font-size: 12px;
  color: #9a7a8b;
}

.signal-value {
  display: block;
  margin: 10px 0 6px;
  font-size: 26px;
  line-height: 1.1;
  font-weight: 800;
  color: #24324b;
}

.signal-hint {
  font-size: 12px;
  color: var(--text-mute);
  line-height: 1.55;
}

.feature-actions {
  gap: 12px;
  margin-top: 24px;
}

.inline-action {
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  background: rgba(250, 251, 253, 0.96);
  color: var(--text-main);
  cursor: pointer;
  font-weight: 600;
  transition: transform 0.22s ease, background 0.22s ease, box-shadow 0.22s ease;
}

.inline-action:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(40, 52, 73, 0.1);
}

.inline-action.primary {
  background: linear-gradient(135deg, #f2bfd0, #f1d7b8);
  color: #6b4860;
}

.feature-side {
  gap: 16px;
}

.feature-visual {
  position: relative;
  min-height: 320px;
  border-radius: 28px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(248, 233, 237, 0.68), rgba(245, 239, 229, 0.62) 44%, rgba(234, 241, 248, 0.64));
  border: 1px solid rgba(233, 225, 229, 0.7);
}

.feature-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.feature-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 12px;
  padding: 26px;
  color: #334156;
}

.cover-badge {
  align-self: flex-start;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.78);
  color: #5b6677;
}

.cover-mark {
  font-size: clamp(72px, 10vw, 112px);
  line-height: 1;
  letter-spacing: -0.08em;
  color: rgba(34, 48, 74, 0.78);
}

.feature-outline,
.compact-card {
  background: var(--surface-strong);
}

.feature-outline {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(233, 228, 231, 0.62);
}

.outline-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.feature-outline h3 {
  margin: 0;
  font-size: 22px;
  color: #22304a;
}

.outline-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(248, 246, 246, 0.96);
  color: #5e6676;
}

.outline-level {
  min-width: 34px;
  height: 24px;
  padding: 0 10px;
  background: linear-gradient(135deg, rgba(242, 217, 226, 0.92), rgba(241, 233, 214, 0.92));
  color: #6b4860;
}

.outline-text {
  line-height: 1.55;
}

.blog-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-self: start;
}

.blog-sidebar {
  position: sticky;
  top: 24px;
}

.aside-card {
  padding: 20px;
  border-radius: 24px;
}

.aside-title {
  margin: 10px 0 12px;
  font-size: 24px;
  color: #22304a;
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
  border-radius: 18px;
  text-align: center;
  background: rgba(249, 248, 248, 0.64);
  border: 1px solid rgba(233, 228, 231, 0.62);
}

.about-stat strong {
  display: block;
  font-size: 24px;
  color: #24324b;
}

.about-stat span {
  color: var(--text-soft);
  font-size: 13px;
}

.publishing-note {
  margin-top: 18px;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(247, 239, 242, 0.68), rgba(247, 243, 237, 0.62));
  border: 1px solid rgba(233, 228, 231, 0.62);
}

.publishing-title {
  margin: 8px 0 10px;
  font-size: 22px;
  line-height: 1.35;
  color: #22304a;
}

.publishing-actions {
  gap: 10px;
  margin-top: 16px;
}

.pulse-card {
  background: linear-gradient(135deg, rgba(247, 239, 242, 0.66), rgba(240, 245, 251, 0.64));
}

.pulse-row {
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.pulse-title {
  margin: 8px 0;
  font-size: 24px;
  color: #22304a;
}

.pulse-badge {
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.58);
  color: #61708a;
  white-space: nowrap;
}

.control-panel {
  padding: 16px 18px;
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.embedded-control-panel {
  margin: -2px 0 18px;
  padding: 0 0 18px;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  border-bottom: 1px solid rgba(233, 228, 231, 0.62);
}

.control-row {
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.control-sub {
  margin: 6px 0 0;
  color: var(--text-soft);
  line-height: 1.7;
}

.tiny-action {
  border: none;
  background: none;
  color: #c7809b;
  cursor: pointer;
  padding: 0;
}

.content-layout {
  display: contents;
}

.feed-column {
  padding: 20px;
  border-radius: 26px;
}

.section-head {
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 12px;
}

.section-title {
  margin: 8px 0 0;
  font-size: clamp(28px, 3vw, 34px);
  color: #22304a;
}

.section-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.feed-intro-card {
  margin-bottom: 12px;
  padding: 16px 18px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(249, 247, 246, 0.64), rgba(244, 248, 252, 0.62));
}

.feed-intro-card h3 {
  margin: 6px 0 8px;
  font-size: 22px;
  color: #22304a;
}

.feed-card {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 18px;
  padding: 18px;
  border-radius: 22px;
  margin-bottom: 16px;
  background: var(--surface-strong);
  border: 1px solid rgba(233, 228, 231, 0.62);
  cursor: pointer;
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

.feed-card:hover,
.mini-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(40, 52, 73, 0.08);
}

.feed-cover-box {
  min-height: 170px;
  border-radius: 20px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(246, 231, 236, 0.64), rgba(244, 239, 232, 0.6) 44%, rgba(232, 241, 248, 0.62));
}

.feed-cover-box.fallback {
  display: grid;
  place-items: center;
  font-size: 46px;
  color: #5f6473;
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

.feed-category {
  padding: 4px 10px;
  background: rgba(246, 232, 237, 0.96);
  color: #6b4860;
}

.feed-badge {
  padding: 4px 10px;
  background: rgba(226, 233, 243, 0.94);
  color: #5c7392;
}

.feed-tag {
  padding: 4px 10px;
  background: rgba(239, 243, 248, 0.96);
  color: #4f657f;
}

.feed-date {
  margin-left: auto;
}

.feed-title {
  margin: 0 0 10px;
  font-size: 24px;
  line-height: 1.3;
  color: #22304a;
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

.mini-list {
  flex-direction: column;
}

.mini-item {
  border: 1px solid rgba(233, 228, 231, 0.62);
  background: rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  text-align: left;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.mini-item.compact {
  padding-block: 11px;
}

.mini-item-title {
  color: var(--text-main);
}

.cloud-tag {
  border: 1px solid rgba(233, 228, 231, 0.88);
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
  border-radius: 24px;
  display: grid;
  place-items: center;
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  background: linear-gradient(135deg, rgba(242, 217, 226, 0.92), rgba(231, 239, 247, 0.92));
  color: #5f6473;
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
  .blog-home,
  .feature-stage,
  .feature-signal-grid {
    grid-template-columns: 1fr;
  }

  .feature-stage,
  .feed-column,
  .blog-sidebar {
    grid-column: 1;
  }

  .overview-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .blog-sidebar {
    position: static;
  }

  .feature-visual,
  .feature-fallback {
    min-height: 280px;
  }
}

@media (max-width: 760px) {
  .blog-home {
    padding: 16px 14px 36px;
  }

  .topbar,
  .overview-strip,
  .category-bridge-head,
  .section-head,
  .pulse-row,
  .control-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .topbar {
    padding: 22px 20px;
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

  .overview-strip {
    grid-template-columns: 1fr;
  }
}
</style>
