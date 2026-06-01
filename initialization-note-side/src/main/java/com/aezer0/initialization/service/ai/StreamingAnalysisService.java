package com.aezer0.initialization.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aezer0.initialization.config.ai.AiAnalysisConfig;
import com.aezer0.initialization.service.apktool.ApkToolService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式分析服务 — SSE 进度管线 + 多阶段 AI 深度融合
 */
@Service
@Slf4j
public class StreamingAnalysisService {

    @Autowired
    private ApkAnalysisService apkAnalysisService;
    @Autowired
    private SoAnalysisService soAnalysisService;
    @Autowired
    private ProtocolAnalysisService protocolAnalysisService;
    @Autowired
    private ApkToolService apkToolService;
    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;
    @Autowired
    @Qualifier("streamingExecutor")
    private ThreadPoolTaskExecutor streamingExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Sinks.Many<String>> taskSinks = new ConcurrentHashMap<>();

    private static final int MAX_FILE_SIZE = 200 * 1024 * 1024; // 200MB

    // ========== 多阶段 AI 深度提示词 — 极致静态分析 + AI 深度融合 ==========

    private static final String STAGE1_APK_PROMPT = """
            你是「AI 移动安全分析引擎 — APK 第1阶段：结构审查与攻击面建模」。

            你的任务是基于 APK 基本元数据，进行深度结构审查：

            1. **包名与签名分析**: 判断是否伪装知名应用（包名相似度检测），证书指纹是否可疑
            2. **SDK 版本风险评估**: 低 minSdkVersion 意味着更多兼容性代码/旧API漏洞；高 targetSdkVersion 意味着更严格的安全策略
            3. **攻击面识别**: 是否存在自定义 Application 类、导出 Provider、BackupAgent
            4. **debuggable/allowBackup 标志**: 如果为 true，说明存在调试后门或备份泄露风险

            输出格式要求：
            <thinking>
            逐条分析以上4个维度，给出具体判断，引用检测到的具体数值。
            对每个风险点打分（高/中/低），说明可能被利用的方式。
            </thinking>

            然后给出 Markdown 格式的结构审查报告，包含：
            - 攻击面评估总结
            - 重点关注建议（2-4条，每条含具体技术理由）
            - 初步风险评分（1-10）""";

    private static final String STAGE2_APK_PROMPT = """
            你是「AI 移动安全分析引擎 — APK 第2阶段：权限与API特征深度分析」。

            你的任务是基于提取的权限列表、敏感API调用、网络域名进行深度威胁分析：

            1. **权限组合攻击链分析**: 不孤立看单个权限，而是分析权限组合可能形成的攻击链
               - 例如: INTERNET + READ_CONTACTS + SEND_SMS + BOOT_COMPLETED → 典型的隐私窃取+远程上报链
               - 例如: CAMERA + RECORD_AUDIO + INTERNET + WAKE_LOCK → 隐蔽的音视频监控链
            2. **API调用与MITRE Mobile ATT&CK映射**: 将检测到的敏感API映射到具体的攻击技术
               - Runtime.exec → T1404 (Exploitation for Privilege Escalation)
               - DexClassLoader → T1406 (Obfuscated Files or Information) / T1575 (Native API)
               - addJavascriptInterface → T1423 (WebView JavaScript Interface Injection)
            3. **网络域名威胁评估**: 分析域名的 TLD、模式、已知恶意性
               - 非标准端口、动态DNS域名、.xyz/.tk/.ml等可疑TLD
               - HTTP明文域名的中间人风险
            4. **敏感API调用链重建**: 哪些API组合在一起表明特定的恶意行为模式

            输出格式要求：
            <thinking>
            逐条分析上述4个维度。对每个威胁模式：
            - 列出涉及的权限/API
            - 映射到具体的MITRE Mobile ATT&CK技术ID
            - 评估危害程度
            - 引用具体被检测到的API名称和权限名
            </thinking>

            然后输出：
            ## 威胁模式分析
            (表格形式：威胁模式 | 涉及权限/API | MITRE ATT&CK | 危害等级)

            ## 可疑域名评估
            (列表每项域名及其风险评估)

            ## 阶段性发现
            用 [PROBE:grep_string:关键字:标签] 格式给出具体可排查建议""";

    private static final String STAGE3_APK_PROMPT = """
            你是「AI 移动安全分析引擎 — APK 第3阶段：深度检测与专业安全判读」。

            你的任务是基于规则引擎检测结果、OWASP合规审计、敏感凭据扫描、加壳检测，进行专业安全解读：

            1. **检测结果确认与误报排除**:
               - 对每项检测结果给出"确认"、"疑似"、"可能误报"的判断
               - 说明判断依据（基于什么证据、缺少什么证据）
            2. **OWASP Mobile Top 10 逐条审计解读**:
               - M1: 平台功能误用 — WebView远程代码执行、导出组件权限缺失
               - M2: 不安全数据存储 — sharedUserId、外部存储
               - M3: 不安全通信 — 明文HTTP、证书验证绕过
               - M4: 不安全身份验证 — 硬编码认证令牌
               - M5: 不足的密码学 — DES/MD5/SHA1/RC4弱算法
               - M6: 不安全授权 — debuggable=true
               - M7: 客户端代码质量 — 日志泄露、硬编码调试信息
               - M8: 代码篡改 — 无完整性校验
               - M9: 逆向工程 — 无加壳/混淆保护
            3. **敏感凭据泄露评估**: 对每类泄露的凭据评估实际风险
               - AWS Key: 可能导致云资源被控制
               - JWT Token: 可能导致身份伪造
               - 数据库连接串: 可能导致数据库拖库
            4. **加壳方案技术解读**: 分析检测到的加壳方案的技术原理、已知弱点、脱壳方法

            输出格式要求：
            <thinking>
            逐条分析检测结果。对每条：
            - 判定：确认威胁 / 疑似 / 可能误报
            - 证据链：引用具体的检测数据
            - 漏洞利用场景：攻击者如何利用
            - CVSS 评分估算
            </thinking>

            然后输出 Markdown 深度解读报告：
            - 威胁确认清单（表格：项 | 判定 | 证据 | CVSS | 建议）
            - 误报排除说明
            - OWASP 合规评分
            - 加壳逆向建议（含 Frida Hook 脚本框架）""";

    private static final String STAGE4_APK_PROMPT = """
            你是「AI 移动安全分析引擎 — APK 第4阶段：综合研判与最终安全报告」。

            你的任务是汇总前三个阶段的所有证据，生成最终的综合安全分析报告：

            1. **综合所有证据进行最终判定**:
               - 汇总各阶段发现，交叉验证
               - 给出 APK 的最终恶意判定（MALWARE / HIGH_RISK / MEDIUM_RISK / LOW_RISK / CLEAN）
               - 列出支撑判定的关键证据链（至少3条）
            2. **恶意软件家族分类**（如适用）:
               - 与已知恶意软件家族的特征对比
               - 行为模式分析：C2通信、数据窃取、扣费、广告欺诈、银行钓鱼、RAT远控、勒索
            3. **IOC 威胁指标提取**:
               - 网络 IOC: C2域名、IP、URL
               - 文件 IOC: 包名、证书MD5、关键字符串
               - 行为 IOC: 权限组合、API调用模式
            4. **处置建议与 Frida 动态验证脚本**:
               - 针对具体风险的处置步骤
               - 生成可直接使用的 Frida Hook 脚本框架
               - 给出动态验证步骤

            输出格式要求：
            <thinking>
            逐层分析：
            - 证据汇总与交叉对比
            - 恶意家族匹配推理（如果适用）
            - 最终评分计算过程
            - IOC 提取依据
            </thinking>

            然后输出完整的 Markdown 最终安全分析报告，包含：

            ## 一、综合判定
            | 项目 | 值 |
            |------|-----|
            | 最终判定 | ... |
            | 恶意家族 | ... |
            | 综合风险评分 | .../100 |
            | 置信度 | ...% |

            ## 二、关键证据链
            1. ...
            2. ...
            3. ...

            ## 三、IOC 威胁指标
            ### 网络 IOC
            ### 文件 IOC
            ### 行为 IOC

            ## 四、ATT&CK 技术映射
            (完整的 MITRE Mobile ATT&CK Matrix 映射)

            ## 五、处置建议
            (分优先级的可操作建议)

            ## 六、Frida 动态验证脚本
            ```javascript
            // 针对本APK的定制化 Frida Hook 脚本
            ```

            最后输出 [PROBE:...] 探针指令用于进一步排查""";

    private static final String STAGE1_SO_PROMPT = """
            你是「AI 移动安全分析引擎 — Native SO 第1阶段：ELF结构安全审计」。

            你的任务是基于 ELF 文件的基本信息，进行深度的结构安全分析：

            1. **ELF 安全属性审计**:
               - PIE (位置无关代码): 未开启则无ASLR，ROP利用更容易
               - RELRO (只读重定位): Partial vs Full，决定GOT表是否可写
               - NX (不可执行栈): 未开启则可执行shellcode
               - Stack Canary: 栈保护，防止缓冲区溢出
               - W^X: 内存页不可同时写+执行
            2. **架构与平台风险评估**:
               - ARM64 vs ARM32 vs x86 — 不同架构的攻击面
               - 若发现x86 SO意味着模拟器/开发环境有额外SO，可能存在调试版本
            3. **段结构分析**:
               - init_array/fini_array 是否存在 — 壳/保护方案的典型入口
               - .text段与.rodata段的比例 — 异常比例可能表示加密/压缩
               - 是否有异常大或空的段
            4. **依赖库风险评估**: 依赖的SO库是否存在已知漏洞

            输出格式要求：
            <thinking>
            逐条评估以上4个维度。对每个安全问题：
            - 具体数值引用（地址、标志位、段大小）
            - 利用难度评估
            - 已知攻击技术关联
            </thinking>

            然后输出 Markdown 结构安全报告：
            - ELF 安全评分卡（每个属性的状态和评价）
            - 关键安全缺陷列表（如果存在）""";

