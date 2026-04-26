package com.aezer0.initialization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aezer0.initialization.domain.KnowledgeBaseMember;
import com.aezer0.initialization.vo.KnowledgeBaseMemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库成员Mapper接口
 */
@Mapper
public interface KnowledgeBaseMemberMapper extends BaseMapper<KnowledgeBaseMember> {

    /**
     * 查询知识库成员列表
     */
    @Select("SELECT m.*, u.username, u.avatar_url, " +
            "       CASE " +
            "           WHEN m.role = 1 THEN '创建者' " +
            "           WHEN m.role = 2 THEN '成员' " +
            "           ELSE '未知' " +
            "       END as role_name " +
            "FROM t_knowledge_base_member m " +
            "LEFT JOIN \"t_user\" u ON m.user_id = u.id " +
            "WHERE m.knowledge_base_id = #{knowledgeBaseId} " +
            "ORDER BY m.role ASC, m.join_time DESC")
    List<KnowledgeBaseMemberVO> selectMembersByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    /**
     * 查询用户在知识库中的成员信息
     */
    @Select("SELECT m.*, u.username, u.avatar_url, " +
            "       CASE " +
            "           WHEN m.role = 1 THEN '创建者' " +
            "           WHEN m.role = 2 THEN '成员' " +
            "           ELSE '未知' " +
            "       END as role_name " +
            "FROM t_knowledge_base_member m " +
            "LEFT JOIN \"t_user\" u ON m.user_id = u.id " +
            "WHERE m.knowledge_base_id = #{knowledgeBaseId} AND m.user_id = #{userId}")
    KnowledgeBaseMemberVO selectMemberInfo(@Param("knowledgeBaseId") Long knowledgeBaseId, 
                                          @Param("userId") Long userId);

    /**
     * 统计知识库成员数量
     */
    @Select("SELECT COUNT(1) FROM t_knowledge_base_member WHERE knowledge_base_id = #{knowledgeBaseId}")
    int countMembersByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    /**
     * 检查用户是否已加入知识库
     */
    @Select("SELECT COUNT(1) > 0 FROM t_knowledge_base_member " +
            "WHERE knowledge_base_id = #{knowledgeBaseId} AND user_id = #{userId}")
    boolean existsByKnowledgeBaseIdAndUserId(@Param("knowledgeBaseId") Long knowledgeBaseId, 
                                           @Param("userId") Long userId);
} 