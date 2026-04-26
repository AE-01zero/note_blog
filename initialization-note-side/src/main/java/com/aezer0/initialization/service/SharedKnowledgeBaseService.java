package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aezer0.initialization.domain.SharedKnowledgeBase;
import com.aezer0.initialization.dto.*;
import com.aezer0.initialization.vo.KnowledgeBaseSquareVO;
import com.aezer0.initialization.vo.SharedKnowledgeBaseVO;

import java.util.List;

/**
 * 共享知识库Service接口
 */
public interface SharedKnowledgeBaseService extends IService<SharedKnowledgeBase> {

    /**
     * 创建共享知识库
     * @param createDTO 创建DTO
     * @param userId 用户ID
     * @return 知识库信息
     */
    SharedKnowledgeBaseVO createKnowledgeBase(SharedKnowledgeBaseCreateDTO createDTO, Long userId);

    /**
     * 更新共享知识库
     * @param knowledgeBaseId 知识库ID
     * @param updateDTO 更新DTO
     * @param userId 用户ID
     * @return 知识库信息
     */
    SharedKnowledgeBaseVO updateKnowledgeBase(Long knowledgeBaseId, SharedKnowledgeBaseUpdateDTO updateDTO, Long userId);

    /**
     * 删除共享知识库
     * @param knowledgeBaseId 知识库ID
     * @param userId 用户ID
     */
    void deleteKnowledgeBase(Long knowledgeBaseId, Long userId);

    /**
     * 获取知识库详情
     * @param knowledgeBaseId 知识库ID
     * @param userId 用户ID
     * @return 知识库详情
     */
    SharedKnowledgeBaseVO getKnowledgeBaseDetail(Long knowledgeBaseId, Long userId);

    /**
     * 获取用户创建的知识库列表
     * @param userId 用户ID
     * @return 知识库列表
     */
    List<SharedKnowledgeBaseVO> getCreatedKnowledgeBases(Long userId);

    /**
     * 获取用户加入的知识库列表
     * @param userId 用户ID
     * @return 知识库列表
     */
    List<SharedKnowledgeBaseVO> getJoinedKnowledgeBases(Long userId);

    /**
     * 搜索知识库广场
     * @param searchDTO 搜索条件
     * @param userId 当前用户ID
     * @return 知识库列表
     */
    IPage<KnowledgeBaseSquareVO> searchKnowledgeBaseSquare(KnowledgeBaseSearchDTO searchDTO, Long userId);

    /**
     * 加入知识库
     * @param joinDTO 加入DTO
     * @param userId 用户ID
     */
    void joinKnowledgeBase(KnowledgeBaseJoinDTO joinDTO, Long userId);

    /**
     * 退出知识库
     * @param knowledgeBaseId 知识库ID
     * @param userId 用户ID
     */
    void leaveKnowledgeBase(Long knowledgeBaseId, Long userId);

    /**
     * 移除成员
     * @param knowledgeBaseId 知识库ID
     * @param memberId 成员ID
     * @param operatorId 操作者ID
     */
    void removeMember(Long knowledgeBaseId, Long memberId, Long operatorId);

    /**
     * 检查用户是否有权限访问知识库
     * @param knowledgeBaseId 知识库ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    boolean hasPermission(Long knowledgeBaseId, Long userId);

    /**
     * 检查用户是否为知识库创建者
     * @param knowledgeBaseId 知识库ID
     * @param userId 用户ID
     * @return 是否为创建者
     */
    boolean isCreator(Long knowledgeBaseId, Long userId);

    /**
     * 获取用户在知识库中的角色
     * @param knowledgeBaseId 知识库ID
     * @param userId 用户ID
     * @return 角色类型
     */
    Integer getUserRole(Long knowledgeBaseId, Long userId);

    /**
     * 刷新共享知识库统计信息（成员数、文件数）
     * @param knowledgeBaseId 知识库ID
     */
    void refreshStatistics(Long knowledgeBaseId);
}
