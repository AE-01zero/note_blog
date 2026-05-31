import { defineStore } from 'pinia'
import request from '@/api/request'
import { ElMessage } from 'element-plus'
import {
  getAnalysisHistory,
  deleteAnalysisHistory,
  clearAnalysisHistory,
  saveAnalysisHistory,
  getDecompileRecords,
  deleteDecompileRecord
} from '@/api/aiAnalysis'

export const useAiSecurityStore = defineStore('aiSecurity', {
  state: () => ({
    // 分析状态
    isAnalyzing: false,
    currentAnalysis: null,
    analysisHistory: [],

    // 历史分页
    historyPagination: { page: 1, size: 50, total: 0 },
    historyLoading: false,

    // 反编译文件记录
    decompileRecords: [],

    // APK分析结果
    apkResult: null,
    apkProgress: 0,
    apkStage: '',

    // SO分析结果
    soResult: null,

    // 协议分析结果
    protocolResult: null,

    // Prompt注入检测结果
    injectionResult: null,

    // AI 身份元数据
    aiIdentity: {
      moduleType: null,
      model: null,
      timestamp: null,
      confidence: null
    },

    // 流式输出状态
    isStreaming: false,
    currentStreamContent: '',

    // 错误状态
    error: null
  }),

  getters: {
    hasResult: (state) => state.apkResult || state.soResult || state.protocolResult,

    currentAiIdentity: (state) => {
      if (state.apkResult?.aiAnalysis) {
        return {
          moduleType: 'APK',
          model: state.apkResult.aiAnalysis.model || 'qwen-plus',
          timestamp: state.apkResult.aiAnalysis.timestamp || Date.now(),
          confidence: state.apkResult.aiAnalysis.confidence || 0.9
        }
      }
      if (state.soResult?.aiAnalysis) {
        return {
          moduleType: 'SO',
          model: state.soResult.aiAnalysis.model || 'qwen-plus',
          timestamp: state.soResult.aiAnalysis.timestamp || Date.now(),
          confidence: 0.9
        }
      }
      if (state.protocolResult?.aiAnalysis) {
        return {
          moduleType: 'PROTOCOL',
          model: state.protocolResult.aiAnalysis.model || 'qwen-plus',
          timestamp: state.protocolResult.aiAnalysis.timestamp || Date.now(),
          confidence: 0.9
        }
      }
      return state.aiIdentity
    },

    riskLevel: (state) => {
      if (state.apkResult?.detection?.verdict) {
        return state.apkResult.detection.verdict
      }
      return null
    },

    malwareType: (state) => {
      if (state.apkResult?.detection?.malwareType) {
        return state.apkResult.detection.malwareType
      }
      return null
    },

    detectedAlgorithms: (state) => {
      if (state.soResult?.algorithms) {
        return state.soResult.algorithms
      }
      return []
    }
  },

  actions: {
    // ========== Prompt注入检测 ==========
    async detectPromptInjection(prompt) {
      this.isAnalyzing = true
      this.error = null

      try {
        const response = await request({
          url: '/ai-security/detect-injection',
          method: 'post',
          data: { prompt }
        })

        this.injectionResult = response.data.data
        return this.injectionResult
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.isAnalyzing = false
      }
    },

    // ========== APK分析 ==========
    async analyzeApk(file, options = {}) {
      this.isAnalyzing = true
      this.apkProgress = 0
      this.apkStage = '上传文件'
      this.error = null

      try {
        const formData = new FormData()
        formData.append('file', file)

        if (options.depth) formData.append('depth', options.depth)
        if (options.aiEnhance) formData.append('aiEnhance', 'true')

        const progressInterval = setInterval(() => {
          if (this.apkProgress < 90) {
            this.apkProgress += Math.random() * 10
          }
        }, 500)

        this.apkStage = '发送请求'
        this.apkProgress = 20

        const response = await request({
          url: '/ai-analysis/apk',
          method: 'post',
          data: formData,
          headers: { 'Content-Type': 'multipart/form-data' },
          timeout: 300000
        })

        clearInterval(progressInterval)
        this.apkProgress = 100
        this.apkStage = '分析完成'

        this.apkResult = response.data.data

        // 持久化到后端数据库
        this._persistHistory('APK', file, this.apkResult)

        return this.apkResult
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.isAnalyzing = false
      }
    },

    // ========== SO文件分析 ==========
    async analyzeSo(file, aiAssist = false) {
      this.isAnalyzing = true
      this.error = null

      try {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('aiAssist', aiAssist)

        const response = await request({
          url: '/ai-analysis/so',
          method: 'post',
          data: formData,
          headers: { 'Content-Type': 'multipart/form-data' },
          timeout: 300000
        })

        this.soResult = response.data.data

        this._persistHistory('SO', file, this.soResult)

        return this.soResult
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.isAnalyzing = false
      }
    },

    // ========== 协议分析 ==========
    async analyzeProtocol(file) {
      this.isAnalyzing = true
      this.error = null

      try {
        const formData = new FormData()
        formData.append('file', file)

        const response = await request({
          url: '/ai-analysis/protocol',
          method: 'post',
          data: formData,
          headers: { 'Content-Type': 'multipart/form-data' },
          timeout: 300000
        })

        this.protocolResult = response.data.data

        this._persistHistory('PROTOCOL', file, this.protocolResult)

        return this.protocolResult
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.isAnalyzing = false
      }
    },

    // ========== 内部：持久化分析历史到后端 ==========
    _buildVerdict(moduleType, result) {
      switch (moduleType) {
        case 'APK':
          return result?.detection?.verdict || 'UNKNOWN'
        case 'SO':
          return result?.algorithms?.length > 0 ? 'HAS_CRYPTO' : 'CLEAN'
        case 'PROTOCOL':
          return result?.protocol?.type || 'UNKNOWN'
        default:
          return 'COMPLETED'
      }
    },

    _buildRiskLevel(moduleType, result) {
      switch (moduleType) {
        case 'APK':
          return result?.detection?.riskLevel || 'INFO'
        case 'SO':
          return result?.algorithms?.length > 3 ? 'HIGH' : 'INFO'
        case 'PROTOCOL':
          return 'INFO'
        default:
          return 'INFO'
      }
    },

    async _persistHistory(moduleType, file, result) {
      try {
        const data = {
          moduleType,
          fileName: file.name,
          fileSize: file.size,
          result,
          verdict: this._buildVerdict(moduleType, result),
          riskLevel: this._buildRiskLevel(moduleType, result)
        }
        await saveAnalysisHistory(data)
        // 重新加载历史列表
        await this.loadHistoryFromBackend()
      } catch (e) {
        console.warn('持久化分析历史失败:', e)
      }
    },

    // ========== AI辅助安全分析 ==========
    async analyzeWithAI(target, analysisType) {
      this.isAnalyzing = true
      this.error = null

      try {
        const response = await request({
          url: '/ai-security/analyze',
          method: 'post',
          data: {
            analysisTarget: target,
            analysisType: analysisType
          }
        })

        this.currentAnalysis = response.data.data
        return this.currentAnalysis
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.isAnalyzing = false
      }
    },

    // ========== 知识检索 ==========
    async searchKnowledge(query, type = 'all', limit = 10) {
      try {
        const response = await request({
          url: '/ai-analysis/search',
          method: 'get',
          params: { q: query, type, limit }
        })

        return response.data.data || []
      } catch (error) {
        console.error('知识检索失败:', error)
        return []
      }
    },

    // ========== 清除结果 ==========
    clearApkResult() {
      this.apkResult = null
      this.apkProgress = 0
      this.apkStage = ''
    },

    clearSoResult() {
      this.soResult = null
    },

    clearProtocolResult() {
      this.protocolResult = null
    },

    clearInjectionResult() {
      this.injectionResult = null
    },

    clearAllResults() {
      this.apkResult = null
      this.soResult = null
      this.protocolResult = null
      this.injectionResult = null
      this.currentAnalysis = null
      this.apkProgress = 0
      this.apkStage = ''
    },

    // ========== 流式分析管理 ==========
    showProgressDialog: false,
    progressModuleType: '',
    progressFileName: '',
    progressFileSize: 0,
    streamingResult: null,

    openProgressDialog(moduleType, fileName, fileSize) {
      this.progressModuleType = moduleType
      this.progressFileName = fileName
      this.progressFileSize = fileSize
      this.showProgressDialog = true
      this.streamingResult = null
    },

    closeProgressDialog() {
      this.showProgressDialog = false
    },

    setStreamingResult(result) {
      this.streamingResult = result
    },

    // ========== 历史记录管理（后端持久化） ==========

    /** 从后端加载分析历史 */
    async loadHistoryFromBackend(moduleType) {
      this.historyLoading = true
      try {
        const response = await getAnalysisHistory({
          moduleType: moduleType || undefined,
          page: this.historyPagination.page,
          size: this.historyPagination.size
        })
        const data = response.data?.data || response.data
        if (data) {
          this.analysisHistory = data.list || []
          this.historyPagination.total = data.total || 0
          this.historyPagination.page = data.page || 1
          this.historyPagination.size = data.size || 50
        }
      } catch (e) {
        console.error('加载分析历史失败:', e)
      } finally {
        this.historyLoading = false
      }
    },

    /** 删除单条历史 */
    async removeFromHistory(id) {
      try {
        await deleteAnalysisHistory(id)
        this.analysisHistory = this.analysisHistory.filter(h => h.id !== id)
        this.historyPagination.total = Math.max(0, this.historyPagination.total - 1)
        ElMessage.success('已删除')
      } catch (e) {
        ElMessage.error('删除失败: ' + (e.message || '未知错误'))
        throw e
      }
    },

    /** 清空所有历史 */
    async clearHistory() {
      try {
        const response = await clearAnalysisHistory()
        this.analysisHistory = []
        this.historyPagination.total = 0
        const data = response.data?.data || response.data
        ElMessage.success(`已清空 ${data?.deletedCount || ''} 条记录`)
      } catch (e) {
        ElMessage.error('清空失败: ' + (e.message || '未知错误'))
        throw e
      }
    },

    /** 加载反编译文件记录 */
    async loadDecompileRecords() {
      try {
        const response = await getDecompileRecords()
        this.decompileRecords = response.data?.data || response.data || []
        return this.decompileRecords
      } catch (e) {
        console.error('加载反编译记录失败:', e)
        return []
      }
    },

    /** 删除反编译记录 */
    async deleteDecompileRecord(recordId) {
      try {
        await deleteDecompileRecord(recordId)
        this.decompileRecords = this.decompileRecords.filter(r => r.id !== recordId)
        ElMessage.success('已删除反编译文件')
      } catch (e) {
        ElMessage.error('删除失败: ' + (e.message || '未知错误'))
        throw e
      }
    },

    // ========== 流式分析 action ==========
    async startStreamingAnalysis(file, moduleType) {
      const { startApkAnalysis, startSoAnalysis, startProtocolAnalysis, startDecompileAnalysis } = await import('@/api/aiAnalysis')

      const startApiMap = {
        apk: startApkAnalysis,
        so: startSoAnalysis,
        protocol: startProtocolAnalysis,
        decompile: startDecompileAnalysis
      }

      const startApi = startApiMap[moduleType]
      if (!startApi) throw new Error(`Unknown module type: ${moduleType}`)

      const response = await startApi(file)
      const taskId = response.data?.data?.taskId || response.data?.taskId
      if (!taskId) throw new Error('未能获取任务ID')
      return taskId
    },

    /** 保存流式分析结果并持久化 */
    saveStreamingResult(moduleType, result) {
      if (!result) return
      this.streamingResult = result

      // 同步到模块特定的结果中
      switch (moduleType) {
        case 'apk':
          this.apkResult = result
          break
        case 'so':
          this.soResult = result
          break
        case 'protocol':
          this.protocolResult = result
          break
      }

      // 持久化到后端
      const moduleTypeUpper = moduleType.toUpperCase()
      const fileName = this.progressFileName
      const fileSize = this.progressFileSize
      saveAnalysisHistory({
        moduleType: moduleTypeUpper,
        fileName,
        fileSize,
        result,
        verdict: this._buildVerdict(moduleTypeUpper, result),
        riskLevel: this._buildRiskLevel(moduleTypeUpper, result)
      }).then(() => {
        this.loadHistoryFromBackend()
      }).catch(e => {
        console.warn('持久化流式分析结果失败:', e)
      })
    }
  }
})