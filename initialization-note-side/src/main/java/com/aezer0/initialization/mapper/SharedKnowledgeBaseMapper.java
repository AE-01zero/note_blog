package com.aezer0.initialization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aezer0.initialization.domain.SharedKnowledgeBase;
import com.aezer0.initialization.vo.KnowledgeBaseSquareVO;
import com.aezer0.initialization.vo.SharedKnowledgeBaseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 共享知识库Mapper接口
 */
@Mapper
public interface SharedKnowledgeBaseMapper extends BaseMapper<SharedKnowledgeBase> {

    /**
     * 查询知识库广场列表
     */
    @Select("<script>" +
            "SELECT kb.*, u.username as creator_name, u.avatar_url as creator_avatar, " +
            "       CASE WHEN kb.password IS NOT NULL AND kb.password != '' THEN true ELSE false END as has_password, " +
            "       CASE WHEN m.user_id IS NOT NULL THEN true ELSE false END as is_joined " +
            "FROM t_shared_knowledge_base kb " +
            "LEFT JOIN \"t_user\" u ON kb.creator_id = u.id " +
            "LEFT JOIN t_knowledge_base_member m ON kb.id = m.knowledge_base_id AND m.user_id = #{currentUserId} " +
            "WHERE kb.status = 1 " +
            "<if test='onlyPublic != null and onlyPublic'> AND kb.is_public = true </if> " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  AND (kb.name LIKE CONCAT('%', #{keyword}, '%') OR kb.description LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if> " +
            "ORDER BY " +
            "<choose>" +
            "  <when test='sortBy == \"member_count\"'>kb.member_count</when>" +
            "  <when test='sortBy == \"file_count\"'>kb.file_count</when>" +
            "  <otherwise>kb.create_time</otherwise>" +
            "</choose> " +
            "<choose>" +
            "  <when test='sortOrder == \"asc\"'>ASC</when>" +
            "  <otherwise>DESC</otherwise>" +
            "</choose>" +
            "</script>")
    IPage<KnowledgeBaseSquareVO> selectSquareList(Page<KnowledgeBaseSquareVO> page, 
                                                  @Param("keyword") String keyword,
                                                  @Param("sortBy") String sortBy,
                                                  @Param("sortOrder") String sortOrder,
                                                  @Param("onlyPublic") Boolean onlyPublic,
                                                  @Param("currentUserId") Long currentUserId);

    /**
     * 查询用户创建的知识库列表
     */
    @Select("SELECT kb.*, u.username as creator_name, u.avatar_url as creator_avatar, " +
            "       CASE WHEN kb.password IS NOT NULL AND kb.password != '' THEN true ELSE false END as has_password, " +
            "       true as is_member, " +
            "       1 as user_role, " +
            "       bc.name as category_name " +
            "FROM t_shared_knowledge_base kb " +
            "LEFT JOIN \"t_user\" u ON kb.creator_id = u.id " +
            "LEFT JOIN t_blog_category bc ON kb.category_id = bc.id " +
            "WHERE kb.creator_id = #{userId} AND kb.status = 1 " +
            "ORDER BY kb.create_time DESC")
    List<SharedKnowledgeBaseVO> selectByCreatorId(@Param("userId") Long userId);

    /**
     * 查询用户加入的知识库列表
     */
    @Select("SELECT kb.*, u.username as creator_name, u.avatar_url as creator_avatar, " +
            "       CASE WHEN kb.password IS NOT NULL AND kb.password != '' THEN true ELSE false END as has_password, " +
            "       true as is_member, " +
            "       m.role as user_role, " +
            "       bc.name as category_name " +
            "FROM t_shared_knowledge_base kb " +
            "LEFT JOIN \"t_user\" u ON kb.creator_id = u.id " +
            "LEFT JOIN t_blog_category bc ON kb.category_id = bc.id " +
            "INNER JOIN t_knowledge_base_member m ON kb.id = m.knowledge_base_id " +
            "WHERE m.user_id = #{userId} AND kb.status = 1 " +
            "ORDER BY m.join_time DESC")
    List<SharedKnowledgeBaseVO> selectByMemberId(@Param("userId") Long userId);

    /**
     * 查询知识库详情（包含用户权限信息）
     */
    @Select("""
        SELECT kb.*, u.username as creator_name, u.avatar_url as creator_avatar,
               CASE WHEN kb.password IS NOT NULL AND kb.password != '' THEN true ELSE false END as has_password,
               CASE WHEN m.user_id IS NOT NULL THEN true ELSE false END as is_member,
               m.role as user_role,
               bc.name as category_name
        FROM t_shared_knowledge_base kb
        LEFT JOIN t_user u ON kb.creator_id = u.id
        LEFT JOIN t_knowledge_base_member m ON kb.id = m.knowledge_base_id AND m.user_id = #{userId}
        LEFT JOIN t_blog_category bc ON kb.category_id = bc.id
        WHERE kb.id = #{knowledgeBaseId} AND kb.status = 1
        """)
    SharedKnowledgeBaseVO selectDetailById(@Param("knowledgeBaseId") Long knowledgeBaseId, 
                                          @Param("userId") Long userId);

    /**
     * 检查用户是否为知识库成员
     */
    @Select("SELECT COUNT(1) > 0 FROM t_knowledge_base_member " +
            "WHERE knowledge_base_id = #{knowledgeBaseId} AND user_id = #{userId}")
    boolean checkMembership(@Param("knowledgeBaseId") Long knowledgeBaseId, 
                           @Param("userId") Long userId);

    /**
     * 检查用户是否为知识库创建者
     */
    @Select("SELECT COUNT(1) > 0 FROM t_shared_knowledge_base " +
            "WHERE id = #{knowledgeBaseId} AND creator_id = #{userId} AND status = 1")
    boolean checkCreator(@Param("knowledgeBaseId") Long knowledgeBaseId, 
                        @Param("userId") Long userId);

    /**
     * 获取用户在知识库中的角色
     */
    @Select("SELECT role FROM t_knowledge_base_member " +
            "WHERE knowledge_base_id = #{knowledgeBaseId} AND user_id = #{userId}")
    Integer getUserRole(@Param("knowledgeBaseId") Long knowledgeBaseId, 
                       @Param("userId") Long userId);
} 