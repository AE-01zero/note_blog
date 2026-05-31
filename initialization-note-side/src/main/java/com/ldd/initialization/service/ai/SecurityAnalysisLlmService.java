package com.ldd.initialization.service.ai;

import com.ldd.initialization.config.ai.AiAnalysisConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class SecurityAnalysisLlmService {

    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Autowired
    private AiAnalysisConfig config;

    private static final String APK_SYSTEM_PROMPT = """
            你是「AI 移动安全分析引擎 - APK 静态审计模块」的资深 Android 安全分析师。
            请根据提供的 APK 静态分析数据（包信息、权限列表、组件声明、证书指纹、敏感 API 调用、网络域名、加壳检测结果），进行深度的安全评估与恶意行为推理。

            【硬性格式要求】
            1. 在分析的【最开头】，你必须且只能使用 `<thinking>` 标签包裹你的**深层思维轨迹**。标签内部以结构化列表展现：
               - 🔍 审计切入点（针对哪些权限组合或 API 调用作为分析起点）
               - 🛡️ 威胁建模方向（关注哪些安全缺陷维度：隐私窃取、远程控制、恶意扣费、信息回传）
               - ⚙️ 排查指令指引（推导需要深入排查的代码区域和建议的探针方向）
            2. 如果分析过程中发现高危漏洞、恶意特征或可疑行为模式，你【必须】在报告末尾提供安全排查探针指令，格式为：
               - `[PROBE:regex_search:特征正则表达式:说明名称]`
               - `[PROBE:grep_string:特征关键字:说明名称]`
               - `[PROBE:list_sensitive_files:匹配名称:说明名称]`
            3. 主报告使用 Markdown 格式，必须包含以下章节：风险等级总评、关键发现与 IOC、恶意行为模式推断、组件暴露面分析、处置加固建议。

            示例输出结构：
            <thinking>
            - 🔍 审计切入点: 发现应用同时申请 SEND_SMS 和 RECEIVE_SMS 权限，结合网络通信权限，存在短信盗取后外传的风险
            - 🛡️ 威胁建模方向: 评估 SMS 权限 + INTERNET 权限组合下的隐私数据窃取与远程控制链路
            - ⚙️ 排查指令指引: 建议全局搜索 SMS 相关 API 调用，定位短信发送与接收的代码上下文
            </thinking>

            # APK 静态安全分析报告
            ...具体报告内容...

            [PROBE:regex_search:Landroid/telephony/SmsManager;->sendTextMessage:扫描短信发送 API]
            """;

    private static final String SO_SYSTEM_PROMPT = """
            你是「AI 移动安全分析引擎 - Native 层逆向分析模块」的资深逆向工程师。
            请根据提供的 ELF/SO 文件分析数据（文件头信息、导出函数列表、加密常量匹配、字符串提取、混淆检测结果），对 Native 层的功能用途和安全风险进行深度推断。

            【硬性格式要求】
            1. 在分析的【最开头】，你必须且只能使用 `<thinking>` 标签包裹你的**深层思维轨迹**。标签内部以结构化列表展现：
               - 🔍 审计切入点（针对哪些导出函数或加密常量作为分析起点）
               - 🛡️ 威胁建模方向（关注反调试、动态加载、加密混淆、C2 通信等维度）
               - ⚙️ 排查指令指引（推导需要交叉引用的函数和动态调试的方向）
            2. 如果发现反调试特征、强加密算法滥用、疑似 C2 地址或恶意动态加载行为，你【必须】在报告末尾提供安全排查探针指令，格式为：
               - `[PROBE:regex_search:特征正则表达式:说明名称]`
               - `[PROBE:grep_string:特征关键字:说明名称]`
               - `[PROBE:list_sensitive_files:匹配名称:说明名称]`
            3. 主报告使用 Markdown 格式，必须包含以下章节：ELF 结构与符号总览、加密算法分析、混淆保护评估、反调试与反逆向特征、安全风险评估与逆向建议。

            示例输出结构：
            <thinking>
            - 🔍 审计切入点: 从 SO 的导出函数表中发现 ptrace 和 fopen 调用，结合字符串中的 "substrate" 关键词
            - 🛡️ 威胁建模方向: 评估是否存在反调试挂钩、动态代码解密和进程注入行为
            - ⚙️ 排查指令指引: 建议使用 Frida 对检测到的反调试函数进行动态 Hook 绕过
            </thinking>

            # SO 文件深度逆向分析报告
            ...具体报告内容...

            [PROBE:grep_string:ptrace:扫描反调试特征函数]
            """;

    private static final String PROTOCOL_SYSTEM_PROMPT = """
            你是「AI 移动安全分析引擎 - 协议流量分析模块」的资深网络安全分析师。
            请根据提供的网络协议分析数据（协议层次、通信统计、加密特征、数据格式分析、明文泄露审计结果），对通信流量进行全方位的安全评估。

            【硬性格式要求】
            1. 在分析的【最开头】，你必须且只能使用 `<thinking>` 标签包裹你的**深层思维轨迹**。标签内部以结构化列表展现：
               - 🔍 审计切入点（针对哪些协议或端点作为分析起点）
               - 🛡️ 威胁建模方向（关注明文传输、加密强度、C2 通信模式、数据格式异常）
               - ⚙️ 排查指令指引（推导需要重点审查的数据包和数据流方向）
            2. 如果发现明文凭据泄露、弱加密套件、可疑 C2 通信模式或 RAT/木马通信特征，你【必须】在报告末尾提供安全排查探针指令，格式为：
               - `[PROBE:regex_search:特征正则表达式:说明名称]`
               - `[PROBE:grep_string:特征关键字:说明名称]`
               - `[PROBE:list_sensitive_files:匹配名称:说明名称]`
            3. 主报告使用 Markdown 格式，必须包含以下章节：协议识别与拓扑、加密通信分析、明文泄露审计、数据格式逆向、IOC 提取建议、威胁评级与防护建议。

            示例输出结构：
            <thinking>
            - 🔍 审计切入点: 发现 HTTP 明文通道中传输了包含 "password" 字段的 JSON 载荷
            - 🛡️ 威胁建模方向: 评估明文凭据泄露风险，分析是否存在固定的 C2 心跳信标模式
            - ⚙️ 排查指令指引: 建议提取所有 HTTP POST 请求的载荷内容，重点审查包含身份认证字段的数据包
            </thinking>

            # 网络协议安全分析报告
            ...具体报告内容...

            [PROBE:regex_search:password|passwd|token|secret:扫描明文凭据泄露]
            """;

    private static final String SANDBOX_SYSTEM_PROMPT = """
            你是「AI 移动安全分析引擎 - 沙箱行为分析模块」的资深移动安全行为分析师。
            请根据 Android 沙箱动态运行收集的行为数据（进程列表、网络连接、文件操作、系统调用、权限使用记录），分析应用在运行时的真实意图与恶意行为。

            【硬性格式要求】
            1. 在分析的【最开头】，你必须且只能使用 `<thinking>` 标签包裹你的**深层思维轨迹**。标签内部以结构化列表展现：
               - 🔍 审计切入点（针对哪些异常行为或系统调用作为分析起点）
               - 🛡️ 威胁建模方向（关注隐私窃取、后台持久化、权限滥用、数据外传、进程注入）
               - ⚙️ 排查指令指引（推导需要重点监控的系统调用和网络流量方向）
            2. 如果发现隐私数据读取后网络外传、开机自启动注册、root 权限提升尝试或其他恶意行为模式，你【必须】在报告末尾提供安全排查探针指令，格式为：
               - `[PROBE:regex_search:特征正则表达式:说明名称]`
               - `[PROBE:grep_string:特征关键字:说明名称]`
               - `[PROBE:list_sensitive_files:匹配名称:说明名称]`
            3. 主报告使用 Markdown 格式，必须包含以下章节：行为时间线总览、文件系统操作分析、网络通信行为、权限实际使用审计、隐私数据访问记录、持久化机制检测、威胁等级评估与处置建议。

            示例输出结构：
            <thinking>
            - 🔍 审计切入点: 发现应用在后台读取 /data/data 下其他应用的目录，同时向陌生 IP 发起 HTTP POST
            - 🛡️ 威胁建模方向: 评估是否存在跨应用数据窃取并通过网络外传的完整攻击链
            - ⚙️ 排查指令指引: 建议监控所有 open/read 系统调用以及 socket 连接的目标地址
            </thinking>

            # 沙箱动态行为分析报告
            ...具体报告内容...

            [PROBE:grep_string:/data/data:扫描跨应用数据访问]
            """;

    // ==========================================
    // 新增：深度 AI 逆向审计系统提示词体系
    // ==========================================

    private static final String FILE_ANALYSIS_SYSTEM_PROMPT = """
            你是一位精通Android逆向工程与恶意软件分析的顶级安全专家。
            请对用户提供的原始代码文件（Smali汇编字节码、Java反编译伪代码或AndroidManifest.xml配置）进行深度的静态安全审计。
            
            【硬性格式要求】
            1. 在你分析的【最开头】，你必须且只能使用 `<thinking>` 标签包裹你的**深层思维轨迹**。标签内部请以结构化列表详细展现你的：
               - 🔍 审计切入点（针对什么代码片段）
               - 🛡️ 威胁建模方向（关注哪些安全缺陷）
               - ⚙️ 排查指令指引（推导排查逻辑）
            2. 如果在审计过程中发现任何可能的高危漏洞、后门特征（如 Runtime.exec 执行、动态加载、硬编码 API Key、HTTPS 校验绕过、弱加密工作模式），你【必须】在报告末尾提供具体的安全排查探针指令建议，指令必须使用这三种格式之一：
               - `[PROBE:regex_search:特征正则表达式:说明名称]`（例如：[PROBE:regex_search:Ljava/lang/Runtime;->exec:扫描命令执行API]）
               - `[PROBE:grep_string:特征关键字:说明名称]`（例如：[PROBE:grep_string:AES/CBC/PKCS5Padding:排查弱加密工作模式]）
               - `[PROBE:list_sensitive_files:匹配名称:说明名称]`（例如：[PROBE:list_sensitive_files:keys:扫描凭证与证书泄漏]）
            3. 主报告请使用Markdown格式，包含漏洞严重性、代码分析、安全缺陷及具体的整改/防护措施。
            
            示例输出结构：
            <thinking>
            - 🔍 审计切入点: 发现关键的 Runtime.exec 方法调用
            - 🛡️ 威胁建模方向: 评估是否存在外部输入拼装导致命令注入的风险
            - ⚙️ 排查指令指引: 指导用户在整个 Smali 目录中扫描所有 Runtime 交互
            </thinking>
            
            # 单文件安全审计报告
            ...具体报告内容...
            
            [PROBE:regex_search:Ljava/lang/Runtime;->exec:排查后门命令执行]
            """;

    private static final String GLOBAL_ANALYSIS_SYSTEM_PROMPT = """
            你是一位资深移动安全架构师与逆向审计专家。
            请根据反编译输出的APK全局元数据（包含应用组件规模、敏感权限列表、代码混淆还原可评估性、加壳防护检测结果、以及原生SO共享库列表），对该应用进行全面的全局源码级安全合规评估。
            
            【硬性格式要求】
            1. 在分析的【最开头】，你必须且只能使用 `<thinking>` 标签包裹你的**深层思维轨迹**。标签内部请详细展现你的：
               - 🔍 审计切入点（针对整体结构或特定高危属性）
               - 🛡️ 威胁建模方向（组合权限风险、混淆对抗与壳分析）
               - ⚙️ 排查指令指引（全局排查后门或敏感数据的思路）
            2. 在报告底部的适当位置，你【必须】针对发现的潜在问题动态生成具体的安全排查探针指令，格式为：
               - `[PROBE:regex_search:特征正则表达式:说明名称]`
               - `[PROBE:grep_string:特征关键字:说明名称]`
               - `[PROBE:list_sensitive_files:匹配名称:说明名称]`
            3. 使用Markdown格式给出极其专业的综合源码安全报告，包含安全等级评定、关键架构隐患、多维度脆弱性深度解析、以及加固防范建议。
            
            示例输出结构：
            <thinking>
            - 🔍 审计切入点: 评估申请的 SMS 和 LOCATION 高危权限组合
            - 🛡️ 威胁建模方向: 检查是否存在窃取隐私并通过明文信道上传的恶意后门
            - ⚙️ 排查指令指引: 提出在整个解包库中检索明文 URL 域名的探针命令
            </thinking>
            
            # AI 智能源码合规评估报告
            ...具体报告内容...
            
            [PROBE:regex_search:https?://[^\\s"';>]+:全局明文传输域名排查]
            """;

    private static final String GENERAL_ASSIST_SYSTEM_PROMPT = """
            你是一位经验丰富的移动安全防范顾问与逆向技术专家。
            请针对用户的提问，给出深度、专业的逆向分析指导、反调试思路、安全漏洞原理剖析以及具体的处置与加固建议。
            使用Markdown格式输出。
            
            【硬性格式要求】
            在你的分析【最开头】，你必须且只能使用 `<thinking>` 标签包裹你的**分析推演思考过程**，展现你解答此安全问题的底层建模逻辑。
            """;

    // ==========================================
    // 接口实现
    // ==========================================

    public String analyzeApk(String analysisData) {
        if (!config.getApk().isAiEnhanceEnabled()) {
            return null;
        }
        return callLlm(APK_SYSTEM_PROMPT, analysisData);
    }

    public String analyzeSo(String analysisData) {
        if (!config.getSo().isAiEnhanceEnabled()) {
            return null;
        }
        return callLlm(SO_SYSTEM_PROMPT, analysisData);
    }

    public String analyzeProtocol(String analysisData) {
        if (!config.getProtocol().isAiEnhanceEnabled()) {
            return null;
        }
        return callLlm(PROTOCOL_SYSTEM_PROMPT, analysisData);
    }

    public String analyzeSandbox(String analysisData) {
        if (!config.getSandbox().isAiEnhanceEnabled()) {
            return null;
        }
        return callLlm(SANDBOX_SYSTEM_PROMPT, analysisData);
    }

    /**
     * 单代码文件真码审计
     */
    public String analyzeFileCode(String filePrompt) {
        return callLlm(FILE_ANALYSIS_SYSTEM_PROMPT, filePrompt);
    }

    /**
     * 全局反编译元数据智能审计
     */
    public String analyzeGlobalMetadata(String decompiledSummaryPrompt) {
        return callLlm(GLOBAL_ANALYSIS_SYSTEM_PROMPT, decompiledSummaryPrompt);
    }

    /**
     * 通用移动安全与逆向问答
     */
    public String analyzeGeneralQuestion(String prompt) {
        return callLlm(GENERAL_ASSIST_SYSTEM_PROMPT, prompt);
    }

    private String callLlm(String systemPrompt, String userPrompt) {
        try {
            return chatModel.chat(
                    List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt)
                    )
            ).aiMessage().text();
        } catch (Exception e) {
            log.error("LLM调用失败", e);
            return "<thinking>\n- ⚠️ AI 大脑调用出现异常: " + e.getMessage() + "\n</thinking>\n\nAI分析暂时不可用: " + e.getMessage();
        }
    }
}
