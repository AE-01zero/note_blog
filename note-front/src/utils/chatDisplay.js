export function getSourceDisplayName(source) {
  if (!source) return ''

  if (typeof source === 'string') {
    return source.trim()
  }

  return String(
    source.fileName ||
    source.originalFilename ||
    source.title ||
    source.name ||
    ''
  ).trim()
}

export function normalizeSourceCitations(sources) {
  if (!Array.isArray(sources)) return []

  const unique = new Map()

  for (const source of sources) {
    const fileName = getSourceDisplayName(source)
    if (!fileName) continue

    const fileType = typeof source?.fileType === 'string' ? source.fileType.trim() : ''
    const category = typeof source?.category === 'string' ? source.category.trim() : ''
    const key = [fileName, fileType, category].join('|').toLowerCase()

    if (!unique.has(key)) {
      unique.set(key, {
        ...(typeof source === 'object' && source !== null ? source : {}),
        fileName,
        ...(fileType ? { fileType } : {}),
        ...(category ? { category } : {})
      })
    }
  }

  return [...unique.values()]
}

export function normalizeKnowledgeResult(event) {
  if (!event || typeof event !== 'object') return event

  return {
    ...event,
    sources: normalizeSourceCitations(event.sources)
  }
}

export default {
  getSourceDisplayName,
  normalizeSourceCitations,
  normalizeKnowledgeResult
}
