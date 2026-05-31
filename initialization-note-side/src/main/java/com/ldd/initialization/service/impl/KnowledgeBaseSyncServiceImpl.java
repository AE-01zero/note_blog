package com.ldd.initialization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ldd.initialization.config.exception.BizException;
import com.ldd.initialization.domain.FileUpInfo;
import com.ldd.initialization.domain.SharedKnowledgeBase;
import com.ldd.initialization.domain.User;
import com.ldd.initialization.enums.KnowledgeBaseRoleEnum;
import com.ldd.initialization.mapper.KnowledgeBaseMemberMapper;
import com.ldd.initialization.mapper.SharedKnowledgeBaseMapper;
import com.ldd.initialization.mapper.UserMapper;
import com.ldd.initialization.result.BizResponseCode;
import com.ldd.initialization.service.FileInfoService;
import com.ldd.initialization.service.KnowledgeBaseFileService;
import com.ldd.initialization.service.KnowledgeBaseSyncService;
import com.ldd.initialization.service.SharedKnowledgeBaseService;
import com.ldd.initialization.service.ai.DocumentService;
import com.ldd.initialization.utils.SimpleMultipartFile;
import com.ldd.initialization.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识库同步服务实现类
 * 用于从 knowledge-base 目录自动同步文档到个人知识库和共享知识库
 */
@Service
@Slf4j
public class KnowledgeBaseSyncServiceImpl implements KnowledgeBaseSyncService {

    /**
     * 正则表达式：匹配以数字开头的文件夹名称
     * 支持格式：
     * - "01-AI安全" → "AI安全"
     * - "02-Android安全" → "Android安全"
     * - "1、ai安全" → "AI安全"
     * - "2、信息安全" → "信息安全"
     * - "1_AI安全" → "AI安全"
     */
    private static final Pattern FOLDER_PATTERN_1 = Pattern.compile("^(\\d{2})[-－_](.+)$");  // 01- 或 01－（全角减号）或 01_
    private static final Pattern FOLDER_PATTERN_2 = Pattern.compile("^(\\d+)[、，,](.+)$");     // 1、 或 1，
    private static final Pattern FOLDER_PATTERN_3 = Pattern.compile("^(\\d+)[_](.+)$");        // 1_xxx

    /**
     * 文件名后缀模式：用于移除文件名中的序号后缀
     * 支持格式：
     * - "ai安全-1.md" → "ai安全.md"
     * - "ai安全_1.md" → "ai安全.md"
     * - "01-AI安全入门.md" → "AI安全入门.md"
     */
    private static final Pattern FILE_SUFFIX_PATTERN = Pattern.compile("^(.+?)[-_]?\\d+(\\.md)$");

    /**
     * 默认管理员手机号
     */
    private static final String DEFAULT_ADMIN_PHONE = "13800000000";

    /**
     * 知识库文档根目录（支持环境变量配置）
     * 默认使用相对于运行目录的路径
     */
    @Value("${knowledge-base.path:#{systemProperties['user.dir']}/knowledge-base}")
    private String knowledgeBasePath;

    @Autowired
    private FileInfoService fileInfoService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    @Lazy
    private SharedKnowledgeBaseService sharedKnowledgeBaseService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SharedKnowledgeBaseMapper sharedKnowledgeBaseMapper;

    @Autowired
    private KnowledgeBaseMemberMapper knowledgeBaseMemberMapper;

    @Autowired
    private KnowledgeBaseFileService knowledgeBaseFileService;

