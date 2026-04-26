package com.aezer0.initialization.service.ai;


import com.aezer0.initialization.utils.DocumentUtils;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DocumentService {

    public static final long MAX_UPLOAD_SIZE = 100 * 1024 * 1024L;
    public static final String SUPPORTED_FILE_TYPES_TEXT = ".md、.txt、.doc、.docx、.pdf";
    
    @Autowired
    private PgVectorEmbeddingStore pgVectorEmbeddingStore;
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @Autowired
    private UserVectorService userVectorService;
    
    @Autowired
    private SharedKnowledgeBaseVectorService sharedKnowledgeBaseVectorService;
    
    /**
     * 通用文档上传和处理方法
     * 支持个人知识库和共享知识库，不限制文件类型
     * 
     * @param file 上传的文件
     * @param knowledgeBaseId 知识库ID（个人知识库为用户ID，共享知识库为共享知识库ID）
     * @param knowledgeBaseType 知识库类型：1-个人知识库 2-共享知识库
     * @param fileId 文件ID
     * @return 处理结果
     */
    public Map<String, Object> uploadAndProcessDocument(MultipartFile file, Long knowledgeBaseId, Integer knowledgeBaseType, Long fileId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证文件类型
            if (!isValidFileType(file)) {
                result.put("success", false);
                result.put("message", "不支持的文件类型，当前支持：" + SUPPORTED_FILE_TYPES_TEXT);
                return result;
            }
            
            // 解析文档（支持多种文件格式）
            // 预先读取文件字节，避免上传中间临时文件被移动或删除导致解析失败
            byte[] fileBytes = file.getBytes();

            // 使用字节数组进行解析，支持多种文件格式
            List<Document> documents = parseDocumentFromBytes(fileBytes, file.getOriginalFilename(), file.getContentType());
            
            if (documents.isEmpty()) {
                result.put("success", false);
                result.put("message", "文档解析失败，请检查文件内容");
                return result;
            }
            
            // 根据知识库类型选择处理方式
            int processedCount;
            if (knowledgeBaseType == 1) {
                // 个人知识库
                processedCount = processAndStoreDocumentsForUser(String.valueOf(knowledgeBaseId), documents, file.getOriginalFilename(), fileId);
            } else if (knowledgeBaseType == 2) {
                // 共享知识库
                processedCount = processAndStoreDocumentsForSharedKnowledgeBase(knowledgeBaseId, documents, file.getOriginalFilename(), fileId);
            } else {
                result.put("success", false);
                result.put("message", "无效的知识库类型");
                return result;
            }
            
            result.put("success", true);
            result.put("message", "文档上传并处理成功");
            result.put("documentCount", processedCount);
            result.put("fileName", file.getOriginalFilename());
            result.put("knowledgeBaseId", knowledgeBaseId);
            result.put("knowledgeBaseType", knowledgeBaseType);
            result.put("fileId", fileId);
            
            log.info("成功处理文档: {}, 知识库ID: {}, 类型: {}, 分割成 {} 个片段，文件ID: {}", 
                    file.getOriginalFilename(), knowledgeBaseId, knowledgeBaseType == 1 ? "个人" : "共享", processedCount, fileId);
            
        } catch (Exception e) {
            log.error("处理文档时发生错误: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文档处理失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 个人知识库文档上传和处理（兼容方法）
     * 
     * @param file 上传的文件
     * @param userId 用户ID
     * @param fileId 文件ID
     * @return 处理结果
     */
    public Map<String, Object> uploadAndProcessDocument(MultipartFile file, String userId, Long fileId) {
        return uploadAndProcessDocument(file, Long.parseLong(userId), 1, fileId);
    }
    
    /**
     * 个人知识库文档上传和处理（兼容方法，无文件ID）
     * 
     * @param file 上传的文件
     * @param userId 用户ID
     * @return 处理结果
     */
    public Map<String, Object> uploadAndProcessDocument(MultipartFile file, String userId) {
        return uploadAndProcessDocument(file, Long.parseLong(userId), 1, null);
    }
    
    /**
     * 共享知识库文档上传和处理（兼容方法）
     * 
     * @param file 上传的文件
     * @param knowledgeBaseId 共享知识库ID
     * @param fileId 文件ID
     * @return 处理结果
     */
    public Map<String, Object> uploadAndProcessSharedKnowledgeBaseDocument(MultipartFile file, Long knowledgeBaseId, Long fileId) {
        return uploadAndProcessDocument(file, knowledgeBaseId, 2, fileId);
    }

    /**
     * 使用字节数组上传并处理文档（避免临时文件被清理的问题）
     * 
     * @param fileBytes 文件字节数组
     * @param fileName 文件名
     * @param contentType 文件类型
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeBaseType 知识库类型：1-个人知识库 2-共享知识库
     * @param fileId 文件ID
     * @return 处理结果
     */
    public Map<String, Object> uploadAndProcessDocumentFromBytes(byte[] fileBytes, String fileName, String contentType, 
                                                                Long knowledgeBaseId, Integer knowledgeBaseType, Long fileId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证文件数据
            if (fileBytes == null || fileBytes.length == 0) {
                result.put("success", false);
                result.put("message", "文件数据为空");
                return result;
            }
            
            if (fileName == null || fileName.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "文件名不能为空");
                return result;
            }
            
            // 验证文件类型
            if (!isValidFileTypeByName(fileName)) {
                result.put("success", false);
                result.put("message", "不支持的文件类型，当前支持：" + SUPPORTED_FILE_TYPES_TEXT);
                return result;
            }
            
            // 解析文档（支持多种文件格式）
            List<Document> documents = parseDocumentFromBytes(fileBytes, fileName, contentType);
            
            if (documents.isEmpty()) {
                result.put("success", false);
                result.put("message", "文档解析失败，请检查文件内容");
                return result;
            }
            
            // 根据知识库类型选择处理方式
            int processedCount;
            if (knowledgeBaseType == 1) {
                // 个人知识库
                processedCount = processAndStoreDocumentsForUser(String.valueOf(knowledgeBaseId), documents, fileName, fileId);
            } else if (knowledgeBaseType == 2) {
                // 共享知识库
                processedCount = processAndStoreDocumentsForSharedKnowledgeBase(knowledgeBaseId, documents, fileName, fileId);
            } else {
                result.put("success", false);
                result.put("message", "无效的知识库类型");
                return result;
            }
            
            result.put("success", true);
            result.put("message", "文档上传并处理成功");
            result.put("documentCount", processedCount);
            result.put("fileName", fileName);
            result.put("knowledgeBaseId", knowledgeBaseId);
            result.put("knowledgeBaseType", knowledgeBaseType);
            result.put("fileId", fileId);
            
            log.info("成功处理文档: {}, 知识库ID: {}, 类型: {}, 分割成 {} 个片段，文件ID: {}", 
                    fileName, knowledgeBaseId, knowledgeBaseType == 1 ? "个人" : "共享", processedCount, fileId);
            
        } catch (Exception e) {
            log.error("处理文档时发生错误: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文档处理失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 直接处理Markdown文本并存储到个人知识库（不需要文件上传）
     *
     * @param markdownText MD文本内容
     * @param title        标题（用于元数据）
     * @param userId       用户ID
     * @param fileId       关联的文件记录ID
     * @return 处理结果
     */
    public Map<String, Object> processMarkdownTextForUser(String markdownText, String title, Long userId, Long fileId) {
        Map<String, Object> result = new HashMap<>();
        try {
            byte[] bytes = markdownText.getBytes(StandardCharsets.UTF_8);
            String fileName = title + ".md";
            List<Document> documents = parseDocumentFromBytes(bytes, fileName, "text/markdown");
            if (documents.isEmpty()) {
                result.put("success", false);
                result.put("message", "文档解析失败");
                return result;
            }
            int count = processAndStoreDocumentsForUser(String.valueOf(userId), documents, fileName, fileId);
            result.put("success", true);
            result.put("documentCount", count);
        } catch (Exception e) {
            log.error("处理Markdown文本失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 删除文档的向量数据（通用方法）
     * 
     * @param fileId 文件ID
     * @param knowledgeBaseId 用户id or 知识库ID
     * @param knowledgeBaseType 知识库类型：1-个人知识库 2-共享知识库
     * @return 删除结果
     */
    public Map<String, Object> deleteDocumentVectors(Long fileId, Long knowledgeBaseId, Integer knowledgeBaseType) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            int deletedCount;
            if (knowledgeBaseType == 1) {
                // 个人知识库：使用用户ID进行删除
                deletedCount = userVectorService.deleteDocumentsByFileIdAndUserId(fileId, String.valueOf(knowledgeBaseId));
            } else if (knowledgeBaseType == 2) {
                // 共享知识库：使用知识库ID进行删除
                deletedCount = sharedKnowledgeBaseVectorService.deleteDocumentsByFileIdAndKnowledgeBaseId(fileId, knowledgeBaseId);
            } else {
                result.put("success", false);
                result.put("message", "无效的知识库类型");
                return result;
            }
            
            result.put("success", true);
            result.put("message", "成功删除文档向量数据");
            result.put("deletedCount", deletedCount);
            result.put("fileId", fileId);
            result.put("knowledgeBaseId", knowledgeBaseId);
            result.put("knowledgeBaseType", knowledgeBaseType);
            
            log.info("成功删除文件ID {} {}知识库 {} 的 {} 个向量记录", 
                    fileId, knowledgeBaseType == 1 ? "个人" : "共享", knowledgeBaseId, deletedCount);
            
        } catch (Exception e) {
            log.error("删除文档向量数据失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "删除向量数据失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 删除个人知识库文档向量数据（兼容方法）
     * 
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 删除结果
     */
    public Map<String, Object> deleteDocumentVectors(Long fileId, String userId) {
        return deleteDocumentVectors(fileId, Long.parseLong(userId), 1);
    }

    /**
     * 批量删除用户的所有向量数据
     * @param userId 用户ID
     * @return 删除结果
     */
    public Map<String, Object> deleteUserDocumentVectors(String userId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            int deletedCount = userVectorService.deleteDocumentsByUserId(userId);
            
            result.put("success", true);
            result.put("message", "成功删除用户所有向量数据");
            result.put("deletedCount", deletedCount);
            result.put("userId", userId);
            
            log.info("成功删除用户 {} 的 {} 个向量记录", userId, deletedCount);
            
        } catch (Exception e) {
            log.error("删除用户向量数据失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "删除向量数据失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取文件的向量数据统计
     * @param fileId 文件ID
     * @return 统计结果
     */
    public Map<String, Object> getFileVectorStats(Long fileId) {
        return userVectorService.getFileVectorStats(fileId);
    }

    /**
     * 验证文件类型
     */
    private boolean isValidFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        // 获取文件扩展名
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        
        String[] supportedTypes = {
            "pdf", "doc", "docx", "txt", "md"
        };
        
        for (String supportedType : supportedTypes) {
            if (supportedType.equals(extension)) {
                return true;
            }
        }
        
        log.warn("不支持的文件类型: {}, 扩展名: {}", fileName, extension);
        return false;
    }
    
    /**
     * 解析文档
     */
    private List<Document> parseDocument(MultipartFile file) throws IOException {
        List<Document> documents = new ArrayList<>();

        // 添加文件类型和大小检查
        if (file.isEmpty()) {
            throw new RuntimeException("上传的文件为空");
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new RuntimeException("文件名不能为空");
        }
        
        // 检查文件大小（限制为100MB）
        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new RuntimeException("文件大小超过限制（100MB）");
        }
        
        // 检查文件类型
        String contentType = file.getContentType();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        log.info("开始解析文档: {}, 大小: {} bytes, 类型: {}, 扩展名: {}", 
                fileName, file.getSize(), contentType, extension);

        try (InputStream inputStream = file.getInputStream()) {
            // 验证输入流
            if (inputStream == null) {
                throw new RuntimeException("无法读取文件内容");
            }
            
            // 使用 Apache Tika 解析文档 (支持 PDF, Word, PPT 等多种格式)
            DocumentParser parser = new ApacheTikaDocumentParser();
            Document document;
            
            try {
                document = parser.parse(inputStream);
            } catch (Exception parseException) {
                log.error("Apache Tika解析失败: {}", parseException.getMessage(), parseException);
                throw new RuntimeException("文档解析失败，可能的原因：" +
                        "1. 文件格式不支持（支持PDF、Word、PPT、TXT等）" +
                        "2. 文件已损坏或加密 " +
                        "3. 文件内容为空。" +
                        "详细错误：" + parseException.getMessage());
            }

            // 验证解析结果
            if (!DocumentUtils.isValidDocument(document)) {
                log.warn("解析的文档内容为空: {}", fileName);
                throw new RuntimeException("文档解析成功但内容为空，请检查文件是否包含可读取的文本内容");
            }

            // Tika 会自动提取文件自身的元数据 (如作者、标题等)，我们在此基础上添加自定义元数据
            document.metadata().put("fileName", fileName);
            document.metadata().put("title", fileName);
            document.metadata().put("sourceName", fileName);
            document.metadata().put("fileSize", file.getSize());
            document.metadata().put("uploadTime", System.currentTimeMillis());
            document.metadata().put("contentType", contentType);
            document.metadata().put("extension", extension);

            documents.add(document);

            log.info("成功解析文档: {}, {}", fileName,
                    DocumentUtils.getDocumentSummary(document));

        } catch (IOException ioException) {
            log.error("读取文件时发生IO错误: {}", ioException.getMessage(), ioException);
            throw new RuntimeException("文件读取失败：" + ioException.getMessage());
        } catch (RuntimeException runtimeException) {
            // 重新抛出已经包装好的运行时异常
            throw runtimeException;
        } catch (Exception e) {
            // 捕获其他未预期的异常
            log.error("解析文档 {} 时发生未知错误: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("解析文档时发生未知错误：" + e.getMessage());
        }

        return documents;
    }
    
    /**
     * 从字节数组解析文档
     */
    private List<Document> parseDocumentFromBytes(byte[] fileBytes, String fileName, String contentType) {
        List<Document> documents = new ArrayList<>();
        
        // 检查文件大小（限制为50MB）
        long maxSize = MAX_UPLOAD_SIZE;
        if (fileBytes.length > maxSize) {
            throw new RuntimeException("文件大小超过限制（100MB）");
        }
        
        // 检查文件类型
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        log.info("开始解析文档: {}, 大小: {} bytes, 类型: {}, 扩展名: {}", 
                fileName, fileBytes.length, contentType, extension);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            DocumentParser parser;
            
            // 根据文件类型选择不同的解析器
            if ("txt".equals(extension)) {
                // 对TXT文件明确使用UTF-8编码
                log.info("使用 TextDocumentParser (UTF-8) 解析TXT文件: {}", fileName);
                parser = new TextDocumentParser(StandardCharsets.UTF_8);
            } else {
                // 其他文件类型使用 Tika 自动检测
                log.info("使用 ApacheTikaDocumentParser 解析文件: {}", fileName);
                parser = new ApacheTikaDocumentParser();
            }

            Document document;
            
            try {
                document = parser.parse(inputStream);
            } catch (Exception parseException) {
                log.error("Apache Tika解析失败: {}", parseException.getMessage(), parseException);
                throw new RuntimeException("文档解析失败，可能的原因：" +
                        "1. 文件格式不支持（支持PDF、Word、PPT、TXT等）" +
                        "2. 文件已损坏或加密 " +
                        "3. 文件内容为空。" +
                        "详细错误：" + parseException.getMessage());
            }

            // 验证解析结果
            if (!DocumentUtils.isValidDocument(document)) {
                log.warn("解析的文档内容为空: {}", fileName);
                throw new RuntimeException("文档解析成功但内容为空，请检查文件是否包含可读取的文本内容");
            }

            // 添加元数据
            document.metadata().put("fileName", fileName);
            document.metadata().put("title", fileName);
            document.metadata().put("sourceName", fileName);
            document.metadata().put("fileSize", fileBytes.length);
            document.metadata().put("uploadTime", System.currentTimeMillis());
            document.metadata().put("contentType", contentType);
            document.metadata().put("extension", extension);

            documents.add(document);

            log.info("成功解析文档: {}, {}", fileName,
                    DocumentUtils.getDocumentSummary(document));

        } catch (RuntimeException runtimeException) {
            // 重新抛出已经包装好的运行时异常
            throw runtimeException;
        } catch (Exception e) {
            // 捕获其他未预期的异常
            log.error("解析文档 {} 时发生未知错误: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("解析文档时发生未知错误：" + e.getMessage());
        }

        return documents;
    }
    
    /**
     * 处理和存储文档
     */
    private int processAndStoreDocuments(List<Document> documents, String fileName) {
        return processAndStoreDocumentsForUser("default", documents, fileName, null);
    }
    
    /**
     * 获取已存储的文档统计信息
     */
    public Map<String, Object> getDocumentStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 这里可以添加查询PostgreSQL获取统计信息的逻辑
            // 暂时返回基本信息
            stats.put("success", true);
            stats.put("message", "文档统计信息获取成功");
            stats.put("totalDocuments", "暂未实现统计功能");
            
        } catch (Exception e) {
            log.error("获取文档统计信息时发生错误: {}", e.getMessage());
            stats.put("success", false);
            stats.put("message", "获取统计信息失败: " + e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * 批量上传并处理多个文档（通用方法）
     * 
     * @param files 上传的文件列表
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeBaseType 知识库类型：1-个人知识库 2-共享知识库
     * @return 处理结果
     */
    public Map<String, Object> uploadAndProcessDocuments(List<MultipartFile> files, Long knowledgeBaseId, Integer knowledgeBaseType) {
        Map<String, Object> result = new HashMap<>();
        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        int totalDocuments = 0;
        
        for (MultipartFile file : files) {
            try {
                // 解析文档（支持多种文件格式）
                List<Document> documents = parseDocument(file);
                if (documents.isEmpty()) {
                    failedFiles.add(file.getOriginalFilename() + " (解析失败)");
                    continue;
                }
                
                // 根据知识库类型处理文档
                int processedCount;
                if (knowledgeBaseType == 1) {
                    processedCount = processAndStoreDocumentsForUser(String.valueOf(knowledgeBaseId), documents, file.getOriginalFilename(), null);
                } else if (knowledgeBaseType == 2) {
                    processedCount = processAndStoreDocumentsForSharedKnowledgeBase(knowledgeBaseId, documents, file.getOriginalFilename(), null);
                } else {
                    failedFiles.add(file.getOriginalFilename() + " (无效的知识库类型)");
                    continue;
                }
                
                successFiles.add(file.getOriginalFilename());
                totalDocuments += processedCount;
                
            } catch (Exception e) {
                log.error("处理文档 {} 时发生错误: {}", file.getOriginalFilename(), e.getMessage());
                failedFiles.add(file.getOriginalFilename() + " (处理异常)");
            }
        }
        
        result.put("success", failedFiles.isEmpty());
        result.put("totalFiles", files.size());
        result.put("successFiles", successFiles);
        result.put("failedFiles", failedFiles);
        result.put("totalDocuments", totalDocuments);
        result.put("knowledgeBaseId", knowledgeBaseId);
        result.put("knowledgeBaseType", knowledgeBaseType);
        
        log.info("批量处理完成，{}知识库 {}，成功: {}, 失败: {}, 总文档片段: {}", 
                knowledgeBaseType == 1 ? "个人" : "共享", knowledgeBaseId, 
                successFiles.size(), failedFiles.size(), totalDocuments);
        
        return result;
    }
    
    /**
     * 批量上传并处理个人知识库文档（兼容方法）
     * 
     * @param files 上传的文件列表
     * @param userId 用户ID
     * @return 处理结果
     */
    public Map<String, Object> uploadAndProcessDocuments(List<MultipartFile> files, String userId) {
        return uploadAndProcessDocuments(files, Long.parseLong(userId), 1);
    }
    
    /**
     * 批量上传并处理文档（兼容旧版本，默认个人知识库）
     * 
     * @param files 上传的文件列表
     * @return 处理结果
     */
    public Map<String, Object> uploadAndProcessDocuments(List<MultipartFile> files) {
        return uploadAndProcessDocuments(files, 1L, 1); // 默认用户ID为1
    }
    
    /**
     * 为指定用户处理和存储文档到个人知识库
     * 
     * @param userId 用户ID，将作为个人知识库的knowledge_base_id
     * @param documents 要处理的文档列表
     * @param fileName 文件名
     * @param fileId 文件ID，用于关联文件记录
     * @return 处理成功的文档片段数量
     */
    private int processAndStoreDocumentsForUser(String userId, List<Document> documents, String fileName, Long fileId) {
        try {
            List<Document> splitDocuments;
            
            try {
                // 尝试使用分割器
                DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);
                splitDocuments = DocumentUtils.splitDocuments(splitter, documents);
            } catch (Exception e) {
                throw new RuntimeException("文档分割失败，请检查文档内容或联系管理员");
            }
            
            if (splitDocuments.isEmpty()) {
                log.warn("文档分割后没有有效片段: {}", fileName);
                return 0;
            }
            
            // 存储到用户的个人知识库
            // 注意：这里的 userId 将被当作个人知识库的 knowledge_base_id
            if (fileId != null) {
                userVectorService.addDocumentsForUser(userId, splitDocuments, fileId);
            } else {
                userVectorService.addDocumentsForUser(userId, splitDocuments);
            }
            
            log.info("成功将文档 {} 的 {} 个片段存储到用户 {} 的个人知识库，文件ID: {}", 
                    fileName, splitDocuments.size(), userId, fileId);
            return splitDocuments.size();
            
        } catch (Exception e) {
            log.error("为用户 {} 处理和存储文档到个人知识库时发生错误: {}, 文件: {}", userId, e.getMessage(), fileName, e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 为共享知识库处理和存储文档
     * 这个方法专门用于处理共享知识库的文档上传
     * 
     * @param knowledgeBaseId 共享知识库ID
     * @param documents 要处理的文档列表
     * @param fileName 文件名
     * @param fileId 文件ID，用于关联文件记录
     * @return 处理成功的文档片段数量
     */
    public int processAndStoreDocumentsForSharedKnowledgeBase(Long knowledgeBaseId, List<Document> documents, String fileName, Long fileId) {
        try {
            List<Document> splitDocuments;
            
            try {
                // 尝试使用分割器
                DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);
                splitDocuments = DocumentUtils.splitDocuments(splitter, documents);
            } catch (Exception e) {
                throw new RuntimeException("文档分割失败，请检查文档内容或联系管理员");
            }
            
            if (splitDocuments.isEmpty()) {
                log.warn("文档分割后没有有效片段: {}", fileName);
                return 0;
            }
            
            // 使用SharedKnowledgeBaseVectorService存储到共享知识库
            sharedKnowledgeBaseVectorService.addDocumentsToSharedKnowledgeBase(knowledgeBaseId, splitDocuments, fileId);
            
            log.info("成功将文档 {} 的 {} 个片段存储到共享知识库 {}，文件ID: {}", 
                    fileName, splitDocuments.size(), knowledgeBaseId, fileId);
            return splitDocuments.size();
            
        } catch (Exception e) {
            log.error("为共享知识库 {} 处理和存储文档时发生错误: {}, 文件: {}", knowledgeBaseId, e.getMessage(), fileName, e);
            throw new RuntimeException("共享知识库文档处理失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取知识库统计信息（通用方法）
     * 
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeBaseType 知识库类型：1-个人知识库 2-共享知识库
     * @return 统计信息
     */
    public Map<String, Object> getKnowledgeBaseStats(Long knowledgeBaseId, Integer knowledgeBaseType) {
        if (knowledgeBaseType == 1) {
            // 个人知识库统计
            return userVectorService.getUserStats(String.valueOf(knowledgeBaseId));
        } else if (knowledgeBaseType == 2) {
            // 共享知识库统计
            return sharedKnowledgeBaseVectorService.getSharedKnowledgeBaseStats(knowledgeBaseId);
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "无效的知识库类型");
            return result;
        }
    }
    
    /**
     * 获取用户个人知识库统计信息（兼容方法）
     * 
     * @param userId 用户ID
     * @return 统计信息
     */
    public Map<String, Object> getUserDocumentStats(String userId) {
        return getKnowledgeBaseStats(Long.parseLong(userId), 1);
    }

    /**
     * 根据文件名验证文件类型
     */
    private boolean isValidFileTypeByName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        // 获取文件扩展名
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        
        String[] supportedTypes = {
            "pdf", "doc", "docx", "txt", "md"
        };
        
        for (String supportedType : supportedTypes) {
            if (supportedType.equals(extension)) {
                return true;
            }
        }
        
        log.warn("不支持的文件类型: {}, 扩展名: {}", fileName, extension);
        return false;
    }
}