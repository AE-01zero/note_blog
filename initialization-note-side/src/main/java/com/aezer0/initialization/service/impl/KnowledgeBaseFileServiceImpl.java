package com.aezer0.initialization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.ai.PgVectorConnectionPool;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.FileUpInfo;
import com.aezer0.initialization.domain.KnowledgeBaseFile;
import com.aezer0.initialization.dto.KnowledgeBaseFileUploadDTO;
import com.aezer0.initialization.enums.FileSourceTypeEnum;
import com.aezer0.initialization.mapper.KnowledgeBaseFileMapper;
import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.service.FileInfoService;
import com.aezer0.initialization.service.KnowledgeBaseFileService;
import com.aezer0.initialization.service.SharedKnowledgeBaseService;
import com.aezer0.initialization.service.ai.DocumentService;
import com.aezer0.initialization.service.ai.SharedKnowledgeBaseVectorService;
import com.aezer0.initialization.utils.DocumentToPdfUtils;
import com.aezer0.initialization.utils.PdfUtils;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.KnowledgeBaseFileVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.IOException;

/**
 * 知识库文件Service实现类
 */
@Service
@Slf4j
public class KnowledgeBaseFileServiceImpl extends ServiceImpl<KnowledgeBaseFileMapper, KnowledgeBaseFile> 
        implements KnowledgeBaseFileService {

    @Autowired
    @Lazy
    private SharedKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private FileInfoService fileInfoService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private SharedKnowledgeBaseVectorService vectorService;

    @Autowired
    private PgVectorConnectionPool connectionPool;

    /**
     * 上传文件到知识库，非PDF格式会自动转换为PDF后处理。
     *
     * @param knowledgeBaseId 知识库ID
     * @param file 要上传的文件
     * @param userId 上传用户ID
     * @return 上传并处理成功的文件信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseFileVO uploadFile(Long knowledgeBaseId, MultipartFile file, Long userId) {
        return uploadFile(knowledgeBaseId, file, userId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseFileVO uploadFile(Long knowledgeBaseId, MultipartFile file, Long userId, String category) {
        // 1. 验证权限
        if (!knowledgeBaseService.hasPermission(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11003, "您不是该知识库的成员");
        }

        try {
            // 获取上传者信息，用于PDF元数据
            String author = UserUtils.getCurrentUser().getRealName();

            // 2. 参数校验
            if (file.isEmpty()) {
                throw new BizException(BizResponseCode.ERROR_1, "上传的文件不能为空");
            }
            if (file.getSize() > DocumentService.MAX_UPLOAD_SIZE) {
                throw new BizException(BizResponseCode.ERROR_1, "文件大小不能超过100MB");
            }

            String originalFilename = file.getOriginalFilename();
            log.info("用户 {} 正在向知识库 {} 上传文件: {}", userId, knowledgeBaseId, originalFilename);

            // 3. 预读原始文件字节到内存
            byte[] originalFileBytes = file.getBytes();

            // 4. 根据文件类型进行转换，准备用于存储和处理的PDF内容
            byte[] bytesToProcess; // 最终用于上传和处理的字节数组
            String filenameToProcess; // 最终用于上传和处理的文件名
            String contentTypeToProcess = "application/pdf"; // 默认PDF，MD文件会覆盖

            // 使用 FilenameUtils 更健壮
            String fileExtension = FilenameUtils.getExtension(originalFilename);
            String baseFilename = FilenameUtils.getBaseName(originalFilename);

            switch (Objects.requireNonNull(fileExtension).toLowerCase()) {
                case "md":
                    log.info("检测到Markdown文件，直接使用原始MD内容。");
                    bytesToProcess = originalFileBytes;
                    filenameToProcess = originalFilename;
                    contentTypeToProcess = "text/markdown";
                    break;
                case "txt":
                    log.info("检测到TXT文件，直接使用原始TXT内容。");
                    bytesToProcess = originalFileBytes;
                    filenameToProcess = originalFilename;
                    contentTypeToProcess = "text/plain";
                    break;
                case "docx":
                    log.info("检测到DOCX文件，开始转换为PDF...");
                    try (InputStream is = new ByteArrayInputStream(originalFileBytes)) {
                        bytesToProcess = DocumentToPdfUtils.convertDocToPdf(baseFilename, is, author);
                    }
                    filenameToProcess = baseFilename + ".pdf";
                    break;
                case "doc":
                    log.info("检测到DOC文件，开始转换为PDF (仅文本内容)...");
                    try (InputStream is = new ByteArrayInputStream(originalFileBytes)) {
                        bytesToProcess = DocumentToPdfUtils.convertDocToPdf(baseFilename, is, author);
                    }
                    filenameToProcess = baseFilename + ".pdf";
                    break;
                case "pdf":
                    log.info("检测到PDF文件，无需转换。");
                    bytesToProcess = originalFileBytes;
                    filenameToProcess = originalFilename;
                    break;
                default:
                    log.warn("不支持的文件类型上传: {}", fileExtension);
                    throw new BizException(BizResponseCode.ERROR_1, "不支持的文件类型: " + fileExtension + "。当前支持：" + DocumentService.SUPPORTED_FILE_TYPES_TEXT);
            }

            // 5. 创建一个可重复读取的 MultipartFile 实例，用于文件存储服务 (现在是PDF)
            MultipartFile pdfMultipartFile = new PdfUtils.CustomMultipartFile(
                    bytesToProcess,
                    filenameToProcess,
                    contentTypeToProcess
            );

            // 6. 上传转换后的PDF文件到文件存储服务
            FileUpInfo fileInfo = fileInfoService.uploadFile(pdfMultipartFile, userId);
            // 若指定了分类，更新 category 字段
            if (category != null && !category.isBlank()) {
                fileInfo.setCategory(category);
                fileInfoService.updateById(fileInfo);
            }
            log.info("转换后的PDF文件已保存，文件ID: {}", fileInfo.getId());


            // 7. 创建知识库文件关联
            KnowledgeBaseFile knowledgeBaseFile = new KnowledgeBaseFile();
            knowledgeBaseFile.setKnowledgeBaseId(knowledgeBaseId);
            knowledgeBaseFile.setFileId(fileInfo.getId()); // 关联的是PDF文件的ID
            knowledgeBaseFile.setUploaderId(userId);
            knowledgeBaseFile.setSourceType(FileSourceTypeEnum.LOCAL_UPLOAD.getValue());
            knowledgeBaseFile.setUploadTime(LocalDateTime.now());
            this.save(knowledgeBaseFile);

            // 8. 处理文档并存储到向量数据库（使用转换后的PDF字节数组）
            try {
                // 注意：这里传递的是转换后的PDF数据
                Map<String, Object> processResult = documentService.uploadAndProcessDocumentFromBytes(
                        bytesToProcess,
                        filenameToProcess,
                        contentTypeToProcess,
                        knowledgeBaseId,
                        2, // 知识库类型: 2-共享 (根据你的业务逻辑)
                        fileInfo.getId() // 关联文件存储服务中PDF文件的记录ID
                );

                if ((Boolean) processResult.get("success")) {
                    knowledgeBaseService.refreshStatistics(knowledgeBaseId);
                    log.info("文件 {} 上传到共享知识库 {} 并处理成功", originalFilename, knowledgeBaseId);
                } else {
                    // 如果向量化失败，可能需要考虑回滚或记录失败状态
                    log.warn("文件 {} (转换后: {}) 向量化处理失败: {}", originalFilename, filenameToProcess, processResult.get("message"));
                    // 可以考虑抛出异常来触发事务回滚
                    throw new BizException(BizResponseCode.ERROR_1, "文件向量化处理失败: " + processResult.get("message"));
                }
            } catch (Exception e) {
                log.error("文件 {} (转换后: {}) 向量化处理时发生异常: {}", originalFilename, filenameToProcess, e.getMessage(), e);
                // 抛出异常以触发事务回滚
                throw new BizException(BizResponseCode.ERROR_1, "文件向量化处理失败: " + e.getMessage());
            }

            // 9. 返回文件详情
            // getFileDetail 方法现在获取的是处理后PDF文件的信息
            return getFileDetail(knowledgeBaseId, fileInfo.getId(), userId);

        } catch (IOException e) {
            log.error("读取上传文件内容失败: {}", e.getMessage(), e);
            throw new BizException(BizResponseCode.ERROR_1, "读取文件内容失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("上传文件到知识库失败: {}", e.getMessage(), e);
            if (e instanceof BizException) {
                throw e; // 直接抛出业务异常
            }
            // 将其他异常包装成业务异常
            throw new BizException(BizResponseCode.ERROR_1, "文件上传失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> copyFilesToKnowledgeBase(KnowledgeBaseFileUploadDTO uploadDTO, Long userId) {
        Map<String, Object> result = new HashMap<>();
        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        // 验证权限
        if (!knowledgeBaseService.hasPermission(uploadDTO.getKnowledgeBaseId(), userId)) {
            throw new BizException(BizResponseCode.ERR_11003, "您不是该知识库的成员");
        }

        if (CollectionUtils.isEmpty(uploadDTO.getFileIds())) {
            throw new BizException(BizResponseCode.ERR_400, "文件ID列表不能为空");
        }

        for (Long fileId : uploadDTO.getFileIds()) {
            try {
                // 检查文件是否存在且属于用户
                FileUpInfo fileInfo = fileInfoService.getById(fileId);
                if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
                    failedFiles.add("文件ID " + fileId + " (文件不存在或无权限)");
                    continue;
                }

                // 检查文件是否已在知识库中
                if (this.baseMapper.existsByKnowledgeBaseIdAndFileId(uploadDTO.getKnowledgeBaseId(), fileId)) {
                    failedFiles.add(fileInfo.getOriginalFilename() + " (已存在)");
                    continue;
                }

                // 添加文件到知识库
                KnowledgeBaseFile knowledgeBaseFile = new KnowledgeBaseFile();
                knowledgeBaseFile.setKnowledgeBaseId(uploadDTO.getKnowledgeBaseId());
                knowledgeBaseFile.setFileId(fileId);
                knowledgeBaseFile.setUploaderId(userId);
                knowledgeBaseFile.setSourceType(uploadDTO.getSourceType());
                this.save(knowledgeBaseFile);

                // 复制向量数据到共享知识库（这里需要实现向量数据复制逻辑）
                try {
                    copyVectorDataToSharedKnowledgeBase(uploadDTO.getKnowledgeBaseId(), fileId, userId);
                } catch (Exception e) {
                    log.warn("复制文件 {} 的向量数据失败: {}", fileInfo.getOriginalFilename(), e.getMessage());
                }

                successFiles.add(fileInfo.getOriginalFilename());

            } catch (Exception e) {
                log.error("复制文件ID {} 到知识库失败: {}", fileId, e.getMessage());
                failedFiles.add("文件ID " + fileId + " (复制失败)");
            }
        }

        result.put("success", failedFiles.isEmpty());
        result.put("totalFiles", uploadDTO.getFileIds().size());
        result.put("successFiles", successFiles);
        result.put("failedFiles", failedFiles);
        result.put("successCount", successFiles.size());
        result.put("failedCount", failedFiles.size());
        if (!successFiles.isEmpty()) {
            knowledgeBaseService.refreshStatistics(uploadDTO.getKnowledgeBaseId());
        }

        log.info("复制文件到共享知识库 {}: 成功 {}, 失败 {}", 
                uploadDTO.getKnowledgeBaseId(), successFiles.size(), failedFiles.size());

        return result;
    }

    @Override
    @Transactional
    public void deleteFile(Long knowledgeBaseId, Long fileId, Long userId) {
        // 验证权限
        if (!knowledgeBaseService.hasPermission(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11003, "您不是该知识库的成员");
        }

        // 检查文件是否在知识库中
        KnowledgeBaseFileVO fileDetail = this.baseMapper.selectFileDetail(knowledgeBaseId, fileId);
        if (fileDetail == null) {
            throw new BizException(BizResponseCode.ERROR_1, "文件不存在于该知识库中");
        }

        // 检查删除权限（创建者可以删除任何文件，成员只能删除自己上传的文件）
        Integer userRole = knowledgeBaseService.getUserRole(knowledgeBaseId, userId);
        if (userRole == null) {
            throw new BizException(BizResponseCode.ERR_11003, "您不是该知识库的成员");
        }

        // 非创建者只能删除自己上传的文件
        if (userRole != 1 && !fileDetail.getUploaderId().equals(userId)) {
            throw new BizException(BizResponseCode.ERR_11004, "您只能删除自己上传的文件");
        }

        // 删除知识库文件关联
        LambdaQueryWrapper<KnowledgeBaseFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseFile::getKnowledgeBaseId, knowledgeBaseId)
               .eq(KnowledgeBaseFile::getFileId, fileId);
        this.remove(wrapper);
        knowledgeBaseService.refreshStatistics(knowledgeBaseId);

        // 删除向量数据
        try {
            vectorService.deleteSharedKnowledgeBaseFileVectors(knowledgeBaseId, fileId);
        } catch (Exception e) {
            log.error("删除文件 {} 在知识库 {} 的向量数据失败: {}", fileId, knowledgeBaseId, e.getMessage());
        }

        log.info("用户 {} 从知识库 {} 删除文件 {} 成功", userId, knowledgeBaseId, fileDetail.getOriginalFilename());
    }

    @Override
    public IPage<KnowledgeBaseFileVO> getKnowledgeBaseFiles(Long knowledgeBaseId, Integer page, Integer size, String keyword, Long userId) {
        // 验证权限
        if (!knowledgeBaseService.hasPermission(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11003, "您不是该知识库的成员");
        }

        Page<KnowledgeBaseFileVO> pageObj = new Page<>(page, size);
        return this.baseMapper.selectFilesByKnowledgeBaseId(pageObj, knowledgeBaseId, keyword);
    }

    @Override
    public List<KnowledgeBaseFileVO> getAllKnowledgeBaseFiles(Long knowledgeBaseId, Long userId) {
        // 验证权限
        if (!knowledgeBaseService.hasPermission(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11003, "您不是该知识库的成员");
        }

        return this.baseMapper.selectAllFilesByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public List<KnowledgeBaseFileVO> getPersonalFiles(Long userId) {
        return this.baseMapper.selectPersonalFiles(userId);
    }

    @Override
    public KnowledgeBaseFileVO getFileDetail(Long knowledgeBaseId, Long fileId, Long userId) {
        // 验证权限
        if (!knowledgeBaseService.hasPermission(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11003, "您不是该知识库的成员");
        }

        KnowledgeBaseFileVO fileDetail = this.baseMapper.selectFileDetail(knowledgeBaseId, fileId);
        if (fileDetail == null) {
            throw new BizException(BizResponseCode.ERROR_1, "文件不存在于该知识库中");
        }

        return fileDetail;
    }

    @Override
    public void updateFileCategory(Long knowledgeBaseId, Long fileId, String category, Long userId) {
        if (!knowledgeBaseService.hasPermission(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11003, "您不是该知识库的成员");
        }
        KnowledgeBaseFileVO detail = this.baseMapper.selectFileDetail(knowledgeBaseId, fileId);
        if (detail == null) {
            throw new BizException(BizResponseCode.ERROR_1, "文件不存在于该知识库中");
        }
        FileUpInfo fileInfo = fileInfoService.getById(detail.getFileId());
        if (fileInfo == null) {
            throw new BizException(BizResponseCode.ERROR_1, "文件信息不存在");
        }
        fileInfo.setCategory(category);
        fileInfoService.updateById(fileInfo);
    }

    @Override
    public boolean isFileInKnowledgeBase(Long knowledgeBaseId, Long fileId) {
        return this.baseMapper.existsByKnowledgeBaseIdAndFileId(knowledgeBaseId, fileId);
    }

    @Override
    public boolean hasFilePermission(Long knowledgeBaseId, Long fileId, Long userId) {
        return this.baseMapper.checkFilePermission(knowledgeBaseId, fileId, userId);
    }

    /**
     * 从个人知识库复制向量数据到共享知识库
     * 
     * @param knowledgeBaseId 共享知识库ID
     * @param fileId 文件ID
     * @param userId 用户ID
     */
    private void copyVectorDataToSharedKnowledgeBase(Long knowledgeBaseId, Long fileId, Long userId) {
        try {
            List<VectorData> vectorDataList = getPersonalVectorDataByFileId(fileId, userId);
            if (vectorDataList.isEmpty()) {
                log.info("No personal vector data found for file {}, skip copy to shared knowledge base {}", fileId, knowledgeBaseId);
                return;
            }
            int copied = copyVectorDataToSharedKB(knowledgeBaseId, vectorDataList, fileId);
            log.info("Copied {} vector records for file {} to shared knowledge base {}", copied, fileId, knowledgeBaseId);
        } catch (Exception e) {
            log.error("Failed to copy vector data for file {} to shared knowledge base {}: {}", fileId, knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("Failed to copy vector data: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从个人知识库获取指定文件的向量数据
     */
    private List<VectorData> getPersonalVectorDataByFileId(Long fileId, Long userId) {
        List<VectorData> vectorDataList = new ArrayList<>();
        
        String sql = """
            SELECT embedding_id, text, metadata, embedding
            FROM documents 
            WHERE file_id = ? AND user_id = ? AND (knowledge_base_type = 1 OR knowledge_base_type IS NULL)
            """;
        
        try (Connection connection = connectionPool.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            
            stmt.setLong(1, fileId);
            stmt.setLong(2, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    VectorData vectorData = new VectorData();
                    vectorData.setEmbeddingId(rs.getString("embedding_id"));
                    vectorData.setText(rs.getString("text"));
                    vectorData.setMetadata(rs.getString("metadata"));
                    vectorData.setEmbedding(rs.getString("embedding"));
                    vectorDataList.add(vectorData);
                }
            }
            
            log.debug("从个人知识库查询到文件 {} 的 {} 条向量数据", fileId, vectorDataList.size());
            
        } catch (SQLException e) {
            log.error("查询个人知识库向量数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询向量数据失败", e);
        }
        
        return vectorDataList;
    }
    
    /**
     * 将向量数据复制到共享知识库
     */
    private int copyVectorDataToSharedKB(Long knowledgeBaseId, List<VectorData> vectorDataList, Long fileId) {
        String sql = """
            INSERT INTO documents (text, metadata, embedding, file_id, knowledge_base_id, knowledge_base_type)
            VALUES (?, ?::jsonb, CAST(? AS vector), ?, ?, ?)
            """;
        
        try (Connection connection = connectionPool.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            
            for (VectorData vectorData : vectorDataList) {
                // 更新元数据，添加共享知识库相关信息
                String updatedMetadata = updateMetadataForSharedKB(vectorData.getMetadata(), knowledgeBaseId);
                
                stmt.setString(1, vectorData.getText());
                stmt.setString(2, updatedMetadata);
                stmt.setString(3, vectorData.getEmbedding());
                stmt.setLong(4, fileId);
                stmt.setLong(5, knowledgeBaseId);
                stmt.setInt(6, 2); // knowledge_base_type = 2 表示共享知识库
                
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            int successCount = 0;
            for (int result : results) {
                if (result > 0) {
                    successCount++;
                }
            }
            
            log.info("成功复制 {} 条向量数据到共享知识库 {}", successCount, knowledgeBaseId);
            return successCount;
            
        } catch (SQLException e) {
            log.error("复制向量数据到共享知识库失败: {}", e.getMessage(), e);
            throw new RuntimeException("复制向量数据失败", e);
        }
    }
    
    /**
     * 更新元数据，添加共享知识库相关信息
     */
    private String updateMetadataForSharedKB(String originalMetadata, Long knowledgeBaseId) {
        try {
            // 解析原始元数据
            Map<String, Object> metadataMap = parseJsonMetadata(originalMetadata);
            
            // 添加共享知识库相关信息
            metadataMap.put("knowledgeBaseId", knowledgeBaseId);
            metadataMap.put("knowledgeBaseType", 2);
            metadataMap.put("copyTime", System.currentTimeMillis());
            metadataMap.put("source", "personalKnowledgeBase");
            
            // 转换回JSON字符串
            return toJsonString(metadataMap);
            
        } catch (Exception e) {
            log.warn("更新元数据失败，使用原始元数据: {}", e.getMessage());
            return originalMetadata;
        }
    }
    
    /**
     * 解析JSON元数据
     */
    private Map<String, Object> parseJsonMetadata(String jsonMetadata) {
        Map<String, Object> metadataMap = new HashMap<>();
        
        if (jsonMetadata == null || jsonMetadata.trim().isEmpty()) {
            return metadataMap;
        }
        
        try {
            // 简单的JSON解析，这里可以根据实际需求使用更复杂的JSON库
            if (jsonMetadata.startsWith("{") && jsonMetadata.endsWith("}")) {
                // 移除首尾的大括号
                String content = jsonMetadata.substring(1, jsonMetadata.length() - 1);
                
                // 简单解析键值对
                String[] pairs = content.split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":");
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replaceAll("\"", "");
                        String value = keyValue[1].trim().replaceAll("\"", "");
                        metadataMap.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析JSON元数据失败: {}", e.getMessage());
        }
        
        return metadataMap;
    }
    
    /**
     * 将Map转换为JSON字符串
     */
    private String toJsonString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value.toString()).append("\"");
            }
            
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }
    

    
    /**
     * 向量数据内部类
     */
    private static class VectorData {
        private String embeddingId;
        private String text;
        private String metadata;
        private String embedding;
        
        public String getEmbeddingId() {
            return embeddingId;
        }
        
        public void setEmbeddingId(String embeddingId) {
            this.embeddingId = embeddingId;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public String getMetadata() {
            return metadata;
        }
        
        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }
        
        public String getEmbedding() {
            return embedding;
        }
        
        public void setEmbedding(String embedding) {
            this.embedding = embedding;
        }
    }
} 