    private static final String STAGE2_SO_PROMPT = """
            你是「AI 移动安全分析引擎 — Native SO 第2阶段：加密算法与函数行为分析」。

            你的任务是基于导出函数列表和加密算法匹配结果，深度分析SO的功能定位和安全风险：

            1. **加密实现安全性评估**:
               - 对检测到的每种加密算法评估其安全性（已知破解方法、弱密钥、IV重用风险）
               - 如果检测到DES/RC4/MD5等弱算法，说明已知攻击方法
               - 如果检测到自定义XOR加密，评估可能的密钥长度和破解难度
               - 分析加密函数的调用模式（加密/解密/签名/验证的配对使用）
            2. **函数功能分类与行为推断**:
               - 将函数按功能分组：加密类、网络类、反调试类、JNI接口类、文件操作类、隐私采集类
               - 从函数名推断SO的总体用途（游戏引擎、支付SDK、通信加密库、反作弊模块、恶意Rootkit？）
               - 识别可疑的函数命名模式（混淆名称、无意义名称）
            3. **JNI 接口风险评估**:
               - JNI动态注册（JNI_OnLoad）的体积和复杂度
               - Java层可以通过哪些JNI函数调用到Native层的能力
            4. **反逆向措施识别**:
               - 检测到的反调试、反Hook、反模拟器技术
               - 评估绕过难度

            输出格式要求：
            <thinking>
            逐项分析。重点关注：
            - 加密算法的具体安全缺陷和攻击方法
            - 函数之间的调用关系和功能组合
            - 可疑行为的危害评估
            </thinking>

            然后输出：
            ## 加密算法安全评估
            (表格：算法 | 安全性 | 已知攻击 | 建议替代)

            ## 功能模块分析
            (按功能分类的模块概览)

            ## JNI攻击面
            (JNI接口列表及风险评估)

            输出 [PROBE:...] 探针指令""";

    private static final String STAGE3_SO_PROMPT = """
            你是「AI 移动安全分析引擎 — Native SO 第3阶段：深度逆向与反混淆策略」。

            你的任务是基于混淆检测结果、字符串分析结果、以及前两个阶段的发现，进行深度逆向工程分析：

            1. **OLLVM 混淆评估与反混淆策略**:
               - 如果是OLLVM，分析：控制流平坦化的特征（状态变量、分发器、相关块数量）
               - 提供具体的IDA Pro脚本思路或D810插件参数建议
               - 如果有字符串加密，推断可能的解密算法和密钥存储位置
            2. **符号混淆评估**:
               - 混淆率分析（混淆函数占比）
               - 识别哪些函数是"有意义的"（可以通过交叉引用和API调用推断）
            3. **敏感字符串深度解读**:
               - 对每个类别的敏感字符串进行威胁评估
               - IP/URL类：通信端点的安全性
               - Key类：密钥的用途推断
               - AntiHook/AntiDebug类：保护方案的具体技术栈
            4. **SO加壳/加固方案深度分析**:
               - 如果检测到加固，分析壳的技术特征
               - 提供针对性的脱壳/反加固策略
               - 生成Frida脱壳脚本框架

            输出格式要求：
            <thinking>
            深度推理：
            - 混淆方案的具体技术原理
            - 反混淆的可行路径和步骤
            - 字符串的实际用途推断
            - 壳的工作原理推测
            </thinking>

            然后输出完整的 Markdown 深度逆向分析报告：

            ## 一、混淆方案深度分析
            ### OLLVM 技术特征
            ### 符号混淆评估
            ### 字符串加密分析

            ## 二、敏感数据威胁评估
            (表格：字符串 | 类别 | 推断用途 | 风险等级)

            ## 三、反混淆与脱壳策略
            ### IDA Pro / Ghidra 分析建议
            ### Frida 动态脱壳脚本
            ```javascript
            // 针对性脱壳/反混淆脚本
            ```

            ## 四、Native层威胁总结
            输出 [PROBE:...] 探针指令""";

    private static final String STAGE1_PROTOCOL_PROMPT = """
            你是「AI 移动安全分析引擎 — 协议分析 第1阶段：通信拓扑与威胁面建模」。

            你的任务是基于协议检测结果和基本统计信息，对通信架构进行深度分析：

            1. **通信架构推断**:
               - 根据协议层次（应用层/传输层）和端口分布，推断通信架构（C/S、P2P、发布/订阅？）
               - 识别可能的服务类型（Web服务、IoT MQTT、工业控制 Modbus/S7comm、VoIP）
            2. **攻击面识别**:
               - 未加密的明文协议端口 → 中间人窃听
               - 非标准端口上的标准协议 → 可能为C2隐蔽通信
               - 多种协议混合 → 可能为多层代理或隧道
               - 如果检测到Telnet/FTP → 极危险的明文认证协议
            3. **端点拓扑分析**:
               - 有多少个独立端点？是否有异常的外部连接？
               - 内网IP vs 外网IP分布
            4. **流量模式初步判断**:
               - 请求/响应比例异常？
               - 存在大量单向流量？（数据外传特征）

            输出格式要求：
            <thinking>
            逐条分析。运用网络威胁情报知识：
            - 引用已知APT/恶意软件的典型通信模式
            - 例如：Cobalt Strike 的 Beacon 模式、Metasploit Meterpreter 的 staged payload
            </thinking>

            然后输出 Markdown 通信拓扑分析报告：
            - 通信架构图（文字描述）
            - 攻击面清单（按严重程度排序）
            - 下一步深度审计重点""";

    private static final String STAGE2_PROTOCOL_PROMPT = """
            你是「AI 移动安全分析引擎 — 协议分析 第2阶段：深度安全审计与IOC提取」。

            你的任务是基于加密分析、数据格式分析、明文泄露审计、DNS/HTTP/TLS分析结果，进行深度安全审计：

            1. **加密通信安全评估**:
               - TLS版本和密码套件安全性（TLS 1.0/1.1已不安全）
               - 证书信息评估（自签名？过期？CN不匹配？）
               - 非TLS加密的熵分析解读（高熵=强加密/压缩，低熵=明文/简单编码）
            2. **明文泄露威胁评估**:
               - 对每类泄露的敏感信息进行威胁定级
               - 密码/Token明文 → 凭证劫持 → 横向移动
               - JWT明文 → 会话劫持 → 身份伪造
               - 信用卡/手机号 → 隐私泄露 → 合规风险（PCI DSS/GDPR）
               - SQL语句明文 → 数据库结构泄露 → SQL注入参考
            3. **C2 通信模式检测**:
               - 心跳/Beacon 检测：定期、固定大小的数据包
               - DNS 隧道检测：异常长的DNS查询、高熵子域名
               - HTTP C2 检测：异常User-Agent、长URI、周期性请求
               - 如果匹配到已知IOC域名或IP模式，标记为高危
            4. **数据泄露风险评估**:
               - 出站流量/入站流量比例异常？
               - 突发大流量传输？
               - 非工作时间流量？
            5. **协议指纹与异常检测**:
               - 如果协议类型与端口不匹配（如80端口跑非HTTP），标记为可疑

            输出格式要求：
            <thinking>
            深度的技术推理：
            - 对每个发现的威胁进行攻击链分析
            - 关联多个指标进行综合判断
            - 提供具体的威胁假设（"如果攻击者做了X，那么Y和Z会同时出现"）
            </thinking>

            然后输出完整的 Markdown 深度审计报告：

            ## 一、加密安全评估
            | 属性 | 值 | 安全性 | 说明 |
            |------|-----|--------|------|

            ## 二、明文泄露审计
            | 泄露类型 | 数量 | 风险等级 | 潜在影响 |

            ## 三、C2/IOC 威胁指标
            ### 疑似C2通信
            ### DNS IOC
            ### HTTP IOC

            ## 四、数据泄露评估

            ## 五、Wireshark/tshark 进一步分析建议
            ```bash
            # 具体的tshark命令
            ```

            输出 [PROBE:...] 探针指令""";

    // ========== 公共 API ==========

    /**
     * 启动 APK 流式分析，返回 taskId
     */
    public String startApkAnalysis(byte[] fileBytes, String filename) {
        String taskId = UUID.randomUUID().toString();
        Sinks.Many<String> sink = createSink(taskId);
        streamingExecutor.execute(() -> executeApkAnalysis(sink, fileBytes, filename, taskId));
        return taskId;
    }

    /**
     * 启动 SO 流式分析，返回 taskId
     */
    public String startSoAnalysis(byte[] fileBytes, String filename) {
        String taskId = UUID.randomUUID().toString();
        Sinks.Many<String> sink = createSink(taskId);
        streamingExecutor.execute(() -> executeSoAnalysis(sink, fileBytes, filename, taskId));
        return taskId;
    }

    /**
     * 启动 Protocol 流式分析，返回 taskId
     */
    public String startProtocolAnalysis(byte[] fileBytes, String filename) {
        return startProtocolAnalysis(fileBytes, filename, false);
    }

    public String startProtocolAnalysis(byte[] fileBytes, String filename, boolean aiAssist) {
        String taskId = UUID.randomUUID().toString();
        Sinks.Many<String> sink = createSink(taskId);
        streamingExecutor.execute(() -> executeProtocolAnalysis(sink, fileBytes, filename, taskId, aiAssist));
        return taskId;
    }

    /**
     * 启动 APK 反编译流式分析，返回 taskId
     */
    public String startDecompileAnalysis(byte[] fileBytes, String filename) {
        String taskId = UUID.randomUUID().toString();
        Sinks.Many<String> sink = createSink(taskId);
        streamingExecutor.execute(() -> executeDecompileAnalysis(sink, fileBytes, filename, taskId));
        return taskId;
    }

    /**
     * 获取指定任务的 SSE Flux（前端 GET SSE 订阅）
     */
    public Flux<String> getTaskStream(String taskId) {
        Sinks.Many<String> sink = taskSinks.get(taskId);
        if (sink == null) {
            return Flux.just(toJson(Map.of("type", "error", "message", "任务不存在或已过期")));
        }
        return Flux.merge(
                sink.asFlux(),
                Flux.interval(Duration.ofSeconds(5)).map(i -> ":ping\n\n")
        ).doOnSubscribe(s -> {
            sink.tryEmitNext(":connected\n\n");
        });
    }

    // ========== 执行管线 ==========

