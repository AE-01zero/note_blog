/**
 * AI 响应解析器 — 解析 <thinking> 思考轨迹、[PROBE:...] 探针指令、AI 身份元数据
 */

const THINKING_RE = /<thinking>([\s\S]*?)<\/thinking>/i
const PROBE_RE = /\[PROBE:([a-zA-Z_]+):([^:]+):([^\]]+)\]/g

/** 提取 <thinking>...</thinking> 内容 */
export function parseThinking(text) {
  if (!text) return ''
  const m = text.match(THINKING_RE)
  return m ? m[1].trim() : ''
}

/** 移除 <thinking>...</thinking> 块 */
export function stripThinking(text) {
  if (!text) return ''
  return text.replace(THINKING_RE, '').trim()
}

/** 解析 [PROBE:type:query:label] 指令，返回探针对象数组 */
export function parseProbes(text) {
  if (!text) return []
  const probes = []
  const re = new RegExp(PROBE_RE.source, 'g')
  let m
  while ((m = re.exec(text)) !== null) {
    probes.push({
      type: m[1],
      query: m[2],
      label: m[3],
      raw: m[0]
    })
  }
  return probes
}

/** 移除 [PROBE:...] 指令 */
export function stripProbes(text) {
  if (!text) return ''
  return text.replace(PROBE_RE, '').trim()
}

/** 结构化解析 thinking 块内的步骤条目 */
export function parseThinkingItems(thinkingText) {
  if (!thinkingText) return []
  const items = []
  const lines = thinkingText.split('\n')
  for (const line of lines) {
    const trimmed = line.replace(/^[-*]\s*/, '').trim()
    if (!trimmed) continue
    const iconMatch = trimmed.match(/^([\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}])\s*(.+?)[：:]\s*(.+)/u)
    if (iconMatch) {
      items.push({ icon: iconMatch[1], label: iconMatch[2].trim(), content: iconMatch[3].trim() })
    } else {
      const colonMatch = trimmed.match(/^(.+?)[：:]\s*(.+)/)
      if (colonMatch && items.length > 0) {
        items[items.length - 1].content += '\n' + trimmed
      } else if (colonMatch) {
        items.push({ icon: '', label: colonMatch[1].trim(), content: colonMatch[2].trim() })
      } else if (items.length > 0) {
        items[items.length - 1].content += '\n' + trimmed
      }
    }
  }
  return items
}

const MODULE_LABELS = {
  APK: 'APK 静态审计',
  SO: 'Native 层逆向分析',
  PROTOCOL: '协议流量分析',
  SANDBOX: '沙箱行为分析',
  FILE: '单文件代码审计',
  GLOBAL: '全局源码合规评估'
}

/** 根据 moduleType 推断身份信息 */
export function extractAiIdentity(metadata = {}) {
  return {
    moduleType: metadata.moduleType || 'GENERAL',
    moduleLabel: MODULE_LABELS[metadata.moduleType] || '安全分析',
    model: metadata.model || 'qwen-plus',
    timestamp: metadata.timestamp || Date.now(),
    confidence: metadata.confidence ?? 0.9
  }
}

/** 一站式解析 AI 响应 */
export function parseFullAiResponse(text, metadata = {}) {
  const thinking = parseThinking(text)
  const reportBody = stripProbes(stripThinking(text))
  return {
    thinking,
    thinkingItems: parseThinkingItems(thinking),
    reportBody,
    probes: parseProbes(text),
    identity: extractAiIdentity(metadata)
  }
}

export default {
  parseThinking,
  stripThinking,
  parseProbes,
  stripProbes,
  parseThinkingItems,
  extractAiIdentity,
  parseFullAiResponse
}
