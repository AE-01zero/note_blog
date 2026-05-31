import request from './request'

// ========== AI安全分析接口 ==========

// Prompt注入检测
export const detectPromptInjection = (prompt, detectionLevel) => {
  return request({
    url: '/ai-security/detect-injection',
    method: 'post',
    data: { prompt, detectionLevel }
  })
}

// AI增强Prompt注入检测 (规则引擎 + 大模型深度分析)
export const detectPromptInjectionEnhanced = (prompt, detectionLevel) => {
  return request({
    url: '/ai-security/detect-injection/enhanced',
    method: 'post',
    data: { prompt, detectionLevel },
    timeout: 60000
  })
}

// AI增强Prompt注入检测 — SSE流式输出
export const detectPromptInjectionEnhancedStreamUrl = () => '/api/ai-security/detect-injection/enhanced/stream'

// 获取AI增强分析开关状态
export const getAiToggleStatus = () => {
  return request({
    url: '/ai-security/ai-toggle',
    method: 'get'
  })
}

// 切换AI增强分析开关
export const updateAiToggle = (enabled) => {
  return request({
    url: '/ai-security/ai-toggle',
    method: 'post',
    data: { enabled }
  })
}

// AI辅助安全分析
export const analyzeWithAI = (target, analysisType) => {
  return request({
    url: '/ai-security/analyze',
    method: 'post',
    data: { analysisTarget: target, analysisType }
  })
}

// ========== APK分析接口 ==========

// APK静态分析
export const analyzeApk = (file, options = {}) => {
  const formData = new FormData()
  formData.append('file', file)

  if (options.depth) formData.append('depth', options.depth)
  if (options.aiEnhance) formData.append('aiEnhance', 'true')
  if (options.includeDecompiled) formData.append('includeDecompiled', 'true')

  return request({
    url: '/ai-analysis/apk',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 6000000
  })
}

// ========== SO文件分析接口 ==========

// SO文件分析
export const analyzeSo = (file, aiAssist = false) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('aiAssist', aiAssist)

  return request({
    url: '/ai-analysis/so',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 6000000
  })
}

// ========== 协议分析接口 ==========

// PCAP协议分析
export const analyzeProtocol = (file, aiAssist = false) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('aiAssist', aiAssist)

  return request({
    url: '/ai-analysis/protocol',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 6000000
  })
}

// ========== 知识检索接口 ==========

// 知识库语义检索
export const searchKnowledge = (query, type = 'all', limit = 10) => {
  return request({
    url: '/ai-analysis/search',
    method: 'get',
    params: { q: query, type, limit }
  })
}

// 检索相似样本
export const findSimilarSamples = (features) => {
  return request({
    url: '/ai-analysis/similar-samples',
    method: 'post',
    data: { features }
  })
}

// ========== 知识库文档管理 ==========

// 获取AI安全知识库文档
export const getAiSecurityDocs = (params) => {
  return request({
    url: '/documents/ai-security',
    method: 'get',
    params
  })
}

