package com.ldd.initialization.service.ai;

import com.ldd.initialization.config.ai.AiAnalysisConfig;
import lombok.extern.slf4j.Slf4j;
import net.fornwall.jelf.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SoAnalysisService {

    @Autowired
    private SecurityAnalysisLlmService llmService;

    @Autowired
    private AiAnalysisConfig aiConfig;

    private static final Map<String, CryptoSignature> CRYPTO_SIGNATURES = new LinkedHashMap<>();

    static {
        CRYPTO_SIGNATURES.put("AES", new CryptoSignature("AES",
                new byte[][]{{0x63, 0x7C, 0x77, 0x7B, (byte)0xF2, 0x6B, 0x6F, (byte)0xC5}},
                List.of("AES_set_encrypt_key", "AES_cbc_encrypt", "AES_encrypt", "aes_encrypt", "aes_init", "rijndael"),
                "对称加密，常用于数据保护和通信加密"));

        CRYPTO_SIGNATURES.put("DES", new CryptoSignature("DES",
                new byte[][]{{0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01}},
                List.of("DES_encrypt", "DES_key_schedule", "des_encrypt", "des_cbc", "des_set_key"),
                "对称加密（已被攻破），可能用于兼容旧系统"));

        CRYPTO_SIGNATURES.put("3DES", new CryptoSignature("3DES",
                new int[]{},
                List.of("DES_ede3", "TDES_encrypt", "des3_encrypt", "triple_des", "3des_set_key"),
                "三重DES，AES过渡期使用的对称加密"));

        CRYPTO_SIGNATURES.put("Blowfish", new CryptoSignature("Blowfish",
                new int[]{},
                List.of("Blowfish_Init", "Blowfish_Encrypt", "bf_init", "blowfish_key", "BF_set_key"),
                "对称分组密码，常用于嵌入式设备加密"));

        CRYPTO_SIGNATURES.put("Twofish", new CryptoSignature("Twofish",
                new int[]{},
                List.of("twofish_encrypt", "twofish_decrypt", "twofish_set_key", "tf_init"),
                "AES候选算法，安全性接近AES"));

        CRYPTO_SIGNATURES.put("ChaCha20", new CryptoSignature("ChaCha20",
                new byte[][]{new byte[]{0x65, 0x78, 0x70, 0x61, 0x6E, 0x64, 0x20, 0x33, 0x32, 0x2D, 0x62, 0x79, 0x74, 0x65, 0x20, 0x6B}},
                List.of("chacha20", "chacha_encrypt", "chacha_init", "chacha_block", "hchacha20"),
                "高速流密码，广泛用于TLS 1.3和VPN"));

        CRYPTO_SIGNATURES.put("Salsa20", new CryptoSignature("Salsa20",
                new int[]{},
                List.of("salsa20", "salsa20_xor", "xsalsa20", "salsa20_init"),
                "djbs流密码，ChaCha20的前身"));

        CRYPTO_SIGNATURES.put("RC4", new CryptoSignature("RC4",
                new int[]{},
                List.of("RC4", "rc4_crypt", "arc4_init", "rc4_setup", "rc4_skip"),
                "流密码（已不安全，禁止用于TLS），常见于旧协议"));

        CRYPTO_SIGNATURES.put("MD5", new CryptoSignature("MD5",
                new int[]{0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476},
                List.of("MD5Init", "MD5Update", "MD5Final", "md5_init", "md5_update", "md5_transform"),
                "哈希算法（已被碰撞攻破），常用于签名校验和完整性验证"));

        CRYPTO_SIGNATURES.put("SHA-1", new CryptoSignature("SHA-1",
                new int[]{0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0},
                List.of("SHA1Init", "SHA1Update", "SHA1Final", "sha1_init", "sha1_transform"),
                "哈希算法（已被SHAttered攻破），用于数字签名"));

        CRYPTO_SIGNATURES.put("SHA-256", new CryptoSignature("SHA-256",
                new int[]{0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19},
                List.of("SHA256_Init", "SHA256_Update", "sha256_init", "sha256_transform", "sha256_final"),
                "SHA-2家族哈希，广泛用于安全签名和密钥派生"));

        CRYPTO_SIGNATURES.put("SHA-512", new CryptoSignature("SHA-512",
                new int[]{},
                List.of("SHA512_Init", "SHA512_Update", "sha512_init", "sha512_transform"),
                "SHA-2家族512位哈希"));

        CRYPTO_SIGNATURES.put("SM3", new CryptoSignature("SM3",
                new int[]{},
                List.of("sm3_init", "sm3_update", "sm3_final", "SM3_hash", "sm3_compress"),
                "中国国家密码标准哈希算法 (GB/T 32905-2016)"));

        CRYPTO_SIGNATURES.put("SM4", new CryptoSignature("SM4",
                new int[]{},
                List.of("sm4_encrypt", "sm4_decrypt", "sm4_set_key", "SM4_cbc", "sm4_ecb"),
                "中国国家密码标准分组加密 (GB/T 32907-2016)"));

        CRYPTO_SIGNATURES.put("TEA/XTEA", new CryptoSignature("TEA/XTEA",
                new int[]{0x9E3779B9, (int)0xC6EF3720},
                List.of("tea_encrypt", "tea_decrypt", "xtea_encrypt", "block_tea", "xxtea_encrypt"),
                "轻量级分组密码，常见于游戏和自定义协议加密"));

        CRYPTO_SIGNATURES.put("RSA", new CryptoSignature("RSA",
                new int[]{},
                List.of("RSA_public_encrypt", "RSA_private_decrypt", "rsa_sign", "rsa_verify", "BN_mod_exp",
                        "RSA_generate_key", "RSA_private_encrypt", "RSA_public_decrypt"),
                "非对称加密，用于密钥交换和数字签名"));

        CRYPTO_SIGNATURES.put("ECDSA", new CryptoSignature("ECDSA",
                new int[]{},
                List.of("ECDSA_sign", "ECDSA_verify", "ecdsa_do_sign", "ECDSA_do_verify", "ecdsa_sign_setup"),
                "椭圆曲线数字签名算法，比RSA更高效"));

        CRYPTO_SIGNATURES.put("Curve25519", new CryptoSignature("Curve25519",
                new int[]{},
                List.of("curve25519", "x25519", "X25519_scalar_mult", "curve25519_donna", "ed25519_sign"),
                "现代椭圆曲线密钥交换 (RFC 7748)"));

        CRYPTO_SIGNATURES.put("SM2", new CryptoSignature("SM2",
                new int[]{},
                List.of("sm2_sign", "sm2_verify", "sm2_encrypt", "sm2_decrypt", "sm2_keygen"),
                "中国国密椭圆曲线公钥密码算法 (GM/T 0003-2012)"));

        CRYPTO_SIGNATURES.put("HMAC", new CryptoSignature("HMAC",
                new int[]{0x36363636, 0x5C5C5C5C},
                List.of("HMAC_Init", "HMAC_Update", "HMAC_Final", "hmac_sha256", "hmac_sha1", "hmac_md5"),
                "消息认证码，用于数据完整性和身份验证"));

        CRYPTO_SIGNATURES.put("Poly1305", new CryptoSignature("Poly1305",
                new int[]{},
                List.of("poly1305", "poly1305_init", "poly1305_update", "poly1305_finish", "crypto_onetimeauth"),
                "消息认证码，常与ChaCha20组合用于AEAD"));

        CRYPTO_SIGNATURES.put("XOR Cipher", new CryptoSignature("XOR Cipher",
                new int[]{},
                List.of("xor_encrypt", "xor_decrypt", "xor_crypt", "xor_cipher", "simple_xor"),
                "简单异或加密，安全性极低但常见于资源混淆"));

        CRYPTO_SIGNATURES.put("Base64", new CryptoSignature("Base64",
                new byte[][]{},
                List.of("base64_encode", "base64_decode", "b64_encode", "Base64Encode", "b64_decode", "base64url"),
                "编码方案（非加密），常用于数据传输编码"));

        CRYPTO_SIGNATURES.put("CRC32", new CryptoSignature("CRC32",
                new int[]{0xEDB88320},
                List.of("crc32", "crc32_z", "CRC32", "crc_table"),
                "循环冗余校验，用于数据完整性校验"));

        CRYPTO_SIGNATURES.put("Custom/Obfuscated", new CryptoSignature("Custom/Obfuscated",
                new int[]{},
                List.of("custom_crypto", "obfuscate", "deobfuscate", "xxtea_decrypt", "custom_encrypt"),
                "自定义/混淆加密实现，常见于游戏反作弊和恶意软件"));
    }

    public Map<String, Object> analyzeSo(byte[] soBytes, String filename) {
        return analyzeSo(soBytes, filename, false);
    }

    public Map<String, Object> analyzeSo(byte[] soBytes, String filename, boolean aiAssist) {
        Map<String, Object> result = new HashMap<>();
        List<String> processSteps = new ArrayList<>();

        try {
            log.info("开始SO分析: {}, 大小: {} bytes", filename, soBytes.length);

            processSteps.add("解析ELF文件头");
            ElfFile elfFile = ElfFile.from(soBytes);
            Map<String, Object> soInfo = extractSoInfo(elfFile, soBytes, filename);
            result.put("soInfo", soInfo);

            processSteps.add("ELF安全属性分析");
            Map<String, Object> elfSecurity = analyzeElfSecurity(elfFile, soBytes);
            result.put("elfSecurity", elfSecurity);

            processSteps.add("提取函数列表");
            List<Map<String, Object>> functions = extractFunctions(elfFile);
            result.put("functions", functions);

            processSteps.add("识别加密算法");
            List<Map<String, Object>> algorithms = identifyCryptoAlgorithms(elfFile, soBytes, functions);
            result.put("algorithms", algorithms);

            processSteps.add("检测代码混淆");
            Map<String, Object> obfuscation = detectObfuscation(elfFile, soBytes, functions);
            result.put("obfuscation", obfuscation);

            processSteps.add("SO加固/加壳检测");
            Map<String, Object> soPacker = detectSoPacker(elfFile, soBytes, functions);
            result.put("soPacker", soPacker);

            processSteps.add("反调试能力检测");
            Map<String, Object> antiDebug = detectAntiDebugCapabilities(elfFile, soBytes, functions);
            result.put("antiDebug", antiDebug);

            processSteps.add("UPX/压缩检测");
            Map<String, Object> compression = detectCompression(elfFile, soBytes);
            result.put("compression", compression);

            processSteps.add("分析敏感字符串");
            List<Map<String, Object>> strings = analyzeStrings(elfFile, soBytes);
            result.put("strings", strings);

            processSteps.add(aiAssist ? "AI 深度融合分析" : "规则引擎分析");
            Map<String, Object> aiAnalysis = performAiAnalysis(algorithms, obfuscation, functions, strings, aiAssist);
            result.put("aiAnalysis", aiAnalysis);

            result.put("processSteps", processSteps);
            result.put("success", true);

        } catch (Exception e) {
            log.error("SO分析失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "ELF解析失败: " + e.getMessage());
            result.put("processSteps", processSteps);
        }

        return result;
    }

    // ========== 步骤方法（供GuidedAnalysis调用） ==========

    public Map<String, Object> stepExtractSoInfo(byte[] soBytes, String filename) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return extractSoInfo(elfFile, soBytes, filename);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public List<Map<String, Object>> stepExtractFunctions(byte[] soBytes) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return extractFunctions(elfFile);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> stepIdentifyCrypto(byte[] soBytes, List<Map<String, Object>> functions) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return identifyCryptoAlgorithms(elfFile, soBytes, functions);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> stepDetectObfuscation(byte[] soBytes, List<Map<String, Object>> functions) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return detectObfuscation(elfFile, soBytes, functions);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public List<Map<String, Object>> stepAnalyzeStrings(byte[] soBytes) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return analyzeStrings(elfFile, soBytes);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> stepAnalyzeElfSecurity(byte[] soBytes) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return analyzeElfSecurity(elfFile, soBytes);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> stepDetectSoPacker(byte[] soBytes, List<Map<String, Object>> functions) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return detectSoPacker(elfFile, soBytes, functions);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> stepDetectAntiDebug(byte[] soBytes, List<Map<String, Object>> functions) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return detectAntiDebugCapabilities(elfFile, soBytes, functions);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> stepDetectCompression(byte[] soBytes) {
        try {
            ElfFile elfFile = ElfFile.from(soBytes);
            return detectCompression(elfFile, soBytes);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> extractSoInfo(ElfFile elfFile, byte[] soBytes, String filename) {
        Map<String, Object> info = new HashMap<>();

        info.put("fileName", filename);
        info.put("fileSize", soBytes.length);
        info.put("architecture", getArchName(elfFile.e_machine));
        info.put("bitClass", elfFile.ei_class == ElfFile.CLASS_32 ? "32-bit" : "64-bit");
        info.put("endianness", elfFile.ei_data == ElfFile.DATA_LSB ? "Little-endian" : "Big-endian");
        info.put("type", getElfTypeName(elfFile.e_type));
        info.put("entryPoint", "0x" + Long.toHexString(elfFile.e_entry));

        // 段信息
        List<String> sections = new ArrayList<>();
        int sectionCount = elfFile.e_shnum;
        for (int i = 0; i < sectionCount; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                if (section.header.getName() != null && !section.header.getName().isEmpty()) {
                    sections.add(section.header.getName());
                }
            } catch (Exception ignored) {}
        }
        info.put("sections", sections);
        info.put("sectionCount", sectionCount);

        // 依赖库
        List<String> dependencies = new ArrayList<>();
        for (int i = 0; i < sectionCount; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                if (section.header.sh_type == ElfSectionHeader.SHT_DYNAMIC) {
                    ElfDynamicSection dynSection = (ElfDynamicSection) section;
                    dependencies.addAll(dynSection.getNeededLibraries());
                }
            } catch (Exception ignored) {}
        }
        info.put("dependencies", dependencies);

        return info;
    }

    private List<Map<String, Object>> extractFunctions(ElfFile elfFile) {
        List<Map<String, Object>> functions = new ArrayList<>();

        for (int i = 0; i < elfFile.e_shnum; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                if (section.header.sh_type == ElfSectionHeader.SHT_DYNSYM ||
                        section.header.sh_type == ElfSectionHeader.SHT_SYMTAB) {
                    ElfSymbolTableSection symSection = (ElfSymbolTableSection) section;
                    int numSymbols = symSection.symbols.length;
                    for (int j = 0; j < numSymbols; j++) {
                        ElfSymbol sym = symSection.symbols[j];
                        if (sym.getType() == ElfSymbol.STT_FUNC && sym.st_size > 0) {
                            Map<String, Object> func = new HashMap<>();
                            func.put("name", sym.getName());
                            func.put("address", "0x" + Long.toHexString(sym.st_value));
                            func.put("size", sym.st_size);
                            func.put("binding", sym.getBinding() == ElfSymbol.BINDING_GLOBAL ? "GLOBAL" : "LOCAL");
                            func.put("category", categorizeFunction(sym.getName()));
                            func.put("purpose", inferFunctionPurpose(sym.getName()));
                            functions.add(func);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        functions.sort((a, b) -> Long.compare(
                Long.parseLong(((String) a.get("address")).substring(2), 16),
                Long.parseLong(((String) b.get("address")).substring(2), 16)));

        return functions;
    }

    private List<Map<String, Object>> identifyCryptoAlgorithms(ElfFile elfFile, byte[] soBytes,
                                                                List<Map<String, Object>> functions) {
        List<Map<String, Object>> algorithms = new ArrayList<>();

        // 1. 基于函数名匹配
        for (Map<String, Object> func : functions) {
            String name = (String) func.get("name");
            for (Map.Entry<String, CryptoSignature> entry : CRYPTO_SIGNATURES.entrySet()) {
                CryptoSignature sig = entry.getValue();
                if (sig.matchesFunctionName(name)) {
                    algorithms.add(createAlgorithmResult(entry.getKey(), name,
                            (String) func.get("address"), 0.9, sig.description, "函数名匹配"));
                    break;
                }
            }
        }

        // 2. 基于.rodata常量扫描
        byte[] rodataBytes = extractSectionBytes(elfFile, ".rodata", soBytes);
        if (rodataBytes == null) {
            rodataBytes = extractSectionBytes(elfFile, ".data", soBytes);
        }

        if (rodataBytes != null) {
            for (Map.Entry<String, CryptoSignature> entry : CRYPTO_SIGNATURES.entrySet()) {
                CryptoSignature sig = entry.getValue();
                if (algorithms.stream().anyMatch(a -> a.get("name").equals(entry.getKey()))) continue;

                int offset = sig.findConstantInBytes(rodataBytes);
                if (offset >= 0) {
                    algorithms.add(createAlgorithmResult(entry.getKey(), "常量匹配",
                            "0x" + Integer.toHexString(offset), 0.85, sig.description, "魔数常量匹配"));
                }
            }
        }

        return algorithms;
    }

    private List<Map<String, Object>> analyzeStrings(ElfFile elfFile, byte[] soBytes) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 从.rodata和.dynstr提取字符串
        String[] sections = {".rodata", ".dynstr", ".data"};
        for (String sectionName : sections) {
            byte[] sectionBytes = extractSectionBytes(elfFile, sectionName, soBytes);
            if (sectionBytes == null) continue;

            long sectionOffset = getSectionOffset(elfFile, sectionName);
            List<StringEntry> strings = extractPrintableStrings(sectionBytes, 4);

            for (StringEntry se : strings) {
                if (seen.contains(se.value)) continue;
                seen.add(se.value);

                String category = categorizeString(se.value);
                if (category != null) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("value", se.value);
                    entry.put("address", "0x" + Long.toHexString(sectionOffset + se.offset));
                    entry.put("category", category);
                    entry.put("section", sectionName);
                    result.add(entry);
                }
            }
        }

        result.sort((a, b) -> {
            int catOrder = getCategoryOrder((String) a.get("category")) - getCategoryOrder((String) b.get("category"));
            return catOrder != 0 ? catOrder : ((String) a.get("value")).compareTo((String) b.get("value"));
        });

        return result.size() > 100 ? result.subList(0, 100) : result;
    }

    private double calculateEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0;
        int[] frequencies = new int[256];
        for (byte b : data) {
            frequencies[b & 0xFF]++;
        }
        double entropy = 0;
        for (int freq : frequencies) {
            if (freq > 0) {
                double p = (double) freq / data.length;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    private String generateDeobfuscationGuide(boolean ollvm, boolean stringEnc) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🛡️ 极客 SO 逆向与反混淆实战指南\n\n");
        if (ollvm) {
            sb.append("#### 1. 绕过 OLLVM 控制流平坦化\n");
            sb.append("检测到控制流平坦化保护。混淆器将原始基本块打碎，并通过一个全局状态变量及大循环 switch 结构动态分发控制流。\n");
            sb.append("**反混淆方案 (IDA Pro + Python / D810 / Amass)**:\n");
            sb.append("- 使用 IDA Pro 的 **D810 插件**，载入预定义规则进行微调，该插件能高效识别并坍缩状态机分发块。\n");
            sb.append("- **符号执行还原**: 编写基于 **Triton** 或 **Angr** 的 Python 脚本，以基本块起始为入口进行符号执行。在计算出状态变量的所有合法跳转分支路径后，静态修补(patch)跳转指令（如将 `CMOV`/`JZ` 修改为无条件跳转 `B`/`JMP`），跳过分发器直接链接真实块。\n\n");
        }
        if (stringEnc) {
            sb.append("#### 2. 解密加密的静态字符串\n");
            sb.append("检测到字符串加密保护。敏感配置常以密文存储，并在 `.init_array` 或 `JNI_OnLoad` 执行时在内存中动态解密。\n");
            sb.append("**Frida 内存转储与动态解码脚本**:\n");
            sb.append("```javascript\n");
            sb.append("/* \n");
            sb.append("  [极致静态安全审计] - 针对 SO 字符串动态解密的 Frida 挂钩解密脚本\n");
            sb.append("  原理: 挂钩 JNI_OnLoad 或动态符号，在解密完成后直接读取并转储内存中的明文字符串。\n");
            sb.append("*/\n");
            sb.append("Java.perform(function () {\n");
            sb.append("    var targetSo = \"libTarget.so\"; // 请修改为您的 SO 名字\n");
            sb.append("    var module = Process.findModuleByName(targetSo);\n");
            sb.append("    if (module) {\n");
            sb.append("        console.log(\"[★] 已定位目标 SO 基址: \" + module.base);\n");
            sb.append("        // 示例: 在 JNI_OnLoad 运行完毕后进行内存快照 dump\n");
            sb.append("        var jniOnLoad = module.findExportByName(\"JNI_OnLoad\");\n");
            sb.append("        if (jniOnLoad) {\n");
            sb.append("            Interceptor.attach(jniOnLoad, {\n");
            sb.append("                onLeave: function (retval) {\n");
            sb.append("                    console.log(\"[✔] JNI_OnLoad 执行完毕，开始静态解密段扫描...\");\n");
            sb.append("                    // 扫描 .rodata 对应内存段，打印可读可打印字符串\n");
            sb.append("                    var address = module.base.add(0x1000); // 替换为目标地址\n");
            sb.append("                    try {\n");
            sb.append("                        console.log(\"[DUMP] 内存明文片段: \" + Memory.readUtf8String(address));\n");
            sb.append("                    } catch(e) {}\n");
            sb.append("                }\n");
            sb.append("            });\n");
            sb.append("        }\n");
            sb.append("    }\n");
            sb.append("});\n");
            sb.append("```\n\n");
        }
        return sb.toString();
    }

    private Map<String, Object> detectObfuscation(ElfFile elfFile, byte[] soBytes,
                                                   List<Map<String, Object>> functions) {
        Map<String, Object> obfuscation = new HashMap<>();

        // 1. 符号混淆检测
        int obfuscatedCount = 0;
        int totalExported = 0;
        for (Map<String, Object> func : functions) {
            if ("GLOBAL".equals(func.get("binding"))) {
                totalExported++;
                String name = (String) func.get("name");
                if (isObfuscatedName(name)) {
                    obfuscatedCount++;
                }
            }
        }
        double symbolObfuscationRatio = totalExported > 0 ? (double) obfuscatedCount / totalExported : 0;
        obfuscation.put("symbolObfuscation", symbolObfuscationRatio > 0.3);
        obfuscation.put("symbolObfuscationRatio", Math.round(symbolObfuscationRatio * 100) + "%");

        // 2. 字符串加密与信息熵审计
        byte[] rodataBytes = extractSectionBytes(elfFile, ".rodata", soBytes);
        boolean stringEncryption = false;
        double entropy = 0;
        if (rodataBytes != null && rodataBytes.length > 100) {
            List<StringEntry> strings = extractPrintableStrings(rodataBytes, 4);
            double stringDensity = (double) strings.size() / (rodataBytes.length / 100.0);
            entropy = calculateEntropy(rodataBytes);
            // 密度过低或者信息熵异常偏高（接近密文的 7.0-8.0）均判定为加密
            stringEncryption = stringDensity < 0.5 || entropy > 6.8;
        }
        obfuscation.put("stringEncryption", stringEncryption);
        obfuscation.put("entropy", String.format("%.2f", entropy));

        // 3. OLLVM 控制流平坦化检测
        byte[] textBytes = extractSectionBytes(elfFile, ".text", soBytes);
        boolean ollvmDetected = false;
        int branchCount = 0;
        if (textBytes != null) {
            branchCount = countBranchInstructions(textBytes, elfFile.e_machine);
            double branchDensity = (double) branchCount / (textBytes.length / 4.0);
            // 密度异常高的分支跳转和 nested 分发状态段
            ollvmDetected = branchDensity > 0.15;
        }
        obfuscation.put("ollvm", ollvmDetected);
        obfuscation.put("controlFlowFlattening", ollvmDetected);

        // 4. 汇总与反混淆高级指导
        Map<String, Object> details = new HashMap<>();
        Map<String, Object> obfData = new HashMap<>();
        
        if (symbolObfuscationRatio > 0.3) {
            obfData.put("ratio", Math.round(symbolObfuscationRatio * 100) + "%");
            obfData.put("count", obfuscatedCount);
            details.put("symbol", obfData);
        }
        if (stringEncryption) {
            details.put("string", Map.of(
                    "density", String.format("%.2f", (double) (rodataBytes != null ? rodataBytes.length : 0) / 1000.0),
                    "entropy", String.format("%.2f", entropy),
                    "msg", "rodata段的信息熵异常高或可读文本密度过低"
            ));
        }
        if (ollvmDetected) {
            details.put("ollvm", Map.of(
                    "branchCount", branchCount,
                    "msg", "检测到大量的控制流分配节点与大跳表块"
            ));
        }

        obfuscation.put("details", details);
        
        // 生成高度可读的研究指导
        if (ollvmDetected || stringEncryption) {
            String deobfuscationGuide = generateDeobfuscationGuide(ollvmDetected, stringEncryption);
            obfuscation.put("deobfuscationGuide", deobfuscationGuide);
        }

        return obfuscation;
    }

    private Map<String, Object> performAiAnalysis(List<Map<String, Object>> algorithms,
                                                  Map<String, Object> obfuscation,
                                                  List<Map<String, Object>> functions,
                                                  List<Map<String, Object>> strings,
                                                  boolean aiAssist) {
        Map<String, Object> aiAnalysis = new HashMap<>();

        if (!aiAssist) {
            aiAnalysis.put("analysis", generateRuleBasedSoAnalysis(algorithms, obfuscation, functions));
            aiAnalysis.put("model", "rule-based");
            aiAnalysis.put("deepAnalysis", false);
            aiAnalysis.put("timestamp", System.currentTimeMillis());
            aiAnalysis.put("moduleType", "SO");
            return aiAnalysis;
        }

        StringBuilder context = new StringBuilder();
        context.append("SO文件深度安全分析数据:\n\n");
        context.append("导出函数 (").append(functions.size()).append("个):\n");
        functions.stream().limit(50).forEach(f ->
                context.append("- ").append(f.get("name")).append(" [").append(f.get("category"))
                        .append("] size=").append(f.get("size")).append(" binding=").append(f.get("binding")).append("\n"));

        if (!algorithms.isEmpty()) {
            context.append("\n识别到的加密算法:\n");
            algorithms.forEach(a -> context.append("- ").append(a.get("name"))
                    .append(" (").append(a.get("matchType")).append(", 置信度:")
                    .append(a.get("confidence")).append(")\n"));
        }

        context.append("\n混淆检测:\n");
        context.append("- 符号混淆: ").append(obfuscation.get("symbolObfuscation")).append("\n");
        context.append("- 字符串加密: ").append(obfuscation.get("stringEncryption")).append("\n");
        context.append("- OLLVM: ").append(obfuscation.get("ollvm")).append("\n");

        if (!strings.isEmpty()) {
            context.append("\n敏感字符串 (前30个):\n");
            strings.stream().limit(30).forEach(s ->
                    context.append("- [").append(s.get("category")).append("] ")
                            .append(s.get("value")).append("\n"));
        }

        context.append("\n请基于以上数据，进行深度安全分析，包含:\n");
        context.append("1. ELF结构安全评估（PIE/NX/RELRO/Canary）\n");
        context.append("2. 加密算法实现安全性分析与潜在漏洞\n");
        context.append("3. 混淆/加固技术识别与反混淆策略\n");
        context.append("4. 反逆向/反调试特征与绕过方案\n");
        context.append("5. 敏感字符串风险评估\n");
        context.append("6. 若适用，生成Frida Hook脚本\n");

        String llmResult = llmService.analyzeSo(context.toString());
        if (llmResult != null) {
            aiAnalysis.put("analysis", llmResult);
            aiAnalysis.put("model", "llm");
            aiAnalysis.put("deepAnalysis", true);
        } else {
            aiAnalysis.put("analysis", generateRuleBasedSoAnalysis(algorithms, obfuscation, functions));
            aiAnalysis.put("model", "rule-based");
            aiAnalysis.put("deepAnalysis", false);
        }
        aiAnalysis.put("timestamp", System.currentTimeMillis());
        aiAnalysis.put("moduleType", "SO");

        return aiAnalysis;
    }

    private String generateRuleBasedSoAnalysis(List<Map<String, Object>> algorithms,
                                               Map<String, Object> obfuscation,
                                               List<Map<String, Object>> functions) {
        StringBuilder sb = new StringBuilder("## SO文件静态分析报告\n\n");

        sb.append("### 加密算法识别\n");
        if (algorithms.isEmpty()) {
            sb.append("未检测到已知加密算法实现\n");
        } else {
            for (Map<String, Object> algo : algorithms) {
                sb.append("- **").append(algo.get("name")).append("**: ")
                        .append(algo.get("description")).append("\n");
            }
        }

        sb.append("\n### 代码保护分析\n");
        if ((boolean) obfuscation.getOrDefault("symbolObfuscation", false)) {
            sb.append("- 检测到符号混淆，函数名被混淆处理\n");
        }
        if ((boolean) obfuscation.getOrDefault("stringEncryption", false)) {
            sb.append("- 检测到字符串加密，敏感数据以加密形式存储\n");
        }
        if ((boolean) obfuscation.getOrDefault("ollvm", false)) {
            sb.append("- 检测到OLLVM控制流平坦化，增加逆向难度\n");
        }

        sb.append("\n### 功能分类\n");
        Map<String, Long> categories = functions.stream()
                .collect(Collectors.groupingBy(f -> (String) f.get("category"), Collectors.counting()));
        categories.forEach((cat, count) -> sb.append("- ").append(cat).append(": ").append(count).append("个函数\n"));

        return sb.toString();
    }

    // ========== 辅助方法 ==========

    private byte[] extractSectionBytes(ElfFile elfFile, String sectionName, byte[] soBytes) {
        for (int i = 0; i < elfFile.e_shnum; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                if (sectionName.equals(section.header.getName())) {
                    long offset = section.header.sh_offset;
                    long size = section.header.sh_size;
                    if (offset + size <= soBytes.length) {
                        byte[] result = new byte[(int) size];
                        System.arraycopy(soBytes, (int) offset, result, 0, (int) size);
                        return result;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private long getSectionOffset(ElfFile elfFile, String sectionName) {
        for (int i = 0; i < elfFile.e_shnum; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                if (sectionName.equals(section.header.getName())) {
                    return section.header.sh_addr;
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private String getArchName(short arch) {
        if (arch == ElfFile.ARCH_AARCH64) return "ARM64-v8a (AArch64)";
        if (arch == ElfFile.ARCH_ARM) return "armeabi-v7a (ARM)";
        if (arch == ElfFile.ARCH_X86_64) return "x86_64";
        if (arch == ElfFile.ARCH_i386) return "x86";
        if (arch == ElfFile.ARCH_MIPS) return "MIPS";
        return "Unknown (0x" + Integer.toHexString(arch & 0xFFFF) + ")";
    }

    private String getElfTypeName(short type) {
        return switch (type) {
            case ElfFile.ET_DYN -> "共享库 (Shared Object)";
            case ElfFile.ET_EXEC -> "可执行文件";
            case ElfFile.ET_REL -> "可重定位文件";
            default -> "未知类型 (" + type + ")";
        };
    }

    private String categorizeFunction(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("jni") || lower.startsWith("java_")) return "JNI";
        if (lower.contains("aes") || lower.contains("des") || lower.contains("encrypt") ||
                lower.contains("decrypt") || lower.contains("cipher") || lower.contains("md5") ||
                lower.contains("sha") || lower.contains("hmac") || lower.contains("rsa") ||
                lower.contains("tea") || lower.contains("rc4") || lower.contains("hash")) return "Crypto";
        if (lower.contains("send") || lower.contains("recv") || lower.contains("socket") ||
                lower.contains("connect") || lower.contains("http") || lower.contains("ssl") ||
                lower.contains("tls")) return "Network";
        if (lower.contains("file") || lower.contains("open") || lower.contains("read") ||
                lower.contains("write") || lower.contains("close") || lower.contains("fopen")) return "File";
        if (lower.contains("device") || lower.contains("imei") || lower.contains("android_id") ||
                lower.contains("phone") || lower.contains("location")) return "Privacy";
        if (lower.contains("anti") || lower.contains("debug") || lower.contains("ptrace") ||
                lower.contains("check") || lower.contains("detect")) return "AntiDebug";
        if (lower.contains("hook") || lower.contains("inline") || lower.contains("got") ||
                lower.contains("plt")) return "Hook";
        return "Other";
    }

    private String inferFunctionPurpose(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("jni_onload")) return "JNI库初始化入口";
        if (lower.startsWith("java_")) return "JNI Native方法实现";
        if (lower.contains("encrypt")) return "数据加密";
        if (lower.contains("decrypt")) return "数据解密";
        if (lower.contains("sign") || lower.contains("verify")) return "签名/验证";
        if (lower.contains("key") && lower.contains("gen")) return "密钥生成";
        if (lower.contains("init")) return "初始化";
        if (lower.contains("anti_debug") || lower.contains("ptrace")) return "反调试保护";
        return "";
    }

    private boolean isObfuscatedName(String name) {
        if (name.startsWith("Java_") || name.startsWith("JNI_")) return false;
        if (name.startsWith("__")) return false;
        if (name.length() <= 3) return true;
        if (name.matches("^[a-zA-Z]{1,2}\\d+$")) return true;
        if (name.matches("^[a-z]{8,}$") && !containsMeaningfulWord(name)) return true;
        return false;
    }

    private boolean containsMeaningfulWord(String name) {
        String[] words = {"init", "start", "stop", "encrypt", "decrypt", "send", "recv",
                "load", "save", "check", "verify", "hash", "key", "auth", "open", "close",
                "read", "write", "get", "set", "create", "destroy", "parse", "format"};
        String lower = name.toLowerCase();
        for (String w : words) {
            if (lower.contains(w)) return true;
        }
        return false;
    }

    private String categorizeString(String value) {
        if (value.matches("https?://.*")) return "URL";
        if (value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}.*")) return "IP";
        if (value.contains("/data/") || value.contains("/sdcard/") || value.contains("/proc/")) return "Path";
        if (value.contains("key") || value.contains("secret") || value.contains("token") ||
                value.contains("password") || value.contains("auth")) return "Key";
        if (value.contains(".so") || value.contains(".dex") || value.contains(".jar")) return "File";
        if (value.matches(".*[A-Z]{3,}.*=.*") || value.contains("AES") || value.contains("RSA") ||
                value.contains("SHA") || value.contains("MD5")) return "Crypto";
        if (value.contains("frida") || value.contains("xposed") || value.contains("substrate") ||
                value.contains("magisk")) return "AntiHook";
        if (value.contains("debug") || value.contains("ptrace") || value.contains("tracerpid")) return "AntiDebug";
        if (value.length() >= 6 && value.matches("[A-Za-z0-9+/=]{20,}")) return "EncodedData";
        return null;
    }

    private int getCategoryOrder(String category) {
        return switch (category) {
            case "URL" -> 0;
            case "IP" -> 1;
            case "Key" -> 2;
            case "Crypto" -> 3;
            case "AntiHook" -> 4;
            case "AntiDebug" -> 5;
            case "Path" -> 6;
            case "File" -> 7;
            case "EncodedData" -> 8;
            default -> 9;
        };
    }

    private List<StringEntry> extractPrintableStrings(byte[] data, int minLength) {
        List<StringEntry> strings = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int startOffset = 0;

        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b >= 0x20 && b < 0x7F) {
                if (current.isEmpty()) startOffset = i;
                current.append((char) b);
            } else {
                if (current.length() >= minLength) {
                    strings.add(new StringEntry(current.toString(), startOffset));
                }
                current.setLength(0);
            }
        }
        if (current.length() >= minLength) {
            strings.add(new StringEntry(current.toString(), startOffset));
        }

        return strings;
    }

    private int countBranchInstructions(byte[] textBytes, short arch) {
        int count = 0;
        if (arch == ElfFile.ARCH_AARCH64) {
            for (int i = 0; i + 3 < textBytes.length; i += 4) {
                int insn = ((textBytes[i + 3] & 0xFF) << 24) | ((textBytes[i + 2] & 0xFF) << 16) |
                        ((textBytes[i + 1] & 0xFF) << 8) | (textBytes[i] & 0xFF);
                int op = (insn >> 26) & 0x3F;
                if (op == 0x05 || op == 0x25 || op == 0x34 || op == 0x35) count++;
            }
        } else if (arch == ElfFile.ARCH_ARM) {
            for (int i = 0; i + 3 < textBytes.length; i += 4) {
                int insn = ((textBytes[i + 3] & 0xFF) << 24) | ((textBytes[i + 2] & 0xFF) << 16) |
                        ((textBytes[i + 1] & 0xFF) << 8) | (textBytes[i] & 0xFF);
                int cond = (insn >> 28) & 0xF;
                int op = (insn >> 24) & 0xF;
                if (op == 0xA || op == 0xB) count++;
            }
        }
        return count;
    }

    private Map<String, Object> createAlgorithmResult(String name, String function, String address,
                                                      double confidence, String description, String matchType) {
        Map<String, Object> algo = new HashMap<>();
        algo.put("name", name);
        algo.put("function", function);
        algo.put("address", address);
        algo.put("confidence", confidence);
        algo.put("description", description);
        algo.put("matchType", matchType);
        return algo;
    }

    // ========== 新增深度检测方法 ==========

    /**
     * ELF安全属性全面分析
     * 检测 PIE/NX/RELRO/Stack Canary/init_array/fini_array/W^X
     */
    private Map<String, Object> analyzeElfSecurity(ElfFile elfFile, byte[] soBytes) {
        Map<String, Object> security = new HashMap<>();

        // PIE (Position Independent Executable)
        boolean isPIE = elfFile.e_type == ElfFile.ET_DYN;
        security.put("isPIE", isPIE);
        security.put("pieImpact", isPIE ? "支持ASLR地址随机化" : "非PIE——固定基址加载，易被ROP利用");

        // NX (No Execute) / W^X
        boolean hasExecStack = false;
        boolean hasWxSegment = false;
        int PT_GNU_STACK = 0x6474e551;
        for (int i = 0; i < elfFile.e_phnum; i++) {
            try {
                ElfSegment seg = elfFile.getProgramHeader(i);
                if (seg.p_type == PT_GNU_STACK) {
                    hasExecStack = seg.isExecutable();
                }
                if (seg.p_type == ElfSegment.PT_LOAD) {
                    if (seg.isWriteable() && seg.isExecutable()) {
                        hasWxSegment = true;
                    }
                }
            } catch (Exception ignored) {}
        }
        security.put("hasExecStack", hasExecStack);
        security.put("hasWxSegment", hasWxSegment);
        security.put("nxStatus", (!hasExecStack && !hasWxSegment) ? "NX/W^X完整保护" : "存在可写+可执行段——高危");

        // RELRO (Read-Only Relocations)
        boolean fullRelro = false;
        boolean partialRelro = false;
        ElfDynamicSection dynSection = elfFile.getDynamicSection();
        if (dynSection != null && dynSection.entries != null) {
            for (ElfDynamicSection.ElfDynamicStructure entry : dynSection.entries) {
                if (entry.d_tag == ElfDynamicSection.DT_BIND_NOW) {
                    fullRelro = true;
                    break;
                }
            }
        }
        for (int i = 0; i < elfFile.e_shnum; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                String name = section.header.getName();
                if (".rel.plt".equals(name) || ".rela.plt".equals(name)) {
                    partialRelro = true;
                }
            } catch (Exception ignored) {}
        }
        security.put("relro", fullRelro ? "Full RELRO" : partialRelro ? "Partial RELRO" : "No RELRO");
        security.put("isFullRelro", fullRelro);

        // Stack Canary
        boolean hasStackCanary = false;
        for (int i = 0; i < elfFile.e_shnum; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                if (section.header.getName().startsWith(".init_array")) {
                    // Check if __stack_chk_guard is referenced
                }
            } catch (Exception ignored) {}
        }
        // Scan for __stack_chk symbols
        byte[] symtabContent = extractSectionBytes(elfFile, ".dynsym", soBytes);
        if (symtabContent != null) {
            String symStr = new String(symtabContent, java.nio.charset.StandardCharsets.ISO_8859_1);
            hasStackCanary = symStr.contains("__stack_chk");
        }
        security.put("hasStackCanary", hasStackCanary);

        // init_array / fini_array
        boolean hasInitArray = false;
        boolean hasFiniArray = false;
        for (int i = 0; i < elfFile.e_shnum; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                String name = section.header.getName();
                if (".init_array".equals(name)) hasInitArray = true;
                if (".fini_array".equals(name)) hasFiniArray = true;
            } catch (Exception ignored) {}
        }
        security.put("hasInitArray", hasInitArray);
        security.put("hasFiniArray", hasFiniArray);

        // 安全评级
        int securityScore = 0;
        if (isPIE) securityScore++;
        if (fullRelro) securityScore += 2;
        else if (partialRelro) securityScore++;
        if (!hasExecStack) securityScore++;
        if (!hasWxSegment) securityScore++;
        if (hasStackCanary) securityScore++;

        String rating;
        if (securityScore >= 5) rating = "EXCELLENT";
        else if (securityScore >= 3) rating = "GOOD";
        else if (securityScore >= 2) rating = "FAIR";
        else rating = "POOR";
        security.put("securityScore", securityScore);
        security.put("securityRating", rating);

        return security;
    }

    /**
     * SO文件加固/加壳检测
     */
    private Map<String, Object> detectSoPacker(ElfFile elfFile, byte[] soBytes,
                                                List<Map<String, Object>> functions) {
        Map<String, Object> result = new HashMap<>();
        result.put("isPacked", false);

        List<String> indicators = new ArrayList<>();
        double packedScore = 0;

        // 1. 检查section数量(加固后section通常很少)
        if (elfFile.e_shnum <= 5) {
            indicators.add("ELF section 数量异常少 (" + elfFile.e_shnum + ")");
            packedScore += 0.3;
        }

        // 2. 导出函数很少但文件很大
        long exportedCount = functions.stream().filter(f -> "GLOBAL".equals(f.get("binding"))).count();
        if (exportedCount <= 5 && soBytes.length > 500_000) {
            indicators.add("导出函数极少 (" + exportedCount + ") 但文件较大 (" + soBytes.length / 1024 + " KB)");
            packedScore += 0.3;
        }

        // 3. 检查是否有大量的加密/解密初始化函数
        long initFuncs = functions.stream().filter(f -> {
            String name = ((String) f.get("name")).toLowerCase();
            return name.contains("init") || name.contains("decrypt") || name.contains("unpack");
        }).count();
        if (initFuncs > 3 && exportedCount <= 10) {
            indicators.add("存在多个init/decrypt/unpack关键函数");
            packedScore += 0.2;
        }

        // 4. JNI_OnLoad大小异常大（壳的入口）
        Optional<Map<String, Object>> jniOnLoad = functions.stream()
                .filter(f -> "JNI_OnLoad".equals(f.get("name")))
                .findFirst();
        if (jniOnLoad.isPresent() && (Integer) jniOnLoad.get().get("size") > 10000) {
            indicators.add("JNI_OnLoad 函数体积异常大 (可能包含解密逻辑)");
            packedScore += 0.2;
        }

        result.put("packedScore", Math.min(packedScore, 1.0));
        result.put("isPacked", packedScore > 0.4);
        result.put("indicators", indicators);
        if (packedScore > 0.4) {
            result.put("assessment", packedScore > 0.7 ? "高度疑似SO加固保护" : "疑似存在SO级加固/加壳");
        }

        return result;
    }

    /**
     * 反调试能力检测
     */
    private Map<String, Object> detectAntiDebugCapabilities(ElfFile elfFile, byte[] soBytes,
                                                             List<Map<String, Object>> functions) {
        Map<String, Object> result = new HashMap<>();
        List<String> techniques = new ArrayList<>();

        // 检查是否存在反调试相关函数名
        for (Map<String, Object> func : functions) {
            String name = ((String) func.get("name")).toLowerCase();
            if (name.contains("ptrace") || name.contains("anti_debug") || name.contains("antidebug") ||
                    name.contains("debug") && (name.contains("detect") || name.contains("check"))) {
                techniques.add("检测到反调试函数: " + func.get("name"));
            }
        }

        // 从字符串中查找反调试相关路径
        byte[][] sections = {
                extractSectionBytes(elfFile, ".rodata", soBytes),
                extractSectionBytes(elfFile, ".data", soBytes)
        };
        for (byte[] sect : sections) {
            if (sect == null) continue;
            String content = new String(sect, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (content.contains("/proc/self/status")) {
                techniques.add("读取/proc/self/status (TracerPid检测)");
            }
            if (content.contains("/proc/self/maps")) {
                techniques.add("读取/proc/self/maps (内存映射检测)");
            }
            if (content.contains("/proc/") && content.contains("cmdline")) {
                techniques.add("读取进程命令行 (调试器识别)");
            }
            if (content.contains("ptrace")) {
                techniques.add("引用ptrace系统调用 (反附加)");
            }
            if (content.contains("fopen") || content.contains("/proc/")) {
                // Generic proc access
            }
            if (content.contains("inotify")) {
                techniques.add("使用inotify文件监控 (检测注入)");
            }
            if (content.contains("TracerPid")) {
                techniques.add("检测TracerPid字段 (反跟踪)");
            }
            if (content.contains("frida") || content.contains("xposed") || content.contains("substrate")) {
                techniques.add("检测注入框架: " +
                        (content.contains("frida") ? "Frida " : "") +
                        (content.contains("xposed") ? "Xposed " : "") +
                        (content.contains("substrate") ? "Substrate" : ""));
            }
            break;
        }

        // 检查 init_array / JNI_OnLoad 中是否有反调试逻辑
        boolean hasJniAntiDebug = functions.stream().anyMatch(f ->
                "JNI_OnLoad".equals(f.get("name")) && (Integer) f.get("size") > 5000);
        if (hasJniAntiDebug) {
            techniques.add("JNI_OnLoad函数体积较大，可能包含反调试初始化逻辑");
        }

        result.put("hasAntiDebug", !techniques.isEmpty());
        result.put("techniques", techniques);
        result.put("count", techniques.size());
        result.put("severity", techniques.size() >= 5 ? "HIGH" : techniques.size() >= 3 ? "MEDIUM" : techniques.isEmpty() ? "NONE" : "LOW");

        return result;
    }

    /**
     * UPX/MPRESS等压缩壳检测
     */
    private Map<String, Object> detectCompression(ElfFile elfFile, byte[] soBytes) {
        Map<String, Object> result = new HashMap<>();
        result.put("isCompressed", false);

        // UPX 特征检测
        boolean hasUpxMarker = false;
        for (int i = 0; i < soBytes.length - 4; i++) {
            if (soBytes[i] == 'U' && soBytes[i + 1] == 'P' && soBytes[i + 2] == 'X' && soBytes[i + 3] == '!') {
                hasUpxMarker = true;
                break;
            }
        }
        result.put("upx", hasUpxMarker);

        // 检查 section name UPX特征
        boolean hasUpxSection = false;
        for (int i = 0; i < elfFile.e_shnum; i++) {
            try {
                ElfSection section = elfFile.getSection(i);
                String name = section.header.getName();
                if (name.startsWith("UPX") || name.contains("upx")) {
                    hasUpxSection = true;
                    break;
                }
            } catch (Exception ignored) {}
        }
        result.put("upxSection", hasUpxSection);

        // MPRESS 检测
        boolean hasMpress = false;
        for (int i = 0; i < Math.min(soBytes.length, 1024); i++) {
            if (soBytes[i] == 'M' && soBytes[i + 1] == 'P' && soBytes[i + 2] == 'R' &&
                    soBytes[i + 3] == 'E' && soBytes[i + 4] == 'S' && soBytes[i + 5] == 'S') {
                hasMpress = true;
                break;
            }
        }
        result.put("mpress", hasMpress);

        // 高熵值检测（压缩/加密特征）
        byte[] textBytes = extractSectionBytes(elfFile, ".text", soBytes);
        if (textBytes != null) {
            double textEntropy = calculateEntropy(textBytes);
            result.put("textEntropy", String.format("%.2f", textEntropy));
            result.put("highEntropy", textEntropy > 7.2);
            if (textEntropy > 7.2 && !hasUpxMarker) {
                result.put("possibleCustomPacker", true);
            }
        }

        boolean isCompressed = hasUpxMarker || hasUpxSection || hasMpress ||
                (result.containsKey("highEntropy") && Boolean.TRUE.equals(result.get("highEntropy")));
        result.put("isCompressed", isCompressed);

        if (hasUpxMarker) result.put("compressionType", "UPX");
        else if (hasMpress) result.put("compressionType", "MPRESS");
        else if (Boolean.TRUE.equals(result.get("possibleCustomPacker"))) result.put("compressionType", "Custom/Unknown Packer");

        return result;
    }

    private record StringEntry(String value, int offset) {}

    private static class CryptoSignature {
        final String name;
        final int[] intConstants;
        final byte[][] byteConstants;
        final List<String> functionNames;
        final String description;

        CryptoSignature(String name, int[] constants, List<String> functionNames, String description) {
            this.name = name;
            this.intConstants = constants;
            this.byteConstants = new byte[0][];
            this.functionNames = functionNames;
            this.description = description;
        }

        CryptoSignature(String name, byte[][] constants, List<String> functionNames, String description) {
            this.name = name;
            this.intConstants = new int[0];
            this.byteConstants = constants;
            this.functionNames = functionNames;
            this.description = description;
        }

        boolean matchesFunctionName(String funcName) {
            String lower = funcName.toLowerCase();
            return functionNames.stream().anyMatch(fn -> lower.contains(fn.toLowerCase()));
        }

        int findConstantInBytes(byte[] data) {
            // 搜索int常量
            if (intConstants.length > 0) {
                ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i <= data.length - 4; i++) {
                    int val = buf.getInt(i);
                    if (val == intConstants[0]) return i;
                }
                buf.order(ByteOrder.BIG_ENDIAN);
                for (int i = 0; i <= data.length - 4; i++) {
                    int val = buf.getInt(i);
                    if (val == intConstants[0]) return i;
                }
            }
            // 搜索byte序列
            if (byteConstants.length > 0 && byteConstants[0].length > 0) {
                byte[] pattern = byteConstants[0];
                for (int i = 0; i <= data.length - pattern.length; i++) {
                    boolean match = true;
                    for (int j = 0; j < pattern.length; j++) {
                        if (data[i + j] != pattern[j]) { match = false; break; }
                    }
                    if (match) return i;
                }
            }
            return -1;
        }
    }
}
