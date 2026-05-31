<template>
  <div class="blog-detail-page" v-loading="loading">
    <SakuraBackground />
    <div class="reading-progress" :style="{ width: scrollProgress + '%' }"></div>
    <div class="detail-layout" v-if="post">
      <aside class="toc-sidebar" v-if="tocItems.length">
        <div class="toc-shell">
          <span class="panel-kicker">Guide Map</span>
          <h4 class="toc-title">目录</h4>
          <p class="toc-meta">{{ headingCount }} 个节点 · 约 {{ estimatedReadMinutes }} 分钟</p>
          <nav class="toc-nav">
            <a v-for="item in tocItems" :key="item.id" :href="'#' + item.id" :class="['toc-link', 'toc-level-' + item.level, { active: activeAnchor === item.id }]" @click.prevent="scrollToAnchor(item.id)">{{ item.text }}</a>
          </nav>
        </div>
      </aside>
      <main class="detail-main">
        <section class="hero-panel">
          <div class="hero-grid">
            <div class="hero-copy">
              <button class="back-btn" type="button" @click="goBack">返回博客列表</button>
              <div class="hero-badges">
                <span class="hero-badge">Recommended Read</span>
                <span v-if="post.isTop" class="hero-badge primary">置顶文章</span>
                <span v-if="post.categoryName" class="hero-badge muted">{{ post.categoryName }}</span>
              </div>
              <h1 class="title">{{ post.title }}</h1>
              <p class="summary">{{ summaryText }}</p>
              <div class="meta-row">
                <span class="meta-category" v-if="post.categoryName">{{ post.categoryName }}</span>
                <span class="meta-item">发布于 {{ formatDate(post.publishTime) }}</span>
                <span class="meta-item">更新于 {{ formatDate(post.updateTime || post.publishTime) }}</span>
                <span class="meta-item">{{ formatCompactNumber(post.viewCount || 0) }} 阅读</span>
              </div>
              <div class="tags" v-if="post.tags && post.tags.length">
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
                  <span class="cover-badge">{{ post.categoryName || 'TECH' }}</span>
                  <strong class="cover-mark">{{ heroMark }}</strong>
                  <p>{{ summaryText }}</p>
                </div>
              </div>
              <div class="outline-card" v-if="tocPreview.length">
                <div class="outline-head">
                  <div>
                    <span class="panel-kicker">Jump List</span>
                    <h3>正文跳转</h3>
                  </div>
                  <span class="outline-count">{{ headingCount }} 节</span>
                </div>
                <a v-for="item in tocPreview" :key="item.id" :href="'#' + item.id" class="outline-link" @click.prevent="scrollToAnchor(item.id)">
                  <span class="outline-level">H{{ item.level }}</span>
                  <span class="outline-text">{{ item.text }}</span>
                </a>
              </div>
            </div>
          </div>
        </section>
        <section class="article-panel">
          <div class="article-head">
            <div>
              <span class="panel-kicker">Article Stream</span>
              <h2 class="panel-title">正文</h2>
            </div>
            <span class="article-chip">{{ formatCompactNumber(articleWordCount) }} 字</span>
          </div>
          <div class="content markdown-body" v-html="renderedHtml" ref="contentRef"></div>
          <div class="post-footer-tags" v-if="post.tags && post.tags.length">
            <span class="footer-tags-label">相关标签</span>
            <span v-for="tag in post.tags" :key="tag.id" class="footer-tag" @click="goTagFilter(tag.id)">{{ tag.name }}</span>
          </div>
        </section>
        <div class="post-nav" v-if="prevPost || nextPost">
          <div class="nav-item" @click="prevPost && goDetail(prevPost.id)" :class="{ disabled: !prevPost }">
            <span class="nav-label">上一篇</span>
            <span class="nav-title">{{ prevPost ? prevPost.title : '没有上一篇了' }}</span>
          </div>
          <div class="nav-item nav-next" @click="nextPost && goDetail(nextPost.id)" :class="{ disabled: !nextPost }">
            <span class="nav-label">下一篇</span>
            <span class="nav-title">{{ nextPost ? nextPost.title : '已经是最后一篇' }}</span>
          </div>
        </div>
        <section class="related-section" v-if="relatedPosts.length">
          <div class="related-head">
            <div>
              <span class="panel-kicker">Signal Relay</span>
              <h3 class="panel-title related-title-main">推荐观看</h3>
            </div>
            <span class="recommend-tip">看完这一篇，可以顺着这些继续读</span>
          </div>
          <div class="related-grid">
            <div v-for="rp in relatedPosts" :key="rp.id" class="related-card" @click="goDetail(rp.id)">
              <div class="related-cover-wrap" v-if="rp.coverUrl"><img :src="rp.coverUrl" class="related-cover" alt="" /></div>
              <div class="related-placeholder" v-else>{{ getPostMark(rp.categoryName || rp.title) }}</div>
              <div class="related-info">
                <span class="related-category" v-if="rp.categoryName">{{ rp.categoryName }}</span>
                <h4 class="related-card-title">{{ rp.title }}</h4>
                <div class="related-meta"><span>{{ formatDate(rp.publishTime) }}</span><span>· {{ rp.viewCount || 0 }} 阅读</span></div>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
    <el-empty v-else-if="!loading" description="博客不存在或未发布" />
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

