<template>
  <div class="blog-detail-page" v-loading="loading">
    <SakuraBackground />
    <div class="reading-progress" :style="{ width: `${scrollProgress}%` }"></div>

    <div v-if="post" class="detail-layout">
      <aside v-if="tocItems.length" class="toc-sidebar">
        <div class="toc-shell">
          <span class="panel-kicker">Reading Guide</span>
          <h4 class="toc-title">文章目录</h4>
          <p class="toc-meta">{{ headingCount }} 个章节 / 预计 {{ estimatedReadMinutes }} 分钟读完</p>

          <nav class="toc-nav">
            <a
              v-for="item in tocItems"
              :key="item.id"
              :href="`#${item.id}`"
              :class="['toc-link', `toc-level-${item.level}`, { active: activeAnchor === item.id }]"
              @click.prevent="scrollToAnchor(item.id)"
            >
              {{ item.text }}
            </a>
          </nav>
        </div>
      </aside>

      <main class="detail-main">
        <section class="hero-panel">
          <div class="hero-grid">
            <div class="hero-copy">
              <button type="button" class="back-btn" @click="goBack">返回博客首页</button>

              <div class="hero-badges">
                <span class="hero-badge">推荐阅读</span>
                <span v-if="post.isTop" class="hero-badge primary">置顶</span>
                <span v-if="post.categoryName" class="hero-badge muted">{{ post.categoryName }}</span>
              </div>

              <h1 class="title">{{ post.title }}</h1>
              <p class="summary">{{ summaryText }}</p>

              <div class="meta-row">
                <span class="meta-item">{{ formatDate(post.publishTime) }}</span>
                <span class="meta-item">{{ formatDate(post.updateTime || post.publishTime) }}</span>
                <span class="meta-item">{{ post.viewCount || 0 }} 次阅读</span>
              </div>

              <div v-if="post.tags?.length" class="tags">
                <span v-for="tag in post.tags" :key="tag.id" class="tag-capsule"># {{ tag.name }}</span>
              </div>

              <div class="signal-grid">
                <article v-for="signal in signalCards" :key="signal.label" class="signal-card">
                  <span class="signal-label">{{ signal.label }}</span>
                  <strong class="signal-value">{{ signal.value }}</strong>
                  <small class="signal-hint">{{ signal.hint }}</small>
                </article>
              </div>
            </div>

            <div class="hero-side">
              <div class="cover-shell">
                <img v-if="post.coverUrl" :src="post.coverUrl" class="cover" alt="" />
                <div v-else class="cover-fallback">
                  <span class="cover-badge">{{ post.categoryName || 'BLOG' }}</span>
                  <strong class="cover-mark">{{ heroMark }}</strong>
                  <p>{{ summaryText }}</p>
                </div>
              </div>

              <div v-if="tocItems.length" class="outline-card">
                <div class="outline-head">
                  <div>
                    <span class="panel-kicker">Jump List</span>
                    <h3>快速跳转</h3>
                  </div>
                  <span class="outline-count">{{ headingCount }} 项</span>
                </div>

                <div class="outline-list">
                  <a
                    v-for="item in tocItems"
                    :key="item.id"
                    :href="`#${item.id}`"
                    :class="['outline-link', { active: activeAnchor === item.id }]"
                    @click.prevent="scrollToAnchor(item.id)"
                  >
                    <span class="outline-level">H{{ item.level }}</span>
                    <span class="outline-text">{{ item.text }}</span>
                  </a>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="article-panel">
          <div class="article-head">
            <div>
              <span class="panel-kicker">Article Stream</span>
              <h2 class="panel-title">全文阅读</h2>
            </div>
            <span class="article-chip">{{ formatCompactNumber(articleWordCount) }} 字</span>
          </div>

          <div ref="contentRef" class="content markdown-body" v-html="renderedHtml"></div>

          <div v-if="post.tags?.length" class="post-footer-tags">
            <span class="footer-tags-label">相关标签</span>
            <button
              v-for="tag in post.tags"
              :key="tag.id"
              type="button"
              class="footer-tag"
              @click="goTagFilter(tag.id)"
            >
              {{ tag.name }}
            </button>
          </div>
        </section>

        <div v-if="prevPost || nextPost" class="post-nav">
          <button
            type="button"
            class="nav-item"
            :class="{ disabled: !prevPost }"
            @click="prevPost && goDetail(prevPost.id)"
          >
            <span class="nav-label">上一篇</span>
            <span class="nav-title">{{ prevPost ? prevPost.title : '没有上一篇了' }}</span>
          </button>

          <button
            type="button"
            class="nav-item nav-next"
            :class="{ disabled: !nextPost }"
            @click="nextPost && goDetail(nextPost.id)"
          >
            <span class="nav-label">下一篇</span>
            <span class="nav-title">{{ nextPost ? nextPost.title : '已经是最新一篇' }}</span>
          </button>
        </div>

        <section v-if="relatedPosts.length" class="related-section">
          <div class="related-head">
            <div>
              <span class="panel-kicker">Signal Relay</span>
              <h3 class="panel-title related-title-main">继续读这些相关文章</h3>
            </div>
            <span class="recommend-tip">如果这篇对你有帮助，可以从下面几篇继续延伸阅读。</span>
          </div>

          <div class="related-grid">
            <button
              v-for="rp in relatedPosts"
              :key="rp.id"
              type="button"
              class="related-card"
              @click="goDetail(rp.id)"
            >
              <div v-if="rp.coverUrl" class="related-cover-wrap">
                <img :src="rp.coverUrl" class="related-cover" alt="" />
              </div>
              <div v-else class="related-placeholder">{{ getPostMark(rp.categoryName || rp.title) }}</div>

              <div class="related-info">
                <span v-if="rp.categoryName" class="related-category">{{ rp.categoryName }}</span>
                <h4 class="related-card-title">{{ rp.title }}</h4>
                <div class="related-meta">
                  <span>{{ formatDate(rp.publishTime) }}</span>
                  <span>{{ rp.viewCount || 0 }} 次阅读</span>
                </div>
              </div>
            </button>
          </div>
        </section>
      </main>
    </div>

    <el-empty v-else-if="!loading" description="文章不存在或暂未发布。" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { getPublicBlogPostDetail, getPublicBlogPosts, getRelatedPosts } from '@/api/blog'
