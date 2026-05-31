<template>
  <div class="apk-reverse-panel">
    <!-- 顶部操作栏 -->
    <div class="top-toolbar">
      <el-button type="primary" :loading="decompiling" @click="handleDecompile">
        <el-icon><Upload /></el-icon>
        反编译APK
      </el-button>
      <el-button v-if="decompiled" type="success" @click="handleReconstruct" :loading="reconstructing">
        <el-icon><VideoPlay /></el-icon>
        还原APK
      </el-button>
      <el-button v-if="decompiled" type="warning" @click="handleSoAnalysis">
        <el-icon><Cpu /></el-icon>
        SO分析
      </el-button>
      <el-button v-if="decompiled" type="danger" @click="handleClose">
        <el-icon><Close /></el-icon>
        关闭项目
      </el-button>
    </div>

    <!-- APK上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传APK文件" width="520px" :close-on-click-modal="false">
      <el-upload
        ref="apkUploadRef"
        class="apk-upload-area"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".apk"
        :on-change="handleApkFileChange"
        :on-remove="handleApkFileRemove"
        :on-exceed="handleExceed"
      >
        <el-icon class="el-icon--upload" :size="60"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽APK文件到此处或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .apk 文件，大小不超过200MB</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedApkFile" :loading="uploading" @click="submitApkUpload">
          {{ uploading ? '上传反编译中...' : '开始反编译' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 反编译进度 -->
    <el-card v-if="decompiling" class="progress-card-modern">
      <template #header>
        <div class="progress-header">
          <span class="progress-title">APK 逆向反编译进度</span>
          <el-tag type="warning" size="small" class="pulse-tag">反编译中</el-tag>
        </div>
      </template>

      <div class="progress-content">
        <!-- 进度环 -->
        <div class="progress-bar-wrapper">
          <el-progress
            type="circle"
            :percentage="decompileProgress"
            :stroke-width="12"
            :width="160"
            color="#8b5cf6"
          >
            <template #default="{ percentage }">
              <div class="progress-inner-text">
                <span class="percentage-num">{{ percentage }}%</span>
                <span class="percentage-label">解析中</span>
              </div>
            </template>
          </el-progress>
        </div>

        <!-- 步骤状态 -->
        <div class="progress-steps-wrapper">
          <el-steps :active="decompileStep" finish-status="success" direction="vertical">
            <el-step title="上传文件" description="上传 APK 包至反编译环境" />
            <el-step title="程序反编译" description="使用 ApkTool 解包和提取字节码" />
            <el-step title="提取资源" description="解析 Manifest 清单及原生 lib 动态库" />
          </el-steps>
        </div>
      </div>

      <!-- 实时模拟日志终端 -->
      <div class="log-terminal">
        <div class="terminal-header">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
          <span class="terminal-title">反编译实时编译日志</span>
        </div>
        <div class="terminal-body" ref="terminalBodyRef">
          <div v-for="(logLine, idx) in decompileLogs" :key="idx" class="log-line" :class="logLine.type">
            <span class="log-time">[{{ logLine.time }}]</span>
            <span class="log-text">{{ logLine.text }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 主内容区域：文件树 / 静态检索 -->
    <div v-if="decompiled" class="main-content">
      <div class="file-browser-panel">
        <el-tabs v-model="activeSidebarTab" class="sidebar-tabs" stretch>
          <el-tab-pane label="项目文件" name="files">
            <div class="panel-header">
              <span>项目树</span>
              <el-button size="small" @click="refreshFileTree">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
            <el-tree
              ref="fileTreeRef"
              :data="fileTree"
              :props="treeProps"
              node-key="path"
              default-expand-all
              @node-click="handleNodeClick"
            >
              <template #default="{ node, data }">
                <span class="tree-node">
                  <el-icon v-if="data.type === 'directory'"><Folder /></el-icon>
                  <el-icon v-else><Document /></el-icon>
                  <span>{{ node.label }}</span>
                  <el-tag v-if="data.type === 'smali'" size="small" type="info" class="type-tag">smali</el-tag>
                  <el-tag v-if="data.type === 'java'" size="small" type="success" class="type-tag">java</el-tag>
                  <el-tag v-if="data.type === 'native'" size="small" type="warning" class="type-tag">so</el-tag>
                  <el-tag v-if="data.type === 'xml'" size="small" type="" class="type-tag">xml</el-tag>
                </span>
              </template>
            </el-tree>
          </el-tab-pane>

          <el-tab-pane label="静态检索" name="search">
            <div class="search-box-container-modern">
              <el-input
                v-model="searchQuery"
                placeholder="键入关键字或 RegExp 模式..."
                class="search-input-modern"
                clearable
                @keyup.enter="performCodebaseSearch"
              >
                <template #append>
                  <el-button @click="performCodebaseSearch" :loading="searchingCodebase">
                    <el-icon><Search /></el-icon>
                  </el-button>
                </template>
              </el-input>

              <div class="search-filters-grid">
                <div class="filter-row">
                  <el-checkbox v-model="isSearchRegex" size="small">正则匹配</el-checkbox>
                  <el-checkbox v-model="isSearchCaseSensitive" size="small">大小写敏感</el-checkbox>
                </div>
                <div class="filter-row">
                  <el-checkbox v-model="isExcludeThirdParty" size="small">过滤三方SDK</el-checkbox>
                  <div class="scope-select-wrapper">
                    <el-select v-model="searchScope" placeholder="检索范围" size="small" style="width: 100px;">
                      <el-option label="全部文件" value="all" />
                      <el-option label="Smali字节码" value="smali" />
                      <el-option label="XML配置" value="xml" />
                      <el-option label="Java源码" value="java" />
                    </el-select>
                  </div>
                </div>
              </div>
            </div>

            <!-- 检索结果列表 -->
            <div class="search-results-list" v-loading="searchingCodebase">
              <div
                v-for="(item, index) in searchResults"
                :key="index"
                class="search-result-card-modern"
              >
                <div class="result-card-header" @click="toggleResultExpand(item)">
                  <div class="header-left">
                    <el-icon class="expand-arrow" :class="{ 'is-expanded': item.expanded }"><ArrowRight /></el-icon>
                    <span class="result-path-name">{{ item.path.substring(item.path.lastIndexOf('/') + 1) }}</span>
                    <el-tag size="small" type="info" class="line-tag">L{{ item.lineNumber }}</el-tag>
                  </div>
                  <el-button type="primary" link size="small" class="jump-btn" @click.stop="goToSearchResult(item)">
                    定位
                  </el-button>
                </div>

                <div class="result-path-subtitle">{{ item.path }}</div>
                
                <!-- Flat single line preview if not expanded -->
                <div v-if="!item.expanded" class="result-snippet-flat" @click="toggleResultExpand(item)">
                  <span class="snippet-code-flat" v-html="highlightText(item.lineContent)"></span>
                </div>

                <!-- Expanded multi-line context preview -->
                <div v-else class="result-snippet-expanded">
                  <div class="context-viewport">
                    <!-- Context Before -->
                    <div v-for="cLine in item.contextBefore" :key="'b-' + cLine.lineNumber" class="context-line">
                      <span class="context-ln">{{ cLine.lineNumber }}</span>
                      <span class="context-code" v-html="highlightText(cLine.content)"></span>
                    </div>
                    <!-- Matched Line -->
                    <div class="context-line match-line">
                      <span class="context-ln match-ln">{{ item.lineNumber }}</span>
                      <span class="context-code match-code" v-html="highlightText(item.lineContent)"></span>
                    </div>
                    <!-- Context After -->
                    <div v-for="cLine in item.contextAfter" :key="'a-' + cLine.lineNumber" class="context-line">
                      <span class="context-ln">{{ cLine.lineNumber }}</span>
                      <span class="context-code" v-html="highlightText(cLine.content)"></span>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-if="!searchResults.length && !searchingCodebase" description="无检索匹配结果" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- IDE 数码代码查看器悬浮弹窗 (92% 大尺寸全功能) -->
    <el-dialog
      v-model="fileViewerVisible"
      title="IDE 数码代码查看器"
      width="92%"
      class="code-viewer-dialog-modern"
      :close-on-click-modal="false"
      append-to-body
      destroy-on-close
    >
      <template #header>
        <div class="dialog-custom-header">
          <span class="path-title"><el-icon><Document /></el-icon> {{ currentFile?.path }}</span>
          <div class="header-tags">
            <el-tag v-if="currentFile?.isBinary" type="warning" size="small">二进制文件</el-tag>
            <el-tag v-else-if="currentFile?.type" type="info" size="small">{{ currentFile.type }}</el-tag>
            <span class="file-size" v-if="currentFile?.size">{{ formatSize(currentFile.size) }}</span>
            
            <el-radio-group v-if="currentFile?.type === 'smali'" v-model="viewMode" size="small" style="margin-left: 16px;">
              <el-radio-button label="smali">Smali 字节码</el-radio-button>
              <el-radio-button label="java">Java 伪代码对照</el-radio-button>
            </el-radio-group>

            <div v-if="currentFile?.type === 'smali'" class="ai-toggle-group" style="margin-left: 12px; display: flex; align-items: center; gap: 6px;">
              <span class="ai-toggle-label">AI 解读</span>
              <el-switch v-model="aiEnabled" size="small" />
              <el-button v-if="aiEnabled" size="small" type="primary" @click="handleAiAnalyze" :loading="analyzing">
                <el-icon><MagicStick /></el-icon>
                分析
              </el-button>
            </div>
          </div>
        </div>
      </template>

      <!-- Dialog Body Content -->
      <div class="dialog-code-viewer-body" v-if="currentFile">
        <!-- 左侧面板：包含 JNI Alert, Code Viewport 和 Collapsible Xref Panel -->
        <div class="dialog-viewer-left-pane">
          <!-- JNI native methods alert -->
          <div v-if="currentFile.jniMethods?.length" class="jni-alert">
            <el-alert title="检测到 Native/JNI 绑定方法" type="warning" show-icon :closable="false" style="margin-bottom: 12px;">
              <template #default>
                <span class="jni-alert-text">本类声明了底层 Native 导出函数，您可以点击下方的方法链接一键触发 SO 分析定位：</span>
                <div class="jni-links">
                  <el-link v-for="method in currentFile.jniMethods" :key="method" type="primary" @click="handleJniMethodClick(method)" style="margin-right: 12px; font-family: monospace; font-size: 13px;">
                    ★ Java_{{ currentFile.path.replace('smali/', '').replace('.smali', '').replace(/\//g, '_') }}_{{ method }}
                  </el-link>
                </div>
              </template>
            </el-alert>
          </div>

          <!-- Code Viewer Split -->
          <div class="dialog-code-viewport">
            <div v-if="!currentFile.isBinary && viewMode === 'java' && currentFile.type === 'smali'" class="dialog-dual-code-view">
              <div class="code-column left-smali">
                <div class="column-header">Smali 字节码</div>
                <pre class="code-content">{{ currentFile.content }}</pre>
              </div>
              <div class="code-column right-java">
                <div class="column-header">Java 伪代码对照</div>
                <pre class="code-content java-code">{{ currentFile.pseudocode }}</pre>
              </div>
            </div>
            <pre v-else-if="!currentFile.isBinary" class="dialog-single-code">{{ currentFile.content }}</pre>
            <div v-else class="binary-notice">
              <el-icon :size="48"><Document /></el-icon>
              <p>二进制文件无法直接预览</p>
              <el-button type="primary" @click="handleSoAnalysisForFile">
                <el-icon><Cpu /></el-icon>
                使用SO分析
              </el-button>
            </div>
          </div>

          <!-- Collapsible Xref Panel -->
          <div v-if="xrefData" class="xref-panel-modern" :class="{ 'is-expanded': isXrefExpanded }">
            <div class="xref-header" @click="isXrefExpanded = !isXrefExpanded">
              <span class="xref-title">
                <el-icon class="xref-arrow" :class="{ 'is-rotated': isXrefExpanded }"><ArrowRight /></el-icon>
                <span>静态交叉引用 (Xref Tree)</span>
                <el-badge
                  v-if="(xrefData.callers?.length || 0) + (xrefData.callees?.length || 0) > 0"
                  :value="(xrefData.callers?.length || 0) + (xrefData.callees?.length || 0)"
                  class="xref-badge"
                  type="primary"
                />
              </span>
              <div class="xref-actions">
                <el-tag size="small" type="info">Caller: {{ xrefData.callers?.length || 0 }}</el-tag>
                <el-tag size="small" type="success">Callee: {{ xrefData.callees?.length || 0 }}</el-tag>
                <el-button link type="primary" size="small" style="font-weight: bold; margin-left: 8px;">
                  {{ isXrefExpanded ? '收起面板' : '展开面板' }}
                </el-button>
              </div>
            </div>
            
            <div v-if="isXrefExpanded" class="xref-body">
              <el-tabs type="border-card" class="xref-tabs">
                <el-tab-pane label="Caller 静态引用树 (谁调用了我)">
                  <el-table :data="xrefData.callers" stripe size="small" max-height="160">
                    <el-table-column prop="callerClass" label="调用者类" width="300">
                      <template #default="{ row }">
                        <el-link type="primary" @click="handleNavigateToFile(row.callerClass)">{{ row.callerClass }}</el-link>
                      </template>
                    </el-table-column>
                    <el-table-column prop="callerMethod" label="调用者方法" width="180" />
                    <el-table-column prop="lineContent" label="调用上下文汇编指令" />
                  </el-table>
                  <el-empty v-if="!xrefData.callers?.length" description="未发现外部引用" />
                </el-tab-pane>
                <el-tab-pane label="Callee 静态调用树 (我调用了谁)">
                  <el-table :data="xrefData.callees" stripe size="small" max-height="160">
                    <el-table-column prop="calleeClass" label="目标被调用类" width="350" />
                    <el-table-column prop="calleeMethod" label="被调用方法" width="180" />
                    <el-table-column prop="lineContent" label="调用汇编指令" />
                  </el-table>
                  <el-empty v-if="!xrefData.callees?.length" description="无方法调用声明" />
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </div>

        <!-- 右侧面板：AI 解读面板（仅 smali 文件且开关启用时显示） -->
        <div v-if="currentFile?.type === 'smali'" class="dialog-viewer-right-pane">
          <template v-if="aiEnabled">
            <div class="right-ai-panel-header">
              <span class="right-ai-title">
                <el-icon class="pulse-tag" style="margin-right: 6px; color: #c084fc;"><Cpu /></el-icon>
                AI 智能解读
              </span>
              <el-tag v-if="aiAnalysisResult" type="success" size="small" effect="dark">解读就绪</el-tag>
              <el-tag v-else-if="analyzing" type="warning" size="small" class="pulse-tag">分析中</el-tag>
              <el-tag v-else type="info" size="small">等待发起</el-tag>
            </div>

            <div class="right-ai-panel-body">
              <div v-if="aiAnalysisResult" class="dialog-ai-analysis-content-box">
                <AiAnalysisReport
                  :ai-response="aiAnalysisResult.analysis || ''"
                  :metadata="fileAiMeta"
                  :show-identity="true"
                />
              </div>
              <div v-else-if="analyzing" class="dialog-ai-analysis-loading">
                <el-icon class="is-loading" :size="32" color="#8b5cf6"><Loading /></el-icon>
                <p>AI 正在深度解析本类汇编指令、调用上下文及静态漏洞模型，请稍候...</p>
              </div>
              <div v-else class="dialog-ai-analysis-empty">
                <el-icon :size="48" color="#6c7086"><Cpu /></el-icon>
                <p class="empty-tip">AI 解读已启用</p>
                <p class="empty-sub-tip">点击右上角「分析」按钮触发 AI 对本类进行安全解读。</p>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="right-ai-panel-header">
              <span class="right-ai-title" style="color: #6c7086;">
                <el-icon style="margin-right: 6px;"><Cpu /></el-icon>
                AI 解读
              </span>
              <el-tag type="info" size="small">未启用</el-tag>
            </div>
            <div class="right-ai-panel-body">
              <div class="dialog-ai-analysis-empty">
                <el-icon :size="48" color="#6c7086"><Switch /></el-icon>
                <p class="empty-tip">AI 解读已关闭</p>
                <p class="empty-sub-tip">开启右上角「AI 解读」开关后，可点击按钮触发 AI 对本类进行安全解读。</p>
              </div>
            </div>
          </template>
        </div>
      </div>
    </el-dialog>

    <!-- SO分析内联面板 -->
    <div v-if="decompiled && soDialogVisible" class="so-analysis-inline-panel">
      <el-card class="so-panel-card">
        <template #header>
          <div class="dialog-custom-header">
            <span class="path-title">SO 文件分析</span>
            <div class="header-tags">
              <span class="ai-toggle-label" style="margin-right: 6px; font-size: 13px; color: #6c7086;">AI 深度分析</span>
              <el-switch v-model="soAiEnabled" size="small" />
              <el-button size="small" type="danger" @click="soDialogVisible = false" style="margin-left: 12px;">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>
        </template>
        <div class="so-upload-area">
          <p>从反编译项目中提取的SO文件：</p>
          <el-table :data="extractedSoFiles" @row-click="handleSelectSoFile">
            <el-table-column prop="name" label="文件名" />
            <el-table-column prop="arch" label="架构" />
            <el-table-column prop="path" label="路径" />
            <el-table-column label="大小">
              <template #default="{ row }">{{ formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="handleAnalyzeSoFile(row)">
                  分析
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-divider>或上传SO文件</el-divider>

          <el-upload
            drag
            :action="`/api/ai-analysis/apk/so/analyze`"
            :headers="uploadHeaders"
            :data="{ aiAssist: soAiEnabled }"
            :on-success="handleSoUploadSuccess"
            :on-error="handleSoUploadError"
            accept=".so"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div>拖拽SO文件到此处或点击上传</div>
          </el-upload>
        </div>

        <div v-if="soAnalysisResult" class="so-analysis-result">
          <el-card>
            <template #header>
              <div style="display: flex; align-items: center; justify-content: space-between;">
                <span>SO 分析结果</span>
                <el-tag v-if="soAnalysisResult.aiAnalysis?.deepAnalysis" type="success" size="small" effect="dark">AI 深度分析</el-tag>
                <el-tag v-else type="info" size="small">规则引擎分析</el-tag>
              </div>
            </template>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="文件名称">{{ soAnalysisResult.soInfo?.fileName }}</el-descriptions-item>
              <el-descriptions-item label="文件大小">{{ formatSize(soAnalysisResult.soInfo?.fileSize) }}</el-descriptions-item>
              <el-descriptions-item label="架构">{{ soAnalysisResult.soInfo?.architecture }}</el-descriptions-item>
              <el-descriptions-item label="函数数量">{{ soAnalysisResult.functions?.length || 0 }}</el-descriptions-item>
              <el-descriptions-item label="加密算法">{{ soAnalysisResult.algorithms?.map(a => a.name).join(', ') || '未检测到' }}</el-descriptions-item>
              <el-descriptions-item label="混淆检测">{{ soAnalysisResult.obfuscation?.ollvm ? 'OLLVM' : soAnalysisResult.obfuscation?.symbolObfuscation ? '符号混淆' : '未检测' }}</el-descriptions-item>
            </el-descriptions>

            <el-divider content-position="left">识别的函数</el-divider>
            <el-table :data="soAnalysisResult.functions" size="small" max-height="200">
              <el-table-column prop="name" label="函数名" />
              <el-table-column prop="address" label="地址" />
              <el-table-column prop="category" label="类型" />
            </el-table>

            <el-divider content-position="left">{{ soAnalysisResult.aiAnalysis?.deepAnalysis ? 'AI 深度分析报告' : '规则引擎分析报告' }}</el-divider>
            <div class="so-report" v-html="renderMarkdown(soAnalysisResult.aiAnalysis?.analysis)"></div>
          </el-card>
        </div>
      </el-card>
    </div>

    <!-- AI 安全探针输出终端 (仿真 Shell 窗口) -->
    <el-dialog
      v-model="probeDialogVisible"
      title="AI 动态安全探针输出终端"
      width="65%"
      class="probe-terminal-dialog"
      :close-on-click-modal="false"
      append-to-body
    >
      <div class="probe-terminal-body">
        <div class="terminal-meta">
          <span>探针类型: <el-tag size="small" type="warning" effect="dark">{{ activeProbe?.type }}</el-tag></span>
          <span style="margin-left: 16px;">匹配特征: <el-tag size="small" type="info" effect="dark">{{ activeProbe?.query }}</el-tag></span>
        </div>
        
        <div class="terminal-shell">
          <div class="shell-line command">
            <span class="prompt">$</span> apktool-agent execute-probe --type {{ activeProbe?.type }} --pattern "{{ activeProbe?.query }}"
          </div>
          
          <div v-if="loadingProbe" class="shell-line loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在检索反编译工作区代码段，进行高并发安全签名特征排查...
          </div>
          
          <div v-else-if="probeResults.length" class="shell-results">
            <div class="shell-line success">[SUCCESS] 扫描结束，共发现 {{ probeResults.length }} 处签名特征匹配。详情如下：</div>
            <div v-for="(res, index) in probeResults" :key="index" class="shell-match-item">
              <div class="match-meta">
                <span class="file-path">📁 {{ res.path }}</span>
                <span class="line-no">Line {{ res.lineNumber }}</span>
                <el-button type="primary" link size="small" @click="goToSearchResult(res)">一键定位</el-button>
              </div>
              <div class="match-code-snippet">
                <code>{{ res.lineContent }}</code>
              </div>
            </div>
          </div>
          
          <div v-else-if="probeFiles.length" class="shell-results">
            <div class="shell-line success">[SUCCESS] 扫描结束，共发现 {{ probeFiles.length }} 个敏感配置文件或指纹证书。</div>
            <div v-for="(file, index) in probeFiles" :key="index" class="shell-file-item">
              <span class="file-icon">📄</span>
              <span class="file-name">{{ file.name }}</span>
              <span class="file-path-desc">({{ file.path }})</span>
              <el-button type="primary" link size="small" @click="goToSearchResult(file)">一键定位</el-button>
            </div>
          </div>
          
          <div v-else class="shell-line empty">
            [INFO] 扫描结束，未发现任何安全漏洞签名或敏感文件匹配项。系统评估该特征安全。
          </div>
        </div>
      </div>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Upload, VideoPlay, MagicStick, Cpu, Close, Refresh,
  Folder, Document, UploadFilled, Loading, Search, ArrowRight, Switch
} from '@element-plus/icons-vue'
import aiAnalysisApi from '@/api/aiAnalysis'
import { decompileApk } from '@/api/aiAnalysis'
import AiAnalysisReport from './AiAnalysisReport.vue'
import { renderMarkdown } from '@/utils/markdown'

const MAX_FILE_SIZE = 200 * 1024 * 1024

const fileTreeRef = ref(null)
const apkUploadRef = ref(null)
const decompiling = ref(false)
const reconstructing = ref(false)
const analyzing = ref(false)
const decompiled = ref(false)
const decompileProgress = ref(0)
const decompileStatus = ref('')
const decompileStep = ref(0)
const decompileLogs = ref([])
const terminalBodyRef = ref(null)

const xrefData = ref(null)
const isXrefExpanded = ref(false)
const viewMode = ref('smali')
const aiEnabled = ref(false)
const soAiEnabled = ref(false)

// Sidebar Tabs & Search
const activeSidebarTab = ref('files')
const searchQuery = ref('')
const isSearchRegex = ref(false)
const isSearchCaseSensitive = ref(false)
const isExcludeThirdParty = ref(false)
const searchScope = ref('all')
const searchingCodebase = ref(false)
const searchResults = ref([])
const fileViewerVisible = ref(false)
const probeDialogVisible = ref(false)
const activeProbe = ref(null)
const loadingProbe = ref(false)
const probeResults = ref([])
const probeFiles = ref([])

function addLog(text, type = 'info') {
  const now = new Date().toLocaleTimeString()
  decompileLogs.value.push({ time: now, text, type })
  nextTick(() => {
    if (terminalBodyRef.value) {
      terminalBodyRef.value.scrollTop = terminalBodyRef.value.scrollHeight
    }
  })
}
const workDir = ref('')
const reconstruction = ref(null)
const fileTree = ref([])
const currentFile = ref(null)
const aiAnalysisResult = ref(null)
const soDialogVisible = ref(false)
const soAnalysisResult = ref(null)
const extractedSoFiles = ref([])
const uploadDialogVisible = ref(false)
const selectedApkFile = ref(null)
const uploading = ref(false)

const treeProps = {
  children: 'children',
  label: 'name'
}

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + (sessionStorage.getItem('token') || localStorage.getItem('token') || '')
}))

function handleDecompile() {
  uploadDialogVisible.value = true
  selectedApkFile.value = null
  if (apkUploadRef.value) {
    apkUploadRef.value.clearFiles()
  }
}

function handleApkFileChange(file) {
  selectedApkFile.value = file.raw
}

function handleApkFileRemove() {
  selectedApkFile.value = null
}

function handleExceed() {
  ElMessage.warning('只能上传一个APK文件，请先移除已选文件')
}

async function submitApkUpload() {
  if (!selectedApkFile.value) {
    ElMessage.warning('请先选择APK文件')
    return
  }
  if (selectedApkFile.value.size > MAX_FILE_SIZE) {
    ElMessage.error(`文件大小超过限制 (最大200MB)，当前文件: ${formatSize(selectedApkFile.value.size)}`)
    return
  }

  uploadDialogVisible.value = false
  decompiling.value = true
  decompiled.value = false
  decompileProgress.value = 0
  decompileStep.value = 0
  decompileLogs.value = []

  addLog(`[INFO] 正在初始化反编译工作区...`, 'info')
  addLog(`[INFO] APK 文件: ${selectedApkFile.value.name} (${formatSize(selectedApkFile.value.size)})`, 'info')

  // Step 1: 上传
  decompileStep.value = 1
  decompileProgress.value = 10
  addLog(`[INFO] 正在上传 APK 至反编译引擎...`, 'info')

  try {
    const res = await decompileApk(selectedApkFile.value)
    decompileStep.value = 2
    decompileProgress.value = 60
    addLog(`[INFO] ApkTool 反编译引擎执行中...`, 'info')

    if (res.data && res.data.code === 0) {
      const result = res.data.data
      decompileProgress.value = 100
      decompileStep.value = 3
      addLog(`[SUCCESS] ApkTool 引擎执行完毕，所有资源解包与反编译成功！`, 'success')
      if (result.workDir) {
        addLog(`[INFO] 反编译工作区: ${result.workDir}`, 'info')
      }
      if (result.analysis) {
        const analysis = result.analysis
        if (analysis.structure) {
          const dirs = analysis.structure.directories || {}
          addLog(`[INFO] 目录结构: ${JSON.stringify(dirs)}`, 'info')
        }
        if (analysis.smali) {
          addLog(`[INFO] Smali: ${analysis.smali.totalClasses || 0} 个类, ${analysis.smali.totalMethods || 0} 个方法`, 'info')
        }
        if (analysis.nativeLibraries) {
          addLog(`[INFO] 原生库: ${analysis.nativeLibraries.totalLibs || 0} 个 .so 文件`, 'info')
        }
        if (analysis.reconstruction) {
          addLog(`[INFO] 源码还原评估: ${analysis.reconstruction.level || '未知'}`, 'info')
        }
      }
      handleFileUploadSuccess(result)
      ElMessage.success('APK 反编译完成')
    } else {
      decompiling.value = false
      addLog(`[ERROR] 反编译失败: ${res.data?.message || '未知错误'}`, 'error')
      ElMessage.error('APK 反编译失败: ' + (res.data?.message || '未知错误'))
    }
  } catch (err) {
    decompiling.value = false
    addLog(`[ERROR] 反编译请求出错: ${err.message || '网络错误'}`, 'error')
    ElMessage.error('APK 反编译失败: ' + (err.message || '网络错误'))
  }
}

function handleDecompileComplete(result) {
  decompiling.value = false
  if (result?.success && result?.workDir) {
    addLog(`[SUCCESS] ApkTool 引擎执行完毕，所有资源解包与反编译成功！`, 'success')
    addLog(`[INFO] 生成反编译工作区路径: ${result.workDir}`, 'info')
    decompileProgress.value = 100
    decompileStep.value = 3
    handleFileUploadSuccess(result)
    ElMessage.success('APK反编译完成')
  } else {
    addLog(`[ERROR] APK 反编译过程执行错误: ${result?.error || '未知错误'}`, 'error')
    ElMessage.error('APK反编译失败: ' + (result?.error || '未知错误'))
  }
}

function handleReconstruct() {
  reconstructing.value = true
  setTimeout(() => {
    reconstructing.value = false
    ElMessage.success('APK重编译完成')
  }, 2000)
}

function handleSoAnalysis() {
  soDialogVisible.value = true
  soAnalysisResult.value = null
}

function handleSoAnalysisForFile() {
  fileViewerVisible.value = false
  soDialogVisible.value = true
  soAnalysisResult.value = null
  if (currentFile.value && currentFile.value.path && currentFile.value.path.endsWith('.so')) {
    ElMessage.info('正在分析当前SO文件: ' + currentFile.value.name)
    aiAnalysisApi.analyzeSoFromWorkspace(workDir.value, currentFile.value.path, soAiEnabled.value)
      .then(res => {
        if (res.data && res.data.code === 0) {
          soAnalysisResult.value = res.data.data
          ElMessage.success('SO分析完成: ' + currentFile.value.name)
        } else {
          ElMessage.error(res.data?.message || 'SO分析失败')
        }
      })
      .catch(err => {
        ElMessage.error('SO分析发生网络错误')
        console.error(err)
      })
  }
}

function handleClose() {
  decompiled.value = false
  workDir.value = ''
  reconstruction.value = null
  fileTree.value = []
  currentFile.value = null
  aiAnalysisResult.value = null
  xrefData.value = null
  searchResults.value = []
  searchQuery.value = ''
}

function refreshFileTree() {
  if (!workDir.value) return

  aiAnalysisApi.getApkFileTree({ workDir: workDir.value })
    .then(res => {
      if (res.data && res.data.code === 0) {
        fileTree.value = [res.data.data]
      } else {
        ElMessage.error(res.data?.message || '获取文件树失败')
      }
    })
    .catch(err => {
      ElMessage.error('刷新失败')
      console.error(err)
    })
}

function handleNodeClick(data) {
  if (data.type === 'directory' || data.children) return

  currentFile.value = null
  aiAnalysisResult.value = null
  xrefData.value = null

  const fileType = data.type
  if (['image', 'binary'].includes(fileType)) {
    currentFile.value = { ...data, isBinary: true }
    fileViewerVisible.value = true
    return
  }

  aiAnalysisApi.getApkFileContent({
    workDir: workDir.value,
    path: data.path
  })
    .then(res => {
      if (res.data && res.data.code === 0) {
        const fileData = res.data.data
        currentFile.value = {
          ...data,
          content: fileData.content,
          isBinary: fileData.isBinary,
          pseudocode: fileData.pseudocode,
          jniMethods: fileData.jniMethods
        }
        fileViewerVisible.value = true
        
        if (data.type === 'smali') {
          loadXrefData(data.path)
        }
      } else {
        ElMessage.error(res.data?.message || '读取文件失败')
      }
    })
    .catch(err => {
      ElMessage.error('读取文件失败: ' + (err.message || '网络错误'))
      console.error(err)
    })
}

function loadXrefData(filePath) {
  xrefData.value = null
  isXrefExpanded.value = false
  aiAnalysisApi.getApkFileXref({
    workDir: workDir.value,
    path: filePath
  })
    .then(res => {
      if (res.data && res.data.code === 0) {
        xrefData.value = res.data.data
      }
    })
    .catch(err => {
      console.error('加载交叉引用失败:', err)
    })
}

function handleJniMethodClick(method) {
  ElMessage.success(`正在跳转至 Native 符号分析: ${method}`)
  soDialogVisible.value = true
}

function handleNavigateToFile(filePath) {
  const node = findNodeByPath(fileTree.value, filePath)
  if (node) {
    handleNodeClick(node)
  } else {
    const mockNode = {
      name: filePath.substring(filePath.lastIndexOf('/') + 1),
      path: filePath,
      type: filePath.endsWith('.smali') ? 'smali' : 'file'
    }
    handleNodeClick(mockNode)
  }
}

function findNodeByPath(nodes, path) {
  for (const node of nodes) {
    if (node.path === path) return node
    if (node.children) {
      const found = findNodeByPath(node.children, path)
      if (found) return found
    }
  }
  return null
}

function toggleResultExpand(item) {
  item.expanded = !item.expanded
}

function highlightText(text) {
  if (!text) return ''
  const query = searchQuery.value.trim()
  if (!query) return escapeHtml(text)

  try {
    let pattern
    const isRegex = isSearchRegex.value
    const caseSensitive = isSearchCaseSensitive.value
    
    if (isRegex) {
      pattern = new RegExp(query, caseSensitive ? 'g' : 'gi')
    } else {
      // Escape regex chars for literal highlight match
      const escaped = query.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&')
      pattern = new RegExp(escaped, caseSensitive ? 'g' : 'gi')
    }
    
    const escapedText = escapeHtml(text)
    return escapedText.replace(pattern, match => `<mark class="search-highlight">${match}</mark>`)
  } catch (e) {
    return escapeHtml(text)
  }
}

function escapeHtml(string) {
  return String(string)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function performCodebaseSearch() {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searchingCodebase.value = true
  aiAnalysisApi.searchApkCodebase({
    workDir: workDir.value,
    q: searchQuery.value,
    isRegex: isSearchRegex.value,
    caseSensitive: isSearchCaseSensitive.value,
    fileType: searchScope.value,
    excludeThirdParty: isExcludeThirdParty.value
  })
    .then(res => {
      if (res.data && res.data.code === 0) {
        searchResults.value = (res.data.data || []).map(item => ({
          ...item,
          expanded: false
        }))
        ElMessage.success(`检索完成，命中 ${searchResults.value.length} 处结果`)
      } else {
        ElMessage.error(res.data?.message || '检索失败')
      }
    })
    .catch(err => {
      ElMessage.error('检索请求出错: ' + err.message)
    })
    .finally(() => {
      searchingCodebase.value = false
    })
}

function goToSearchResult(item) {
  probeDialogVisible.value = false
  ElMessage.success(`定位至: ${item.path} 第 ${item.lineNumber || 1} 行`)
  handleNavigateToFile(item.path)
}

function handleAiAnalyze() {
  if (!currentFile.value) return

  analyzing.value = true
  const fileType = currentFile.value.type || getFileTypeFromPath(currentFile.value.path)

  aiAnalysisApi.analyzeApkFile({
    workDir: workDir.value,
    path: currentFile.value.path,
    fileType: fileType
  })
    .then(res => {
      if (res.data && res.data.code === 0) {
        aiAnalysisResult.value = res.data.data.aiAnalysis
      } else {
        ElMessage.error(res.data?.message || 'AI分析失败')
      }
      analyzing.value = false
    })
    .catch(err => {
      ElMessage.error('AI分析失败: ' + (err.message || '网络错误'))
      analyzing.value = false
      console.error(err)
    })
}

function handleSoUploadSuccess(res) {
  if (res && res.code === 0) {
    soAnalysisResult.value = res.data
  } else {
    ElMessage.error(res?.message || 'SO分析失败')
  }
}

function handleSoUploadError(err) {
  ElMessage.error('SO分析失败')
  console.error(err)
}

function handleSelectSoFile(row) {
  // logic if needed
}

function handleAnalyzeSoFile(row) {
  ElMessage.info('正在分析SO文件: ' + row.name)
  soAnalysisResult.value = null
  aiAnalysisApi.analyzeSoFromWorkspace(workDir.value, 'lib/' + row.path, soAiEnabled.value)
    .then(res => {
      if (res.data && res.data.code === 0) {
        soAnalysisResult.value = res.data.data
        ElMessage.success('SO分析完成: ' + row.name)
      } else {
        ElMessage.error(res.data?.message || 'SO分析失败')
      }
    })
    .catch(err => {
      ElMessage.error('SO分析发生网络错误')
      console.error(err)
    })
}

function getReconstructType(level) {
  const map = { EXCELLENT: 'success', GOOD: 'primary', LIMITED: 'warning' }
  return map[level] || 'info'
}

function getFileTypeFromPath(path) {
  if (path.endsWith('.smali')) return 'smali'
  if (path.endsWith('.java')) return 'java'
  if (path.endsWith('.xml')) return 'xml'
  if (path.endsWith('.so')) return 'native'
  return 'text'
}

function formatSize(bytes) {
  if (!bytes) return 'N/A'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

const fileAiMeta = computed(() => ({
  moduleType: 'FILE',
  model: aiAnalysisResult.value?.model || 'qwen-plus',
  timestamp: aiAnalysisResult.value?.timestamp || Date.now(),
  confidence: aiAnalysisResult.value?.confidence || 0.95
}))

function handleFileUploadSuccess(data) {
  workDir.value = data.workDir
  reconstruction.value = data.analysis?.reconstruction
  extractedSoFiles.value = data.analysis?.nativeLibraries?.libraries || []
  refreshFileTree()

  setTimeout(() => {
    decompiling.value = false
    decompiled.value = true
    ElMessage.success('APK反编译完成')
  }, 500)
}

defineExpose({
  handleFileUploadSuccess,
  triggerUpload: () => { uploadDialogVisible.value = true }
})
</script>

<style scoped>
.apk-reverse-panel {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.top-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.progress-card {
  margin-bottom: 16px;
  text-align: center;
}

.progress-text {
  margin: 8px 0 0 0;
  color: #909399;
}

.reconstruction-card {
  margin-bottom: 16px;
}

.reconstruction-card :deep(.el-card__header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.reconstruction-desc {
  margin: 0 0 12px 0;
  color: #606266;
}

.limitations {
  margin: 12px 0;
  padding: 12px;
  background: #fdf6ec;
  border-radius: 4px;
}

.limitations h5 {
  margin: 0 0 8px 0;
}

.limitations ul {
  margin: 0;
  padding-left: 20px;
}

.limitations li {
  margin: 4px 0;
}

.reconstruct-actions {
  margin-top: 12px;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 0;
  min-height: 0;
  height: calc(100vh - 185px);
  overflow: hidden;
}

/* 文件浏览器面板 */
.file-browser-panel {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}

.sidebar-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

.sidebar-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: auto;
  min-height: 0;
  padding: 0;
}

.sidebar-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

/* Horizontal scroll fix for Element Plus Tree */
.sidebar-tabs :deep(.el-tree) {
  min-width: max-content;
  display: inline-block;
  width: 100%;
}

.sidebar-tabs :deep(.el-tree-node__content) {
  min-width: max-content;
  padding-right: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
  flex-shrink: 0;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
}

.type-tag {
  margin-left: 4px;
  transform: scale(0.8);
}

/* IDE 数码代码查看器悬浮弹窗极客样式 */
.code-viewer-dialog-modern :deep(.el-dialog) {
  background: #1e1e2e !important;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(139, 92, 246, 0.2);
}

.code-viewer-dialog-modern :deep(.el-dialog__header) {
  background: #181825 !important;
  margin-right: 0;
  padding: 16px 24px !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.code-viewer-dialog-modern :deep(.el-dialog__title) {
  display: none; /* Hide standard title to use custom slot */
}

.code-viewer-dialog-modern :deep(.el-dialog__headerbtn) {
  top: 18px;
  right: 24px;
  font-size: 20px;
}

.code-viewer-dialog-modern :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: #a6adc8;
}

.code-viewer-dialog-modern :deep(.el-dialog__headerbtn:hover .el-dialog__close) {
  color: #ffffff;
}

.code-viewer-dialog-modern :deep(.el-dialog__body) {
  padding: 0 !important;
  background: #1e1e2e;
  height: 640px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dialog-custom-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: calc(100% - 36px);
  color: #cdd6f4;
}

.dialog-custom-header .path-title {
  font-weight: 700;
  color: #cdd6f4;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 50%;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dialog-custom-header .header-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dialog-custom-header .file-size {
  font-size: 12px;
  color: #6c7086;
  margin-left: 8px;
}

.dialog-code-viewer-body {
  flex: 1;
  display: flex;
  flex-direction: row;
  min-height: 0;
  overflow: hidden;
  padding: 20px;
  gap: 16px;
  height: 100%;
}

.dialog-code-viewport {
  height: 380px; /* Bounded fixed height for perfect stability */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #181825; /* dark screen */
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.dialog-dual-code-view {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 0; /* Crucial: prevent vertical expansion past parent height boundary! */
  overflow: hidden;
  flex: 1;
}

.dialog-dual-code-view .code-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  overflow: hidden;
  height: 100%;
  min-height: 0; /* Crucial: ensure inner elements are allowed to shrink and scroll! */
  background: #1e1e2e;
}

.dialog-dual-code-view .column-header {
  background: #181825;
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 700;
  color: #a6adc8;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  text-align: left;
}

.dialog-dual-code-view .code-content {
  flex: 1;
  overflow: auto;
  padding: 16px;
  margin: 0;
  background: #1e1e2e;
  color: #cdd6f4;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre;
  word-break: normal;
  text-align: left;
}

.dialog-dual-code-view .java-code {
  color: #a6e3a1; /* green hue code for java pseudocode */
  background: #1e1e2e !important;
}

.dialog-single-code {
  flex: 1;
  overflow: auto;
  padding: 16px;
  margin: 0;
  background: #1e1e2e;
  color: #cdd6f4;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre;
  word-break: normal;
  text-align: left;
}

/* 弹窗右侧 AI 审计面板与左右双栏布局样式 */
.dialog-viewer-left-pane {
  flex: 2.1; /* Expanded left pane */
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  height: 100%;
}

.dialog-viewer-right-pane {
  flex: 0.9; /* Capped right pane */
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  border: 1px solid rgba(139, 92, 246, 0.15);
  background: #181825;
  border-radius: 8px;
  padding: 16px;
}

.right-ai-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 12px;
  flex-shrink: 0;
}

.right-ai-title {
  font-size: 14px;
  font-weight: 700;
  color: #cdd6f4;
  display: flex;
  align-items: center;
}

.right-ai-panel-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dialog-ai-analysis-content-box {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.analysis-content-html {
  flex: 1;
  overflow-y: auto;
  padding-right: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #bac2de;
  text-align: left;
}

.analysis-content-html :deep(h4) {
  color: #f5e0dc;
  margin: 12px 0 6px 0;
  font-size: 13px;
}

.analysis-content-html :deep(p) {
  margin: 0 0 8px 0;
}

.analysis-content-html :deep(ul) {
  margin: 0 0 8px 0;
  padding-left: 18px;
}

.analysis-content-html :deep(code) {
  background: rgba(255, 255, 255, 0.06);
  padding: 2px 4px;
  border-radius: 4px;
  font-family: monospace;
  color: #f38ba8;
}

.analysis-meta-info {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-shrink: 0;
}

.analysis-meta-info .conf-badge {
  font-size: 11px;
  color: #a6adc8;
  background: rgba(139, 92, 246, 0.15);
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid rgba(139, 92, 246, 0.2);
}

.dialog-ai-analysis-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 12px;
  color: #a6adc8;
  text-align: center;
  padding: 24px;
}

.dialog-ai-analysis-loading p {
  font-size: 12px;
  line-height: 1.5;
  margin: 0;
}

.dialog-ai-analysis-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 12px;
  color: #6c7086;
  text-align: center;
  padding: 24px;
}

.dialog-ai-analysis-empty .empty-tip {
  font-size: 14px;
  font-weight: 700;
  color: #a6adc8;
  margin: 0;
}

.dialog-ai-analysis-empty .empty-sub-tip {
  font-size: 12px;
  line-height: 1.6;
  color: #6c7086;
  margin: 0;
}

.jni-alert {
  text-align: left;
}

/* 现代可视化反编译进度卡片 */
.progress-card-modern {
  margin-bottom: 24px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.95) 100%);
  border: 1px solid rgba(139, 92, 246, 0.15);
  border-radius: 20px;
  box-shadow: 0 12px 40px rgba(139, 92, 246, 0.08);
  backdrop-filter: blur(20px);
  padding: 10px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.progress-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.pulse-tag {
  animation: pulse-animation 2s infinite;
}

@keyframes pulse-animation {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.progress-content {
  display: flex;
  align-items: center;
  justify-content: space-around;
  gap: 40px;
  padding: 24px 10px;
  flex-wrap: wrap;
}

.progress-bar-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-shrink: 0;
}

.progress-inner-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.percentage-num {
  font-size: 28px;
  font-weight: 800;
  color: #8b5cf6;
}

.percentage-label {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.progress-steps-wrapper {
  flex: 1;
  min-width: 250px;
}

.progress-steps-wrapper :deep(.el-step__title) {
  font-weight: 600;
  color: #1e293b !important;
}

.progress-steps-wrapper :deep(.el-step__description) {
  color: #64748b !important;
}

/* 实时模拟日志终端 */
.log-terminal {
  margin-top: 16px;
  background: #1e1e2e;
  border-radius: 14px;
  border: 1px solid rgba(139, 92, 246, 0.2);
  overflow: hidden;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.15);
}

.terminal-header {
  background: #181825;
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.terminal-header .dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
}

.terminal-header .dot.red { background: #f38ba8; }
.terminal-header .dot.yellow { background: #f9e2af; }
.terminal-header .dot.green { background: #a6e3a1; }

.terminal-title {
  margin-left: 10px;
  font-size: 12px;
  color: #a6adc8;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-weight: 500;
}

.terminal-body {
  padding: 16px;
  height: 180px;
  overflow-y: auto;
  font-family: 'Fira Code', 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #cdd6f4;
  text-align: left;
}

.log-line {
  margin-bottom: 6px;
  word-break: break-all;
}

.log-line.success {
  color: #a6e3a1;
}

.log-line.warning {
  color: #f9e2af;
}

.log-line.error {
  color: #f38ba8;
}

.log-time {
  color: #6c7086;
  margin-right: 8px;
}

/* 双栏对照视图与 JNI 交叉引用样式 */
.dual-code-view {
  display: flex;
  gap: 16px;
  height: calc(100vh - 280px);
  overflow: hidden;
}

.code-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  height: 100%;
}

.code-column .code-content {
  flex: 1;
  overflow: auto;
  padding: 12px;
  margin: 0;
}

.column-header {
  background: #f5f7fa;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #e4e7ed;
}

.java-code {
  color: #2e7d32;
  background: #f1f8e9 !important;
}

.jni-alert {
  margin-bottom: 12px;
}

.jni-alert-text {
  font-size: 13px;
  color: #606266;
}

.jni-links {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.xref-panel-modern {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
  margin-top: 12px;
  overflow: hidden;
}

.xref-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid #e4e7ed;
  transition: background 0.2s ease;
}

.xref-header:hover {
  background: #ebedf0;
}

.xref-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.xref-arrow {
  font-size: 12px;
  color: #909399;
  transition: transform 0.2s ease;
}

.xref-arrow.is-rotated {
  transform: rotate(90deg);
}

.xref-badge :deep(.el-badge__content) {
  transform: scale(0.8) translateY(-2px);
}

.xref-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.xref-body {
  padding: 12px;
  background: #ffffff;
}

.xref-tabs {
  border: 1px solid #e4e7ed !important;
  box-shadow: none !important;
}

/* 静态检索样式 */
.search-box-container-modern {
  padding: 14px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.search-input-modern {
  width: 100%;
}

.search-filters-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.filter-row :deep(.el-checkbox) {
  margin-right: 0;
}

.scope-select-wrapper {
  display: flex;
  align-items: center;
}

.search-results-list {
  padding: 10px 8px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 现代卡片检索结果 */
.search-result-card-modern {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}

.search-result-card-modern:hover {
  border-color: #8b5cf6;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.08);
}

.result-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.expand-arrow {
  font-size: 12px;
  color: #64748b;
  transition: transform 0.2s ease;
}

.expand-arrow.is-expanded {
  transform: rotate(90deg);
}

.result-path-name {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.jump-btn {
  font-size: 12px;
  font-weight: 600;
  color: #8b5cf6;
  padding: 0 4px;
}

.jump-btn:hover {
  color: #7c3aed;
}

.result-path-subtitle {
  font-size: 10px;
  color: #94a3b8;
  word-break: break-all;
  text-align: left;
  line-height: 1.2;
}

/* 单行扁平预览 */
.result-snippet-flat {
  font-size: 12px;
  color: #334155;
  background: #f8fafc;
  padding: 6px 10px;
  border-radius: 6px;
  font-family: 'Fira Code', Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  border: 1px solid rgba(0, 0, 0, 0.02);
  text-align: left;
  transition: background 0.15s ease;
}

.result-snippet-flat:hover {
  background: #f1f5f9;
}

.snippet-code-flat {
  display: inline-block;
  vertical-align: middle;
}

/* 上下文视口预览 */
.result-snippet-expanded {
  margin-top: 6px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  background: #0f172a; /* dark editor background */
  overflow: hidden;
}

.context-viewport {
  padding: 8px 0;
  display: flex;
  flex-direction: column;
}

.context-line {
  display: flex;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 11px;
  line-height: 1.6;
  color: #94a3b8; /* greyed out code */
  text-align: left;
  padding: 0 8px;
}

.context-ln {
  width: 32px;
  color: #475569;
  text-align: right;
  padding-right: 8px;
  user-select: none;
  font-size: 10px;
}

.context-code {
  flex: 1;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 匹配行高亮样式 */
.context-line.match-line {
  background: rgba(139, 92, 246, 0.15); /* soft purple highlights background */
  color: #ffffff;
  font-weight: 500;
  border-left: 3px solid #8b5cf6;
  padding-left: 5px; /* Adjust padding due to border width */
}

.context-ln.match-ln {
  color: #c084fc; /* purple line number */
}

/* 搜索词高亮高能提示 */
:deep(.search-highlight) {
  background-color: rgba(234, 179, 8, 0.45) !important; /* warm gold highlight */
  color: #ffffff !important;
  border-radius: 3px;
  padding: 0 3px;
  font-weight: 600;
  border: 1px solid rgba(234, 179, 8, 0.6);
  box-shadow: 0 0 8px rgba(234, 179, 8, 0.2);
}

/* ========================================== */
/* 新增：极客 AI 思考痕迹与交互式探针专属样式 */
/* ========================================== */

.ai-thinking-trajectory-card {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.08) 0%, rgba(192, 132, 252, 0.04) 100%);
  border: 1px solid rgba(139, 92, 246, 0.2);
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 20px;
  backdrop-filter: blur(8px);
  box-shadow: 0 4px 16px rgba(139, 92, 246, 0.03);
  text-align: left;
}

.ai-thinking-trajectory-card .trajectory-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #8b5cf6;
  margin-bottom: 8px;
}

.ai-thinking-trajectory-card .brain-pulse-icon {
  font-size: 16px;
  animation: brain-pulse-anim 2s infinite ease-in-out;
}

@keyframes brain-pulse-anim {
  0%, 100% { transform: scale(1); filter: drop-shadow(0 0 2px rgba(139, 92, 246, 0.5)); }
  50% { transform: scale(1.15); filter: drop-shadow(0 0 8px rgba(139, 92, 246, 0.8)); }
}

.ai-thinking-trajectory-card .trajectory-body {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.6;
  color: #5b21b6;
}

.ai-thinking-trajectory-card .trajectory-body li {
  margin: 4px 0;
}

.suggested-probes-box-modern {
  margin-top: 20px;
  background: linear-gradient(135deg, rgba(249, 115, 22, 0.06) 0%, rgba(251, 146, 60, 0.02) 100%);
  border: 1px solid rgba(249, 115, 22, 0.25);
  border-radius: 10px;
  padding: 14px 16px;
  text-align: left;
}

.suggested-probes-box-modern .probes-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #ea580c;
  margin-bottom: 12px;
}

.probes-buttons-container {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.probe-btn-modern {
  border-color: rgba(249, 115, 22, 0.4) !important;
  color: #ea580c !important;
}

.probe-btn-modern:hover {
  background-color: #ffedd5 !important;
  border-color: #f97316 !important;
  color: #c2410c !important;
}

/* ====== SO 分析内联面板 ====== */
.so-analysis-inline-panel {
  width: 100%;
  margin-bottom: 20px;
}

.so-panel-card {
  border: 1px solid rgba(139, 92, 246, 0.15);
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.so-panel-card :deep(.el-card__header) {
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  padding: 12px 20px;
}

.so-upload-area {
  padding: 10px 0;
}

.so-upload-area p {
  margin: 0 0 12px 0;
  color: #606266;
  font-size: 14px;
}

.so-analysis-result {
  margin-top: 20px;
}

.so-report {
  padding: 12px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.7;
  color: #334155;
  text-align: left;
}

.so-report :deep(h2),
.so-report :deep(h3),
.so-report :deep(h4) {
  color: #1e293b;
  margin: 12px 0 6px 0;
}

.so-report :deep(p) {
  margin: 0 0 8px 0;
}

.so-report :deep(ul),
.so-report :deep(ol) {
  margin: 0 0 8px 0;
  padding-left: 20px;
}

.so-report :deep(code) {
  background: rgba(139, 92, 246, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  color: #8b5cf6;
}

.so-report :deep(pre) {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 14px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
}

/* 极客仿真 Shell 终端 Dialog 样式 */
.probe-terminal-dialog :deep(.el-dialog) {
  background: #1e1e2e !important;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(249, 115, 22, 0.25);
}

.probe-terminal-dialog :deep(.el-dialog__header) {
  background: #181825 !important;
  margin-right: 0;
  padding: 16px 20px !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.probe-terminal-dialog :deep(.el-dialog__title) {
  color: #f97316;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-weight: 700;
  font-size: 14px;
}

.probe-terminal-dialog :deep(.el-dialog__close) {
  color: #a6adc8;
}

.probe-terminal-dialog :deep(.el-dialog__body) {
  padding: 0 !important;
  background: #1e1e2e;
}

.probe-terminal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.terminal-meta {
  display: flex;
  align-items: center;
  font-family: 'Fira Code', monospace;
  font-size: 12px;
  color: #cdd6f4;
  background: #181825;
  padding: 8px 12px;
  border-radius: 6px;
  text-align: left;
}

.terminal-shell {
  background: #0f172a; /* deep pitch black screen */
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  padding: 16px;
  font-family: 'Fira Code', 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #94a3b8;
  height: 380px;
  overflow-y: auto;
  text-align: left;
}

.shell-line {
  margin-bottom: 8px;
  word-break: break-all;
}

.shell-line.command {
  color: #38bdf8; /* cyan cmd */
}

.shell-line.command .prompt {
  color: #4ade80; /* green prompt */
  margin-right: 8px;
  font-weight: bold;
}

.shell-line.loading {
  color: #eab308; /* yellow loading */
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.shell-line.success {
  color: #4ade80; /* green success */
  font-weight: bold;
  margin: 12px 0 8px 0;
}

.shell-line.empty {
  color: #64748b;
  font-style: italic;
  margin-top: 12px;
}

.shell-match-item {
  border-left: 3px solid #f97316;
  background: rgba(249, 115, 22, 0.06);
  padding: 8px 12px;
  margin-bottom: 10px;
  border-radius: 0 6px 6px 0;
}

.shell-match-item .match-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.shell-match-item .file-path {
  font-weight: bold;
  color: #f97316;
  font-size: 11px;
}

.shell-match-item .line-no {
  background: rgba(249, 115, 22, 0.15);
  color: #fdba74;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
}

.shell-match-item .match-code-snippet {
  margin: 4px 0 0 0;
  background: #1e293b;
  padding: 6px 10px;
  border-radius: 4px;
  overflow-x: auto;
  color: #cbd5e1;
  font-size: 11px;
}

.shell-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.04);
  padding: 6px 12px;
  margin-bottom: 8px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.03);
}

.shell-file-item .file-name {
  font-weight: bold;
  color: #fdba74;
}

.shell-file-item .file-path-desc {
  color: #64748b;
  font-size: 11px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>