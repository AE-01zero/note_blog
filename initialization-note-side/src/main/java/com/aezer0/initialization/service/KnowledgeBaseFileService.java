package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aezer0.initialization.domain.KnowledgeBaseFile;
import com.aezer0.initialization.dto.KnowledgeBaseFileUploadDTO;
import com.aezer0.initialization.vo.KnowledgeBaseFileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库文件Service接口
 */
public interface KnowledgeBaseFileService extends IService<KnowledgeBaseFile> {

    /**
     * 上传文件到知识库（本地文件）
     * @param knowledgeBaseId 知识库ID
     * @param file 文件
     * @param userId 用户ID
     * @return 上传结果
     */
    KnowledgeBaseFileVO uploadFile(Long knowledgeBaseId, MultipartFile file, Long userId);

    KnowledgeBaseFileVO uploadFile(Long knowledgeBaseId, MultipartFile file, Long userId, String category);

    void updateFileCategory(Long knowledgeBaseId, Long fileId, String category, Long userId);

    /**
     * 复制文件到知识库（从个人知识库）
     * @param uploadDTO 上传DTO
     * @param userId 用户ID
     * @return 复制结果
     */
    Map<String, Object> copyFilesToKnowledgeBase(KnowledgeBaseFileUploadDTO uploadDTO, Long userId);

    /**
     * 删除知识库文件
     * @param knowledgeBaseId 知识库ID
     * @param fileId 文件ID
     * @param userId 用户ID
     */
    void deleteFile(Long knowledgeBaseId, Long fileId, Long userId);

    /**
     * 获取知识库文件列表（分页）
     * @param knowledgeBaseId 知识库ID
     * @param page 页码
     * @param size 每页大小
     * @param keyword 搜索关键词
     * @param userId 用户ID
     * @return 文件列表
     */
    IPage<KnowledgeBaseFileVO> getKnowledgeBaseFiles(Long knowledgeBaseId, Integer page, Integer size, String keyword, Long userId);

    /**
     * 获取知识库所有文件
     * @param knowledgeBaseId 知识库ID
     * @param userId 用户ID
     * @return 文件列表
     */
    List<KnowledgeBaseFileVO> getAllKnowledgeBaseFiles(Long knowledgeBaseId, Long userId);

    /**
     * 获取用户个人知识库的文件列表（用于复制）
     * @param userId 用户ID
     * @return 文件列表
     */
    List<KnowledgeBaseFileVO> getPersonalFiles(Long userId);

    /**
     * 获取文件详情
     * @param knowledgeBaseId 知识库ID
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件详情
     */
    KnowledgeBaseFileVO getFileDetail(Long knowledgeBaseId, Long fileId, Long userId);

    /**
     * 检查文件是否在知识库中
     * @param knowledgeBaseId 知识库ID
     * @param fileId 文件ID
     * @return 是否存在
     */
    boolean isFileInKnowledgeBase(Long knowledgeBaseId, Long fileId);

    /**
     * 检查用户是否有权限操作文件
     * @param knowledgeBaseId 知识库ID
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    boolean hasFilePermission(Long knowledgeBaseId, Long fileId, Long userId);
} 