import SakuraBackground from '@/components/SakuraBackground.vue'

const route = useRoute()
const router = useRouter()

const md = new MarkdownIt({ html: false, linkify: true, breaks: true, typographer: true })

const loading = ref(false)
const post = ref(null)
const contentRef = ref(null)
const relatedPosts = ref([])
const prevPost = ref(null)
const nextPost = ref(null)
const tocItems = ref([])
const activeAnchor = ref('')
const scrollProgress = ref(0)
const pendingAnchorId = ref('')
const pendingAnchorUntil = ref(0)

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

const renderedHtml = computed(() => {
  return !post.value?.contentMd
    ? '<p>暂无文章内容。</p>'
    : DOMPurify.sanitize(md.render(post.value.contentMd))
})

const articleWordCount = computed(() => cleanMarkdownText(post.value?.contentMd || '').length)
const estimatedReadMinutes = computed(() => Math.max(1, Math.ceil(articleWordCount.value / 420)))
const headingCount = computed(() => tocItems.value.length)

const summaryText = computed(() => {
  if (post.value?.summary?.trim()) return post.value.summary.trim()
  const plain = cleanMarkdownText(post.value?.contentMd || '')
  return plain.length > 150 ? `${plain.slice(0, 150)}...` : (plain || '即使没有单独摘要，这篇文章也已经可以直接开始阅读。')
})

const heroMark = computed(() => ((post.value?.categoryName || post.value?.title || 'BLOG').replace(/\s+/g, '') || 'BLOG').slice(0, 2).toUpperCase())

