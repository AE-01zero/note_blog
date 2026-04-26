package com.aezer0.initialization.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.domain.FileUpInfo;
import com.aezer0.initialization.enums.FileTypeEnum;
import com.aezer0.initialization.mapper.FileInfoMapper;
import com.aezer0.initialization.service.FileInfoService;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileUpInfo> implements FileInfoService {

    // 允许上传的文件类型
    private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "video/mp4"
    );

    // 最大文件大小（100MB）
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    @Autowired
    private FileStorageService fileStorageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUpInfo uploadFile(MultipartFile file, Long userId) {

        try {
            byte[] fileBytes = file.getBytes();
            // 1. 文件类型验证
            String contentType = file.getContentType();
            // 2. 文件大小验证
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("文件大小超过限制");
            }

            // 3. 生成文件存储名称（防止重名）
            String originalFilename = file.getOriginalFilename();
            String extension = FileNameUtil.extName(originalFilename);
            String filename = UUID.randomUUID().toString() + "." + extension;

            // 4. 计算文件MD5
            String md5 = DigestUtils.md5DigestAsHex(new ByteArrayInputStream(fileBytes));

            // 5. 检查文件是否已存在（可选：根据MD5查重）
            QueryWrapper<FileUpInfo> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("file_md5", md5).eq("user_id", userId);
            FileUpInfo existFile = getOne(queryWrapper);
            if (existFile != null) {
                log.info("文件已存在，返回已有文件信息: {}", existFile.getOriginalFilename());
                return existFile;
            }

            // 6. 确定文件类型
            FileTypeEnum fileType = FileTypeEnum.getByMimeType(contentType);

            // 7. 根据文件类型确定存储路径
            String storagePath = "uploads/" + fileType.name().toLowerCase() + "/"
                    + DateUtil.format(new Date(), "yyyyMMdd");

            // 8. 上传文件到存储系统
            org.dromara.x.file.storage.core.FileInfo upload = fileStorageService.of(new ByteArrayInputStream(fileBytes))
                    .setPath(storagePath)
                    .setName( filename)
                    .upload();

            // 9. 创建文件信息对象
            FileUpInfo fileInfo = new FileUpInfo();

            // 10. 保存文件信息到数据库
            fileInfo.setOriginalFilename(originalFilename);
            fileInfo.setFileName(filename);
            fileInfo.setFileExtension(extension);
            fileInfo.setFileSize(file.getSize());
            fileInfo.setFilePath(upload.getPath());
            fileInfo.setFileUrl(upload.getUrl());
            fileInfo.setUserId(userId);
            fileInfo.setFileMd5(md5);
            fileInfo.setFileType(fileType.name());
            fileInfo.setMimeType(contentType);
            fileInfo.setUpdateTime(LocalDateTime.now());
            fileInfo.setCreateTime(LocalDateTime.now());
            // 11. 保存到数据库
            save(fileInfo);

            log.info("文件上传成功: {}, 用户ID: {}", originalFilename, userId);
            return fileInfo;
            
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }
}