    /**
     * 获取默认管理员用户ID
     */
    private Long getDefaultAdminUserId() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, DEFAULT_ADMIN_PHONE);
        User admin = userMapper.selectList(wrapper).stream().findFirst().orElse(null);

        if (admin == null) {
            throw new BizException(BizResponseCode.ERROR_1, "未找到默认管理员账户: " + DEFAULT_ADMIN_PHONE);
        }
        return admin.getId();
    }

    @Override
    public Map<String, Object> syncAllDocuments() {
        Map<String, Object> result = new HashMap<>();
        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        int categoriesCreated = 0;
        int categoriesSkipped = 0;

        try {
            Long userId = getDefaultAdminUserId();
            log.info("开始同步知识库文档，用户ID: {}", userId);

            Path rootPath = Paths.get(knowledgeBasePath);
            if (!Files.exists(rootPath)) {
                result.put("success", false);
                result.put("message", "知识库目录不存在: " + knowledgeBasePath);
                return result;
            }

            // 遍历一级目录（每个目录对应一个分类，如 "01-AI安全"）
            List<Path> categoryDirs = Files.list(rootPath)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());

            for (Path categoryDir : categoryDirs) {
                String categoryName = parseFolderName(categoryDir.getFileName().toString());

                // 检查是否已存在同名共享知识库
                SharedKnowledgeBase existingKB = findSharedKnowledgeBaseByName(categoryName);

                if (existingKB != null) {
                    log.info("共享知识库 '{}' 已存在，跳过创建", categoryName);
                    categoriesSkipped++;
                    // 同步该分类下的文档到已存在的知识库
                    syncCategoryFiles(categoryDir, categoryName, userId, existingKB.getId(), successFiles, failedFiles);
                } else {
                    // 创建新的共享知识库
                    SharedKnowledgeBase newKB = createSharedKnowledgeBase(categoryName, userId);
                    categoriesCreated++;
                    // 同步该分类下的文档到新创建的知识库
                    syncCategoryFiles(categoryDir, categoryName, userId, newKB.getId(), successFiles, failedFiles);
                }
            }

            result.put("success", failedFiles.isEmpty());
            result.put("message", failedFiles.isEmpty() ? "同步完成" : "同步完成但有部分文件失败");
            result.put("totalCategories", categoryDirs.size());
            result.put("categoriesCreated", categoriesCreated);
            result.put("categoriesSkipped", categoriesSkipped);
            result.put("totalFiles", successFiles.size() + failedFiles.size());
            result.put("successCount", successFiles.size());
            result.put("failedCount", failedFiles.size());
            result.put("successFiles", successFiles);
            result.put("failedFiles", failedFiles);

            log.info("知识库同步完成: 分类{}个(新建{}个), 文件{}个(成功{}个, 失败{}个)",
                    categoryDirs.size(), categoriesCreated, successFiles.size() + failedFiles.size(),
                    successFiles.size(), failedFiles.size());

        } catch (Exception e) {
            log.error("同步知识库文档失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> syncCategory(String categoryName) {
        Map<String, Object> result = new HashMap<>();
        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        try {
            Long userId = getDefaultAdminUserId();
            log.info("开始同步分类 '{}' 的文档，用户ID: {}", categoryName, userId);

            // 查找对应的目录
            Path rootPath = Paths.get(knowledgeBasePath);
            Path categoryPath = findCategoryPath(rootPath, categoryName);

            if (categoryPath == null) {
                result.put("success", false);
                result.put("message", "未找到分类目录: " + categoryName);
                return result;
            }

            // 检查或创建共享知识库
            SharedKnowledgeBase knowledgeBase = findSharedKnowledgeBaseByName(categoryName);
            Long knowledgeBaseId;

            if (knowledgeBase != null) {
                knowledgeBaseId = knowledgeBase.getId();
                log.info("使用已存在的共享知识库: {}, ID: {}", categoryName, knowledgeBaseId);
            } else {
                SharedKnowledgeBase newKB = createSharedKnowledgeBase(categoryName, userId);
                knowledgeBaseId = newKB.getId();
                log.info("创建新的共享知识库: {}, ID: {}", categoryName, knowledgeBaseId);
            }

            // 同步文件
            syncCategoryFiles(categoryPath, categoryName, userId, knowledgeBaseId, successFiles, failedFiles);

            result.put("success", failedFiles.isEmpty());
            result.put("message", failedFiles.isEmpty() ? "同步完成" : "同步完成但有部分文件失败");
            result.put("category", categoryName);
            result.put("knowledgeBaseId", knowledgeBaseId);
            result.put("totalFiles", successFiles.size() + failedFiles.size());
            result.put("successCount", successFiles.size());
            result.put("failedCount", failedFiles.size());
            result.put("successFiles", successFiles);
            result.put("failedFiles", failedFiles);

        } catch (Exception e) {
            log.error("同步分类 '{}' 失败: {}", categoryName, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> getSyncPreview() {
        Map<String, Object> preview = new HashMap<>();

        try {
            Path rootPath = Paths.get(knowledgeBasePath);
            if (!Files.exists(rootPath)) {
                preview.put("success", false);
                preview.put("message", "知识库目录不存在: " + knowledgeBasePath);
                return preview;
            }

            List<Map<String, Object>> categories = new ArrayList<>();
            int totalFiles = 0;

            // 遍历一级目录
            List<Path> categoryDirs = Files.list(rootPath)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());

            for (Path categoryDir : categoryDirs) {
                Map<String, Object> categoryInfo = new HashMap<>();
                String categoryName = parseFolderName(categoryDir.getFileName().toString());
                categoryInfo.put("originalName", categoryDir.getFileName().toString());
                categoryInfo.put("displayName", categoryName);

                // 检查共享知识库是否存在
                SharedKnowledgeBase existingKB = findSharedKnowledgeBaseByName(categoryName);
                categoryInfo.put("knowledgeBaseExists", existingKB != null);
                categoryInfo.put("knowledgeBaseId", existingKB != null ? existingKB.getId() : null);

                // 统计该分类下的 md 文件数量
                int fileCount = countMarkdownFiles(categoryDir);
                categoryInfo.put("fileCount", fileCount);
                totalFiles += fileCount;

                // 列出文件（最多显示5个）
                List<String> fileNames = Files.walk(categoryDir, 1)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                        .map(p -> p.getFileName().toString())
                        .limit(5)
                        .collect(Collectors.toList());
                categoryInfo.put("files", fileNames);
                if (fileCount > 5) {
                    categoryInfo.put("moreFiles", fileCount - 5);
                }

                categories.add(categoryInfo);
            }

            preview.put("success", true);
            preview.put("knowledgeBasePath", knowledgeBasePath);
            preview.put("totalCategories", categories.size());
            preview.put("totalFiles", totalFiles);
            preview.put("categories", categories);

        } catch (Exception e) {
            log.error("获取同步预览失败: {}", e.getMessage(), e);
            preview.put("success", false);
            preview.put("message", "获取预览失败: " + e.getMessage());
        }

        return preview;
    }

    @Override
    public String parseFolderName(String folderName) {
        if (!StringUtils.hasText(folderName)) {
            return folderName;
        }

        String trimmed = folderName.trim();

        // 尝试格式1: "01-AI安全" 或 "01－AI安全" (全角减号) 或 "01_AI安全"
        Matcher matcher1 = FOLDER_PATTERN_1.matcher(trimmed);
        if (matcher1.matches()) {
            return normalizeName(matcher1.group(2).trim());
        }

        // 尝试格式2: "1、ai安全" 或 "1，ai安全" (顿号或逗号分隔)
        Matcher matcher2 = FOLDER_PATTERN_2.matcher(trimmed);
        if (matcher2.matches()) {
            return normalizeName(matcher2.group(2).trim());
        }

        // 尝试格式3: "1_AI安全" (下划线分隔，单或双数字)
        Matcher matcher3 = FOLDER_PATTERN_3.matcher(trimmed);
        if (matcher3.matches()) {
            return normalizeName(matcher3.group(2).trim());
        }

        // 无匹配，返回原始名称（去除首尾空格）
        return trimmed;
    }

    /**
     * 解析文件名，移除序号后缀
     * 支持格式：
     * - "01-AI安全入门.md" → "AI安全入门.md"
     * - "ai安全-1.md" → "ai安全.md"
     * - "ai安全_1.md" → "ai安全.md"
     *
     * @param fileName 原始文件名
     * @return 解析后的文件名
     */
    public String parseFileName(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.toLowerCase().endsWith(".md")) {
            return fileName;
        }

        String trimmed = fileName.trim();
        Matcher matcher = FILE_SUFFIX_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            String baseName = matcher.group(1).trim();
            // 移除前置序号 "01-" "02-" 等
            baseName = baseName.replaceFirst("^\\d{2}[-－_]", "");
            return baseName + matcher.group(2); // 加回 .md
        }

        return trimmed;
    }

    /**
     * 规范化名称：首字母大写等
     */
    private String normalizeName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // 如果是纯英文或拼音，首字母大写
        if (name.matches("^[a-zA-Z].*")) {
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }

    @Override
    @Transactional
    public Map<String, Object> syncSingleFile(String filePath, String category) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long userId = getDefaultAdminUserId();
            Path file = Paths.get(filePath);

            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                result.put("success", false);
                result.put("message", "文件不存在: " + filePath);
                return result;
            }

            String fileName = file.getFileName().toString();
            if (!fileName.toLowerCase().endsWith(".md")) {
                result.put("success", false);
                result.put("message", "仅支持 md 文件: " + filePath);
                return result;
            }

            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(file);
            String content = new String(fileContent, StandardCharsets.UTF_8);

            // 上传文件到文件存储服务（使用原始 MD 文件）
            org.springframework.web.multipart.MultipartFile multipartFile =
                    createMultipartFile(fileName, fileContent);

            FileUpInfo fileInfo = fileInfoService.uploadFile(multipartFile, userId);

            // 设置分类
            if (StringUtils.hasText(category)) {
                fileInfo.setCategory(category);
                fileInfoService.updateById(fileInfo);
            }

            // 处理文档并存储到向量数据库（个人知识库）
            documentService.uploadAndProcessDocumentFromBytes(
                    fileContent,
                    fileName,
                    "text/markdown",
                    userId,  // 个人知识库使用用户ID
                    1,       // 知识库类型：1-个人
                    fileInfo.getId()
            );

            result.put("success", true);
            result.put("message", "文件同步成功");
            result.put("fileId", fileInfo.getId());
            result.put("fileName", fileName);
            result.put("category", category);

            log.info("成功同步文件 '{}' 到用户 {} 的个人知识库", fileName, userId);

        } catch (Exception e) {
            log.error("同步文件失败: {}, 错误: {}", filePath, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 同步分类目录下的所有文件
     */
    private void syncCategoryFiles(Path categoryDir, String categoryName, Long userId,
                                   Long knowledgeBaseId, List<String> successFiles,
                                   List<String> failedFiles) {
        try {
            // 遍历该分类下的所有 md 文件（包括子目录）
            List<Path> mdFiles = Files.walk(categoryDir, Integer.MAX_VALUE)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                    .collect(Collectors.toList());

            log.info("分类 '{}' 下发现 {} 个 md 文件", categoryName, mdFiles.size());

            for (Path mdFile : mdFiles) {
                try {
                    syncFileToKnowledgeBases(mdFile, categoryName, userId, knowledgeBaseId);
                    successFiles.add(categoryName + "/" + categoryDir.relativize(mdFile).toString().replace("\\", "/"));
                } catch (Exception e) {
                    log.error("同步文件失败: {}, 错误: {}", mdFile, e.getMessage());
                    failedFiles.add(categoryDir.relativize(mdFile).toString() + " - " + e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("遍历分类目录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步单个文件到个人知识库和共享知识库
     */
    private void syncFileToKnowledgeBases(Path mdFile, String category, Long userId, Long knowledgeBaseId) throws IOException {
        // 读取文件内容
        byte[] fileContent = Files.readAllBytes(mdFile);
        String fileName = mdFile.getFileName().toString();
        // 解析文件名，移除序号后缀
        String parsedFileName = parseFileName(fileName);

        // 1. 上传原始文件到文件存储服务（使用原始文件名存储）
        org.springframework.web.multipart.MultipartFile multipartFile = createMultipartFile(fileName, fileContent);
        FileUpInfo fileInfo = fileInfoService.uploadFile(multipartFile, userId);

        // 设置分类
        fileInfo.setCategory(category);
        // 设置解析后的文件名作为显示名称
        fileInfo.setOriginalFilename(parsedFileName);
        fileInfoService.updateById(fileInfo);

        // 2. 处理文档并存储到个人知识库的向量数据库（使用解析后的文件名）
        documentService.uploadAndProcessDocumentFromBytes(
                fileContent,
                parsedFileName,
                "text/markdown",
                userId,  // 个人知识库使用用户ID
                1,       // 知识库类型：1-个人
                fileInfo.getId()
        );

        // 3. 处理文档并存储到共享知识库的向量数据库（使用解析后的文件名）
        documentService.uploadAndProcessDocumentFromBytes(
                fileContent,
                parsedFileName,
                "text/markdown",
                knowledgeBaseId,  // 共享知识库ID
                2,               // 知识库类型：2-共享
                fileInfo.getId()
        );

        // 4. 添加文件到共享知识库的文件列表
        com.ldd.initialization.domain.KnowledgeBaseFile kbFile = new com.ldd.initialization.domain.KnowledgeBaseFile();
        kbFile.setKnowledgeBaseId(knowledgeBaseId);
        kbFile.setFileId(fileInfo.getId());
        kbFile.setUploaderId(userId);
        kbFile.setSourceType(1); // 本地上传
        kbFile.setUploadTime(LocalDateTime.now());

        // 检查是否已存在
        if (!knowledgeBaseFileService.isFileInKnowledgeBase(knowledgeBaseId, fileInfo.getId())) {
            knowledgeBaseFileService.save(kbFile);
            log.info("文件 '{}' 已添加到共享知识库 {}", fileName, knowledgeBaseId);
        }

        log.info("成功同步文件 '{}' 到个人知识库和共享知识库 {}", fileName, knowledgeBaseId);
    }

    /**
     * 创建共享知识库
     */
    @Transactional
    public SharedKnowledgeBase createSharedKnowledgeBase(String name, Long userId) {
        // 检查同名知识库是否已存在
        LambdaQueryWrapper<SharedKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SharedKnowledgeBase::getName, name)
               .eq(SharedKnowledgeBase::getStatus, 1);
        SharedKnowledgeBase existing = sharedKnowledgeBaseMapper.selectOne(wrapper);

        if (existing != null) {
            log.info("共享知识库 '{}' 已存在", name);
            return existing;
        }

        SharedKnowledgeBase knowledgeBase = new SharedKnowledgeBase();
        knowledgeBase.setName(name);
        knowledgeBase.setDescription("由 " + DEFAULT_ADMIN_PHONE + " 发布的 " + name + " 知识库");
        knowledgeBase.setCreatorId(userId);
        knowledgeBase.setMemberCount(1);
        knowledgeBase.setFileCount(0);
        knowledgeBase.setStatus(1);
        knowledgeBase.setIsPublic(true);
        knowledgeBase.setCoverUrl("https://bpic.588ku.com/back_origin_min_pic/20/06/21/53cfa4e4505d62bbdce784b2ce6c4be8.jpg");
        knowledgeBase.setCreateTime(LocalDateTime.now());
        knowledgeBase.setUpdateTime(LocalDateTime.now());

        sharedKnowledgeBaseMapper.insert(knowledgeBase);

        // 添加创建者为成员
        com.ldd.initialization.domain.KnowledgeBaseMember member = new com.ldd.initialization.domain.KnowledgeBaseMember();
        member.setKnowledgeBaseId(knowledgeBase.getId());
        member.setUserId(userId);
        member.setRole(KnowledgeBaseRoleEnum.CREATOR.getValue());
        member.setJoinTime(LocalDateTime.now());
        knowledgeBaseMemberMapper.insert(member);

        log.info("创建共享知识库 '{}', ID: {}", name, knowledgeBase.getId());
        return knowledgeBase;
    }

    /**
     * 根据名称查找共享知识库
     */
    private SharedKnowledgeBase findSharedKnowledgeBaseByName(String name) {
        LambdaQueryWrapper<SharedKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SharedKnowledgeBase::getName, name)
               .eq(SharedKnowledgeBase::getStatus, 1);
        return sharedKnowledgeBaseMapper.selectOne(wrapper);
    }

    /**
     * 查找分类目录路径
     */
    private Path findCategoryPath(Path rootPath, String categoryName) {
        try {
            return Files.list(rootPath)
                    .filter(Files::isDirectory)
                    .filter(dir -> {
                        String originalName = dir.getFileName().toString();
                        String parsedName = parseFolderName(originalName);
                        return parsedName.equals(categoryName) || originalName.equals(categoryName);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.error("查找分类目录失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 统计目录下的 md 文件数量
     */
    private int countMarkdownFiles(Path dir) {
        try {
            return (int) Files.walk(dir, Integer.MAX_VALUE)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                    .count();
        } catch (IOException e) {
            log.error("统计文件数量失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 创建 MultipartFile 对象
     */
    private org.springframework.web.multipart.MultipartFile createMultipartFile(String fileName, byte[] content) {
        return new SimpleMultipartFile(
                fileName,
                fileName,
                "text/markdown",
                content
        );
    }
}