const signalCards = computed(() => [
  { label: '阅读时长', value: `${estimatedReadMinutes.value} 分钟`, hint: '根据正文内容估算。' },
  { label: '章节数量', value: `${headingCount.value}`, hint: '可通过目录快速定位到对应小节。' },
  { label: '全文字数', value: formatCompactNumber(articleWordCount.value), hint: '已剔除 Markdown 语法后的近似字数。' }
])

const createHeadingId = (text, index, seenMap) => {
  const normalizedText = `${text || ''}`
  const asciiBase = (normalizedText.normalize ? normalizedText.normalize('NFKD') : normalizedText)
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  const base = asciiBase ? `section-${index + 1}-${asciiBase}` : `section-${index + 1}`
  const seen = seenMap.get(base) || 0
  seenMap.set(base, seen + 1)
  return seen > 0 ? `${base}-${seen + 1}` : base
}

const getHeadingElements = () => {
  return contentRef.value ? Array.from(contentRef.value.querySelectorAll('h1, h2, h3, h4')) : []
}

const findHeadingElement = (id) => {
  if (!id) return null
  const decodedId = decodeURIComponent(id)
  return getHeadingElements().find((heading) => heading.dataset.anchorId === decodedId || heading.id === decodedId)
    || document.getElementById(decodedId)
    || null
}

