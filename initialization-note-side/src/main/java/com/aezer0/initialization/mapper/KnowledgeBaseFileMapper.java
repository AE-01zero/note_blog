package com.aezer0.initialization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aezer0.initialization.domain.KnowledgeBaseFile;
import com.aezer0.initialization.vo.KnowledgeBaseFileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库文件Mapper接口
 */
@Mapper
public interface KnowledgeBaseFileMapper extends BaseMapper<KnowledgeBaseFile> {

    /**
     * 分页查询知识库文件列表
     */
    @Select("<script>" +
            "SELECT kf.*, f.original_filename, f.file_name, f.file_extension as extension, f.file_size, " +
            "       f.file_url, f.file_type, f.mime_type, f.category, " +
            "       u.username as uploader_name, u.avatar_url as uploader_avatar, " +
            "       CASE " +
            "           WHEN kf.source_type = 1 THEN '本地上传' " +
            "           WHEN kf.source_type = 2 THEN '个人知识库复制' " +
            "           ELSE '未知' " +
            "       END as source_type_name, " +
            "       COALESCE(doc_stats.chunk_count, 0) as chunk_count, " +
            "       COALESCE(doc_stats.summary, '') as summary " +
            "FROM t_knowledge_base_file kf " +
            "LEFT JOIN t_file_info f ON kf.file_id = f.id " +
            "LEFT JOIN \"t_user\" u ON kf.uploader_id = u.id " +
            "LEFT JOIN (" +
            "    SELECT file_id, knowledge_base_id, COUNT(*) as chunk_count, MIN(LEFT(text, 220)) as summary " +
            "    FROM documents WHERE knowledge_base_type = 2 GROUP BY file_id, knowledge_base_id" +
            ") doc_stats ON doc_stats.file_id = kf.file_id AND doc_stats.knowledge_base_id = kf.knowledge_base_id " +
            "WHERE kf.knowledge_base_id = #{knowledgeBaseId} " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  AND f.original_filename LIKE CONCAT('%', #{keyword}, '%') " +
            "</if> " +
            "ORDER BY kf.upload_time DESC" +
            "</script>")
    IPage<KnowledgeBaseFileVO> selectFilesByKnowledgeBaseId(Page<KnowledgeBaseFileVO> page,
                                                           @Param("knowledgeBaseId") Long knowledgeBaseId,
                                                           @Param("keyword") String keyword);

    /**
     * 查询知识库所有文件
     */
    @Select("SELECT kf.*, f.original_filename, f.file_name, f.file_extension as extension, f.file_size, " +
            "       f.file_url, f.file_type, f.mime_type, f.category, " +
            "       u.username as uploader_name, u.avatar_url as uploader_avatar, " +
            "       CASE " +
            "           WHEN kf.source_type = 1 THEN '本地上传' " +
            "           WHEN kf.source_type = 2 THEN '个人知识库复制' " +
            "           ELSE '未知' " +
            "       END as source_type_name, " +
            "       COALESCE(doc_stats.chunk_count, 0) as chunk_count, " +
            "       COALESCE(doc_stats.summary, '') as summary " +
            "FROM t_knowledge_base_file kf " +
            "LEFT JOIN t_file_info f ON kf.file_id = f.id " +
            "LEFT JOIN \"t_user\" u ON kf.uploader_id = u.id " +
            "LEFT JOIN (" +
            "    SELECT file_id, knowledge_base_id, COUNT(*) as chunk_count, MIN(LEFT(text, 220)) as summary " +
            "    FROM documents WHERE knowledge_base_type = 2 GROUP BY file_id, knowledge_base_id" +
            ") doc_stats ON doc_stats.file_id = kf.file_id AND doc_stats.knowledge_base_id = kf.knowledge_base_id " +
            "WHERE kf.knowledge_base_id = #{knowledgeBaseId} " +
            "ORDER BY kf.upload_time DESC")
    List<KnowledgeBaseFileVO> selectAllFilesByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    /**
     * 查询知识库文件详情
     */
    @Select("SELECT kf.*, f.original_filename, f.file_name, f.file_extension as extension, f.file_size, " +
            "       f.file_url, f.file_type, f.mime_type, f.category, " +
            "       u.username as uploader_name, u.avatar_url as uploader_avatar, " +
            "       CASE " +
            "           WHEN kf.source_type = 1 THEN '本地上传' " +
            "           WHEN kf.source_type = 2 THEN '个人知识库复制' " +
            "           ELSE '未知' " +
            "       END as source_type_name " +
            "FROM t_knowledge_base_file kf " +
            "LEFT JOIN t_file_info f ON kf.file_id = f.id " +
            "LEFT JOIN \"t_user\" u ON kf.uploader_id = u.id " +
            "WHERE kf.knowledge_base_id = #{knowledgeBaseId} AND kf.file_id = #{fileId}")
    KnowledgeBaseFileVO selectFileDetail(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                        @Param("fileId") Long fileId);

    /**
     * 检查文件是否已在知识库中
     */
    @Select("SELECT COUNT(1) > 0 FROM t_knowledge_base_file " +
            "WHERE knowledge_base_id = #{knowledgeBaseId} AND file_id = #{fileId}")
    boolean existsByKnowledgeBaseIdAndFileId(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                           @Param("fileId") Long fileId);

    /**
     * 统计知识库文件数量
     */
    @Select("SELECT COUNT(1) FROM t_knowledge_base_file WHERE knowledge_base_id = #{knowledgeBaseId}")
    int countFilesByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    /**
     * 查询用户个人知识库的文件列表（用于复制到共享知识库）
     */
    @Select("SELECT f.*, 'SUCCESS' as processing_status " +
            "FROM t_file_info f " +
            "WHERE f.user_id = #{userId} " +
            "ORDER BY f.create_time DESC")
    List<KnowledgeBaseFileVO> selectPersonalFiles(@Param("userId") Long userId);

    @Select("SELECT DISTINCT f.category " +
            "FROM t_knowledge_base_file kf " +
            "INNER JOIN t_file_info f ON kf.file_id = f.id " +
            "WHERE kf.knowledge_base_id = #{knowledgeBaseId} " +
            "AND f.category IS NOT NULL AND TRIM(f.category) <> '' " +
            "ORDER BY f.category")
    List<String> selectCategoriesByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    /**
     * 检查用户是否有权限操作该文件
     */
    @Select("SELECT COUNT(1) > 0 FROM t_knowledge_base_file kf " +
            "INNER JOIN t_knowledge_base_member m ON kf.knowledge_base_id = m.knowledge_base_id " +
            "WHERE kf.knowledge_base_id = #{knowledgeBaseId} AND kf.file_id = #{fileId} " +
            "AND m.user_id = #{userId}")
    boolean checkFilePermission(@Param("knowledgeBaseId") Long knowledgeBaseId,
                               @Param("fileId") Long fileId,
                               @Param("userId") Long userId);
} 