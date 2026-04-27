export const AI_META_START = '[[AI_META]]'
export const AI_META_END = '[[/AI_META]]'

export const stripAiMetaBlock = (content = '') => {
  const startIndex = content.indexOf(AI_META_START)
  if (startIndex === -1) return content
  return content.slice(0, startIndex).trimEnd()
}

export const extractAiMetaBlock = (content = '') => {
  const startIndex = content.indexOf(AI_META_START)
  if (startIndex === -1) return ''
  const metaStart = startIndex + AI_META_START.length
  const endIndex = content.indexOf(AI_META_END, metaStart)
  const raw = endIndex === -1 ? content.slice(metaStart) : content.slice(metaStart, endIndex)
  return raw.trim()
}

export const parseAiMeta = (content = '') => {
  const meta = {
    routeLabel: '',
    routeReason: '',
    answerMode: '',
    boundaryPolicy: '',
    categoryFilter: '',
    references: []
  }

  const block = extractAiMetaBlock(content)
  if (!block) return meta

  let section = ''
  for (const rawLine of block.split('\n')) {
    const line = rawLine.trim()
    if (!line) continue

    if (line === '参考来源：' || line === '参考来源:') {
      section = 'references'
      continue
    }

    if (section === 'references') {
      if (/^-+\s*/.test(line)) {
        meta.references.push(line.replace(/^-+\s*/, '').trim())
        continue
      }
      section = ''
    }

    const kv = line.match(/^([^：:]+)[：:]\s*(.+)$/)
    if (!kv) continue

    const key = kv[1].trim()
    const value = kv[2].trim()
    if (!value) continue

    switch (key) {
      case '检索策略':
        meta.routeLabel = value
        break
      case '策略原因':
        meta.routeReason = value
        break
      case '回答模式':
        meta.answerMode = value
        break
      case '边界策略':
        meta.boundaryPolicy = value
        break
      case '分类限定':
        meta.categoryFilter = value
        break
      default:
        break
    }
  }

  return meta
}

export const renderAiMessageHtml = (content = '') => {
  return stripAiMetaBlock(content)
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
}