const resolveHashId = () => decodeURIComponent((route.hash || window.location.hash || '').replace(/^#/, ''))
const getScrollOffset = () => (window.innerWidth <= 768 ? 88 : 108)
const isScrollableElement = (element) => {
  if (!element) return false

  const style = window.getComputedStyle(element)
  return /(auto|scroll|overlay)/.test(style.overflowY) && element.scrollHeight - element.clientHeight > 4
}

const isDocumentScroller = (scroller) => {
  return scroller === window
    || scroller === document
    || scroller === document.body
    || scroller === document.documentElement
    || scroller === document.scrollingElement
}

const getPageScroller = () => {
  const appRoot = document.getElementById('app')
  if (isScrollableElement(appRoot)) return appRoot
  return document.scrollingElement || document.documentElement || document.body
}

const getPageScrollTop = () => {
  const scroller = getPageScroller()
  return isDocumentScroller(scroller)
    ? (window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0)
    : (scroller.scrollTop || 0)
}

const getPageScrollHeight = () => {
  const scroller = getPageScroller()
  if (isDocumentScroller(scroller)) {
    const pageRoot = document.scrollingElement || document.documentElement
    return Math.max(0, pageRoot.scrollHeight - window.innerHeight)
  }

  return Math.max(0, scroller.scrollHeight - scroller.clientHeight)
}

const getRelativeTop = (element, scroller = getPageScroller()) => {
  if (!element) return 0
  const containerTop = isDocumentScroller(scroller) ? 0 : scroller.getBoundingClientRect().top
  return element.getBoundingClientRect().top - containerTop
}

const getScrollTargetTop = (element, scroller = getPageScroller()) => {
  return getPageScrollTop() + getRelativeTop(element, scroller) - getScrollOffset()
}

const scrollPageTo = (top, behavior = 'auto', scroller = getPageScroller()) => {
  const nextTop = Math.max(top, 0)

  if (isDocumentScroller(scroller)) {
    window.scrollTo({ top: nextTop, behavior })
    return
  }

  scroller.scrollTo({ top: nextTop, behavior })
}

const scrollAnchorIntoView = (element, behavior = 'auto') => {
  if (!element) return

  try {
    element.scrollIntoView({
      behavior,
      block: 'start',
      inline: 'nearest'
    })
  } catch {
    element.scrollIntoView(true)
  }
}

const adjustAnchorOffset = (element, scroller = getPageScroller()) => {
  if (!element) return

  const delta = getRelativeTop(element, scroller) - getScrollOffset()
  if (Math.abs(delta) <= 4) return

  if (isDocumentScroller(scroller)) {
    window.scrollBy({ top: delta, behavior: 'auto' })
    return
  }

  scroller.scrollBy({ top: delta, behavior: 'auto' })
}

const setPendingAnchor = (id, duration = 0) => {
  pendingAnchorId.value = id || ''
  pendingAnchorUntil.value = id ? Date.now() + duration : 0
}

const clearPendingAnchor = (id) => {
  if (!id || pendingAnchorId.value === id) {
    pendingAnchorId.value = ''
    pendingAnchorUntil.value = 0
  }
}

const syncScrollState = () => {
  const scroller = getPageScroller()
  let current = ''
  const threshold = getScrollOffset() + 16

  for (const item of tocItems.value) {
    const element = findHeadingElement(item.id)
    if (element && getRelativeTop(element, scroller) <= threshold) current = item.id
  }

  if (pendingAnchorId.value) {
    const pendingElement = findHeadingElement(pendingAnchorId.value)
    const pendingTop = pendingElement ? getRelativeTop(pendingElement, scroller) : null
    const pendingReached = typeof pendingTop === 'number' && pendingTop <= threshold + 12
    const pendingExpired = Date.now() >= pendingAnchorUntil.value

    if (pendingReached || pendingExpired) {
      clearPendingAnchor(pendingAnchorId.value)
    } else {
      activeAnchor.value = pendingAnchorId.value
    }
  }

  if (!pendingAnchorId.value) {
    activeAnchor.value = current || tocItems.value[0]?.id || ''
  }

  const scrollHeight = getPageScrollHeight()
  scrollProgress.value = scrollHeight > 0 ? (getPageScrollTop() / scrollHeight) * 100 : 0
}

const updateHash = async (id) => {
  const nextHash = `#${id}`

  if (route.hash === nextHash) {
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}${nextHash}`)
    return
  }

  try {
    await router.replace({ path: route.path, query: route.query, hash: nextHash })
  } catch {
  }
}

const scrollToAnchor = async (id, options = {}) => {
  const { updateHashValue = true, smooth = true } = options
  const element = findHeadingElement(id)
  if (!element) return

  const scroller = getPageScroller()
  const top = Math.max(getScrollTargetTop(element, scroller), 0)
  const settleDuration = smooth ? 1200 : 160

  setPendingAnchor(id, settleDuration)
  activeAnchor.value = id
  scrollAnchorIntoView(element, smooth ? 'smooth' : 'auto')

  window.setTimeout(() => {
    const latestScroller = getPageScroller()
    adjustAnchorOffset(element, latestScroller)

    // Fallback to the computed absolute target when native anchor scrolling doesn't move the page.
    const deltaAfterAdjust = Math.abs(getRelativeTop(element, latestScroller) - getScrollOffset())
    if (deltaAfterAdjust > 12) {
      scrollPageTo(top, 'auto', latestScroller)
    }

    syncScrollState()
  }, smooth ? 80 : 0)

  try {
    element.focus({ preventScroll: true })
  } catch {
    element.focus()
  }

  if (updateHashValue) {
    await updateHash(id)
  }
}

const buildToc = () => nextTick(() => {
  if (!contentRef.value) return

  const seenMap = new Map()
  tocItems.value = getHeadingElements().map((heading, index) => {
    const id = createHeadingId(heading.textContent || '', index, seenMap)
    heading.id = id
    heading.setAttribute('data-anchor-id', id)
    heading.tabIndex = -1

    return {
      id,
      text: (heading.textContent || '').trim() || `Section ${index + 1}`,
      level: Number(heading.tagName.slice(1))
    }
  })

  syncScrollState()

  const hashId = resolveHashId()
  if (hashId) {
    requestAnimationFrame(() => {
      scrollToAnchor(hashId, { updateHashValue: false, smooth: false })
    })
  }
})

let scrollHandler = null
let scrollTarget = null

const setupScrollSpy = () => {
  if (!scrollHandler) {
    scrollHandler = () => syncScrollState()
  }

  const pageScroller = getPageScroller()
  const nextTarget = isDocumentScroller(pageScroller) ? window : pageScroller

  if (scrollTarget && scrollTarget !== nextTarget) {
    scrollTarget.removeEventListener('scroll', scrollHandler)
  }

  if (scrollTarget !== nextTarget) {
    scrollTarget = nextTarget
    scrollTarget?.addEventListener('scroll', scrollHandler, { passive: true })
  }

  syncScrollState()
}

const loadRelated = async (postId) => {
  try {
    const res = await getRelatedPosts(postId, { limit: 6 })
    relatedPosts.value = res.data.data || []
  } catch {
    relatedPosts.value = []
  }
}

const loadPrevNext = async (currentId) => {
  try {
    const params = { page: 1, size: 100 }
    if (post.value?.categoryId) params.categoryId = post.value.categoryId

    const res = await getPublicBlogPosts(params)
    const data = res.data.data
    const list = data.data || data.records || []
    const idx = list.findIndex((item) => String(item.id) === String(currentId))

    prevPost.value = idx > 0 ? list[idx - 1] : null
    nextPost.value = idx >= 0 && idx < list.length - 1 ? list[idx + 1] : null
  } catch {
    prevPost.value = null
    nextPost.value = null
  }
}

const loadDetail = async () => {
  const postId = route.params.id
  if (!postId) return

  loading.value = true
  tocItems.value = []
  activeAnchor.value = ''
  clearPendingAnchor()

  try {
    const response = await getPublicBlogPostDetail(postId)
    post.value = response.data.data
    scrollPageTo(0)
    await buildToc()
    loadRelated(postId)
    loadPrevNext(postId)
  } catch {
    post.value = null
    ElMessage.error('加载博客详情失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => router.push('/blog')
const goDetail = (id) => router.push(`/blog/${id}`)
const goTagFilter = (tagId) => router.push({ path: '/blog', query: { tag: tagId } })

const formatDate = (dateStr) => {
  return dateStr
    ? new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
    : ''
}

const formatCompactNumber = (value) => {
  const numeric = Number(value || 0)
  if (numeric >= 10000) return `${(numeric / 10000).toFixed(1).replace(/\.0$/, '')}w`
  if (numeric >= 1000) return `${(numeric / 1000).toFixed(1).replace(/\.0$/, '')}k`
  return `${numeric}`
}

const getPostMark = (text = '') => (`${text}`.trim() || 'BL').slice(0, 2).toUpperCase()

onMounted(() => {
  loadDetail()
  setupScrollSpy()
  window.addEventListener('resize', syncScrollState, { passive: true })
})

onUnmounted(() => {
  if (scrollHandler && scrollTarget) scrollTarget.removeEventListener('scroll', scrollHandler)
  window.removeEventListener('resize', syncScrollState)
})

watch(() => route.params.id, () => {
  loadDetail()
})

watch(() => route.hash, (hash) => {
  const id = decodeURIComponent((hash || '').replace(/^#/, ''))
  if (!id) return

  requestAnimationFrame(() => {
    scrollToAnchor(id, { updateHashValue: false, smooth: false })
  })
})
</script>

<style scoped>
.blog-detail-page {
  --surface: rgba(255, 255, 255, 0.66);
  --surface-strong: rgba(255, 255, 255, 0.78);
  --border-soft: rgba(232, 220, 223, 0.42);
  --text-main: #1f2a44;
  --text-soft: #5f6779;
  --text-mute: #7f8798;
  --brand-1: #ef9ab6;
  --brand-2: #f3c49a;
  --brand-3: #9cbddc;
  --shadow: 0 18px 36px rgba(40, 52, 73, 0.08);
  position: relative;
  min-height: 100vh;
  padding: 24px 20px 48px;
  overflow: visible;
  color: var(--text-main);
  background:
    radial-gradient(circle at 14% 10%, rgba(248, 228, 234, 0.12), transparent 18%),
    radial-gradient(circle at 84% 12%, rgba(223, 234, 244, 0.1), transparent 20%),
    linear-gradient(180deg, rgba(255, 249, 248, 0.78) 0%, rgba(255, 250, 249, 0.74) 58%, rgba(252, 253, 255, 0.78) 100%);
}

.blog-detail-page::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(225, 226, 235, 0.16) 1px, transparent 1px),
    linear-gradient(90deg, rgba(225, 226, 235, 0.16) 1px, transparent 1px);
  background-size: 36px 36px;
  opacity: 0.12;
}

.reading-progress {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 99;
  height: 4px;
  border-radius: 0 999px 999px 0;
  background: linear-gradient(90deg, var(--brand-1), var(--brand-2), var(--brand-3));
  box-shadow: 0 0 18px rgba(239, 154, 182, 0.35);
  transition: width 0.15s ease;
}

.detail-layout {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
  max-width: 1400px;
  margin: 0 auto;
}

.detail-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.toc-sidebar {
  position: sticky;
  top: 24px;
  align-self: start;
}

.toc-shell,
.hero-panel,
.article-panel,
.nav-item,
.related-card {
  background: var(--surface);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow);
  backdrop-filter: blur(22px) saturate(135%);
}

.toc-shell {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 48px);
  padding: 20px;
  border-radius: 24px;
  overflow: hidden;
}

.panel-kicker {
  display: inline-block;
  margin-bottom: 6px;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #d585a1;
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
}

.toc-title,
.outline-head h3,
.panel-title {
  margin: 0;
  color: #22304a;
}

.toc-title {
  font-size: 22px;
}

.toc-meta,
.recommend-tip,
.footer-tags-label,
.related-meta,
.nav-label {
  font-size: 12px;
  color: var(--text-mute);
}

.toc-meta {
  margin: 8px 0 0;
  line-height: 1.7;
}

.toc-nav,
.outline-list {
  min-height: 0;
  padding-right: 6px;
  scrollbar-width: thin;
  scrollbar-color: rgba(213, 133, 161, 0.52) rgba(244, 241, 241, 0.72);
}

.toc-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  margin-top: 14px;
  overflow-y: auto;
}

.toc-nav::-webkit-scrollbar,
.outline-list::-webkit-scrollbar {
  width: 8px;
}

.toc-nav::-webkit-scrollbar-track,
.outline-list::-webkit-scrollbar-track {
  border-radius: 999px;
  background: rgba(244, 241, 241, 0.72);
}

.toc-nav::-webkit-scrollbar-thumb,
.outline-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(239, 154, 182, 0.9), rgba(156, 189, 220, 0.88));
}

.toc-link {
  display: block;
  width: 100%;
  padding: 10px 12px 10px calc(14px + var(--indent, 0px));
  border: 0;
  border-radius: 14px;
  color: #5b6677;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  line-height: 1.55;
  background: rgba(248, 246, 246, 0.96);
  transition: all 0.2s ease;
}

.toc-link:hover,
.toc-link.active {
  color: #22304a;
  background: linear-gradient(135deg, rgba(246, 231, 236, 0.94), rgba(232, 241, 248, 0.9));
}

.toc-level-2 {
  --indent: 12px;
}

.toc-level-3 {
  --indent: 24px;
  font-size: 12px;
}

.toc-level-4 {
  --indent: 36px;
  font-size: 12px;
}

.hero-panel {
  padding: 30px;
  border-radius: 30px;
  background:
    radial-gradient(circle at 0% 0%, rgba(248, 231, 236, 0.34), transparent 24%),
    radial-gradient(circle at 100% 0%, rgba(236, 241, 248, 0.3), transparent 24%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(251, 248, 246, 0.66));
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(300px, 0.85fr);
  gap: 22px;
}

.back-btn,
.hero-badge,
.tag-capsule,
.article-chip,
.related-category,
.footer-tag {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  font-weight: 700;
}

.back-btn {
  padding: 8px 14px;
  border: 1px solid rgba(219, 225, 236, 0.92);
  background: rgba(255, 255, 255, 0.94);
  color: var(--text-main);
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.back-btn:hover,
.footer-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(40, 52, 73, 0.08);
}

.hero-badges,
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-badges {
  margin: 16px 0;
}

.hero-badge,
.tag-capsule {
  padding: 6px 12px;
  font-size: 12px;
  border: 1px solid rgba(231, 219, 223, 0.96);
  background: rgba(255, 255, 255, 0.9);
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

.title {
  margin: 0 0 16px;
  font-size: clamp(38px, 4.4vw, 54px);
  line-height: 1.04;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #22304a;
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
}

.summary {
  margin: 0;
  max-width: 760px;
  color: var(--text-soft);
  font-size: 16px;
  line-height: 1.92;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: center;
  margin: 22px 0 16px;
  color: var(--text-mute);
}

.meta-item {
  display: inline-flex;
  align-items: center;
}

.signal-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 22px;
}

.signal-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(252, 250, 249, 0.92);
  border: 1px solid rgba(233, 228, 231, 0.88);
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

.hero-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cover-shell {
  position: relative;
  overflow: hidden;
  min-height: 320px;
  border-radius: 26px;
  background: linear-gradient(135deg, rgba(246, 231, 236, 0.94), rgba(244, 239, 232, 0.94) 44%, rgba(232, 241, 248, 0.94));
  border: 1px solid rgba(233, 225, 229, 0.94);
}

.cover,
.related-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover {
  min-height: 320px;
}

.cover-fallback {
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
  display: inline-flex;
  align-self: flex-start;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #5b6677;
  background: rgba(255, 255, 255, 0.82);
}

.cover-mark {
  font-size: clamp(72px, 10vw, 112px);
  line-height: 1;
  letter-spacing: -0.08em;
  color: rgba(34, 48, 74, 0.78);
}

.cover-fallback p {
  margin: 0;
  line-height: 1.8;
  color: var(--text-soft);
}

.outline-card {
  display: flex;
  flex-direction: column;
  max-height: 360px;
  padding: 18px;
  border-radius: 22px;
  background: var(--surface-strong);
  border: 1px solid rgba(233, 228, 231, 0.88);
  overflow: hidden;
}

.outline-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.outline-count {
  font-size: 12px;
  color: var(--text-mute);
}

.outline-list {
  overflow-y: auto;
}

.outline-link {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  width: 100%;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(248, 246, 246, 0.96);
  color: #5b6677;
  text-align: left;
  text-decoration: none;
  transition: all 0.2s ease;
}

.outline-link:hover,
.outline-link.active {
  background: linear-gradient(135deg, rgba(246, 231, 236, 0.94), rgba(232, 241, 248, 0.9));
}

.outline-level {
  min-width: 34px;
  height: 24px;
  padding: 0 10px;
  background: linear-gradient(135deg, rgba(242, 217, 226, 0.92), rgba(241, 233, 214, 0.92));
  color: #6b4860;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.outline-text {
  line-height: 1.55;
}

.article-panel {
  padding: 30px;
  border-radius: 30px;
  background: var(--surface-strong);
}

.article-head,
.related-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.panel-title {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.related-title-main {
  font-size: 26px;
}

.article-chip,
.related-category,
.footer-tag {
  padding: 6px 12px;
  border: 1px solid rgba(231, 219, 223, 0.96);
  font-size: 12px;
  color: #6b4860;
  background: rgba(255, 255, 255, 0.92);
}

.content {
  max-width: 900px;
  margin: 0 auto;
  color: #4b5563;
  font-size: 15px;
  line-height: 1.95;
}

:deep(.content > :first-child) {
  margin-top: 0;
}

:deep(.content h1),
:deep(.content h2),
:deep(.content h3),
:deep(.content h4) {
  margin-top: 1.9em;
  margin-bottom: 0.68em;
  color: #1f2a44;
  line-height: 1.28;
  scroll-margin-top: 104px;
  letter-spacing: -0.03em;
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
}

:deep(.content h1) {
  font-size: 2.2em;
}

:deep(.content h2) {
  font-size: 1.68em;
  padding-bottom: 0.46em;
  border-bottom: 1px solid rgba(225, 226, 235, 0.78);
}

:deep(.content h3) {
  font-size: 1.34em;
}

:deep(.content h4) {
  font-size: 1.12em;
}

:deep(.content p),
:deep(.content ul),
:deep(.content ol),
:deep(.content blockquote),
:deep(.content pre),
:deep(.content table),
:deep(.content hr) {
  margin: 1.12em 0;
}

:deep(.content ul),
:deep(.content ol) {
  padding-left: 1.45em;
}

:deep(.content li) {
  margin: 0.45em 0;
}

:deep(.content a) {
  color: #3f6f9f;
  text-decoration: none;
}

:deep(.content a:hover) {
  text-decoration: underline;
}

:deep(.content pre) {
  padding: 20px;
  border-radius: 20px;
  overflow: auto;
  color: #f8fbff;
  background: linear-gradient(180deg, #415069, #55637b);
  border: 1px solid rgba(118, 136, 171, 0.25);
}

:deep(.content code) {
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
}

:deep(.content p code),
:deep(.content li code),
:deep(.content td code) {
  padding: 0.18em 0.45em;
  border-radius: 8px;
  color: #854b67;
  background: rgba(246, 231, 236, 0.76);
}

:deep(.content blockquote) {
  padding: 16px 20px;
  border-radius: 0 18px 18px 0;
  border-left: 4px solid #e7a7bf;
  background: linear-gradient(135deg, rgba(247, 238, 241, 0.96), rgba(244, 246, 249, 0.96));
  color: #626b7a;
}

:deep(.content img) {
  max-width: 100%;
  border-radius: 18px;
  border: 1px solid rgba(225, 226, 235, 0.76);
  box-shadow: 0 18px 32px rgba(40, 52, 73, 0.08);
}

:deep(.content hr) {
  border: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(207, 214, 226, 0.82), transparent);
}

:deep(.content table) {
  width: 100%;
  border-collapse: collapse;
  overflow: hidden;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(225, 226, 235, 0.78);
}

:deep(.content th),
:deep(.content td) {
  padding: 12px 14px;
  border: 1px solid rgba(225, 226, 235, 0.62);
  text-align: left;
}

:deep(.content th) {
  background: rgba(246, 247, 250, 0.92);
  color: #2d3b54;
}

.post-footer-tags {
  max-width: 900px;
  margin: 30px auto 0;
  padding-top: 20px;
  border-top: 1px solid rgba(225, 226, 235, 0.72);
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.post-nav {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.nav-item {
  padding: 22px 24px;
  border-radius: 22px;
  cursor: pointer;
  text-align: left;
  background: var(--surface);
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

.nav-item:hover:not(.disabled),
.related-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 22px 42px rgba(40, 52, 73, 0.1);
}

.nav-item.disabled {
  opacity: 0.55;
  cursor: default;
}

.nav-next {
  text-align: right;
}

.nav-title {
  display: block;
  margin-top: 10px;
  color: #22304a;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.5;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.related-card {
  overflow: hidden;
  border-radius: 22px;
  cursor: pointer;
  text-align: left;
  background: var(--surface-strong);
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

.related-cover-wrap,
.related-placeholder {
  height: 168px;
}

.related-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(246, 231, 236, 0.94), rgba(244, 239, 232, 0.94) 44%, rgba(232, 241, 248, 0.94));
  color: #5f6473;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.08em;
}

.related-info {
  padding: 18px;
}

.related-card-title {
  margin: 12px 0 8px;
  color: #22304a;
  font-size: 16px;
  line-height: 1.45;
  font-weight: 700;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.related-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 1260px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .toc-sidebar {
    display: none;
  }

  .outline-card {
    max-height: none;
  }

  .outline-list {
    overflow: visible;
    padding-right: 0;
  }
}

@media (max-width: 900px) {
  .hero-grid,
  .signal-grid,
  .related-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .blog-detail-page {
    padding: 16px 14px 36px;
  }

  .hero-panel,
  .article-panel {
    padding: 20px;
    border-radius: 24px;
  }

  .title {
    font-size: 30px;
  }

  .summary {
    font-size: 15px;
  }

  .panel-title {
    font-size: 24px;
  }

  .post-nav,
  .related-grid {
    grid-template-columns: 1fr;
  }

  .cover-shell,
  .cover {
    min-height: 260px;
  }

  .content {
    font-size: 15px;
  }
}
</style>