// 上传文档到知识库
export const uploadToKnowledgeBase = (file, category) => {
  const formData = new FormData()
  formData.append('file', file)
  if (category) formData.append('category', category)

  return request({
    url: '/documents/upload-to-knowledge-base',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ========== 分析历史 ==========

// 获取分析历史（支持模块筛选和分页）
export const getAnalysisHistory = (params) => {
  return request({
    url: '/ai-analysis/history',
    method: 'get',
    params
  })
}

// 删除单条分析历史
export const deleteAnalysisHistory = (historyId) => {
  return request({
    url: `/ai-analysis/history/${historyId}`,
    method: 'delete'
  })
}

// 清空所有分析历史
export const clearAnalysisHistory = () => {
  return request({
    url: '/ai-analysis/history',
    method: 'delete'
  })
}

// 保存分析历史
export const saveAnalysisHistory = (data) => {
  return request({
    url: '/ai-analysis/history',
    method: 'post',
    data
  })
}

// ========== 反编译文件管理 ==========

// 获取反编译记录列表
export const getDecompileRecords = () => {
  return request({
    url: '/ai-analysis/decompile-records',
    method: 'get'
  })
}

// 删除反编译记录（清理磁盘文件）
export const deleteDecompileRecord = (recordId) => {
  return request({
    url: `/ai-analysis/decompile-records/${recordId}`,
    method: 'delete'
  })
}

// 导出分析报告
export const exportAnalysisReport = (analysisId) => {
  return request({
    url: `/ai-analysis/report/${analysisId}`,
    method: 'get',
    responseType: 'blob'
  })
}

// ========== ApkTool逆向分析接口 ==========

// APK反编译
export const decompileApk = (file) => {
  const formData = new FormData()
  formData.append('file', file)

  // 大文件超时60分钟
  const timeout = file.size > 100 * 1024 * 1024 ? 3600000 : 1800000

  return request({
    url: '/ai-analysis/apk/decompile',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout
  })
}

// 获取源码文件列表
export const getApkSourceFiles = (workDir) => {
  return request({
    url: '/ai-analysis/apk/files',
    method: 'get',
    params: { workDir }
  })
}

// 获取文件内容
export const getApkFileContent = (params) => {
  return request({
    url: '/ai-analysis/apk/file/content',
    method: 'get',
    params
  })
}

// 获取文件树
export const getApkFileTree = (params) => {
  return request({
    url: '/ai-analysis/apk/file-tree',
    method: 'get',
    params
  })
}

// 获取静态交叉引用
export const getApkFileXref = (params) => {
  return request({
    url: '/ai-analysis/apk/file/xref',
    method: 'get',
    params
  })
}

// 全局静态代码检索
export const searchApkCodebase = (params) => {
  return request({
    url: '/ai-analysis/apk/file/search',
    method: 'get',
    params
  })
}

// AI分析指定文件
export const analyzeApkFile = (params) => {
  return request({
    url: '/ai-analysis/apk/file/analyze',
    method: 'post',
    params
  })
}

// AI辅助分析SO文件
export const analyzeSoFromApk = (file, aiAssist = false) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('aiAssist', aiAssist)

  return request({
    url: '/ai-analysis/apk/so/analyze',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 6000000
  })
}

// AI辅助分析工作区SO文件
export const analyzeSoFromWorkspace = (workDir, path, aiAssist = false) => {
  return request({
    url: '/ai-analysis/apk/so/analyze-workspace',
    method: 'post',
    params: { workDir, path, aiAssist }
  })
}

// 获取源码还原评估
export const getReconstructionAssessment = (workDir) => {
  return request({
    url: '/ai-analysis/apk/reconstruction-assessment',
    method: 'get',
    params: { workDir }
  })
}

// AI源码综合分析
export const analyzeApkSource = (params) => {
  return request({
    url: '/ai-analysis/apk/source-analyze',
    method: 'post',
    params
  })
}

// 执行AI安全探针
export const executeApkProbe = (params) => {
  return request({
    url: '/ai-analysis/apk/execute-probe',
    method: 'post',
    params
  })
}

// ========== AI指导分析接口 ==========

// 创建指导分析会话
export const startGuidedAnalysis = (file, moduleType, focusAreas) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('moduleType', moduleType)
  if (focusAreas) formData.append('focusAreas', focusAreas)

  return request({
    url: '/ai-analysis/guided/start',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

// 获取会话状态
export const getGuidedSession = (sessionId) => {
  return request({
    url: `/ai-analysis/guided/session/${sessionId}`,
    method: 'get'
  })
}

// 执行下一步
export const guidedNextStep = (sessionId, userInstruction) => {
  return request({
    url: `/ai-analysis/guided/${sessionId}/next`,
    method: 'post',
    data: { userInstruction }
  })
}

// 跳过当前步骤
export const guidedSkipStep = (sessionId) => {
  return request({
    url: `/ai-analysis/guided/${sessionId}/skip`,
    method: 'post'
  })
}

// 终止分析
export const guidedTerminate = (sessionId) => {
  return request({
    url: `/ai-analysis/guided/${sessionId}/terminate`,
    method: 'post'
  })
}

// 创建SSE连接（返回EventSource URL）
export const getGuidedStreamUrl = (sessionId) => {
  return `/api/ai-analysis/guided/stream/${sessionId}`
}

// ========== 流式分析接口 (SSE 进度管线) ==========

// 启动 APK 流式分析
export const startApkAnalysis = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/ai-analysis/apk/start',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  })
}

// 启动 SO 流式分析
export const startSoAnalysis = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/ai-analysis/so/start',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  })
}

// 启动 Protocol 流式分析
export const startProtocolAnalysis = (file, aiAssist = false) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('aiAssist', aiAssist)
  return request({
    url: '/ai-analysis/protocol/start',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  })
}

// 启动反编译流式分析
export const startDecompileAnalysis = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/ai-analysis/apk/decompile/start',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  })
}

// 获取 SSE 流 URL
export const getApkStreamUrl = (taskId) => `/api/ai-analysis/apk/stream/${taskId}`
export const getSoStreamUrl = (taskId) => `/api/ai-analysis/so/stream/${taskId}`
export const getProtocolStreamUrl = (taskId) => `/api/ai-analysis/protocol/stream/${taskId}`
export const getDecompileStreamUrl = (taskId) => `/api/ai-analysis/apk/decompile/stream/${taskId}`

// 默认导出（方便使用 import api from '@/api/aiAnalysis'）
export default {
  detectPromptInjection,
  detectPromptInjectionEnhanced,
  getAiToggleStatus,
  updateAiToggle,
  analyzeWithAI,
  analyzeApk,
  analyzeSo,
  analyzeProtocol,
  searchKnowledge,
  findSimilarSamples,
  getAiSecurityDocs,
  uploadToKnowledgeBase,
  getAnalysisHistory,
  deleteAnalysisHistory,
  clearAnalysisHistory,
  saveAnalysisHistory,
  getDecompileRecords,
  deleteDecompileRecord,
  exportAnalysisReport,
  decompileApk,
  getApkSourceFiles,
  getApkFileContent,
  getApkFileTree,
  getApkFileXref,
  searchApkCodebase,
  analyzeApkFile,
  analyzeSoFromApk,
  analyzeSoFromWorkspace,
  getReconstructionAssessment,
  analyzeApkSource,
  executeApkProbe,
  startGuidedAnalysis,
  getGuidedSession,
  guidedNextStep,
  guidedSkipStep,
  guidedTerminate,
  getGuidedStreamUrl
}