    private void executeApkAnalysis(Sinks.Many<String> sink, byte[] fileBytes, String filename, String taskId) {
        if (fileBytes.length > MAX_FILE_SIZE) {
            emit(sink, "error", Map.of("message", "文件大小超过限制 (最大200MB)", "recoverable", false));
            sink.tryEmitComplete();
            cleanup(taskId);
            return;
        }
        Path tempApk = null;
        try {
            tempApk = writeTempFile(fileBytes, filename);

            emit(sink, "progress", Map.of("stage", "init", "percent", 3, "message", "正在解析 APK 文件结构..."));
            Map<String, Object> apkInfo = apkAnalysisService.stepExtractApkInfo(tempApk, fileBytes, filename);
            emit(sink, "progress", Map.of("stage", "parse", "percent", 10, "message", "已提取包名/版本/证书信息"));
            emit(sink, "step_result", Map.of("stepName", "apkInfo", "data", apkInfo));

            // 阶段1: AI 结构审查
            emit(sink, "progress", Map.of("stage", "ai_stage1", "percent", 12, "message", "AI 正在审查 APK 结构并规划审计策略..."));
            String stage1Context = buildApkStage1Context(apkInfo, filename, fileBytes.length);
            streamAiResponse(sink, stage1Context, STAGE1_APK_PROMPT, "ai_stage1");
            emit(sink, "progress", Map.of("stage", "ai_stage1_done", "percent", 15, "message", "AI 结构审查完成"));

            // 特征提取
            emit(sink, "progress", Map.of("stage", "features", "percent", 18, "message", "正在提取 APK 特征（权限/组件/原生库/敏感API）..."));
            Map<String, Object> features = apkAnalysisService.stepExtractFeatures(tempApk);
            emit(sink, "step_result", Map.of("stepName", "features", "data", features));

            List<Map<String, Object>> permissions = apkAnalysisService.stepAnalyzePermissions(features);
            emit(sink, "step_result", Map.of("stepName", "permissions", "data", permissions));
            emit(sink, "progress", Map.of("stage", "permissions", "percent", 22, "message", "已分析 " + permissions.size() + " 项权限请求"));

            List<String> domains = apkAnalysisService.stepExtractNetworkDomains(tempApk);
            emit(sink, "step_result", Map.of("stepName", "domains", "data", domains));
            emit(sink, "progress", Map.of("stage", "domains", "percent", 26, "message", "已提取 " + domains.size() + " 个网络域名"));

            // 新增: WebView/SSL/导出组件/反射检测
            emit(sink, "progress", Map.of("stage", "webview", "percent", 28, "message", "正在检测 WebView 安全风险..."));
            Map<String, Object> webViewSecurity = apkAnalysisService.stepDetectWebViewSecurity(tempApk);
            emit(sink, "step_result", Map.of("stepName", "webViewSecurity", "data", webViewSecurity));
            if (Boolean.TRUE.equals(webViewSecurity.get("hasJavascriptInterface"))) {
                emit(sink, "finding", Map.of("severity", "HIGH", "title", "WebView addJavascriptInterface 远程代码执行风险",
                        "description", String.valueOf(webViewSecurity.get("risks"))));
            }

            emit(sink, "progress", Map.of("stage", "ssl", "percent", 30, "message", "正在检测 SSL/TLS 证书验证绕过..."));
            Map<String, Object> sslBypass = apkAnalysisService.stepDetectSSL(tempApk);
            emit(sink, "step_result", Map.of("stepName", "sslBypass", "data", sslBypass));
            if (Boolean.TRUE.equals(sslBypass.get("hasCustomTrustManager"))) {
                emit(sink, "finding", Map.of("severity", "HIGH", "title", "检测到 SSL 证书验证绕过",
                        "description", String.valueOf(sslBypass.get("details"))));
            }

            emit(sink, "progress", Map.of("stage", "export", "percent", 32, "message", "正在分析组件导出风险..."));
            List<Map<String, Object>> exportedComponents = apkAnalysisService.stepDetectComponentExport(tempApk);
            emit(sink, "step_result", Map.of("stepName", "exportedComponents", "data", exportedComponents));
            if (!exportedComponents.isEmpty()) {
                long highRisk = exportedComponents.stream().filter(c -> "HIGH".equals(c.get("risk"))).count();
                if (highRisk > 0) {
                    emit(sink, "finding", Map.of("severity", "HIGH", "title", "发现 " + highRisk + " 个高危导出组件", "description", "未受权限保护的导出组件"));
                }
            }

            emit(sink, "progress", Map.of("stage", "reflection", "percent", 34, "message", "正在检测反射与动态加载..."));
            Map<String, Object> reflection = apkAnalysisService.stepDetectReflection(tempApk);
            emit(sink, "step_result", Map.of("stepName", "reflection", "data", reflection));

            // 阶段2: AI 特征分析 (增强版：包含 WebView/SSL/导出/反射数据)
            emit(sink, "progress", Map.of("stage", "ai_stage2", "percent", 36, "message", "AI 正在深度分析提取的特征数据..."));
            String stage2Context = buildApkStage2Context(features, permissions, domains)
                    + buildApkStage2EnhancedContext(webViewSecurity, sslBypass, exportedComponents, reflection);
            streamAiResponse(sink, stage2Context, STAGE2_APK_PROMPT, "ai_stage2");
            emit(sink, "progress", Map.of("stage", "ai_stage2_done", "percent", 42, "message", "AI 特征分析完成"));

            // 深度检测
            emit(sink, "progress", Map.of("stage", "antiEmulator", "percent", 44, "message", "正在检测反模拟器/反调试能力..."));
            Map<String, Object> antiEmulator = apkAnalysisService.stepDetectAntiEmulator(tempApk);
            emit(sink, "step_result", Map.of("stepName", "antiEmulator", "data", antiEmulator));
            if (Boolean.TRUE.equals(antiEmulator.get("hasAntiEmulation"))) {
                emit(sink, "finding", Map.of("severity", "MEDIUM", "title", "检测到反模拟器/反沙箱机制",
                        "description", String.valueOf(antiEmulator.get("indicators"))));
            }

            emit(sink, "progress", Map.of("stage", "packer", "percent", 46, "message", "正在检测加壳/加固方案..."));
            Map<String, Object> packerInfo = apkAnalysisService.stepDetectPacker(tempApk);
            emit(sink, "step_result", Map.of("stepName", "packerInfo", "data", packerInfo));
            if (Boolean.TRUE.equals(packerInfo.get("isPacked"))) {
                emit(sink, "finding", Map.of("severity", "HIGH", "title", "检测到加壳: " + packerInfo.get("packer"), "description", packerInfo.get("evidence")));
            }

            emit(sink, "progress", Map.of("stage", "sdk", "percent", 48, "message", "正在识别第三方 SDK..."));
            List<Map<String, Object>> sdks = apkAnalysisService.stepIdentifySdks(tempApk);
            emit(sink, "step_result", Map.of("stepName", "sdks", "data", sdks));
            if (!sdks.isEmpty()) {
                emit(sink, "progress", Map.of("stage", "sdk_done", "percent", 50, "message", "已识别 " + sdks.size() + " 个第三方 SDK"));
            }

            emit(sink, "progress", Map.of("stage", "dexObfuscation", "percent", 52, "message", "正在评估 DEX 混淆程度..."));
            Map<String, Object> dexObfuscation = apkAnalysisService.stepAssessDexObfuscation(tempApk);
            emit(sink, "step_result", Map.of("stepName", "dexObfuscation", "data", dexObfuscation));

            emit(sink, "progress", Map.of("stage", "malware", "percent", 54, "message", "正在执行恶意行为检测..."));
            Map<String, Object> detection = apkAnalysisService.stepMalwareDetection(features, permissions, domains);
            emit(sink, "step_result", Map.of("stepName", "detection", "data", detection));

            emit(sink, "progress", Map.of("stage", "secrets", "percent", 58, "message", "正在扫描敏感凭据与密钥..."));
            List<Map<String, Object>> secrets = apkAnalysisService.stepScanSecrets(tempApk);
            emit(sink, "step_result", Map.of("stepName", "secrets", "data", secrets));
            if (!secrets.isEmpty()) {
                emit(sink, "finding", Map.of("severity", "HIGH", "title", "发现 " + secrets.size() + " 处敏感凭据泄露",
                        "description", "包括: " + secrets.stream().map(s -> s.get("type")).distinct().toList()));
            }

            emit(sink, "progress", Map.of("stage", "owasp", "percent", 62, "message", "正在执行 OWASP 移动安全合规审计..."));
            List<Map<String, Object>> owaspMatches = apkAnalysisService.stepScanOwaspRules(tempApk, features, domains);
            emit(sink, "step_result", Map.of("stepName", "owaspMatches", "data", owaspMatches));

            // 阶段3: AI 深度检测解读 (增强版: 包含全部新检测数据)
            emit(sink, "progress", Map.of("stage", "ai_stage3", "percent", 64, "message", "AI 正在对检测结果进行深度专业解读..."));
            String stage3Context = buildApkStage3Context(detection, secrets, owaspMatches, packerInfo)
                    + buildApkStage3EnhancedContext(webViewSecurity, sslBypass, exportedComponents, reflection, antiEmulator, dexObfuscation);
            streamAiResponse(sink, stage3Context, STAGE3_APK_PROMPT, "ai_stage3");
            emit(sink, "progress", Map.of("stage", "ai_stage3_done", "percent", 74, "message", "AI 深度检测解读完成"));

            // 阶段4: AI 综合研判
            emit(sink, "progress", Map.of("stage", "ai_stage4", "percent", 76, "message", "AI 正在进行综合研判，生成最终安全分析报告..."));
            String stage4Context = buildApkStage4Context(apkInfo, features, permissions, domains, detection, secrets, owaspMatches, packerInfo)
                    + buildApkStage2EnhancedContext(webViewSecurity, sslBypass, exportedComponents, reflection)
                    + buildApkStage3EnhancedContext(webViewSecurity, sslBypass, exportedComponents, reflection, antiEmulator, dexObfuscation)
                    + "\n## 第三方 SDK\n" + sdks.stream().map(s -> "- " + s.get("name") + " (签名: " + s.getOrDefault("signature", "") + ")").reduce("", (a, b) -> a + b + "\n");
            streamAiResponse(sink, stage4Context, STAGE4_APK_PROMPT, "ai_stage4");
            emit(sink, "progress", Map.of("stage", "ai_stage4_done", "percent", 94, "message", "AI 综合研判完成"));

            // 生成建议
            emit(sink, "progress", Map.of("stage", "recommendations", "percent", 96, "message", "正在生成处置建议..."));
            List<Map<String, Object>> recommendations = apkAnalysisService.stepGenerateRecommendations(detection);
            emit(sink, "step_result", Map.of("stepName", "recommendations", "data", recommendations));

            Map<String, Object> finalResult = new LinkedHashMap<>();
            finalResult.put("apkInfo", apkInfo);
            finalResult.put("features", features);
            finalResult.put("permissions", permissions);
            finalResult.put("networkDomains", domains);
            finalResult.put("webViewSecurity", webViewSecurity);
            finalResult.put("sslBypass", sslBypass);
            finalResult.put("exportedComponents", exportedComponents);
            finalResult.put("reflection", reflection);
            finalResult.put("antiEmulator", antiEmulator);
            finalResult.put("sdks", sdks);
            finalResult.put("dexObfuscation", dexObfuscation);
            finalResult.put("packerInfo", packerInfo);
            finalResult.put("detection", detection);
            finalResult.put("secrets", secrets);
            finalResult.put("owaspMatches", owaspMatches);
            finalResult.put("recommendations", recommendations);
            finalResult.put("success", true);

            emit(sink, "progress", Map.of("stage", "done", "percent", 100, "message", "分析完成"));
            emit(sink, "complete", Map.of("result", finalResult));
            sink.tryEmitComplete();

        } catch (Exception e) {
            log.error("APK 流式分析失败", e);
            emit(sink, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "未知错误", "recoverable", false));
            sink.tryEmitComplete();
        } finally {
            if (tempApk != null) {
                try { Files.deleteIfExists(tempApk); } catch (IOException ignored) {}
            }
            cleanup(taskId);
        }
    }

    private void executeSoAnalysis(Sinks.Many<String> sink, byte[] fileBytes, String filename, String taskId) {
        if (fileBytes.length > MAX_FILE_SIZE) {
            emit(sink, "error", Map.of("message", "文件大小超过限制 (最大200MB)", "recoverable", false));
            sink.tryEmitComplete();
            cleanup(taskId);
            return;
        }
        try {
            emit(sink, "progress", Map.of("stage", "init", "percent", 5, "message", "正在解析 ELF 文件头..."));
            Map<String, Object> soInfo = soAnalysisService.stepExtractSoInfo(fileBytes, filename);
            emit(sink, "step_result", Map.of("stepName", "soInfo", "data", soInfo));
            emit(sink, "progress", Map.of("stage", "elf_parsed", "percent", 10, "message", "ELF 解析完成，架构: " + soInfo.get("architecture")));

            // 新增: ELF 安全属性审计
            emit(sink, "progress", Map.of("stage", "elf_security", "percent", 12, "message", "正在审计 ELF 安全属性（PIE/RELRO/NX/Stack Canary）..."));
            Map<String, Object> elfSecurity = soAnalysisService.stepAnalyzeElfSecurity(fileBytes);
            emit(sink, "step_result", Map.of("stepName", "elfSecurity", "data", elfSecurity));
            if (elfSecurity.containsKey("securityScore")) {
                int score = ((Number) elfSecurity.get("securityScore")).intValue();
                if (score < 70) {
                    emit(sink, "finding", Map.of("severity", "HIGH", "title", "ELF 安全评分较低: " + score + "/100", "description", elfSecurity.get("issues")));
                }
            }

            // 阶段1: AI 结构审查 (增强版: 包含 ELF 安全审计数据)
            emit(sink, "progress", Map.of("stage", "ai_stage1", "percent", 15, "message", "AI 正在审查 ELF 结构安全性..."));
            String stage1Ctx = buildSoStage1Context(soInfo, elfSecurity, fileBytes.length, filename);
            streamAiResponse(sink, stage1Ctx, STAGE1_SO_PROMPT, "ai_stage1");

            emit(sink, "progress", Map.of("stage", "functions", "percent", 25, "message", "正在提取函数列表..."));
            List<Map<String, Object>> functions = soAnalysisService.stepExtractFunctions(fileBytes);
            emit(sink, "step_result", Map.of("stepName", "functions", "data", functions));
            emit(sink, "progress", Map.of("stage", "functions_done", "percent", 32, "message", "已提取 " + functions.size() + " 个导出函数"));

            emit(sink, "progress", Map.of("stage", "crypto", "percent", 36, "message", "正在匹配加密算法特征..."));
            List<Map<String, Object>> algorithms = soAnalysisService.stepIdentifyCrypto(fileBytes, functions);
            emit(sink, "step_result", Map.of("stepName", "algorithms", "data", algorithms));
            if (!algorithms.isEmpty()) {
                emit(sink, "finding", Map.of("severity", "MEDIUM", "title", "识别到 " + algorithms.size() + " 种加密算法",
                        "description", algorithms.stream().map(a -> String.valueOf(a.get("name"))).toList()));
            }

            // 新增: SO 加壳/加固检测
            emit(sink, "progress", Map.of("stage", "so_packer", "percent", 40, "message", "正在检测 SO 加壳/加固方案..."));
            Map<String, Object> soPacker = soAnalysisService.stepDetectSoPacker(fileBytes, functions);
            emit(sink, "step_result", Map.of("stepName", "soPacker", "data", soPacker));
            if (Boolean.TRUE.equals(soPacker.get("isPacked"))) {
                emit(sink, "finding", Map.of("severity", "HIGH", "title", "检测到 SO 加固: " + soPacker.get("packer"), "description", soPacker.get("evidence")));
            }

            // 阶段2: AI 特征分析 (增强版: 包含 ELF 安全属性 + SO 加壳数据)
            emit(sink, "progress", Map.of("stage", "ai_stage2", "percent", 44, "message", "AI 正在深度分析 SO 特征数据..."));
            String stage2Ctx = buildSoStage2Context(soInfo, functions, algorithms)
                    + "\n## ELF 安全属性审计\n" + elfSecurity + "\n"
                    + "\n## SO 加壳/加固\n" + soPacker + "\n";
            streamAiResponse(sink, stage2Ctx, STAGE2_SO_PROMPT, "ai_stage2");

            // 新增: 反调试检测
            emit(sink, "progress", Map.of("stage", "anti_debug", "percent", 52, "message", "正在检测反调试/反Hook能力..."));
            Map<String, Object> antiDebug = soAnalysisService.stepDetectAntiDebug(fileBytes, functions);
            emit(sink, "step_result", Map.of("stepName", "antiDebug", "data", antiDebug));
            if (Boolean.TRUE.equals(antiDebug.get("hasAntiDebug"))) {
                emit(sink, "finding", Map.of("severity", "HIGH", "title", "检测到反调试/反Hook机制", "description", antiDebug.get("techniques")));
            }

            // 新增: 压缩/加壳检测
            emit(sink, "progress", Map.of("stage", "compression", "percent", 56, "message", "正在检测压缩/加壳（UPX/MPRESS）..."));
            Map<String, Object> compression = soAnalysisService.stepDetectCompression(fileBytes);
            emit(sink, "step_result", Map.of("stepName", "compression", "data", compression));
            if (Boolean.TRUE.equals(compression.get("isCompressed"))) {
                emit(sink, "finding", Map.of("severity", "MEDIUM", "title", "检测到 SO 压缩: " + compression.get("compressionType"), "description", ""));
            }

            emit(sink, "progress", Map.of("stage", "obfuscation", "percent", 60, "message", "正在检测代码混淆..."));
            Map<String, Object> obfuscation = soAnalysisService.stepDetectObfuscation(fileBytes, functions);
            emit(sink, "step_result", Map.of("stepName", "obfuscation", "data", obfuscation));
            if (Boolean.TRUE.equals(obfuscation.get("ollvm"))) {
                emit(sink, "finding", Map.of("severity", "HIGH", "title", "检测到 OLLVM 混淆", "description", "控制流平坦化特征"));
            }

            emit(sink, "progress", Map.of("stage", "strings", "percent", 66, "message", "正在提取敏感字符串..."));
            List<Map<String, Object>> strings = soAnalysisService.stepAnalyzeStrings(fileBytes);
            emit(sink, "step_result", Map.of("stepName", "strings", "data", strings));

            // 阶段3: AI 深度逆向分析 (增强版: 包含全部新增检测数据)
            emit(sink, "progress", Map.of("stage", "ai_stage3", "percent", 70, "message", "AI 正在进行深度逆向分析..."));
            String stage3Ctx = buildSoStage3Context(soInfo, functions, algorithms, obfuscation, strings)
                    + "\n## ELF 安全属性\n" + elfSecurity + "\n"
                    + "\n## SO 加壳分析\n" + soPacker + "\n"
                    + "\n## 反调试能力\n" + antiDebug + "\n"
                    + "\n## 压缩检测\n" + compression + "\n";
            streamAiResponse(sink, stage3Ctx, STAGE3_SO_PROMPT, "ai_stage3");

            Map<String, Object> finalResult = new LinkedHashMap<>();
            finalResult.put("soInfo", soInfo);
            finalResult.put("elfSecurity", elfSecurity);
            finalResult.put("functions", functions);
            finalResult.put("algorithms", algorithms);
            finalResult.put("soPacker", soPacker);
            finalResult.put("antiDebug", antiDebug);
            finalResult.put("compression", compression);
            finalResult.put("obfuscation", obfuscation);
            finalResult.put("strings", strings);
            finalResult.put("success", true);

            emit(sink, "progress", Map.of("stage", "done", "percent", 100, "message", "分析完成"));
            emit(sink, "complete", Map.of("result", finalResult));
            sink.tryEmitComplete();

        } catch (Exception e) {
            log.error("SO 流式分析失败", e);
            emit(sink, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "未知错误", "recoverable", false));
            sink.tryEmitComplete();
        } finally {
            cleanup(taskId);
        }
    }

    private void executeProtocolAnalysis(Sinks.Many<String> sink, byte[] fileBytes, String filename, String taskId, boolean aiAssist) {
        if (fileBytes.length > MAX_FILE_SIZE) {
            emit(sink, "error", Map.of("message", "文件大小超过限制 (最大200MB)", "recoverable", false));
            sink.tryEmitComplete();
            cleanup(taskId);
            return;
        }
        Path tempPcap = null;
        try {
            tempPcap = writeTempFile(fileBytes, filename);

            emit(sink, "progress", Map.of("stage", "validate", "percent", 5, "message", "正在验证 PCAP 文件格式..."));
            Map<String, Object> basicInfo = protocolAnalysisService.stepValidateAndBasicInfo(fileBytes, filename);
            emit(sink, "step_result", Map.of("stepName", "basicInfo", "data", basicInfo));

            emit(sink, "progress", Map.of("stage", "protocol", "percent", 12, "message", "正在解析协议层次..."));
            Map<String, Object> protocol = protocolAnalysisService.stepParseProtocol(tempPcap);
            emit(sink, "step_result", Map.of("stepName", "protocol", "data", protocol));
            emit(sink, "progress", Map.of("stage", "protocol_done", "percent", 20, "message", "协议识别: " + protocol.getOrDefault("type", "Unknown")));

            // 新增: DNS 查询提取
            emit(sink, "progress", Map.of("stage", "dns", "percent", 24, "message", "正在提取 DNS 查询记录..."));
            List<Map<String, Object>> dnsQueries = protocolAnalysisService.stepExtractDnsQueries(tempPcap);
            emit(sink, "step_result", Map.of("stepName", "dnsQueries", "data", dnsQueries));
            emit(sink, "progress", Map.of("stage", "dns_done", "percent", 26, "message", "已提取 " + dnsQueries.size() + " 条 DNS 查询"));

            // 新增: HTTP 请求提取
            emit(sink, "progress", Map.of("stage", "http", "percent", 28, "message", "正在提取 HTTP 请求..."));
            List<Map<String, Object>> httpRequests = protocolAnalysisService.stepExtractHttpRequests(tempPcap);
            emit(sink, "step_result", Map.of("stepName", "httpRequests", "data", httpRequests));

            // 新增: TLS 证书提取
            emit(sink, "progress", Map.of("stage", "tls", "percent", 30, "message", "正在提取 TLS 证书信息..."));
            List<Map<String, Object>> tlsCerts = protocolAnalysisService.stepExtractTlsCertificates(tempPcap);
            emit(sink, "step_result", Map.of("stepName", "tlsCerts", "data", tlsCerts));
            if (!tlsCerts.isEmpty()) {
                emit(sink, "finding", Map.of("severity", "INFO", "title", "提取到 " + tlsCerts.size() + " 个 TLS 证书", "description", ""));
            }

            // 阶段1: AI 拓扑分析 (仅在 aiAssist 开启时执行)
            if (aiAssist) {
                emit(sink, "progress", Map.of("stage", "ai_stage1", "percent", 34, "message", "AI 正在分析通信拓扑结构..."));
                String stage1Ctx = buildProtocolStage1Context(protocol, basicInfo, dnsQueries, httpRequests, tlsCerts, fileBytes.length);
                streamAiResponse(sink, stage1Ctx, STAGE1_PROTOCOL_PROMPT, "ai_stage1");
            } else {
                emit(sink, "progress", Map.of("stage", "ai_stage1", "percent", 34, "message", "跳过 AI 拓扑分析（AI 辅助已关闭）"));
            }

            emit(sink, "progress", Map.of("stage", "stats", "percent", 42, "message", "正在统计通信信息..."));
            Map<String, Object> stats = protocolAnalysisService.stepCalculateStats(tempPcap);
            emit(sink, "step_result", Map.of("stepName", "stats", "data", stats));

            emit(sink, "progress", Map.of("stage", "encryption", "percent", 50, "message", "正在分析加密特征..."));
            Map<String, Object> encryption = protocolAnalysisService.stepAnalyzeEncryption(tempPcap, fileBytes);
            emit(sink, "step_result", Map.of("stepName", "encryption", "data", encryption));
            if (Boolean.TRUE.equals(encryption.get("identified"))) {
                emit(sink, "finding", Map.of("severity", "INFO", "title", "检测到加密通信: " + encryption.get("type"), "description", ""));
            }

            emit(sink, "progress", Map.of("stage", "dataformat", "percent", 58, "message", "正在分析数据格式..."));
            Map<String, Object> dataFormat = protocolAnalysisService.stepAnalyzeDataFormat(tempPcap);
            emit(sink, "step_result", Map.of("stepName", "dataFormat", "data", dataFormat));
            if (dataFormat.containsKey("plaintextLeaks")) {
                @SuppressWarnings("unchecked")
                List<?> leaks = (List<?>) dataFormat.get("plaintextLeaks");
                if (!leaks.isEmpty()) {
                    emit(sink, "finding", Map.of("severity", "HIGH", "title", "发现 " + leaks.size() + " 处明文凭据泄露", "description", ""));
                }
            }

            // 新增: UDP 会话分析
            emit(sink, "progress", Map.of("stage", "udp", "percent", 62, "message", "正在分析 UDP 会话..."));
            Map<String, Object> udpAnalysis = protocolAnalysisService.stepAnalyzeUdpConversations(tempPcap);
            emit(sink, "step_result", Map.of("stepName", "udpAnalysis", "data", udpAnalysis));

            // 新增: 时序分析
            emit(sink, "progress", Map.of("stage", "temporal", "percent", 64, "message", "正在进行流量时序分析..."));
            Map<String, Object> temporalAnalysis = protocolAnalysisService.stepAnalyzeTemporalPatterns(tempPcap);
            emit(sink, "step_result", Map.of("stepName", "temporalAnalysis", "data", temporalAnalysis));

            // 新增: IOC 匹配
            emit(sink, "progress", Map.of("stage", "ioc", "percent", 66, "message", "正在匹配已知恶意 IOC..."));
            List<Map<String, Object>> maliciousIocs = protocolAnalysisService.stepMatchMaliciousIoCs(tempPcap, dnsQueries, httpRequests);
            emit(sink, "step_result", Map.of("stepName", "maliciousIocs", "data", maliciousIocs));
            if (!maliciousIocs.isEmpty()) {
                emit(sink, "finding", Map.of("severity", "HIGH", "title", "匹配到 " + maliciousIocs.size() + " 个疑似恶意 IOC", "description", ""));
            }

            // 阶段2: AI 深度审计 (仅在 aiAssist 开启时执行)
            if (aiAssist) {
                emit(sink, "progress", Map.of("stage", "ai_stage2", "percent", 70, "message", "AI 正在进行深度协议安全审计..."));
                String stage2Ctx = buildProtocolStage2Context(protocol, stats, encryption, dataFormat)
                        + "\n## DNS 查询 (" + dnsQueries.size() + " 条)\n" + dnsQueries + "\n"
                        + "\n## HTTP 请求 (" + httpRequests.size() + " 条)\n" + httpRequests + "\n"
                        + "\n## TLS 证书 (" + tlsCerts.size() + " 个)\n" + tlsCerts + "\n"
                        + "\n## UDP 会话分析\n" + udpAnalysis + "\n"
                        + "\n## 时序分析\n" + temporalAnalysis + "\n"
                        + "\n## 恶意 IOC 匹配 (" + maliciousIocs.size() + " 条)\n" + maliciousIocs + "\n";
                streamAiResponse(sink, stage2Ctx, STAGE2_PROTOCOL_PROMPT, "ai_stage2");
            } else {
                emit(sink, "progress", Map.of("stage", "ai_stage2", "percent", 70, "message", "跳过 AI 深度审计（AI 辅助已关闭）"));
            }

            Map<String, Object> finalResult = new LinkedHashMap<>();
            finalResult.put("basicInfo", basicInfo);
            finalResult.put("protocol", protocol);
            finalResult.put("dnsQueries", dnsQueries);
            finalResult.put("httpRequests", httpRequests);
            finalResult.put("tlsCerts", tlsCerts);
            finalResult.put("stats", stats);
            finalResult.put("encryption", encryption);
            finalResult.put("dataFormat", dataFormat);
            finalResult.put("udpAnalysis", udpAnalysis);
            finalResult.put("temporalAnalysis", temporalAnalysis);
            finalResult.put("maliciousIocs", maliciousIocs);
            finalResult.put("success", true);

            emit(sink, "progress", Map.of("stage", "done", "percent", 100, "message", "分析完成"));
            emit(sink, "complete", Map.of("result", finalResult));
            sink.tryEmitComplete();

        } catch (Exception e) {
            log.error("Protocol 流式分析失败", e);
            emit(sink, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "未知错误", "recoverable", false));
            sink.tryEmitComplete();
        } finally {
            if (tempPcap != null) {
                try { Files.deleteIfExists(tempPcap); } catch (IOException ignored) {}
            }
            cleanup(taskId);
        }
    }

    private void executeDecompileAnalysis(Sinks.Many<String> sink, byte[] fileBytes, String filename, String taskId) {
        if (fileBytes.length > MAX_FILE_SIZE) {
            emit(sink, "error", Map.of("message", "文件大小超过限制 (最大200MB)", "recoverable", false));
            sink.tryEmitComplete();
            cleanup(taskId);
            return;
        }
        try {
            emit(sink, "progress", Map.of("stage", "decompile_start", "percent", 5, "message", "正在启动 ApkTool 反编译引擎..."));
            Map<String, Object> result = apkToolService.decompile(fileBytes, filename);

            emit(sink, "progress", Map.of("stage", "decompile_done", "percent", 80, "message", "反编译完成，正在分析输出..."));
            emit(sink, "step_result", Map.of("stepName", "decompile", "data", result));

            emit(sink, "progress", Map.of("stage", "done", "percent", 100, "message", "反编译完成"));
            emit(sink, "complete", Map.of("result", result));
            sink.tryEmitComplete();

        } catch (Exception e) {
            log.error("反编译流式分析失败", e);
            emit(sink, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "未知错误", "recoverable", false));
            sink.tryEmitComplete();
        } finally {
            cleanup(taskId);
        }
    }

    // ========== 内部方法 ==========

    private Sinks.Many<String> createSink(String taskId) {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer(256);
        taskSinks.put(taskId, sink);
        return sink;
    }

    private void cleanup(String taskId) {
        Sinks.Many<String> sink = taskSinks.remove(taskId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    private Path writeTempFile(byte[] data, String filename) throws IOException {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "streaming-analysis");
        Files.createDirectories(tempDir);
        Path tempFile = tempDir.resolve(UUID.randomUUID().toString() + "_" + filename);
        Files.write(tempFile, data);
        return tempFile;
    }

    private void emit(Sinks.Many<String> sink, String type, Map<String, Object> data) {
        Map<String, Object> event = new LinkedHashMap<>(data);
        event.put("type", type);
        event.put("timestamp", Instant.now().toEpochMilli());
        sink.tryEmitNext(toJson(event));
    }

    private void streamAiResponse(Sinks.Many<String> sink, String userContext, String systemPrompt, String stage) {
        try {
            String response = chatModel.chat(List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userContext)
            )).aiMessage().text();

            String[] paragraphs = response.split("\n\n");
            for (String paragraph : paragraphs) {
                emit(sink, "ai_chunk", Map.of("stage", stage, "content", paragraph + "\n\n"));
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            }

            emit(sink, "ai_chunk_done", Map.of("stage", stage));
        } catch (Exception e) {
            log.warn("AI 调用失败: stage={}", stage, e);
            emit(sink, "ai_chunk", Map.of("stage", stage, "content", "\n> ⚠️ AI 分析暂时不可用，继续基于规则的分析...\n\n"));
        }
    }

    private String toJson(Map<String, Object> map) {
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { return "{\"type\":\"error\",\"message\":\"序列化失败\"}"; }
    }

    // ========== 上下文构建 ==========

    private String buildApkStage1Context(Map<String, Object> apkInfo, String filename, int fileSize) {
        StringBuilder sb = new StringBuilder();
        sb.append("【APK 结构审查数据】\n\n");
        sb.append(String.format("""
                        文件名: %s
                        文件大小: %d bytes (%.2f MB)
                        包名: %s
                        版本: %s (versionCode: %s)
                        应用名: %s
                        minSdkVersion: %s
                        targetSdkVersion: %s
                        MD5: %s
                        SHA-256: %s
                        """,
                filename, fileSize, fileSize / (1024.0 * 1024.0),
                apkInfo.getOrDefault("packageName", "N/A"),
                apkInfo.getOrDefault("versionName", "N/A"),
                apkInfo.getOrDefault("versionCode", "N/A"),
                apkInfo.getOrDefault("label", "N/A"),
                apkInfo.getOrDefault("minSdkVersion", "N/A"),
                apkInfo.getOrDefault("targetSdkVersion", "N/A"),
                apkInfo.getOrDefault("md5", "N/A"),
                apkInfo.getOrDefault("sha256", "N/A")));

        // 安全属性
        sb.append("\n【安全属性】\n");
        sb.append("debuggable: ").append(apkInfo.getOrDefault("debuggable", "unknown")).append("\n");
        sb.append("allowBackup: ").append(apkInfo.getOrDefault("allowBackup", "unknown")).append("\n");
        sb.append("usesCleartextTraffic: ").append(apkInfo.getOrDefault("usesCleartextTraffic", "unknown")).append("\n");
        sb.append("hasBackupAgent: ").append(apkInfo.getOrDefault("hasBackupAgent", "unknown")).append("\n");
        sb.append("customApplication: ").append(apkInfo.getOrDefault("customApplication", "无")).append("\n");
        sb.append("hasExportedProvider: ").append(apkInfo.getOrDefault("hasExportedProvider", "unknown")).append("\n");

        // 证书信息
        @SuppressWarnings("unchecked")
        Map<String, Object> cert = (Map<String, Object>) apkInfo.get("certificate");
        if (cert != null) {
            sb.append("\n【证书信息】\n");
            sb.append("签名算法: ").append(cert.getOrDefault("signAlgorithm", "N/A")).append("\n");
            sb.append("证书MD5: ").append(cert.getOrDefault("certMd5", "N/A")).append("\n");
        }

        return sb.toString();
    }

    private String buildApkStage2Context(Map<String, Object> features, List<Map<String, Object>> permissions, List<String> domains) {
        StringBuilder sb = new StringBuilder();
        sb.append("【APK 特征数据 — 第2阶段 AI 分析输入】\n\n");

        // 权限列表按风险分级
        sb.append("## 权限列表 (").append(permissions.size()).append(" 项)\n\n");
        sb.append("### CRITICAL 权限:\n");
        permissions.stream().filter(p -> "CRITICAL".equals(p.get("risk")))
                .forEach(p -> sb.append("- ").append(p.get("name")).append(" | 用途: ").append(p.get("purpose")).append("\n"));
        sb.append("\n### HIGH 权限:\n");
        permissions.stream().filter(p -> "HIGH".equals(p.get("risk")))
                .forEach(p -> sb.append("- ").append(p.get("name")).append(" | 用途: ").append(p.get("purpose")).append("\n"));
        sb.append("\n### MEDIUM 权限:\n");
        permissions.stream().filter(p -> "MEDIUM".equals(p.get("risk")))
                .forEach(p -> sb.append("- ").append(p.get("name")).append(" | 用途: ").append(p.get("purpose")).append("\n"));

        // 敏感API按类别
        @SuppressWarnings("unchecked")
        List<String> apis = (List<String>) features.getOrDefault("sensitiveApis", List.of());
        sb.append("\n## 敏感 API 调用 (").append(apis.size()).append(" 处)\n\n");
        // 按类别分组
        Map<String, List<String>> apiCategories = new LinkedHashMap<>();
        for (String api : apis) {
            String cat;
            if (api.contains("TelephonyManager") || api.contains("getDeviceId") || api.contains("getSubscriberId")) cat = "设备标识与隐私";
            else if (api.contains("Runtime.exec") || api.contains("ProcessBuilder")) cat = "命令执行";
            else if (api.contains("SmsManager") || api.contains("SmsMessage")) cat = "短信操作";
            else if (api.contains("DexClassLoader") || api.contains("PathClassLoader") || api.contains("reflect") || api.contains("forName")) cat = "动态加载与反射";
            else if (api.contains("Cipher") || api.contains("MessageDigest") || api.contains("SecretKeySpec")) cat = "加密/解密";
            else if (api.contains("TrustManager") || api.contains("HostnameVerifier") || api.contains("SSLSocket")) cat = "SSL/TLS绕过";
            else if (api.contains("WebView") || api.contains("addJavascriptInterface") || api.contains("setJavaScriptEnabled")) cat = "WebView风险";
            else if (api.contains("Camera") || api.contains("MediaRecorder") || api.contains("AudioRecord")) cat = "摄像头与音频";
            else if (api.contains("LocationManager") || api.contains("getLastKnownLocation")) cat = "位置追踪";
            else if (api.contains("Notification") || api.contains("AccessibilityService")) cat = "通知与无障碍";
            else if (api.contains("System.getProperty") || api.contains("Debug.isDebuggerConnected")) cat = "反模拟器/反调试";
            else cat = "其他";
            apiCategories.computeIfAbsent(cat, k -> new ArrayList<>()).add(api);
        }
        for (Map.Entry<String, List<String>> entry : apiCategories.entrySet()) {
            sb.append("### ").append(entry.getKey()).append(":\n");
            for (String api : entry.getValue()) {
                sb.append("- ").append(api).append("\n");
            }
        }

        // 域名
        sb.append("\n## 提取的网络域名 (").append(domains.size()).append(" 个)\n");
        for (String d : domains) {
            String tld = d.contains(".") ? d.substring(d.lastIndexOf('.')) : "";
            boolean suspicious = tld.matches("\\.(?:xyz|top|tk|ml|ga|cf|gq|pw|cc|ws|bid|win|loan)");
            sb.append("- ").append(d).append(suspicious ? " ⚠️ 可疑TLD" : "").append("\n");
        }

        // 原生库
        @SuppressWarnings("unchecked")
        List<String> nativeLibs = (List<String>) features.getOrDefault("nativeLibraries", List.of());
        if (!nativeLibs.isEmpty()) {
            sb.append("\n## 原生库 (").append(nativeLibs.size()).append(" 个)\n");
            for (String lib : nativeLibs) {
                sb.append("- ").append(lib).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildApkStage3Context(Map<String, Object> detection, List<Map<String, Object>> secrets,
                                          List<Map<String, Object>> owasp, Map<String, Object> packerInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("【深度检测结果 — 第3阶段 AI 分析输入】\n\n");

        sb.append("## 恶意行为检测\n");
        sb.append("- 判定: ").append(detection.getOrDefault("verdict", "N/A")).append("\n");
        sb.append("- 风险评分: ").append(detection.getOrDefault("riskScore", 0)).append("/100\n");
        sb.append("- 恶意类型: ").append(detection.getOrDefault("malwareType", "未知")).append("\n");
        sb.append("- 置信度: ").append(detection.getOrDefault("confidence", 0)).append("\n");
        sb.append("- 摘要: ").append(detection.getOrDefault("summary", "N/A")).append("\n");

        // 规则匹配详情
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ruleMatches = (List<Map<String, Object>>) detection.get("ruleMatches");
        if (ruleMatches != null && !ruleMatches.isEmpty()) {
            sb.append("\n### 规则匹配详情:\n");
            for (Map<String, Object> rule : ruleMatches) {
                sb.append("- [").append(rule.get("severity")).append("] ").append(rule.get("name"))
                        .append(": ").append(rule.get("description")).append("\n");
            }
        }

        // 行为列表
        @SuppressWarnings("unchecked")
        List<String> behaviors = (List<String>) detection.get("behaviors");
        if (behaviors != null && !behaviors.isEmpty()) {
            sb.append("\n### 推断的恶意行为:\n");
            for (String b : behaviors) {
                sb.append("- ").append(b).append("\n");
            }
        }

        // IOC
        @SuppressWarnings("unchecked")
        Map<String, Object> iocs = (Map<String, Object>) detection.get("iocs");
        if (iocs != null) {
            sb.append("\n### 已提取 IOC:\n");
            @SuppressWarnings("unchecked")
            List<String> iocPerms = (List<String>) iocs.get("permissions");
            if (iocPerms != null && !iocPerms.isEmpty()) {
                sb.append("- 恶意权限: ").append(String.join(", ", iocPerms)).append("\n");
            }
            @SuppressWarnings("unchecked")
            List<String> iocDomains = (List<String>) iocs.get("domains");
            if (iocDomains != null && !iocDomains.isEmpty()) {
                sb.append("- IOC域名: ").append(String.join(", ", iocDomains)).append("\n");
            }
        }

        // 加壳检测
        sb.append("\n## 加壳/加固检测\n");
        sb.append("- 是否加壳: ").append(packerInfo.getOrDefault("isPacked", false)).append("\n");
        if (Boolean.TRUE.equals(packerInfo.get("isPacked"))) {
            sb.append("- 加固方案: ").append(packerInfo.getOrDefault("packer", "未知")).append("\n");
            @SuppressWarnings("unchecked")
            List<String> evidence = (List<String>) packerInfo.get("evidence");
            if (evidence != null) {
                sb.append("- 检测证据:\n");
                for (String e : evidence) {
                    sb.append("  * ").append(e).append("\n");
                }
            }
            sb.append("- 技术影响: ").append(packerInfo.getOrDefault("impact", "N/A")).append("\n");
        }

        // 敏感凭据
        sb.append("\n## 敏感凭据扫描 (").append(secrets.size()).append(" 处)\n");
        for (Map<String, Object> s : secrets) {
            sb.append("- 类型: ").append(s.get("type"))
                    .append(" | 值(脱敏): ").append(s.get("value"))
                    .append(" | 熵值: ").append(s.get("entropy")).append("\n");
        }

        // OWASP 合规
        sb.append("\n## OWASP Mobile Top 10 合规审计 (").append(owasp.size()).append(" 条命中)\n");
        for (Map<String, Object> o : owasp) {
            sb.append("- [").append(o.get("severity")).append("] ")
                    .append(o.get("category")).append("\n")
                    .append("  描述: ").append(o.get("description")).append("\n");
        }

        return sb.toString();
    }

    private String buildApkStage4Context(Map<String, Object> apkInfo, Map<String, Object> features,
                                          List<Map<String, Object>> permissions, List<String> domains,
                                          Map<String, Object> detection, List<Map<String, Object>> secrets,
                                          List<Map<String, Object>> owasp, Map<String, Object> packerInfo) {
        return buildApkStage1Context(apkInfo, (String) apkInfo.getOrDefault("fileName", "unknown.apk"),
                (int) apkInfo.getOrDefault("fileSize", 0)) + "\n" +
                buildApkStage2Context(features, permissions, domains) + "\n" +
                buildApkStage3Context(detection, secrets, owasp, packerInfo);
    }

    private String buildSoStage2Context(Map<String, Object> soInfo, List<Map<String, Object>> functions,
                                         List<Map<String, Object>> algorithms) {
        StringBuilder sb = new StringBuilder();
        sb.append("【SO 特征数据 — 第2阶段 AI 分析输入】\n\n");

        sb.append("## 基本信息\n");
        sb.append("- 架构: ").append(soInfo.get("architecture")).append("\n");
        sb.append("- 位宽: ").append(soInfo.getOrDefault("bitClass", "N/A")).append("\n");
        sb.append("- 端序: ").append(soInfo.getOrDefault("endianness", "N/A")).append("\n");
        sb.append("- ELF类型: ").append(soInfo.getOrDefault("type", "N/A")).append("\n");
        sb.append("- 入口点: ").append(soInfo.getOrDefault("entryPoint", "N/A")).append("\n");
        sb.append("- 段数量: ").append(soInfo.getOrDefault("sectionCount", 0)).append("\n\n");

        // 按类别分组的函数
        Map<String, List<Map<String, Object>>> funcByCategory = new LinkedHashMap<>();
        for (Map<String, Object> f : functions) {
            String cat = (String) f.getOrDefault("category", "Other");
            funcByCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(f);
        }

        sb.append("## 导出函数分析 (").append(functions.size()).append(" 个)\n");
        for (Map.Entry<String, List<Map<String, Object>>> entry : funcByCategory.entrySet()) {
            sb.append("\n### ").append(entry.getKey()).append(" (").append(entry.getValue().size()).append(" 个):\n");
            for (Map<String, Object> f : entry.getValue()) {
                sb.append("- ").append(f.get("name")).append(" @ ").append(f.get("address"))
                        .append(" (size=").append(f.getOrDefault("size", 0)).append(")")
                        .append(" [").append(f.getOrDefault("binding", "LOCAL")).append("]");
                String purpose = (String) f.getOrDefault("purpose", "");
                if (!purpose.isEmpty()) sb.append(" — ").append(purpose);
                sb.append("\n");
            }
        }

        sb.append("\n## 加密算法识别 (").append(algorithms.size()).append(" 种)\n");
        for (Map<String, Object> a : algorithms) {
            sb.append("- **").append(a.get("name")).append("**: ").append(a.get("function"))
                    .append(" @ ").append(a.get("address"))
                    .append(" | 置信度: ").append(a.get("confidence"))
                    .append(" | 匹配方式: ").append(a.getOrDefault("matchType", ""))
                    .append(" | 描述: ").append(a.getOrDefault("description", "")).append("\n");
        }

        @SuppressWarnings("unchecked")
        List<String> deps = (List<String>) soInfo.getOrDefault("dependencies", List.of());
        if (!deps.isEmpty()) {
            sb.append("\n## 依赖库 (").append(deps.size()).append(" 个)\n");
            for (String dep : deps) {
                sb.append("- ").append(dep).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildSoStage3Context(Map<String, Object> soInfo, List<Map<String, Object>> functions,
                                         List<Map<String, Object>> algorithms, Map<String, Object> obfuscation,
                                         List<Map<String, Object>> strings) {
        StringBuilder sb = new StringBuilder(buildSoStage2Context(soInfo, functions, algorithms));

        sb.append("\n## 混淆检测结果\n");
        sb.append("- 符号混淆: ").append(obfuscation.getOrDefault("symbolObfuscation", false))
                .append(" (混淆率: ").append(obfuscation.getOrDefault("symbolObfuscationRatio", "N/A")).append(")\n");
        sb.append("- 字符串加密: ").append(obfuscation.getOrDefault("stringEncryption", false))
                .append(" (熵值: ").append(obfuscation.getOrDefault("entropy", "N/A")).append(")\n");
        sb.append("- OLLVM控制流平坦化: ").append(obfuscation.getOrDefault("ollvm", false)).append("\n");
        sb.append("- 控制流混淆: ").append(obfuscation.getOrDefault("controlFlowFlattening", false)).append("\n");

        // 混淆详情
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) obfuscation.get("details");
        if (details != null) {
            sb.append("\n### 混淆详情:\n");
            if (details.containsKey("symbol")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sym = (Map<String, Object>) details.get("symbol");
                sb.append("- 符号混淆率: ").append(sym.getOrDefault("ratio", "N/A"))
                        .append(" (").append(sym.getOrDefault("count", 0)).append(" 个混淆函数)\n");
            }
            if (details.containsKey("string")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> str = (Map<String, Object>) details.get("string");
                sb.append("- 数据段熵值: ").append(str.getOrDefault("entropy", "N/A")).append("\n");
                sb.append("- 评估: ").append(str.getOrDefault("msg", "")).append("\n");
            }
            if (details.containsKey("ollvm")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ol = (Map<String, Object>) details.get("ollvm");
                sb.append("- 分支跳转指令数: ").append(ol.getOrDefault("branchCount", 0)).append("\n");
                sb.append("- 评估: ").append(ol.getOrDefault("msg", "")).append("\n");
            }
        }

        // 去混淆指南
        String deobfuscationGuide = (String) obfuscation.get("deobfuscationGuide");
        if (deobfuscationGuide != null && !deobfuscationGuide.isEmpty()) {
            sb.append("\n### 已有去混淆指引:\n").append(deobfuscationGuide).append("\n");
        }

        sb.append("\n## 敏感字符串 (").append(strings.size()).append(" 条)\n");
        // 按类别分组
        Map<String, List<String>> strByCategory = new LinkedHashMap<>();
        for (Map<String, Object> s : strings) {
            String cat = (String) s.getOrDefault("category", "Other");
            strByCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add((String) s.get("value"));
        }
        for (Map.Entry<String, List<String>> entry : strByCategory.entrySet()) {
            sb.append("\n### ").append(entry.getKey()).append(":\n");
            for (String val : entry.getValue()) {
                sb.append("- ").append(val).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildProtocolStage2Context(Map<String, Object> protocol, Map<String, Object> stats,
                                               Map<String, Object> encryption, Map<String, Object> dataFormat) {
        StringBuilder sb = new StringBuilder();
        sb.append("【协议深度审计数据 — 第2阶段 AI 分析输入】\n\n");

        sb.append("## 协议识别\n");
        sb.append("- 类型: ").append(protocol.getOrDefault("type", "Unknown")).append("\n");
        sb.append("- 传输层: ").append(protocol.getOrDefault("transport", "N/A")).append("\n");
        sb.append("- 端口: ").append(protocol.getOrDefault("port", "N/A")).append("\n");
        @SuppressWarnings("unchecked")
        List<String> detectedProtocols = (List<String>) protocol.getOrDefault("detectedProtocols", List.of());
        sb.append("- 检测到的协议: ").append(String.join(", ", detectedProtocols)).append("\n");

        sb.append("\n## 通信统计\n");
        sb.append("- 总包数: ").append(stats.getOrDefault("totalPackets", 0)).append("\n");
        sb.append("- 总字节: ").append(stats.getOrDefault("totalBytes", 0)).append("\n");
        sb.append("- 总时长: ").append(stats.getOrDefault("duration", "N/A")).append(" 秒\n");
        sb.append("- 独立端点: ").append(stats.getOrDefault("uniqueEndpoints", 0)).append("\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) stats.get("endpoints");
        if (endpoints != null && !endpoints.isEmpty()) {
            sb.append("\n### 通信端点:\n");
            for (Map<String, Object> ep : endpoints) {
                sb.append("- ").append(ep.get("srcIp")).append(":").append(ep.get("srcPort"))
                        .append(" <-> ").append(ep.get("dstIp")).append(":").append(ep.get("dstPort"))
                        .append(" | 包: ").append(ep.get("packets"))
                        .append(" | 字节: ").append(ep.get("bytes")).append("\n");
            }
        }

        sb.append("\n## 加密分析\n");
        sb.append("- 是否TLS: ").append(encryption.getOrDefault("hasTls", false)).append("\n");
        sb.append("- Payload熵值: ").append(encryption.getOrDefault("payloadEntropy", "N/A")).append("\n");
        sb.append("- 疑似加密: ").append(encryption.getOrDefault("likelyEncrypted", false)).append("\n");
        sb.append("- 加密类型: ").append(encryption.getOrDefault("type", "N/A")).append("\n");
        sb.append("- 密钥交换: ").append(encryption.getOrDefault("keyExchange", "N/A")).append("\n");
        sb.append("- 安全评估: ").append(encryption.getOrDefault("assessment", "N/A")).append("\n");
        String tlsDetails = (String) encryption.getOrDefault("tlsDetails", "");
        if (!tlsDetails.isEmpty()) {
            sb.append("- TLS详情: ").append(tlsDetails).append("\n");
        }

        sb.append("\n## 数据格式分析\n");
        sb.append("- 格式: ").append(dataFormat.getOrDefault("format", "N/A")).append("\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> leaks = (List<Map<String, Object>>) dataFormat.getOrDefault("plaintextLeaks", List.of());
        sb.append("\n### 明文泄露审计 (").append(leaks.size()).append(" 处):\n");
        for (Map<String, Object> l : leaks) {
            sb.append("- 字段: **").append(l.get("field")).append("**")
                    .append(" | 脱敏值: `").append(l.get("value")).append("`")
                    .append(" | 包序号: ").append(l.get("packetIndex"))
                    .append(" | 上下文: ").append(l.get("snippet")).append("\n");
        }

        String protoSchema = (String) dataFormat.getOrDefault("protoSchema", "");
        if (protoSchema != null && !protoSchema.isEmpty()) {
            sb.append("\n### 自动重构的协议Schema:\n```protobuf\n").append(protoSchema).append("\n```\n");
        }

        @SuppressWarnings("unchecked")
        List<String> examples = (List<String>) dataFormat.getOrDefault("examples", List.of());
        if (examples != null && !examples.isEmpty()) {
            sb.append("\n### Payload 十六进制示例:\n```\n");
            for (String e : examples) {
                sb.append(e).append("\n\n");
            }
            sb.append("```\n");
        }

        return sb.toString();
    }

    // ========== 增强上下文构建器 ==========

    private String buildApkStage2EnhancedContext(Map<String, Object> webViewSecurity, Map<String, Object> sslBypass,
                                                  List<Map<String, Object>> exportedComponents, Map<String, Object> reflection) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## WebView 安全分析\n");
        sb.append("- 是否使用 addJavascriptInterface: ").append(webViewSecurity.getOrDefault("hasJavascriptInterface", false)).append("\n");
        sb.append("- 是否允许文件访问: ").append(webViewSecurity.getOrDefault("hasFileAccess", false)).append("\n");
        sb.append("- 是否允许 file://URL 跨域: ").append(webViewSecurity.getOrDefault("hasUniversalAccessFromFileURLs", false)).append("\n");
        @SuppressWarnings("unchecked")
        List<String> risks = (List<String>) webViewSecurity.get("risks");
        if (risks != null && !risks.isEmpty()) {
            sb.append("- 风险详情:\n");
            for (String r : risks) { sb.append("  * ").append(r).append("\n"); }
        }

        sb.append("\n## SSL/TLS 证书验证绕过检测\n");
        sb.append("- 自定义 TrustManager (接受所有证书): ").append(sslBypass.getOrDefault("hasCustomTrustManager", false)).append("\n");
        sb.append("- HostnameVerifier 绕过: ").append(sslBypass.getOrDefault("hasHostnameVerifierBypass", false)).append("\n");
        @SuppressWarnings("unchecked")
        List<String> details = (List<String>) sslBypass.get("details");
        if (details != null && !details.isEmpty()) {
            sb.append("- 详情:\n");
            for (String d : details) { sb.append("  * ").append(d).append("\n"); }
        }

        sb.append("\n## 组件导出风险 (").append(exportedComponents.size()).append(" 个)\n");
        for (Map<String, Object> c : exportedComponents) {
            sb.append("- [").append(c.getOrDefault("risk", "N/A")).append("] ")
                    .append(c.getOrDefault("type", "N/A")).append(": ")
                    .append(c.getOrDefault("name", "N/A"))
                    .append(" | 显式导出: ").append(c.getOrDefault("exported", false))
                    .append(" | Intent Filter: ").append(c.getOrDefault("hasIntentFilter", false)).append("\n");
        }

        sb.append("\n## 反射与动态加载分析\n");
        sb.append("- 反射调用次数: ").append(reflection.getOrDefault("reflectionCount", 0)).append("\n");
        sb.append("- 动态加载调用次数: ").append(reflection.getOrDefault("dynamicLoadCount", 0)).append("\n");
        @SuppressWarnings("unchecked")
        List<String> refCalls = (List<String>) reflection.get("reflectionCalls");
        if (refCalls != null && !refCalls.isEmpty()) {
            sb.append("- 反射调用详情: ").append(String.join(", ", refCalls)).append("\n");
        }
        @SuppressWarnings("unchecked")
        List<String> dynCalls = (List<String>) reflection.get("dynamicLoadCalls");
        if (dynCalls != null && !dynCalls.isEmpty()) {
            sb.append("- 动态加载详情: ").append(String.join(", ", dynCalls)).append("\n");
        }

        return sb.toString();
    }

    private String buildApkStage3EnhancedContext(Map<String, Object> webViewSecurity, Map<String, Object> sslBypass,
                                                  List<Map<String, Object>> exportedComponents, Map<String, Object> reflection,
                                                  Map<String, Object> antiEmulator, Map<String, Object> dexObfuscation) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 反模拟器/反沙箱检测\n");
        sb.append("- 有反模拟特征: ").append(antiEmulator.getOrDefault("hasAntiEmulation", false)).append("\n");
        sb.append("- 检测指标数量: ").append(antiEmulator.getOrDefault("indicatorCount", 0)).append("\n");
        @SuppressWarnings("unchecked")
        List<String> indicators = (List<String>) antiEmulator.get("indicators");
        if (indicators != null && !indicators.isEmpty()) {
            sb.append("- 技术列表:\n");
            for (String ind : indicators) {
                sb.append("  * ").append(ind).append("\n");
            }
        }

        sb.append("\n## DEX 混淆评估\n");
        sb.append("- 混淆级别: ").append(dexObfuscation.getOrDefault("level", "N/A")).append("\n");
        sb.append("- DEX 文件数: ").append(dexObfuscation.getOrDefault("dexCount", 0)).append("\n");
        sb.append("- 单字符类名混淆: ").append(dexObfuscation.getOrDefault("hasSingleCharClassNames", false)).append("\n");
        sb.append("- ProGuard/R8混淆: ").append(dexObfuscation.getOrDefault("proguardObfuscated", false)).append("\n");
        sb.append("- DexGuard商业混淆器: ").append(dexObfuscation.getOrDefault("dexguardObfuscated", false)).append("\n");
        sb.append("- 描述: ").append(dexObfuscation.getOrDefault("description", "N/A")).append("\n");

        return sb.toString();
    }

    private String buildSoStage1Context(Map<String, Object> soInfo, Map<String, Object> elfSecurity, int fileSize, String filename) {
        StringBuilder sb = new StringBuilder();
        sb.append("【ELF 结构安全审计数据 — 第1阶段 AI 分析输入】\n\n");
        sb.append("文件名: ").append(filename).append("\n");
        sb.append("文件大小: ").append(fileSize).append(" bytes\n");
        sb.append("架构: ").append(soInfo.get("architecture")).append("\n");
        sb.append("位宽: ").append(soInfo.getOrDefault("bitClass", "N/A")).append("\n");
        sb.append("端序: ").append(soInfo.getOrDefault("endianness", "N/A")).append("\n");
        sb.append("ELF类型: ").append(soInfo.getOrDefault("type", "N/A")).append("\n");
        sb.append("入口点: ").append(soInfo.getOrDefault("entryPoint", "N/A")).append("\n");
        sb.append("段数量: ").append(soInfo.getOrDefault("sectionCount", 0)).append("\n");
        @SuppressWarnings("unchecked")
        List<String> sectionNames = (List<String>) soInfo.get("sections");
        if (sectionNames != null && !sectionNames.isEmpty()) {
            sb.append("段列表: ").append(String.join(", ", sectionNames)).append("\n");
        }
        sb.append("\n");

        sb.append("## ELF 安全属性\n");
        sb.append("- PIE (位置无关代码): ").append(elfSecurity.getOrDefault("pie", "N/A")).append("\n");
        sb.append("- RELRO (只读重定位): ").append(elfSecurity.getOrDefault("relro", "N/A")).append("\n");
        sb.append("- NX (不可执行栈): ").append(elfSecurity.getOrDefault("nx", "N/A")).append("\n");
        sb.append("- Stack Canary: ").append(elfSecurity.getOrDefault("stackCanary", "N/A")).append("\n");
        sb.append("- W^X: ").append(elfSecurity.getOrDefault("wx", "N/A")).append("\n");
        sb.append("- 安全评分: ").append(elfSecurity.getOrDefault("securityScore", "N/A")).append("/100\n");

        @SuppressWarnings("unchecked")
        List<String> issues = (List<String>) elfSecurity.get("issues");
        if (issues != null && !issues.isEmpty()) {
            sb.append("\n### 安全问题:\n");
            for (String issue : issues) {
                sb.append("- ").append(issue).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildProtocolStage1Context(Map<String, Object> protocol, Map<String, Object> basicInfo,
                                               List<Map<String, Object>> dnsQueries, List<Map<String, Object>> httpRequests,
                                               List<Map<String, Object>> tlsCerts, int fileSize) {
        StringBuilder sb = new StringBuilder();
        sb.append("【通信拓扑分析数据 — 第1阶段 AI 分析输入】\n\n");

        sb.append("## 基本信息\n");
        sb.append("- 文件大小: ").append(fileSize).append(" bytes\n");
        sb.append("- 有效PCAP: ").append(basicInfo.getOrDefault("validPcap", false)).append("\n\n");

        sb.append("## 协议识别\n");
        sb.append("- 类型: ").append(protocol.getOrDefault("type", "Unknown")).append("\n");
        sb.append("- 传输层: ").append(protocol.getOrDefault("transport", "N/A")).append("\n");
        sb.append("- 端口: ").append(protocol.getOrDefault("port", "N/A")).append("\n");
        @SuppressWarnings("unchecked")
        List<String> detectedProtocols = (List<String>) protocol.getOrDefault("detectedProtocols", List.of());
        sb.append("- 检测到的协议: ").append(String.join(", ", detectedProtocols)).append("\n");

        sb.append("\n## DNS 查询 (").append(dnsQueries.size()).append(" 条)\n");
        for (Map<String, Object> d : dnsQueries) {
            sb.append("- ").append(d.getOrDefault("queryName", "N/A"))
                    .append(" (类型: ").append(d.getOrDefault("queryType", "N/A")).append(")")
                    .append(" → ").append(d.getOrDefault("aRecord", "N/A")).append("\n");
        }

        sb.append("\n## HTTP 请求 (").append(httpRequests.size()).append(" 条)\n");
        for (Map<String, Object> h : httpRequests) {
            sb.append("- ").append(h.getOrDefault("method", "N/A"))
                    .append(" ").append(h.getOrDefault("uri", "N/A"))
                    .append(" | Host: ").append(h.getOrDefault("host", "N/A"))
                    .append(" | User-Agent: ").append(h.getOrDefault("userAgent", "N/A")).append("\n");
        }

        sb.append("\n## TLS 证书 (").append(tlsCerts.size()).append(" 个)\n");
        for (Map<String, Object> c : tlsCerts) {
            sb.append("- CN: ").append(c.getOrDefault("commonName", "N/A"))
                    .append(" | O: ").append(c.getOrDefault("organization", "N/A"))
                    .append(" | SAN: ").append(c.getOrDefault("subjectAltNames", "N/A"))
                    .append(" | 证书大小: ").append(c.getOrDefault("certSize", "N/A")).append("\n");
        }

        return sb.toString();
    }
}
