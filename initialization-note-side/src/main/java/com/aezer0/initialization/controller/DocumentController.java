package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aezer0.initialization.domain.FileUpInfo;
import com.aezer0.initialization.dto.FileListRequestDTO;
import com.aezer0.initialization.result.PageResult;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.FileInfoService;
import com.aezer0.initialization.service.ai.DocumentService;
import com.aezer0.initialization.utils.DocumentToPdfUtils;
import com.aezer0.initialization.utils.PdfUtils;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.FileUploadVO;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@Slf4j
public class DocumentController {
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private FileInfoService fileInfoService;
    
    /**
     * 上传单个文档（自动获取当前用户）
     */
    /**
     * 上传单个文档（自动获取当前用户）
     * 支持 .md, .txt, .doc, .docx, .pdf 格式。
     * 非PDF格式文件将自动转换为PDF后进行处理和存储。
     */
    @SaCheckLogin
    @PostMapping("/upload")
    public Result<FileUploadVO> uploadDocument(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "category", required = false) String category) {
        log.info("接收到文档上传请求: {}, 分类: {}", file.getOriginalFilename(), category);

        try {
            // 获取当前登录用户ID和作者信息
            Long userId = Long.valueOf(StpUtil.getLoginId().toString());
            String author = UserUtils.getCurrentUser().getRealName();
            // 参数校验
            if (file.isEmpty()) {
                return Result.error("上传的文件不能为空");
            }

            // 文件大小校验（100MB）
            if (file.getSize() > 100 * 1024 * 1024) {
                return Result.error("文件大小不能超过100MB");
            }

            // 1. 预先读取原始文件字节到内存
            byte[] originalFileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();

            // 2. 根据文件类型进行转换，准备用于存储和处理的内容
            byte[] bytesToUploadAndProcess;
            String filenameToUploadAndProcess;
            String contentTypeToUploadAndProcess = file.getContentType();

            String fileExtension = getFileExtension(originalFilename);
            String baseFilename = getFileNameWithoutExtension(originalFilename);

            switch (fileExtension.toLowerCase()) {
                case "md":
                    log.info("检测到Markdown文件，保留原始MD内容上传与处理。");
                    bytesToUploadAndProcess = originalFileBytes;
                    filenameToUploadAndProcess = originalFilename;
                    contentTypeToUploadAndProcess = "text/markdown";
                    break;
                case "txt":
                    log.info("检测到TXT文件，保留原始TXT内容上传与处理。");
                    bytesToUploadAndProcess = originalFileBytes;
                    filenameToUploadAndProcess = originalFilename;
                    contentTypeToUploadAndProcess = "text/plain";
                    break;
                case "docx":
                    log.info("检测到DOCX文件，开始转换为PDF...");
                    try (InputStream is = new ByteArrayInputStream(originalFileBytes)) {
                        bytesToUploadAndProcess = DocumentToPdfUtils.convertDocToPdf(baseFilename, is, author);
                    }
                    filenameToUploadAndProcess = baseFilename + ".pdf";
                    contentTypeToUploadAndProcess = "application/pdf";
                    break;
                case "doc":
                    log.info("检测到DOC文件，开始转换为PDF (仅文本内容)...");
                    try (InputStream is = new ByteArrayInputStream(originalFileBytes)) {
                        bytesToUploadAndProcess = DocumentToPdfUtils.convertDocToPdf(baseFilename, is, author);
                    }
                    filenameToUploadAndProcess = baseFilename + ".pdf";
                    contentTypeToUploadAndProcess = "application/pdf";
                    break;
                case "pdf":
                    log.info("检测到PDF文件，无需转换。");
                    bytesToUploadAndProcess = originalFileBytes;
                    filenameToUploadAndProcess = originalFilename;
                    contentTypeToUploadAndProcess = "application/pdf";
                    break;
                default:
                    log.warn("不支持的文件类型上传: {}", fileExtension);
                    return Result.error("不支持的文件类型: " + fileExtension + "。当前支持：" + DocumentService.SUPPORTED_FILE_TYPES_TEXT);
            }

            // 3. 创建一个可重复读取的 MultipartFile 实例，用于文件存储服务
            MultipartFile uploadMultipartFile = new PdfUtils.CustomMultipartFile(
                    bytesToUploadAndProcess,
                    filenameToUploadAndProcess,
                    contentTypeToUploadAndProcess
            );

            // 4. 使用处理后的 MultipartFile 实例来保存文件信息
            FileUpInfo fileInfo = fileInfoService.uploadFile(uploadMultipartFile, userId);
            if (category != null && !category.trim().isEmpty()) {
                fileInfo.setCategory(category.trim());
                fileInfoService.updateById(fileInfo);
            }
            log.info("转换后的PDF文件信息已保存到文件存储服务，文件ID: {}", fileInfo.getId());

            // 5. 使用转换后的PDF字节数组来处理文档并存储到向量数据库
            Map<String, Object> processResult = documentService.uploadAndProcessDocumentFromBytes(
                    bytesToUploadAndProcess,
                    filenameToUploadAndProcess,
                    contentTypeToUploadAndProcess,
                    userId,         // 个人知识库ID
                    1,              // 知识库类型: 1-个人
                    fileInfo.getId() // 关联文件存储服务中PDF文件的记录ID
            );

            // 6. 构建返回结果
            FileUploadVO result = new FileUploadVO();
            // 注意：这里复制的是 fileInfo 的属性，fileInfo 现在代表的是转换后的PDF文件信息
            BeanUtils.copyProperties(fileInfo, result);

            // 添加文档处理信息
            if ((Boolean) processResult.get("success")) {
                result.setDocumentCount((Integer) processResult.get("documentCount"));
                result.setProcessingStatus("SUCCESS");
                result.setProcessingMessage("文档上传并处理成功");
            } else {
                result.setProcessingStatus("FAILED");
                result.setProcessingMessage((String) processResult.get("message"));
            }

            log.info("用户 {} 上传文档成功: {}, 文件ID: {}, 处理状态: {}", userId, originalFilename, fileInfo.getId(), result.getProcessingStatus());
            return Result.success(result);

        } catch (IOException e) {
            log.error("读取文件字节失败: {}", e.getMessage(), e);
            return Result.error("文件读取失败: " + e.getMessage());
        } catch (RuntimeException e) { // 捕获自定义的运行时异常，例如PDF生成失败
            log.error("文档转换或处理失败: {}", e.getMessage(), e);
            return Result.error("文档转换或处理失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("文档上传处理发生未知错误: {}", e.getMessage(), e);
            return Result.error("文档上传失败: " + e.getMessage());
        }
    }



    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * 获取不带扩展名的文件名
     */
    private String getFileNameWithoutExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex == -1) {
            return filename;
        }
        return filename.substring(0, dotIndex);
    }
    /**
     * 获取用户上传的文件列表（POST请求，使用JSON请求体）
     */
    @SaCheckLogin
    @PostMapping("/files/list")
    public Result<PageResult<FileUpInfo>> getUserFiles(@RequestBody FileListRequestDTO request) {
        try {
            // 获取当前登录用户ID
            Long userId = Long.valueOf(StpUtil.getLoginId().toString());

            log.info("获取用户 {} 的文件列表，请求参数: {}", userId, request);

            // --- 1. 参数校验和默认值处理 (利用 DTO 的初始值和业务层的二次校验) ---
            int pageNum = request.getPage();
            int pageSize = request.getSize();

            if (pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize < 1 || pageSize > 100) {
                pageSize = 10;
            }

            // 获取 DTO 中已经处理过默认值的排序字段和方向
            String finalSortBy = request.getSortBy();
            String finalSortOrder = request.getSortOrder(); // 已经转为小写

            // --- 2. 链式构建 QueryWrapper ---
            QueryWrapper<FileUpInfo> queryWrapper = new QueryWrapper<FileUpInfo>()
                    .eq("user_id", userId); // 用户ID是强制条件

            // 关键字查询：使用 and(condition, consumer) 方法，当 condition 为 true 时，才执行 consumer 里的逻辑
            queryWrapper.and(StringUtils.hasText(request.getKeyword()), wrapper -> wrapper
                    .like("original_filename", request.getKeyword())
                    .or()
                    .like("file_name", request.getKeyword())
            );

            // 文件类型查询：使用 eq(condition, column, value) 方法
            // todo 再次提醒：请确认 fileType 对应的是 file_extension 字段
            queryWrapper.eq(StringUtils.hasText(request.getFileType()), "file_extension", request.getFileType());

            // 分类查询
            queryWrapper.eq(StringUtils.hasText(request.getCategory()), "category", request.getCategory());

            // 动态排序：首先验证排序字段的安全性，然后使用 orderBy(condition, isAsc, columns) 方法
            if (!isValidSortField(finalSortBy)) {
                finalSortBy = "create_time"; // 如果 DTO 给出的字段无效，则强制使用安全的默认字段
                log.warn("用户 {} 提供的排序字段 {} 无效，已回退到默认字段: create_time", userId, request.getSortBy());
            }

            // isAsc 为 true 表示升序，false 表示降序
            boolean isAsc = "asc".equals(finalSortOrder);
            queryWrapper.orderBy(true, isAsc, finalSortBy); // 第一个 true 表示这个排序条件始终生效

            // --- 3. 分页查询 ---
            Page<FileUpInfo> filePage = new Page<>(pageNum, pageSize); // 使用校验后的页码和大小
            fileInfoService.page(filePage, queryWrapper);

            log.info("用户 {} 的文件列表查询完成，总数: {}", userId, filePage.getTotal());
            return Result.success(PageResult.convert(filePage));

        } catch (Exception e) {
            log.error("获取用户文件列表失败: {}", e.getMessage(), e);
            return Result.error("获取文件列表失败，请稍后重试。");
        }
    }
    /**
     * 删除用户上传的文件
     */
    @SaCheckLogin
    @DeleteMapping("/files/{fileId}")
    public Result<Void> deleteUserFile(@PathVariable @NotNull Long fileId) {
        try {
            // 获取当前登录用户ID
            Long userId = Long.valueOf(StpUtil.getLoginId().toString());
            
            // 查询文件信息
            FileUpInfo fileInfo = fileInfoService.getById(fileId);
            if (fileInfo == null) {
                return Result.error("文件不存在");
            }
            
            // 验证文件所有者
            if (!userId.equals(fileInfo.getUserId())) {
                return Result.error("无权限删除该文件");
            }
            
            // 1. 先删除向量数据库中的相关记录
            try {
                Map<String, Object> vectorDeleteResult = documentService.deleteDocumentVectors(fileId, userId.toString());
                if ((Boolean) vectorDeleteResult.get("success")) {
                    log.info("成功删除文件 {} 的向量数据，删除记录数: {}", 
                            fileId, vectorDeleteResult.get("deletedCount"));
                } else {
                    log.warn("删除文件 {} 的向量数据失败: {}", fileId, vectorDeleteResult.get("message"));
                }
            } catch (Exception e) {
                log.error("删除文件 {} 的向量数据时发生异常: {}", fileId, e.getMessage(), e);
                // 向量数据删除失败不影响文件删除，只记录日志
            }
            
            // 2. 删除文件记录
            fileInfoService.removeById(fileId);
            
            log.info("用户 {} 删除文件成功: {}", userId, fileInfo.getOriginalFilename());
            return Result.success(null);
            
        } catch (Exception e) {
            log.error("删除文件失败: {}", e.getMessage(), e);
            return Result.error("删除文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新文件分类
     */
    @SaCheckLogin
    @PatchMapping("/files/{fileId}/category")
    public Result<Void> updateFileCategory(@PathVariable @NotNull Long fileId,
                                           @RequestParam String category) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        FileUpInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            return Result.error("文件不存在或无权限");
        }
        fileInfo.setCategory(category);
        fileInfoService.updateById(fileInfo);
        return Result.success(null);
    }

    /**
     * 测试文档解析功能
     */
    @SaIgnore
    @PostMapping("/test-parse")
    public Result<Map<String, Object>> testDocumentParsing(@RequestParam("file") MultipartFile file) {
        try {
            // 简单的文件验证
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            log.info("开始测试文档解析，文件名: {}, 大小: {} bytes", 
                    file.getOriginalFilename(), file.getSize());
            
            // 调用文档服务进行解析测试
            Map<String, Object> result = documentService.uploadAndProcessDocument(file, 1L, 1, null);
            
            if ((Boolean) result.get("success")) {
                log.info("文档解析测试成功");
                return Result.success(result);
            } else {
                log.error("文档解析测试失败: {}", result.get("message"));
                return Result.error(result.get("message").toString());
            }
            
        } catch (Exception e) {
            log.error("测试文档解析时发生异常: {}", e.getMessage(), e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证排序字段是否有效
     */
    private boolean isValidSortField(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return false;
        }

        // 定义允许的排序字段（数据库字段名）
        String[] validFields = {
                "id", "create_time", "update_time", "file_size",
                "original_filename", "file_name", "file_type", "mime_type", "file_hash"
        };

        for (String field : validFields) {
            if (field.equals(sortBy)) {
                return true;
            }
        }

        return false;
    }   

}