const cleanMarkdownText = (content = '') => content.replace(/```[\s\S]*?```/g, ' ').replace(/`[^`]*`/g, ' ').replace(/!\[[^\]]*\]\([^)]+\)/g, ' ').replace(/\[([^\]]+)\]\([^)]+\)/g, '$1').replace(/^#{1,6}\s+/gm, '').replace(/^>\s?/gm, '').replace(/[*_~|-]/g, ' ').replace(/\d+\.\s+/g, ' ').replace(/\n+/g, ' ').replace(/\s+/g, ' ').trim()
const renderedHtml = computed(() => !post.value?.contentMd ? '<p>暂无正文内容</p>' : DOMPurify.sanitize(md.render(post.value.contentMd)))
const articleWordCount = computed(() => cleanMarkdownText(post.value?.contentMd || '').length)
const estimatedReadMinutes = computed(() => Math.max(1, Math.ceil(articleWordCount.value / 420)))
const headingCount = computed(() => tocItems.value.length)
const tocPreview = computed(() => tocItems.value.slice(0, 5))
const summaryText = computed(() => {
  if (post.value?.summary?.trim()) return post.value.summary.trim()
  const plain = cleanMarkdownText(post.value?.contentMd || '')
  return plain.length > 120 ? `${plain.slice(0, 120)}...` : (plain || '这篇文章暂时没有摘要，但正文已经准备好了。')
})
const heroMark = computed(() => ((post.value?.categoryName || post.value?.title || 'BLOG').replace(/\s+/g, '') || 'BLOG').slice(0, 2).toUpperCase())
const signalCards = computed(() => [
  { label: '阅读耗时', value: `${estimatedReadMinutes.value} min`, hint: '按正文字符估算' },
  { label: '目录锚点', value: `${headingCount.value}`, hint: '页面可跳转标题数' },
  { label: '正文字数', value: formatCompactNumber(articleWordCount.value), hint: '已剔除 Markdown 标记' }
])

const createHeadingId = (text, index, seenMap) => {
  const base = (text || '').toLowerCase().replace(/[^\u4e00-\u9fa5a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || `section-${index + 1}`
  const seen = seenMap.get(base) || 0
  seenMap.set(base, seen + 1)
  return seen > 0 ? `${base}-${seen + 1}` : base
}

const buildToc = () => nextTick(() => {
  if (!contentRef.value) return
  const seenMap = new Map()
  tocItems.value = Array.from(contentRef.value.querySelectorAll('h1, h2, h3, h4')).map((heading, index) => {
    const id = createHeadingId(heading.textContent || '', index, seenMap)
    heading.id = id
    return { id, text: heading.textContent || `Section ${index + 1}`, level: Number(heading.tagName.slice(1)) }
  })
})

let scrollHandler = null
const setupScrollSpy = () => {
  scrollHandler = () => {
    let current = ''
    for (const item of tocItems.value) {
      const element = document.getElementById(item.id)
      if (element && element.getBoundingClientRect().top <= 120) current = item.id
    }
    activeAnchor.value = current
    const docHeight = document.documentElement.scrollHeight - window.innerHeight
    scrollProgress.value = docHeight > 0 ? (window.scrollY / docHeight) * 100 : 0
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
}

const scrollToAnchor = (id) => {
  const element = document.getElementById(id)
  if (!element) return
  const top = window.scrollY + element.getBoundingClientRect().top - 96
  window.scrollTo({ top, behavior: 'smooth' })
  activeAnchor.value = id
}

const loadRelated = async (postId) => {
  try {
    const res = await getRelatedPosts(postId, { limit: 6 })
    relatedPosts.value = res.data.data || []
  } catch { relatedPosts.value = [] }
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
  try {
    const response = await getPublicBlogPostDetail(postId)
    post.value = response.data.data
    window.scrollTo({ top: 0, behavior: 'auto' })
    buildToc()
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
const formatDate = (dateStr) => dateStr ? new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }) : ''
const formatCompactNumber = (value) => {
  const numeric = Number(value || 0)
  if (numeric >= 10000) return `${(numeric / 10000).toFixed(1).replace(/\.0$/, '')}w`
  if (numeric >= 1000) return `${(numeric / 1000).toFixed(1).replace(/\.0$/, '')}k`
  return `${numeric}`
}
const getPostMark = (text = '') => (`${text}`.trim() || 'BL').slice(0, 2).toUpperCase()

onMounted(() => { loadDetail(); setupScrollSpy() })
onUnmounted(() => { if (scrollHandler) window.removeEventListener('scroll', scrollHandler) })
watch(() => route.params.id, () => loadDetail())
</script>

<style scoped>
.blog-detail-page{position:relative;min-height:100vh;padding:24px 20px 48px;background:radial-gradient(circle at 10% 8%,rgba(255,200,220,.36),transparent 22%),radial-gradient(circle at 88% 12%,rgba(154,225,236,.24),transparent 20%),linear-gradient(180deg,#fff8fb 0%,#fffaf4 48%,#f8fbff 100%);overflow:visible}.blog-detail-page::before{content:'';position:absolute;inset:0;background-image:linear-gradient(rgba(255,192,215,.08) 1px,transparent 1px),linear-gradient(90deg,rgba(173,214,255,.08) 1px,transparent 1px);background-size:30px 30px;pointer-events:none}.reading-progress{position:fixed;top:0;left:0;z-index:99;height:4px;border-radius:0 999px 999px 0;background:linear-gradient(90deg,#ff9fc5 0%,#ffcc9c 46%,#7cc7ff 100%);box-shadow:0 0 18px rgba(255,163,198,.45);transition:width .15s ease}.detail-layout{position:relative;z-index:1;max-width:1460px;margin:0 auto;display:grid;grid-template-columns:minmax(320px,360px) minmax(0,1fr);gap:24px;align-items:start}.detail-main{min-width:0;display:flex;flex-direction:column;gap:22px}.toc-sidebar{position:sticky;top:24px}.toc-shell,.hero-panel,.article-panel,.nav-item,.related-card{background:rgba(255,255,255,.78);border:1px solid rgba(255,196,215,.34);box-shadow:0 20px 44px rgba(215,148,187,.14);backdrop-filter:blur(18px)}.toc-shell{padding:20px;border-radius:28px}.panel-kicker{display:inline-block;margin-bottom:6px;font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:#f08bab;font-family:"JetBrains Mono","Cascadia Code",Consolas,monospace}.toc-title,.outline-head h3,.panel-title{margin:0;color:#53374f}.toc-title{font-size:22px}.toc-meta,.recommend-tip,.footer-tags-label,.related-meta,.nav-label{font-size:12px;color:#8b7284}.toc-meta{margin:8px 0 0;line-height:1.7}.toc-nav{display:flex;flex-direction:column;gap:8px;margin-top:14px}.toc-link{display:block;padding:10px 12px 10px calc(14px + var(--indent,0px));border-radius:14px;color:#6b5b76;line-height:1.55;text-decoration:none;white-space:normal;word-break:break-word;background:rgba(255,244,248,.72);transition:all .2s ease}.toc-link:hover,.toc-link.active{color:#51314b;background:linear-gradient(135deg,rgba(255,214,228,.82),rgba(232,243,255,.88))}.toc-level-2{--indent:12px}.toc-level-3{--indent:24px;font-size:12px}.toc-level-4{--indent:36px;font-size:12px}.hero-panel{padding:28px;border-radius:32px;background:radial-gradient(circle at 0% 0%,rgba(255,214,228,.48),transparent 24%),radial-gradient(circle at 100% 0%,rgba(255,223,188,.42),transparent 24%),linear-gradient(135deg,rgba(255,250,252,.96),rgba(255,246,238,.92) 48%,rgba(244,249,255,.96) 100%)}.hero-grid{display:grid;grid-template-columns:minmax(0,1.18fr) minmax(300px,.82fr);gap:20px}.back-btn,.hero-badge,.tag-capsule,.article-chip,.related-category,.footer-tag{display:inline-flex;align-items:center;border-radius:999px;font-weight:700}.back-btn{padding:8px 14px;border:1px solid rgba(255,184,206,.45);background:rgba(255,255,255,.78);color:#7b516a;cursor:pointer;transition:all .2s ease}.back-btn:hover,.footer-tag:hover{transform:translateY(-1px)}.hero-badges,.tags{display:flex;flex-wrap:wrap;gap:8px}.hero-badges{margin:16px 0}.hero-badge,.tag-capsule{padding:6px 12px;font-size:12px;border:1px solid rgba(255,196,215,.34);background:rgba(255,248,251,.86);color:#7a6072}.hero-badge.primary{background:linear-gradient(135deg,#ffc2d8,#ffd7b5);color:#73465e}.hero-badge.muted{color:#4c7aa6;border-color:rgba(173,214,255,.34);background:rgba(243,250,255,.88)}.title{margin:0 0 16px;font-size:clamp(32px,4vw,52px);line-height:1.08;font-weight:800;letter-spacing:-.04em;color:#4b3147;font-family:"Source Han Serif SC","Noto Serif SC","Songti SC",serif}.summary{margin:0;max-width:760px;color:#735f70;font-size:17px;line-height:1.85}.meta-row{display:flex;flex-wrap:wrap;gap:12px 16px;align-items:center;margin:22px 0 16px;color:#8b7284}.meta-category{display:inline-flex;align-items:center;padding:6px 14px;border-radius:999px;font-size:12px;font-weight:700;color:#73465e;background:linear-gradient(135deg,#ffd1e2,#ffe5bf)}.signal-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:22px}.signal-card{padding:16px;border-radius:22px;background:rgba(255,250,252,.82);border:1px solid rgba(255,210,225,.32)}.signal-label,.signal-hint{display:block}.signal-label{font-size:12px;color:#a17f92}.signal-value{display:block;margin:10px 0 6px;font-size:26px;line-height:1.1;font-weight:800;color:#5a3f53}.signal-hint{font-size:12px;color:#a68a97;line-height:1.5}.hero-side{display:flex;flex-direction:column;gap:16px}.cover-shell{position:relative;overflow:hidden;min-height:320px;border-radius:28px;background:linear-gradient(135deg,rgba(255,232,239,.92),rgba(255,242,228,.9) 44%,rgba(238,246,255,.92));border:1px solid rgba(255,196,215,.34)}.cover,.related-cover{width:100%;height:100%;object-fit:cover;display:block}.cover{min-height:320px}.cover-fallback{position:absolute;inset:0;display:flex;flex-direction:column;justify-content:flex-end;gap:12px;padding:26px;color:#5a3f53}.cover-badge{display:inline-flex;align-self:flex-start;padding:6px 12px;border-radius:999px;font-size:11px;letter-spacing:.1em;text-transform:uppercase;color:#73465e;background:linear-gradient(135deg,#ffd1e2,#ffe5bf)}.cover-mark{font-size:clamp(72px,10vw,112px);line-height:1;letter-spacing:-.08em}.cover-fallback p{margin:0;line-height:1.75;color:#755f71}.outline-card{padding:18px;border-radius:24px;background:rgba(255,252,253,.82);border:1px solid rgba(255,210,225,.32)}.outline-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:12px}.outline-count{font-size:12px;color:#9b7e91}.outline-link{display:grid;grid-template-columns:auto 1fr;gap:10px;margin-top:10px;padding:10px 12px;border-radius:16px;text-decoration:none;background:rgba(255,244,248,.72);color:#6b5b76;transition:all .2s ease}.outline-link:hover{background:linear-gradient(135deg,rgba(255,214,228,.82),rgba(232,243,255,.88))}.outline-level{display:inline-flex;align-items:center;justify-content:center;min-width:34px;height:24px;border-radius:999px;font-size:11px;font-weight:700;color:#73465e;background:linear-gradient(135deg,#ffd1e2,#ffe5bf)}.outline-text{line-height:1.5}.article-panel{padding:28px;border-radius:32px}.article-head,.related-head{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:20px}.panel-title{font-size:30px;font-weight:800;letter-spacing:-.03em}.related-title-main{font-size:26px}.article-chip,.related-category,.footer-tag{padding:6px 12px;font-size:12px;color:#73465e;background:rgba(255,244,248,.84);border:1px solid rgba(255,196,215,.32)}.content{max-width:880px;margin:0 auto;color:#3f455d;font-size:16px;line-height:1.92}:deep(.content > :first-child){margin-top:0}:deep(.content h1),:deep(.content h2),:deep(.content h3),:deep(.content h4){margin-top:1.8em;margin-bottom:.65em;color:#4b3147;line-height:1.28;scroll-margin-top:96px;letter-spacing:-.03em;font-family:"Source Han Serif SC","Noto Serif SC","Songti SC",serif}:deep(.content h1){font-size:2.1em}:deep(.content h2){font-size:1.62em;padding-bottom:.42em;border-bottom:1px solid rgba(255,196,215,.34)}:deep(.content h3){font-size:1.3em}:deep(.content h4){font-size:1.1em}:deep(.content p),:deep(.content ul),:deep(.content ol),:deep(.content blockquote),:deep(.content pre),:deep(.content table),:deep(.content hr){margin:1.1em 0}:deep(.content ul),:deep(.content ol){padding-left:1.4em}:deep(.content li){margin:.45em 0}:deep(.content a){color:#4d7dbc;text-decoration:none}:deep(.content a:hover){text-decoration:underline}:deep(.content pre){padding:20px;border-radius:22px;overflow:auto;color:#f8fbff;background:linear-gradient(180deg,#46506d,#576786);border:1px solid rgba(131,151,194,.25)}:deep(.content code){font-family:"JetBrains Mono","Cascadia Code",Consolas,monospace}:deep(.content p code),:deep(.content li code),:deep(.content td code){padding:.18em .45em;border-radius:8px;color:#8c3f67;background:rgba(255,214,228,.62)}:deep(.content blockquote){padding:16px 20px;border-radius:0 18px 18px 0;border-left:4px solid #ffb1ca;background:linear-gradient(135deg,rgba(255,225,235,.72),rgba(255,246,232,.64));color:#6d5a68}:deep(.content img){max-width:100%;border-radius:20px;border:1px solid rgba(255,196,215,.34);box-shadow:0 20px 40px rgba(215,148,187,.16)}:deep(.content hr){border:0;height:1px;background:linear-gradient(90deg,transparent,rgba(255,182,204,.42),transparent)}:deep(.content table){width:100%;border-collapse:collapse;overflow:hidden;border-radius:18px;background:rgba(255,255,255,.82);border:1px solid rgba(255,196,215,.3)}:deep(.content th),:deep(.content td){padding:12px 14px;border:1px solid rgba(255,196,215,.24);text-align:left}:deep(.content th){background:rgba(255,244,248,.82);color:#5a3f53}.post-footer-tags{max-width:880px;margin:30px auto 0;padding-top:20px;border-top:1px solid rgba(255,196,215,.24);display:flex;flex-wrap:wrap;gap:10px;align-items:center}.post-nav{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.nav-item{padding:22px 24px;border-radius:26px;cursor:pointer;background:linear-gradient(135deg,rgba(255,250,252,.96),rgba(255,245,238,.94) 48%,rgba(241,247,255,.96));transition:all .25s ease}.nav-item:hover:not(.disabled),.related-card:hover{transform:translateY(-4px);box-shadow:0 28px 56px rgba(215,148,187,.18)}.nav-item.disabled{opacity:.55;cursor:default}.nav-next{text-align:right}.nav-title{display:block;margin-top:10px;color:#53374f;font-size:15px;font-weight:700;line-height:1.5}.related-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.related-card{overflow:hidden;border-radius:24px;cursor:pointer}.related-cover-wrap,.related-placeholder{height:168px}.related-placeholder{display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,rgba(255,220,232,.88),rgba(255,236,214,.88) 44%,rgba(230,244,255,.88));color:#73465e;font-size:34px;font-weight:800;letter-spacing:-.08em}.related-info{padding:18px}.related-card-title{margin:12px 0 8px;color:#53374f;font-size:16px;line-height:1.45;font-weight:700;display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical;-webkit-line-clamp:2}.related-meta{display:flex;flex-wrap:wrap;gap:6px}@media (max-width:1260px){.detail-layout{grid-template-columns:1fr}.toc-sidebar{position:static}}@media (max-width:900px){.hero-grid{grid-template-columns:1fr}.signal-grid,.related-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media (max-width:768px){.blog-detail-page{padding:16px 14px 36px}.hero-panel,.article-panel{padding:20px;border-radius:24px}.title{font-size:30px}.summary{font-size:15px}.panel-title{font-size:24px}.signal-grid,.post-nav,.related-grid{grid-template-columns:1fr}.cover-shell,.cover{min-height:260px}.content{font-size:15px}}
</style>
