package com.aezer0.initialization.service.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Frida脚本执行器
 * 负责在Android设备上执行Frida脚本进行动态分析
 */
@Service
@Slf4j
public class FriggaScriptExecutor {

    private static final String FRIDA_SERVER_HOST = "localhost";
    private static final int FRIDA_SERVER_PORT = 27042;

    /**
     * 监控应用行为
     */
    public Map<String, Object> monitorApp(String deviceSerial, String packageName, int durationSeconds) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();

        try {
            log.info("开始监控应用: {}", packageName);

            // 生成监控脚本
            String script = generateMonitorScript(packageName);

            // 执行Frida脚本
            String output = executeFridaScript(deviceSerial, packageName, script, durationSeconds);

            // 解析输出
            events.addAll(parseFridaOutput(output));

            result.put("success", true);
            result.put("events", events);
            result.put("eventCount", events.size());
            result.put("packageName", packageName);

        } catch (Exception e) {
            log.error("监控应用失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Hook加密函数
     */
    public Map<String, Object> hookCrypto(String deviceSerial, String packageName) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> findings = new ArrayList<>();

        try {
            log.info("Hook加密函数: {}", packageName);

            // 生成Crypto Hook脚本
            String script = generateCryptoHookScript();

            // 执行Frida脚本
            String output = executeFridaScript(deviceSerial, packageName, script, 30);

            // 解析结果
            findings.addAll(parseCryptoFindings(output));

            result.put("success", true);
            result.put("findings", findings);
            result.put("cryptoCount", findings.size());

        } catch (Exception e) {
            log.error("Hook加密函数失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 拦截网络请求
     */
    public Map<String, Object> interceptNetwork(String deviceSerial, String packageName) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> requests = new ArrayList<>();

        try {
            log.info("拦截网络请求: {}", packageName);

            // 生成网络拦截脚本
            String script = generateNetworkInterceptScript();

            // 执行Frida脚本
            String output = executeFridaScript(deviceSerial, packageName, script, 60);

            // 解析结果
            requests.addAll(parseNetworkRequests(output));

            result.put("success", true);
            result.put("requests", requests);
            result.put("requestCount", requests.size());

        } catch (Exception e) {
            log.error("拦截网络请求失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 枚举应用类
     */
    public Map<String, Object> enumerateClasses(String deviceSerial, String packageName) {
        Map<String, Object> result = new HashMap<>();

        try {
            String script = generateEnumerateClassesScript();
            String output = executeFridaScript(deviceSerial, packageName, script, 15);

            List<String> classes = new ArrayList<>();
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.startsWith("CLASS:")) {
                    classes.add(line.substring(6).trim());
                }
            }

            result.put("success", true);
            result.put("classes", classes);
            result.put("classCount", classes.size());

        } catch (Exception e) {
            log.error("枚举类失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 提取字符串
     */
    public Map<String, Object> extractStrings(String deviceSerial, String packageName) {
        Map<String, Object> result = new HashMap<>();

        try {
            String script = generateStringExtractionScript();
            String output = executeFridaScript(deviceSerial, packageName, script, 30);

            List<String> strings = new ArrayList<>();
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.startsWith("STRING:")) {
                    strings.add(line.substring(7).trim());
                }
            }

            result.put("success", true);
            result.put("strings", strings);
            result.put("stringCount", strings.size());

        } catch (Exception e) {
            log.error("提取字符串失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    // ========== Frida脚本生成方法 ==========

    /**
     * 生成监控脚本
     */
    private String generateMonitorScript(String packageName) {
        return """
            Java.perform(function () {
                var packageName = "%s";

                // Hook Activity生命周期
                var activityClass = Java.use("android.app.Activity");
                activityClass.onCreate.implementation = function (bundle) {
                    send({type: "ACTIVITY", action: "onCreate", data: this.getClassName()});
                    this.onCreate(bundle);
                };

                // Hook网络请求
                var httpClient = Java.use("okhttp3.OkHttpClient");
                httpClient.newCall.implementation = function (request) {
                    send({type: "NETWORK", action: "REQUEST", url: request.url().toString()});
                    return this.newCall(request);
                };

                // Hook文件操作
                var fileOutputStream = Java.use("java.io.FileOutputStream");
                fileOutputStream.$init.implementation = function (name, append) {
                    send({type: "FILE", action: "WRITE", path: name.toString()});
                    return this.$init(name, append);
                };

                // Hook SharedPreferences
                var sharedPrefsClass = Java.use("android.app.SharedPreferencesImpl$EditorImpl");
                sharedPrefsClass.putString.implementation = function (key, value) {
                    send({type: "DATA", action: "PREF_WRITE", key: key.toString()});
                    return this.putString(key, value);
                };

                console.log("Monitor script loaded for: " + packageName);
            });
            """.formatted(packageName);
    }

    /**
     * 生成加密Hook脚本
     */
    private String generateCryptoHookScript() {
        return """
            Java.perform(function () {
                // Hook javax.crypto
                var cipherClass = Java.use("javax.crypto.Cipher");
                cipherClass.doFinal.overload("[B").implementation = function (data) {
                    var result = this.doFinal(data);
                    send({
                        type: "CRYPTO",
                        action: "CIPHER_DO_FINAL",
                        algorithm: this.getAlgorithm(),
                        inputLength: data.length,
                        outputLength: result.length
                    });
                    console.log("CRYPTO:CIPHER:" + this.getAlgorithm() + ":" + data.length + ":" + result.length);
                    return result;
                };

                cipherClass.doFinal.overload("[B", "int").implementation = function (data, offset) {
                    var result = this.doFinal(data, offset);
                    send({
                        type: "CRYPTO",
                        action: "CIPHER_DO_FINAL_OFFSET",
                        algorithm: this.getAlgorithm()
                    });
                    console.log("CRYPTO:CIPHER_OFFSET:" + this.getAlgorithm());
                    return result;
                };

                // Hook SecretKeySpec (常见的AES密钥)
                var secretKeySpecClass = Java.use("javax.crypto.spec.SecretKeySpec");
                secretKeySpecClass.$init.implementation = function (key, algorithm) {
                    var keyHex = bytesToHex(key);
                    send({
                        type: "CRYPTO",
                        action: "KEY_CREATED",
                        algorithm: algorithm,
                        keyPreview: keyHex.substring(0, Math.min(16, keyHex.length)) + "..."
                    });
                    console.log("CRYPTO:KEY:" + algorithm + ":" + keyHex);
                    return this.$init(key, algorithm);
                };

                // Hook MessageDigest
                var messageDigestClass = Java.use("java.security.MessageDigest");
                messageDigestClass.digest.overload("[B").implementation = function (data) {
                    var result = this.digest(data);
                    send({
                        type: "CRYPTO",
                        action: "HASH",
                        algorithm: this.getAlgorithm()
                    });
                    console.log("CRYPTO:HASH:" + this.getAlgorithm() + ":" + bytesToHex(result));
                    return result;
                };

                function bytesToHex(bytes) {
                    var result = "";
                    for (var i = 0; i < bytes.length; i++) {
                        result += ("0" + (bytes[i] & 0xff).toString(16)).slice(-2);
                    }
                    return result;
                }

                console.log("Crypto hook script loaded");
            });
            """;
    }

    /**
     * 生成网络拦截脚本
     */
    private String generateNetworkInterceptScript() {
        return """
            Java.perform(function () {
                // Hook OkHttp3
                try {
                    var OkHttpClient = Java.use("okhttp3.OkHttpClient");
                    OkHttpClient.newCall.implementation = function (request) {
                        var url = request.url().toString();
                        var method = request.method();
                        send({type: "NETWORK", action: "REQUEST", method: method, url: url});
                        console.log("NETWORK:REQUEST:" + method + ":" + url);
                        return this.newCall(request);
                    };
                } catch (e) {
                    console.log("OkHttp3 hook failed: " + e);
                }

                // Hook HttpURLConnection
                try {
                    var HttpURLConnectionClass = Java.use("java.net.HttpURLConnection");
                    HttpURLConnectionClass.connect.implementation = function () {
                        var url = this.getURL().toString();
                        send({type: "NETWORK", action: "CONNECT", url: url});
                        console.log("NETWORK:CONNECT:" + url);
                        return this.connect();
                    };
                } catch (e) {
                    console.log("HttpURLConnection hook failed: " + e);
                }

                // Hook java.net.Socket
                try {
                    var SocketClass = Java.use("java.net.Socket");
                    SocketClass.$init.overload("java.lang.String", "int").implementation = function (host, port) {
                        send({type: "NETWORK", action: "SOCKET_CONNECT", host: host, port: port});
                        console.log("NETWORK:SOCKET:" + host + ":" + port);
                        return this.$init(host, port);
                    };
                } catch (e) {
                    console.log("Socket hook failed: " + e);
                }

                console.log("Network intercept script loaded");
            });
            """;
    }

    /**
     * 生成类枚举脚本
     */
    private String generateEnumerateClassesScript() {
        return """
            Java.perform(function () {
                var classes = Java.enumerateLoadedClasses();
                classes.forEach(function (className) {
                    console.log("CLASS:" + className);
                });
                console.log("Total classes: " + classes.length);
            });
            """;
    }

    /**
     * 生成字符串提取脚本
     */
    private String generateStringExtractionScript() {
        return """
            Java.perform(function () {
                var classes = Java.enumerateLoadedClasses();
                var stringCount = 0;

                classes.forEach(function (className) {
                    try {
                        var clz = Java.use(className);
                        var methods = clz.class.getDeclaredMethods();

                        methods.forEach(function (method) {
                            var methodStr = method.toString();

                            // 查找包含字符串常量的方法
                            var stringPattern = /"([^"]{4,100})"/g;
                            var match;
                            while ((match = stringPattern.exec(methodStr)) !== null) {
                                console.log("STRING:" + match[1]);
                                stringCount++;
                            }
                        });
                    } catch (e) {
                        // 忽略访问错误
                    }
                });

                console.log("Total strings extracted: " + stringCount);
            });
            """;
    }

    // ========== 执行和解析方法 ==========

    /**
     * 执行Frida脚本
     */
    private String executeFridaScript(String deviceSerial, String packageName,
                                      String script, int timeoutSeconds) throws Exception {
        // 创建临时脚本文件
        Path scriptFile = Files.createTempFile("frida_script_", ".js");
        Files.write(scriptFile, script.getBytes());

        try {
            // 构建Frida命令
            String fridaHost = FRIDA_SERVER_HOST + ":" + FRIDA_SERVER_PORT;
            String[] commands = {
                "frida",
                "-H", fridaHost,
                "-d",
                "-l", scriptFile.toString(),
                "-f", packageName,
                "--no-pause"
            };

            ProcessBuilder pb = new ProcessBuilder(Arrays.asList(commands));
            pb.redirectErrorStream(true);
            pb.environment().put("ANDROID_SERIAL", deviceSerial);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);

                while (System.currentTimeMillis() < endTime) {
                    if (process.waitFor(100, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                    while (reader.ready()) {
                        line = reader.readLine();
                        if (line != null) {
                            output.append(line).append("\n");
                            // 检查是否收到足够数据
                            if (output.toString().contains("Monitor script loaded") ||
                                output.toString().contains("Crypto hook script loaded") ||
                                output.toString().contains("Network intercept script loaded")) {
                                break;
                            }
                        }
                    }
                }
            }

            // 终止进程
            if (process.isAlive()) {
                process.destroy();
            }

            return output.toString();

        } finally {
            Files.deleteIfExists(scriptFile);
        }
    }

    /**
     * 解析Frida输出
     */
    private List<Map<String, Object>> parseFridaOutput(String output) {
        List<Map<String, Object>> events = new ArrayList<>();

        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("NETWORK:") || line.startsWith("FILE:") ||
                line.startsWith("ACTIVITY:") || line.startsWith("DATA:")) {
                Map<String, Object> event = new HashMap<>();
                event.put("time", System.currentTimeMillis());

                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    event.put("type", parts[0]);
                    event.put("action", parts[1]);

                    if (parts.length >= 3) {
                        event.put("data", parts[2]);
                    }
                }

                events.add(event);
            }
        }

        return events;
    }

    /**
     * 解析加密发现
     */
    private List<Map<String, Object>> parseCryptoFindings(String output) {
        List<Map<String, Object>> findings = new ArrayList<>();

        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("CRYPTO:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    Map<String, Object> finding = new HashMap<>();
                    finding.put("time", System.currentTimeMillis());
                    finding.put("category", parts[1]);
                    finding.put("algorithm", parts[2]);
                    if (parts.length >= 4) {
                        finding.put("details", parts[3]);
                    }
                    findings.add(finding);
                }
            }
        }

        return findings;
    }

    /**
     * 解析网络请求
     */
    private List<Map<String, Object>> parseNetworkRequests(String output) {
        List<Map<String, Object>> requests = new ArrayList<>();

        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("NETWORK:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    Map<String, Object> request = new HashMap<>();
                    request.put("time", System.currentTimeMillis());
                    request.put("action", parts[1]);

                    if (parts[1].equals("REQUEST")) {
                        request.put("method", parts[2]);
                        request.put("url", parts[3]);
                    } else if (parts[1].equals("SOCKET")) {
                        request.put("host", parts[2]);
                        request.put("port", Integer.parseInt(parts[3]));
                    } else {
                        request.put("url", parts[2]);
                    }

                    requests.add(request);
                }
            }
        }

        return requests;
    }
}
