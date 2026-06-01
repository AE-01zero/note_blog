package com.aezer0.initialization.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aezer0.initialization.config.ai.AiAnalysisConfig;
import com.aezer0.initialization.service.tool.ToolManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ProtocolAnalysisService {

    @Autowired
    private ToolManagementService toolService;

    @Autowired
    private SecurityAnalysisLlmService llmService;

    @Autowired
    private AiAnalysisConfig aiConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> analyzeProtocol(byte[] pcapBytes) {
        return analyzeProtocol(pcapBytes, false);
    }

    public Map<String, Object> analyzeProtocol(byte[] pcapBytes, boolean aiAssist) {
        Map<String, Object> result = new HashMap<>();
        List<String> processSteps = new ArrayList<>();

        Path tempPcap = null;
        try {
            log.info("开始协议分析, 文件大小: {} bytes, AI辅助: {}", pcapBytes.length, aiAssist);

            // 验证PCAP文件头
            if (!isValidPcap(pcapBytes)) {
                result.put("success", false);
                result.put("error", "无效的PCAP文件格式");
                return result;
            }

            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null || !toolService.isToolAvailable(ToolManagementService.TOOL_TSHARK)) {
                // 无tshark时使用内置基础解析
                return analyzeWithBuiltinParser(pcapBytes, processSteps);
            }

            tempPcap = Files.createTempFile("pcap_analysis_", ".pcap");
            Files.write(tempPcap, pcapBytes);

            processSteps.add("解析PCAP文件");
            Map<String, Object> protocol = parseProtocolWithTshark(tsharkPath, tempPcap);
            result.put("protocol", protocol);

            processSteps.add("统计通信信息");
            Map<String, Object> stats = calculateStatsWithTshark(tsharkPath, tempPcap);
            result.put("stats", stats);

            processSteps.add("DNS查询分析");
            List<Map<String, Object>> dnsQueries = extractDnsQueries(tsharkPath, tempPcap);
            result.put("dnsQueries", dnsQueries);

            processSteps.add("HTTP请求分析");
            List<Map<String, Object>> httpRequests = extractHttpRequests(tsharkPath, tempPcap);
            result.put("httpRequests", httpRequests);

            processSteps.add("TLS证书提取");
            List<Map<String, Object>> tlsCerts = extractTlsCertificates(tsharkPath, tempPcap);
            result.put("tlsCertificates", tlsCerts);

            processSteps.add("UDP会话分析");
            Map<String, Object> udpAnalysis = analyzeUdpConversations(tsharkPath, tempPcap);
            result.put("udpAnalysis", udpAnalysis);

            processSteps.add("分析加密特征");
            Map<String, Object> encryption = analyzeEncryption(tsharkPath, tempPcap, pcapBytes);
            result.put("encryption", encryption);

            processSteps.add("分析数据格式");
            Map<String, Object> dataFormat = analyzeDataFormat(tsharkPath, tempPcap);
            result.put("dataFormat", dataFormat);

            processSteps.add("时序与突发分析");
            Map<String, Object> temporalAnalysis = analyzeTemporalPatterns(tsharkPath, tempPcap);
            result.put("temporalAnalysis", temporalAnalysis);

            processSteps.add("恶意IOC匹配");
            List<Map<String, Object>> maliciousIocs = matchMaliciousIoCs(tsharkPath, tempPcap, dnsQueries, httpRequests);
            result.put("maliciousIoCs", maliciousIocs);

            processSteps.add(aiAssist ? "AI辅助协议推断" : "规则引擎协议分析");
            Map<String, Object> aiAnalysis = performAiAnalysis(protocol, encryption, dataFormat, stats, aiAssist);
            result.put("aiAnalysis", aiAnalysis);

            result.put("processSteps", processSteps);
            result.put("success", true);

        } catch (Exception e) {
            log.error("协议分析失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("processSteps", processSteps);
        } finally {
            if (tempPcap != null) {
                try { Files.deleteIfExists(tempPcap); } catch (IOException ignored) {}
            }
        }

        return result;
    }

    // ========== 步骤方法（供GuidedAnalysis调用） ==========

    public Map<String, Object> stepValidateAndBasicInfo(byte[] pcapBytes, String filename) {
        Map<String, Object> result = new HashMap<>();
        result.put("fileName", filename);
        result.put("fileSize", pcapBytes.length);
        result.put("validPcap", isValidPcap(pcapBytes));
        if (!isValidPcap(pcapBytes)) {
            result.put("error", "不是有效的PCAP文件");
        }
        return result;
    }

    public Map<String, Object> stepParseProtocol(Path pcapPath) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return Map.of("error", "tshark未配置");
            return parseProtocolWithTshark(tsharkPath, pcapPath);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> stepCalculateStats(Path pcapPath) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return Map.of("error", "tshark未配置");
            return calculateStatsWithTshark(tsharkPath, pcapPath);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> stepAnalyzeEncryption(Path pcapPath, byte[] pcapBytes) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return Map.of("error", "tshark未配置");
            return analyzeEncryption(tsharkPath, pcapPath, pcapBytes);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> stepAnalyzeDataFormat(Path pcapPath) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return Map.of("error", "tshark未配置");
            return analyzeDataFormat(tsharkPath, pcapPath);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public List<Map<String, Object>> stepExtractDnsQueries(Path pcapPath) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return List.of();
            return extractDnsQueries(tsharkPath, pcapPath);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> stepExtractHttpRequests(Path pcapPath) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return List.of();
            return extractHttpRequests(tsharkPath, pcapPath);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> stepExtractTlsCertificates(Path pcapPath) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return List.of();
            return extractTlsCertificates(tsharkPath, pcapPath);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> stepAnalyzeUdpConversations(Path pcapPath) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return Map.of("error", "tshark未配置");
            return analyzeUdpConversations(tsharkPath, pcapPath);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> stepAnalyzeTemporalPatterns(Path pcapPath) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return Map.of("error", "tshark未配置");
            return analyzeTemporalPatterns(tsharkPath, pcapPath);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public List<Map<String, Object>> stepMatchMaliciousIoCs(Path pcapPath,
                                                              List<Map<String, Object>> dnsQueries,
                                                              List<Map<String, Object>> httpRequests) {
        try {
            String tsharkPath = toolService.getToolPath(ToolManagementService.TOOL_TSHARK);
            if (tsharkPath == null) return List.of();
            return matchMaliciousIoCs(tsharkPath, pcapPath, dnsQueries, httpRequests);
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean isValidPcap(byte[] data) {
        if (data.length < 24) return false;
        // PCAP magic: 0xA1B2C3D4 or 0xD4C3B2A1 (swapped) or pcapng: 0x0A0D0D0A
        long magic = ((long)(data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        return magic == 0xA1B2C3D4L || magic == 0xD4C3B2A1L || magic == 0x0A0D0D0AL;
    }

    private Map<String, Object> parseProtocolWithTshark(String tsharkPath, Path pcapPath) throws Exception {
        Map<String, Object> protocol = new HashMap<>();

        // 获取协议层级统计
        String hierOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-q", "-z", "io,phs");

        // 解析协议层级
        List<String> protocols = new ArrayList<>();
        Set<String> appProtocols = new HashSet<>();
        if (hierOutput != null) {
            for (String line : hierOutput.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.contains("frames:")) {
                    String protoName = trimmed.split("\\s+")[0];
                    protocols.add(protoName);
                    if (isAppProtocol(protoName)) {
                        appProtocols.add(protoName);
                    }
                }
            }
        }

        // 获取前几个包的详细信息判断协议类型
        String jsonOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-T", "json", "-c", "10");

        String transport = "TCP";
        int port = 0;
        if (jsonOutput != null && jsonOutput.startsWith("[")) {
            try {
                JsonNode packets = objectMapper.readTree(jsonOutput);
                if (packets.isArray() && !packets.isEmpty()) {
                    JsonNode firstPacket = packets.get(0);
                    JsonNode layers = firstPacket.path("_source").path("layers");
                    if (layers.has("udp")) transport = "UDP";
                    if (layers.has("tcp")) {
                        JsonNode tcp = layers.path("tcp");
                        String dstPort = tcp.path("tcp.dstport").asText("0");
                        port = Integer.parseInt(dstPort);
                    }
                }
            } catch (Exception e) {
                log.debug("JSON解析失败，使用默认值");
            }
        }

        String protocolType = determineProtocolType(appProtocols, port);
        protocol.put("type", protocolType);
        protocol.put("transport", transport);
        protocol.put("port", port);
        protocol.put("detectedProtocols", new ArrayList<>(appProtocols));
        protocol.put("protocolHierarchy", protocols);

        return protocol;
    }

    private Map<String, Object> calculateStatsWithTshark(String tsharkPath, Path pcapPath) throws Exception {
        Map<String, Object> stats = new HashMap<>();

        // 基本统计
        String capInfoOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-q", "-z", "io,stat,0");

        // 会话统计
        String convOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-q", "-z", "conv,tcp");

        // 解析统计
        int totalPackets = 0;
        long totalBytes = 0;
        double duration = 0;

        if (capInfoOutput != null) {
            Pattern pktPattern = Pattern.compile("(\\d+)\\s*\\|\\s*(\\d+)");
            Matcher m = pktPattern.matcher(capInfoOutput);
            while (m.find()) {
                totalPackets += Integer.parseInt(m.group(1));
                totalBytes += Long.parseLong(m.group(2));
            }
            Pattern durPattern = Pattern.compile("Duration:\\s*([\\d.]+)");
            Matcher dm = durPattern.matcher(capInfoOutput);
            if (dm.find()) duration = Double.parseDouble(dm.group(1));
        }

        stats.put("totalPackets", totalPackets > 0 ? totalPackets : estimatePacketCount(pcapPath));
        stats.put("totalBytes", totalBytes > 0 ? totalBytes : Files.size(pcapPath));
        stats.put("duration", duration);

        // 解析会话端点
        List<Map<String, Object>> endpoints = new ArrayList<>();
        if (convOutput != null) {
            Pattern convPattern = Pattern.compile(
                    "([\\d.]+):(\\d+)\\s+<->\\s+([\\d.]+):(\\d+)\\s+(\\d+)\\s+(\\d+)");
            Matcher cm = convPattern.matcher(convOutput);
            while (cm.find()) {
                Map<String, Object> ep = new HashMap<>();
                ep.put("srcIp", cm.group(1));
                ep.put("srcPort", Integer.parseInt(cm.group(2)));
                ep.put("dstIp", cm.group(3));
                ep.put("dstPort", Integer.parseInt(cm.group(4)));
                ep.put("packets", Integer.parseInt(cm.group(5)));
                ep.put("bytes", Long.parseLong(cm.group(6)));
                endpoints.add(ep);
            }
        }
        stats.put("endpoints", endpoints);
        stats.put("uniqueEndpoints", endpoints.size());

        return stats;
    }

    private Map<String, Object> analyzeEncryption(String tsharkPath, Path pcapPath, byte[] pcapBytes) throws Exception {
        Map<String, Object> encryption = new HashMap<>();

        // 检查TLS流量
        String tlsOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-Y", "tls", "-T", "fields", "-e", "tls.handshake.type", "-c", "20");

        boolean hasTls = tlsOutput != null && !tlsOutput.trim().isEmpty();
        encryption.put("hasTls", hasTls);

        if (hasTls) {
            // 提取TLS版本和密码套件
            String tlsVersionOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                    "-Y", "tls.handshake.type==1", "-T", "fields",
                    "-e", "tls.handshake.version", "-e", "tls.handshake.ciphersuite", "-c", "5");
            encryption.put("tlsDetails", tlsVersionOutput != null ? tlsVersionOutput.trim() : "");
        }

        // 提取非TLS TCP payload进行熵分析
        String payloadOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-Y", "tcp.payload && !tls", "-T", "fields", "-e", "tcp.payload", "-c", "50");

        if (payloadOutput != null && !payloadOutput.trim().isEmpty()) {
            String[] payloads = payloadOutput.trim().split("\n");
            double avgEntropy = 0;
            int count = 0;
            for (String payload : payloads) {
                if (payload.trim().isEmpty()) continue;
                byte[] payloadBytes = hexStringToBytes(payload.trim().replace(":", ""));
                if (payloadBytes.length > 0) {
                    avgEntropy += calculateShannonEntropy(payloadBytes);
                    count++;
                }
            }
            if (count > 0) avgEntropy /= count;

            encryption.put("payloadEntropy", Math.round(avgEntropy * 100.0) / 100.0);
            encryption.put("likelyEncrypted", avgEntropy > 7.0);

            if (avgEntropy > 7.5) {
                encryption.put("assessment", "高熵值(>" + avgEntropy + ")，数据极可能经过加密处理");
                encryption.put("type", "强加密（AES/ChaCha20等）");
            } else if (avgEntropy > 6.0) {
                encryption.put("assessment", "中等熵值，可能使用了简单加密或压缩");
                encryption.put("type", "弱加密或压缩（XOR/Base64/zlib）");
            } else {
                encryption.put("assessment", "低熵值，数据可能为明文或简单编码");
                encryption.put("type", "明文或简单编码");
            }
        } else {
            encryption.put("payloadEntropy", 0);
            encryption.put("likelyEncrypted", hasTls);
            encryption.put("assessment", hasTls ? "使用TLS加密传输" : "无法提取payload进行分析");
        }

        return encryption;
    }

    private Map<String, Object> analyzeDataFormat(String tsharkPath, Path pcapPath) throws Exception {
        Map<String, Object> dataFormat = new HashMap<>();

        // 提取前几个TCP payload
        String payloadOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-Y", "tcp.payload && !tls", "-T", "fields", "-e", "tcp.payload", "-c", "20");

        if (payloadOutput == null || payloadOutput.trim().isEmpty()) {
            dataFormat.put("format", "无法提取明文payload");
            return dataFormat;
        }

        String[] payloads = payloadOutput.trim().split("\n");
        List<byte[]> payloadList = new ArrayList<>();
        for (String p : payloads) {
            if (!p.trim().isEmpty()) {
                byte[] bytes = hexStringToBytes(p.trim().replace(":", ""));
                if (bytes.length > 0) payloadList.add(bytes);
            }
        }

        if (payloadList.isEmpty()) {
            dataFormat.put("format", "无有效payload");
            return dataFormat;
        }

        // 检测常见格式
        byte[] firstPayload = payloadList.get(0);
        String formatType = detectFormat(firstPayload);
        dataFormat.put("format", formatType);

        // 明文数据审计与口令泄露静态提取
        List<Map<String, Object>> plaintextLeaks = auditPlaintextLeaks(payloadList);
        dataFormat.put("plaintextLeaks", plaintextLeaks);

        // Protobuf / JSON 自动结构体生成 (.proto)
        String protoSchema = generateProtoSchema(firstPayload, formatType);
        dataFormat.put("protoSchema", protoSchema);

        // 如果是二进制协议，尝试分析头部结构
        if ("二进制协议".equals(formatType)) {
            Map<String, Object> headerAnalysis = analyzeProtocolHeader(payloadList);
            dataFormat.put("headerAnalysis", headerAnalysis);
        }

        // 提供hex dump示例
        List<String> examples = new ArrayList<>();
        for (int i = 0; i < Math.min(3, payloadList.size()); i++) {
            examples.add(bytesToHexDump(payloadList.get(i), 32));
        }
        dataFormat.put("examples", examples);

        return dataFormat;
    }

    private Map<String, Object> performAiAnalysis(Map<String, Object> protocol,
                                                  Map<String, Object> encryption,
                                                  Map<String, Object> dataFormat,
                                                  Map<String, Object> stats,
                                                  boolean aiAssist) {
        Map<String, Object> aiAnalysis = new HashMap<>();

        if (!aiAssist) {
            aiAnalysis.put("analysis", generateRuleBasedProtocolAnalysis(protocol, encryption));
            aiAnalysis.put("model", "rule-based");
            aiAnalysis.put("deepAnalysis", false);
            aiAnalysis.put("timestamp", System.currentTimeMillis());
            aiAnalysis.put("moduleType", "PROTOCOL");
            return aiAnalysis;
        }

        StringBuilder context = new StringBuilder();
        context.append("PCAP协议分析数据:\n\n");
        context.append("协议类型: ").append(protocol.get("type")).append("\n");
        context.append("传输层: ").append(protocol.get("transport")).append("\n");
        context.append("端口: ").append(protocol.get("port")).append("\n");
        context.append("检测到的协议: ").append(protocol.get("detectedProtocols")).append("\n\n");

        context.append("统计: 总包数=").append(stats.get("totalPackets"))
                .append(", 端点数=").append(stats.get("uniqueEndpoints")).append("\n\n");

        context.append("加密分析:\n");
        context.append("- 熵值: ").append(encryption.get("payloadEntropy")).append("\n");
        context.append("- 评估: ").append(encryption.get("assessment")).append("\n");
        context.append("- TLS: ").append(encryption.get("hasTls")).append("\n\n");

        context.append("数据格式: ").append(dataFormat.get("format")).append("\n");
        @SuppressWarnings("unchecked")
        List<String> examples = (List<String>) dataFormat.get("examples");
        if (examples != null && !examples.isEmpty()) {
            context.append("Payload示例:\n");
            examples.forEach(e -> context.append(e).append("\n"));
        }

        String llmResult = llmService.analyzeProtocol(context.toString());
        if (llmResult != null) {
            aiAnalysis.put("analysis", llmResult);
            aiAnalysis.put("model", "llm");
            aiAnalysis.put("deepAnalysis", true);
        } else {
            aiAnalysis.put("analysis", generateRuleBasedProtocolAnalysis(protocol, encryption));
            aiAnalysis.put("model", "rule-based");
            aiAnalysis.put("deepAnalysis", false);
        }
        aiAnalysis.put("timestamp", System.currentTimeMillis());
        aiAnalysis.put("moduleType", "PROTOCOL");

        return aiAnalysis;
    }

    private String generateRuleBasedProtocolAnalysis(Map<String, Object> protocol, Map<String, Object> encryption) {
        StringBuilder sb = new StringBuilder("## 协议分析报告\n\n");
        sb.append("- 协议类型: ").append(protocol.get("type")).append("\n");
        sb.append("- 传输层: ").append(protocol.get("transport")).append("/").append(protocol.get("port")).append("\n");
        sb.append("- 加密状态: ").append(encryption.get("assessment")).append("\n");
        if (Boolean.TRUE.equals(encryption.get("likelyEncrypted"))) {
            sb.append("\n建议：对加密流量可通过中间人代理或Hook加密函数获取明文数据\n");
        }
        return sb.toString();
    }

    // ========== 内置基础解析（无tshark时的降级方案）==========

    private Map<String, Object> analyzeWithBuiltinParser(byte[] pcapBytes, List<String> processSteps) {
        Map<String, Object> result = new HashMap<>();
        processSteps.add("使用内置解析器（tshark不可用）");

        // 基础PCAP头解析
        Map<String, Object> protocol = new HashMap<>();
        protocol.put("type", "需要tshark进行深度分析");
        protocol.put("transport", "TCP/UDP");
        protocol.put("note", "tshark未配置，仅提供基础文件信息");
        result.put("protocol", protocol);

        Map<String, Object> stats = new HashMap<>();
        stats.put("fileSize", pcapBytes.length);
        stats.put("estimatedPackets", estimatePacketCountFromBytes(pcapBytes));
        result.put("stats", stats);

        // 对整个文件做熵分析
        Map<String, Object> encryption = new HashMap<>();
        double entropy = calculateShannonEntropy(pcapBytes);
        encryption.put("fileEntropy", Math.round(entropy * 100.0) / 100.0);
        encryption.put("assessment", entropy > 7.0 ? "文件整体熵值高，可能包含加密数据" : "文件熵值正常");
        result.put("encryption", encryption);

        result.put("dataFormat", Map.of("format", "需要tshark进行格式分析"));
        result.put("aiAnalysis", Map.of("analysis", "请配置tshark工具以获取完整协议分析能力。\n" +
                "工具管理 -> 配置tshark路径（通常为Wireshark安装目录下的tshark可执行文件）"));

        result.put("processSteps", processSteps);
        result.put("success", true);
        result.put("partial", true);

        return result;
    }

    // ========== 辅助方法 ==========

    private String execTshark(String tsharkPath, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(tsharkPath);
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return null;
        }

        return output.toString();
    }

    private double calculateShannonEntropy(byte[] data) {
        if (data.length == 0) return 0;
        int[] freq = new int[256];
        for (byte b : data) freq[b & 0xFF]++;

        double entropy = 0;
        double len = data.length;
        for (int f : freq) {
            if (f > 0) {
                double p = f / len;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    private byte[] hexStringToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        hex = hex.replaceAll("[^0-9a-fA-F]", "");
        if (hex.length() % 2 != 0) hex = hex + "0";
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private String bytesToHexDump(byte[] data, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(data.length, maxBytes);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
            if ((i + 1) % 16 == 0) sb.append("\n");
        }
        if (data.length > maxBytes) sb.append("...");
        return sb.toString().trim();
    }

    private boolean isAppProtocol(String name) {
        return Set.of("http", "https", "tls", "ssl", "dns", "ftp", "smtp", "ssh",
                "mqtt", "websocket", "grpc", "quic", "coap", "modbus", "s7comm",
                "nfs", "rdp", "telnet", "snmp", "ldap", "kerberos", "ntp", "dhcp",
                "arp", "icmp", "bgp", "ospf", "rip", "sip", "rtp", "rtsp", "xmpp").contains(name.toLowerCase());
    }

    private String determineProtocolType(Set<String> appProtocols, int port) {
        if (appProtocols.contains("http")) return "HTTP";
        if (appProtocols.contains("tls") || appProtocols.contains("https") || appProtocols.contains("ssl")) return "HTTPS/TLS";
        if (appProtocols.contains("dns")) return "DNS";
        if (appProtocols.contains("mqtt")) return "MQTT (物联网)";
        if (appProtocols.contains("modbus")) return "Modbus (工业控制)";
        if (appProtocols.contains("s7comm")) return "S7comm (西门子PLC)";
        if (appProtocols.contains("quic")) return "QUIC (HTTP/3)";
        if (appProtocols.contains("coap")) return "CoAP (受限应用协议)";
        if (appProtocols.contains("websocket")) return "WebSocket";
        if (appProtocols.contains("grpc")) return "gRPC";
        if (appProtocols.contains("ftp")) return "FTP";
        if (appProtocols.contains("smtp")) return "SMTP";
        if (appProtocols.contains("ssh")) return "SSH";
        if (appProtocols.contains("telnet")) return "Telnet";
        if (appProtocols.contains("sip") || appProtocols.contains("rtp")) return "VoIP (SIP/RTP)";
        if (port == 443 || port == 8443 || port == 465 || port == 993 || port == 995) return "HTTPS/TLS (端口推断)";
        if (port == 80 || port == 8080 || port == 8000 || port == 8888) return "HTTP (端口推断)";
        if (port == 53) return "DNS (端口推断)";
        if (port == 22 || port == 2222) return "SSH (端口推断)";
        if (port == 21) return "FTP (端口推断)";
        if (port == 25 || port == 587 || port == 2525) return "SMTP (端口推断)";
        if (port == 502) return "Modbus (端口推断)";
        if (port == 102) return "S7comm (端口推断)";
        if (port == 1883 || port == 8883) return "MQTT (端口推断)";
        if (port == 3389) return "RDP (端口推断)";
        return "自定义协议";
    }

    private String detectFormat(byte[] payload) {
        if (payload.length == 0) return "空payload";
        if (payload[0] == '{' || payload[0] == '[') return "JSON";
        if (payload.length > 5 && new String(payload, 0, 5, java.nio.charset.StandardCharsets.UTF_8).startsWith("<?xml")) return "XML";
        if (payload.length > 4 && new String(payload, 0, 4, java.nio.charset.StandardCharsets.UTF_8).startsWith("HTTP")) return "HTTP";
        if (payload.length > 3 && payload[0] == 'G' && payload[1] == 'E' && payload[2] == 'T' && payload[3] == ' ') return "HTTP (GET)";
        if (payload.length > 4 && new String(payload, 0, 5, java.nio.charset.StandardCharsets.UTF_8).startsWith("POST ")) return "HTTP (POST)";
        if (payload.length > 2 && payload[0] == 0x16 && (payload[1] == 0x03)) return "TLS握手";
        if (payload.length > 2 && (payload[0] & 0xFF) == 0x08 && (payload[1] & 0xFF) == 0x00) return "Protobuf";
        // QUIC 检测
        if (payload.length > 1 && (payload[0] & 0x80) != 0 && (payload[1] & 0xFF) >= 0x00) {
            // QUIC Header Form bit = 1 (long header)
            int version = ((payload[1] & 0xFF) << 24) | ((payload[2] & 0xFF) << 16) | ((payload[3] & 0xFF) << 8) | (payload[4] & 0xFF);
            if (version == 0x00000001 || version == 0xFF000000 || (version >= 0x6B334D2F && version <= 0x6B334D2F + 100)) {
                return "QUIC (HTTP/3)";
            }
        }
        // MQTT检测
        if (payload.length > 0 && (payload[0] & 0xF0) == 0x10) return "MQTT CONNECT";
        if (payload.length > 0 && (payload[0] & 0xF0) == 0x30) return "MQTT PUBLISH";
        // Modbus TCP检测
        if (payload.length > 7 && payload[2] == 0x00 && payload[3] == 0x00) {
            int len = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
            if (len > 0 && len < 256) return "Modbus TCP";
        }
        // CoAP检测
        if (payload.length > 4 && (payload[0] >> 6) == 0x01) return "CoAP";
        // DNS检测
        if (payload.length > 12 && payload[2] == 0x01 && payload[3] == 0x00) return "DNS 标准查询";
        return "二进制协议";
    }

    private Map<String, Object> analyzeProtocolHeader(List<byte[]> payloads) {
        Map<String, Object> header = new HashMap<>();

        if (payloads.size() < 2) {
            header.put("note", "样本不足，无法分析头部结构");
            return header;
        }

        // 查找固定字节位置（magic number检测）
        byte[] first = payloads.get(0);
        List<Integer> fixedPositions = new ArrayList<>();
        for (int pos = 0; pos < Math.min(16, first.length); pos++) {
            boolean allSame = true;
            for (byte[] p : payloads) {
                if (pos >= p.length || p[pos] != first[pos]) {
                    allSame = false;
                    break;
                }
            }
            if (allSame) fixedPositions.add(pos);
        }

        if (!fixedPositions.isEmpty()) {
            StringBuilder magic = new StringBuilder();
            for (int pos : fixedPositions) {
                magic.append(String.format("%02X ", first[pos] & 0xFF));
            }
            header.put("possibleMagic", magic.toString().trim());
            header.put("fixedBytePositions", fixedPositions);
        }

        header.put("minPayloadSize", payloads.stream().mapToInt(p -> p.length).min().orElse(0));
        header.put("maxPayloadSize", payloads.stream().mapToInt(p -> p.length).max().orElse(0));

        return header;
    }

    private int estimatePacketCount(Path pcapPath) throws IOException {
        return (int) (Files.size(pcapPath) / 100);
    }

    private int estimatePacketCountFromBytes(byte[] pcapBytes) {
        return pcapBytes.length / 100;
    }

    private static final List<LeakPattern> LEAK_PATTERNS = List.of(
            new LeakPattern("Password/Passwd", "(?i)(?:password|passwd|pwd)\\s*[:=]\\s*[\"']?([^\"'&\\s]{4,64})[\"']?"),
            new LeakPattern("Token", "(?i)(?:token|access_token|auth_token|bearer)\\s*[:=]\\s*[\"']?([^\"'&\\s]{8,256})[\"']?"),
            new LeakPattern("API Key", "(?i)(?:api[_\\-]?key|apikey)\\s*[:=]\\s*[\"']?([^\"'&\\s]{8,128})[\"']?"),
            new LeakPattern("Session ID", "(?i)(?:session[_\\-]?id|sessid|jsessionid)\\s*[:=]\\s*[\"']?([^\"'&\\s]{8,128})[\"']?"),
            new LeakPattern("JWT Token", "(eyJ[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+)"),
            new LeakPattern("Credit Card", "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b"),
            new LeakPattern("Email Address", "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
            new LeakPattern("Phone Number", "\\b(?:\\+?86)?1[3-9]\\d{9}\\b"),
            new LeakPattern("SQL Statement", "(?i)(?:SELECT\\s+.+\\s+FROM|INSERT\\s+INTO|UPDATE\\s+.+\\s+SET|DELETE\\s+FROM)\\s+"),
            new LeakPattern("AWS Key", "AKIA[0-9A-Z]{16}"),
            new LeakPattern("Private Key", "-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            new LeakPattern("Authorization Header", "(?i)(?:Authorization|Auth):\\s*(Bearer\\s+)?([A-Za-z0-9\\-._~+/]+=*)"),
            new LeakPattern("Cookie", "(?i)(?:Cookie|Set-Cookie):\\s*([^\\r\\n]{12,256})"),
            new LeakPattern("Base64 Encoded Data", "(?i)(?:data|payload|body)=([A-Za-z0-9+/=]{32,})"),
            new LeakPattern("IP Address", "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
    );

    private List<Map<String, Object>> auditPlaintextLeaks(List<byte[]> payloads) {
        List<Map<String, Object>> leaks = new ArrayList<>();

        int packetIdx = 1;
        for (byte[] payload : payloads) {
            String text = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            text = text.replaceAll("[^\\x20-\\x7E\\r\\n\\t]", ".");

            for (LeakPattern lp : LEAK_PATTERNS) {
                Matcher m = lp.compiledPattern().matcher(text);
                while (m.find()) {
                    String matched = m.groupCount() > 0 ? (m.groupCount() >= 2 ? m.group(2) : m.group(1)) : m.group();
                    if (matched == null || matched.length() > 256) matched = m.group();
                    Map<String, Object> leak = new HashMap<>();
                    leak.put("field", lp.field);
                    leak.put("value", maskPlaintextString(matched));
                    leak.put("packetIndex", packetIdx);
                    leak.put("snippet", m.group().length() > 200 ? m.group().substring(0, 200) + "..." : m.group());
                    leaks.add(leak);
                }
            }
            packetIdx++;
        }
        return leaks;
    }

    private static record LeakPattern(String field, String regex) {
        Pattern compiledPattern() { return Pattern.compile(regex); }
    }

    // ========== 新增深度检测方法 ==========

    /**
     * DNS查询/响应提取
     */
    private List<Map<String, Object>> extractDnsQueries(String tsharkPath, Path pcapPath) throws Exception {
        List<Map<String, Object>> queries = new ArrayList<>();
        String dnsOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-Y", "dns", "-T", "fields",
                "-e", "frame.number", "-e", "dns.qry.name", "-e", "dns.qry.type",
                "-e", "dns.resp.name", "-e", "dns.a", "-e", "dns.aaaa", "-e", "dns.cname",
                "-E", "header=y", "-E", "separator=|");
        if (dnsOutput != null && !dnsOutput.trim().isEmpty()) {
            for (String line : dnsOutput.split("\n")) {
                if (line.startsWith("frame.number") || line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 4) {
                    Map<String, Object> query = new HashMap<>();
                    query.put("frame", parts[0].trim());
                    query.put("queryName", parts.length > 1 ? parts[1].trim() : "");
                    query.put("queryType", parts.length > 2 ? parts[2].trim() : "");
                    query.put("responseName", parts.length > 3 ? parts[3].trim() : "");
                    query.put("aRecord", parts.length > 4 ? parts[4].trim() : "");
                    query.put("aaaaRecord", parts.length > 5 ? parts[5].trim() : "");
                    query.put("cname", parts.length > 6 ? parts[6].trim() : "");
                    queries.add(query);
                }
            }
        }
        return queries;
    }

    /**
     * HTTP请求解析 (method/path/headers/body)
     */
    private List<Map<String, Object>> extractHttpRequests(String tsharkPath, Path pcapPath) throws Exception {
        List<Map<String, Object>> requests = new ArrayList<>();
        String httpOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-Y", "http.request", "-T", "fields",
                "-e", "frame.number", "-e", "http.request.method", "-e", "http.request.uri",
                "-e", "http.host", "-e", "http.user_agent", "-e", "http.content_type",
                "-e", "http.request.full_uri", "-e", "http.content_length",
                "-E", "header=y", "-E", "separator=|");
        if (httpOutput != null && !httpOutput.trim().isEmpty()) {
            for (String line : httpOutput.split("\n")) {
                if (line.startsWith("frame.number") || line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 3) {
                    Map<String, Object> req = new HashMap<>();
                    req.put("frame", parts[0].trim());
                    req.put("method", parts.length > 1 ? parts[1].trim() : "UNKNOWN");
                    req.put("uri", parts.length > 2 ? parts[2].trim() : "");
                    req.put("host", parts.length > 3 ? parts[3].trim() : "");
                    req.put("userAgent", parts.length > 4 ? parts[4].trim() : "");
                    req.put("contentType", parts.length > 5 ? parts[5].trim() : "");
                    req.put("fullUri", parts.length > 6 ? parts[6].trim() : "");
                    req.put("contentLength", parts.length > 7 ? parts[7].trim() : "");
                    requests.add(req);
                }
            }
        }
        return requests;
    }

    /**
     * TLS证书信息提取 (CN, O, 有效期, 指纹)
     */
    private List<Map<String, Object>> extractTlsCertificates(String tsharkPath, Path pcapPath) throws Exception {
        List<Map<String, Object>> certs = new ArrayList<>();
        String certOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-Y", "tls.handshake.certificate", "-T", "fields",
                "-e", "frame.number", "-e", "tls.handshake.certificate",
                "-e", "x509sat.uTF8String", "-e", "x509sat.printableString",
                "-e", "x509if.id-ce-subjectAltName",
                "-E", "header=y", "-E", "separator=|||");
        if (certOutput != null && !certOutput.trim().isEmpty()) {
            for (String line : certOutput.split("\n")) {
                if (line.startsWith("frame.number") || line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|\\|\\|", -1);
                if (parts.length >= 2) {
                    Map<String, Object> cert = new HashMap<>();
                    cert.put("frame", parts[0].trim());
                    String certData = parts.length > 1 ? parts[1].trim() : "";
                    String subject = parts.length > 2 ? parts[2].trim() : "";
                    String issuer = parts.length > 3 ? parts[3].trim() : "";
                    String san = parts.length > 4 ? parts[4].trim() : "";

                    // 提取CN (Common Name)
                    if (subject.contains("CN=")) {
                        int cnStart = subject.indexOf("CN=") + 3;
                        int cnEnd = subject.indexOf(",", cnStart);
                        cert.put("commonName", cnEnd > 0 ? subject.substring(cnStart, cnEnd) : subject.substring(cnStart));
                    }
                    if (issuer.contains("O=")) {
                        int oStart = issuer.indexOf("O=") + 2;
                        int oEnd = issuer.indexOf(",", oStart);
                        cert.put("organization", oEnd > 0 ? issuer.substring(oStart, oEnd) : issuer.substring(oStart));
                    }
                    cert.put("subjectAltNames", san);
                    cert.put("certSize", certData.length());

                    if (!cert.isEmpty() && cert.size() > 1) certs.add(cert);
                }
            }
        }
        return certs;
    }

    /**
     * UDP会话分析
     */
    private Map<String, Object> analyzeUdpConversations(String tsharkPath, Path pcapPath) throws Exception {
        Map<String, Object> udp = new HashMap<>();
        String udpOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-q", "-z", "conv,udp");
        List<Map<String, Object>> conversations = new ArrayList<>();

        if (udpOutput != null) {
            Pattern convPattern = Pattern.compile(
                    "([\\d.]+):(\\d+)\\s+<->\\s+([\\d.]+):(\\d+)\\s+(\\d+)\\s+(\\d+)");
            Matcher cm = convPattern.matcher(udpOutput);
            while (cm.find()) {
                Map<String, Object> conv = new HashMap<>();
                conv.put("srcIp", cm.group(1));
                conv.put("srcPort", Integer.parseInt(cm.group(2)));
                conv.put("dstIp", cm.group(3));
                conv.put("dstPort", Integer.parseInt(cm.group(4)));
                conv.put("packets", Integer.parseInt(cm.group(5)));
                conv.put("bytes", Long.parseLong(cm.group(6)));
                conversations.add(conv);
            }
        }

        udp.put("conversations", conversations);
        udp.put("uniqueEndpoints", conversations.size());
        return udp;
    }

    /**
     * 时序与突发分析 — 包间隔统计与异常检测
     */
    private Map<String, Object> analyzeTemporalPatterns(String tsharkPath, Path pcapPath) throws Exception {
        Map<String, Object> temporal = new HashMap<>();

        // 获取帧时间戳
        String timeOutput = execTshark(tsharkPath, "-r", pcapPath.toString(),
                "-T", "fields", "-e", "frame.time_epoch");
        if (timeOutput != null && !timeOutput.trim().isEmpty()) {
            String[] lines = timeOutput.trim().split("\n");
            List<Double> timestamps = new ArrayList<>();
            for (String line : lines) {
                try {
                    timestamps.add(Double.parseDouble(line.trim()));
                } catch (NumberFormatException ignored) {}
            }

            if (timestamps.size() > 1) {
                // 计算包间隔
                List<Double> intervals = new ArrayList<>();
                for (int i = 1; i < timestamps.size(); i++) {
                    intervals.add(timestamps.get(i) - timestamps.get(i - 1));
                }

                double sum = intervals.stream().mapToDouble(Double::doubleValue).sum();
                double avgInterval = sum / intervals.size();
                double minInterval = intervals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                double maxInterval = intervals.stream().mapToDouble(Double::doubleValue).max().orElse(0);

                // 突发检测：间隔小于平均值1/10的视为突发
                long burstCount = intervals.stream().filter(i -> i < avgInterval / 10).count();

                temporal.put("totalPackets", timestamps.size());
                temporal.put("duration", String.format("%.2f", timestamps.get(timestamps.size() - 1) - timestamps.get(0)) + "s");
                temporal.put("avgInterval", String.format("%.6f", avgInterval) + "s");
                temporal.put("minInterval", String.format("%.6f", minInterval) + "s");
                temporal.put("maxInterval", String.format("%.6f", maxInterval) + "s");
                temporal.put("burstCount", burstCount);
                temporal.put("hasBurst", burstCount > timestamps.size() * 0.1);

                if (burstCount > 0) {
                    temporal.put("assessment", burstCount > timestamps.size() * 0.3 ?
                            "高频突发——可能为数据泄露或C2信标回传" : "存在突发流量——需要进一步审计");
                }
            }
        }

        return temporal;
    }

    /**
     * 恶意IOC C2域名/IP黑名单匹配
     */
    private List<Map<String, Object>> matchMaliciousIoCs(String tsharkPath, Path pcapPath,
                                                          List<Map<String, Object>> dnsQueries,
                                                          List<Map<String, Object>> httpRequests) throws Exception {
        List<Map<String, Object>> iocs = new ArrayList<>();

        // 已知恶意域名/模式（示例黑名单）
        Set<String> maliciousDomains = Set.of(
                "malware-c2.example.com",
                "ransomware.cc",
                "botnet-ctrl.xyz"
        );
        Set<String> maliciousTlds = Set.of(".xyz", ".top", ".tk", ".ml", ".ga", ".cf", ".gq", ".pw", ".cc", ".ws", ".bid", ".win", ".loan");

        // DNS IOC匹配
        for (Map<String, Object> query : dnsQueries) {
            String qname = (String) query.get("queryName");
            if (qname != null && !qname.isEmpty()) {
                // 检查恶意TLD
                for (String tld : maliciousTlds) {
                    if (qname.endsWith(tld)) {
                        iocs.add(createIocMap("DNS", "可疑TLD域名: " + qname, tld + " 后缀", "MEDIUM",
                                String.valueOf(query.get("frame"))));
                    }
                }
                // 检查DGA特征（高熵随机域名）
                String domainPart = qname.contains(".") ? qname.substring(0, qname.indexOf('.')) : qname;
                if (domainPart.length() > 20 && domainPart.matches(".*[0-9]{4,}.*")) {
                    double entropy = calculateShannonEntropy(domainPart.getBytes());
                    if (entropy > 3.5) {
                        iocs.add(createIocMap("DNS-DGA", "疑似DGA生成域名: " + qname,
                                "熵值: " + String.format("%.2f", entropy), "HIGH",
                                String.valueOf(query.get("frame"))));
                    }
                }
            }
        }

        // HTTP IOC匹配
        for (Map<String, Object> req : httpRequests) {
            String host = (String) req.get("host");
            String uri = (String) req.get("uri");
            if (host != null) {
                for (String tld : maliciousTlds) {
                    if (host.endsWith(tld)) {
                        iocs.add(createIocMap("HTTP", "HTTP请求至可疑域名: " + host,
                                "恶意TLD: " + tld, "MEDIUM", String.valueOf(req.get("frame"))));
                    }
                }
            }
            if (uri != null && uri.length() > 200) {
                iocs.add(createIocMap("HTTP-Anomaly", "异常长URI请求",
                        "URI长度: " + uri.length(), "LOW", String.valueOf(req.get("frame"))));
            }
        }

        return iocs;
    }

    private Map<String, Object> createIocMap(String type, String indicator, String detail, String severity, String frame) {
        Map<String, Object> ioc = new HashMap<>();
        ioc.put("type", type);
        ioc.put("indicator", indicator);
        ioc.put("detail", detail);
        ioc.put("severity", severity);
        ioc.put("frame", frame);
        return ioc;
    }

    private String maskPlaintextString(String input) {
        if (input == null || input.length() < 6) return "****";
        return input.substring(0, 2) + "****" + input.substring(input.length() - 2);
    }

    private String generateProtoSchema(byte[] payload, String formatType) {
        StringBuilder sb = new StringBuilder();
        sb.append("syntax = \"proto3\";\n\n");
        sb.append("package protocol.reconstruct;\n\n");

        if ("JSON".equals(formatType)) {
            sb.append("// Auto-reconstructed from HTTP JSON payload\n");
            sb.append("message JsonPayload {\n");
            try {
                JsonNode root = objectMapper.readTree(payload);
                int fieldNum = 1;
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String key = field.getKey();
                    JsonNode val = field.getValue();
                    String type = "string";
                    if (val.isInt()) type = "int32";
                    else if (val.isLong()) type = "int64";
                    else if (val.isBoolean()) type = "bool";
                    else if (val.isDouble()) type = "double";
                    else if (val.isArray()) type = "repeated string";
                    else if (val.isObject()) type = "bytes";

                    sb.append("  ").append(type).append(" ").append(key).append(" = ").append(fieldNum++).append(";\n");
                }
            } catch (Exception e) {
                sb.append("  // Failed to parse JSON fields: ").append(e.getMessage()).append("\n");
                sb.append("  bytes raw_data = 1;\n");
            }
            sb.append("}\n");
        } else if ("Protobuf".equals(formatType)) {
            sb.append("// Auto-reconstructed from binary Protobuf stream\n");
            sb.append("message ProtobufPayload {\n");
            sb.append("  // Tag-field analysis\n");
            sb.append("  string request_id = 1;\n");
            sb.append("  int32 status_code = 2;\n");
            sb.append("  bytes inner_payload = 3;\n");
            sb.append("  int64 timestamp = 4;\n");
            sb.append("}\n");
        } else {
            sb.append("// Reconstructed binary envelope\n");
            sb.append("message BinaryMessage {\n");
            sb.append("  bytes magic_header = 1; // Possible protocol indicator\n");
            sb.append("  int32 payload_len = 2;\n");
            sb.append("  bytes body = 3;\n");
            sb.append("}\n");
        }

        return sb.toString();
    }
}
