package com.aezer0.initialization.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.KnowledgeBaseFile;
import com.aezer0.initialization.domain.KnowledgeBaseMember;
import com.aezer0.initialization.domain.SharedKnowledgeBase;
import com.aezer0.initialization.dto.*;
import com.aezer0.initialization.enums.KnowledgeBaseRoleEnum;
import com.aezer0.initialization.mapper.KnowledgeBaseMemberMapper;
import com.aezer0.initialization.mapper.SharedKnowledgeBaseMapper;
import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.service.KnowledgeBaseFileService;
import com.aezer0.initialization.service.SharedKnowledgeBaseService;
import com.aezer0.initialization.service.ai.SharedKnowledgeBaseVectorService;
import com.aezer0.initialization.vo.KnowledgeBaseSquareVO;
import com.aezer0.initialization.vo.SharedKnowledgeBaseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 共享知识库Service实现类
 */
@Service
@Slf4j
public class SharedKnowledgeBaseServiceImpl extends ServiceImpl<SharedKnowledgeBaseMapper, SharedKnowledgeBase>
        implements SharedKnowledgeBaseService {
    private static final String DEFAULT_KNOWLEDGE_BASE_COVER =
            "https://bpic.588ku.com/back_origin_min_pic/20/06/21/53cfa4e4505d62bbdce784b2ce6c4be8.jpg";

    @Autowired
    private KnowledgeBaseMemberMapper memberMapper;

    @Autowired
    @Lazy
    private KnowledgeBaseFileService fileService;

    @Autowired
    private SharedKnowledgeBaseVectorService vectorService;

    @Override
    @Transactional
    public SharedKnowledgeBaseVO createKnowledgeBase(SharedKnowledgeBaseCreateDTO createDTO, Long userId) {
        // 检查同分类下是否已有同名知识库
        LambdaQueryWrapper<SharedKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SharedKnowledgeBase::getName, createDTO.getName())
                .eq(SharedKnowledgeBase::getStatus, 1);
        if (createDTO.getCategoryId() != null) {
            wrapper.eq(SharedKnowledgeBase::getCategoryId, createDTO.getCategoryId());
        } else {
            wrapper.isNull(SharedKnowledgeBase::getCategoryId);
        }

        if (this.count(wrapper) > 0) {
            throw new BizException(BizResponseCode.ERROR_1, "该分类中已存在同名知识库");
        }

        SharedKnowledgeBase knowledgeBase = new SharedKnowledgeBase();
        BeanUtils.copyProperties(createDTO, knowledgeBase);
        knowledgeBase.setCreatorId(userId);
        knowledgeBase.setMemberCount(1);
        knowledgeBase.setFileCount(0);
        knowledgeBase.setStatus(1);
        knowledgeBase.setCreateTime(LocalDateTimeUtil.now());
        knowledgeBase.setUpdateTime(LocalDateTimeUtil.now());

        if (StrUtil.isBlank(knowledgeBase.getCoverUrl())) {
            knowledgeBase.setCoverUrl(DEFAULT_KNOWLEDGE_BASE_COVER);
        }
        // 如果设置了密码，进行加密
        if (StrUtil.isNotBlank(createDTO.getPassword())) {
            knowledgeBase.setPassword(DigestUtil.sha256Hex(createDTO.getPassword()));
        }

        this.save(knowledgeBase);

        // 添加创建者为成员
        KnowledgeBaseMember member = new KnowledgeBaseMember();
        member.setKnowledgeBaseId(knowledgeBase.getId());
        member.setUserId(userId);
        member.setRole(KnowledgeBaseRoleEnum.CREATOR.getValue());
        member.setJoinTime(LocalDateTimeUtil.now());
        memberMapper.insert(member);

        // 如果有初始文件，复制到知识库
        if (!CollectionUtils.isEmpty(createDTO.getInitialFileIds())) {
            try {
                KnowledgeBaseFileUploadDTO uploadDTO = new KnowledgeBaseFileUploadDTO();
                uploadDTO.setKnowledgeBaseId(knowledgeBase.getId());
                uploadDTO.setFileIds(createDTO.getInitialFileIds());
                uploadDTO.setSourceType(2);
                fileService.copyFilesToKnowledgeBase(uploadDTO, userId);
            } catch (Exception e) {
                log.warn("复制初始文件到知识库失败: {}", e.getMessage());
                throw new RuntimeException("复制初始文件到知识库失败", e);
            }
        }

        log.info("用户 {} 创建共享知识库成功: {}", userId, knowledgeBase.getName());
        refreshStatistics(knowledgeBase.getId());
        return getKnowledgeBaseDetail(knowledgeBase.getId(), userId);
    }

    @Override
    @Transactional
    public SharedKnowledgeBaseVO updateKnowledgeBase(Long knowledgeBaseId, SharedKnowledgeBaseUpdateDTO updateDTO, Long userId) {
        // 验证权限
        if (!isCreator(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11004, "只有创建者可以修改知识库信息");
        }

        SharedKnowledgeBase knowledgeBase = this.getById(knowledgeBaseId);
        if (knowledgeBase == null || knowledgeBase.getStatus() != 1) {
            throw new BizException(BizResponseCode.ERROR_1, "知识库不存在");
        }

        if (!knowledgeBase.getName().equals(updateDTO.getName())) {
            LambdaQueryWrapper<SharedKnowledgeBase> nameCheck = new LambdaQueryWrapper<>();
            nameCheck.eq(SharedKnowledgeBase::getName, updateDTO.getName())
                    .eq(SharedKnowledgeBase::getStatus, 1)
                    .ne(SharedKnowledgeBase::getId, knowledgeBaseId);
            Long targetCategoryId = updateDTO.getCategoryId() != null ? updateDTO.getCategoryId() : knowledgeBase.getCategoryId();
            if (targetCategoryId != null) {
                nameCheck.eq(SharedKnowledgeBase::getCategoryId, targetCategoryId);
            } else {
                nameCheck.isNull(SharedKnowledgeBase::getCategoryId);
            }
            if (this.count(nameCheck) > 0) {
                throw new BizException(BizResponseCode.ERROR_1, "该分类中已存在同名知识库");
            }
        }

        BeanUtils.copyProperties(updateDTO, knowledgeBase);

        if (StrUtil.isNotBlank(updateDTO.getPassword())) {
            knowledgeBase.setPassword(DigestUtil.sha256Hex(updateDTO.getPassword()));
        } else if (StrUtil.isBlank(updateDTO.getPassword())) {
            knowledgeBase.setPassword(null); // 清空密码
        }

        this.updateById(knowledgeBase);

        log.info("用户 {} 更新共享知识库成功: {}", userId, knowledgeBase.getName());
        return getKnowledgeBaseDetail(knowledgeBaseId, userId);
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(Long knowledgeBaseId, Long userId) {
        // 验证权限
        if (!isCreator(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11004, "只有创建者可以删除知识库");
        }

        SharedKnowledgeBase knowledgeBase = this.getById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BizException(BizResponseCode.ERROR_1, "知识库不存在");
        }

        // 删除知识库
        baseMapper.deleteById(knowledgeBaseId);
        // 删除向量数据
        try {
            vectorService.deleteSharedKnowledgeBaseVectors(knowledgeBaseId);
        } catch (Exception e) {
            log.error("删除知识库 {} 向量数据失败: {}", knowledgeBaseId, e.getMessage());
        }
        // 删除文件
        fileService.remove(new LambdaQueryWrapper<KnowledgeBaseFile>().eq(KnowledgeBaseFile::getKnowledgeBaseId, knowledgeBaseId));
        // 删除成员
        memberMapper.delete(new LambdaQueryWrapper<KnowledgeBaseMember>().eq(KnowledgeBaseMember::getKnowledgeBaseId, knowledgeBaseId));
        log.info("用户 {} 删除共享知识库成功: {}", userId, knowledgeBase.getName());
    }

    @Override
    public SharedKnowledgeBaseVO getKnowledgeBaseDetail(Long knowledgeBaseId, Long userId) {
        SharedKnowledgeBaseVO detail = this.baseMapper.selectDetailById(knowledgeBaseId, userId);
        if (detail == null) {
            throw new BizException(BizResponseCode.ERROR_1, "知识库不存在");
        }

        if (detail.getHasPassword() == null) {
            detail.setHasPassword(false);
        }

        return detail;
    }

    @Override
    public List<SharedKnowledgeBaseVO> getCreatedKnowledgeBases(Long userId) {
        return this.baseMapper.selectByCreatorId(userId);
    }

    @Override
    public List<SharedKnowledgeBaseVO> getJoinedKnowledgeBases(Long userId) {
        return this.baseMapper.selectByMemberId(userId);
    }

    @Override
    public IPage<KnowledgeBaseSquareVO> searchKnowledgeBaseSquare(KnowledgeBaseSearchDTO searchDTO, Long userId) {
        Page<KnowledgeBaseSquareVO> page = new Page<>(searchDTO.getPage(), searchDTO.getSize());
        return this.baseMapper.selectSquareList(page, searchDTO.getKeyword(),
                searchDTO.getSortBy(), searchDTO.getSortOrder(),
                searchDTO.getOnlyPublic(), userId);
    }

    @Override
    @Transactional
    public void joinKnowledgeBase(KnowledgeBaseJoinDTO joinDTO, Long userId) {
        SharedKnowledgeBase knowledgeBase = this.getById(joinDTO.getKnowledgeBaseId());
        if (knowledgeBase == null || knowledgeBase.getStatus() != 1) {
            throw new BizException(BizResponseCode.ERROR_1, "知识库不存在");
        }

        // 检查是否已经是成员
        if (memberMapper.existsByKnowledgeBaseIdAndUserId(joinDTO.getKnowledgeBaseId(), userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "您已经是该知识库成员");
        }

        // 验证密码
        if (StrUtil.isNotBlank(knowledgeBase.getPassword())) {
            if (StrUtil.isBlank(joinDTO.getPassword())) {
                throw new BizException(BizResponseCode.ERROR_1, "该知识库需要密码才能加入");
            }

            String encryptedPassword = DigestUtil.sha256Hex(joinDTO.getPassword());
            if (!encryptedPassword.equals(knowledgeBase.getPassword())) {
                throw new BizException(BizResponseCode.ERROR_1, "密码错误");
            }
        }

        // 添加成员
        KnowledgeBaseMember member = new KnowledgeBaseMember();
        member.setKnowledgeBaseId(joinDTO.getKnowledgeBaseId());
        member.setUserId(userId);
        member.setRole(KnowledgeBaseRoleEnum.MEMBER.getValue());
        member.setJoinTime(LocalDateTime.now());
        memberMapper.insert(member);
        refreshStatistics(joinDTO.getKnowledgeBaseId());

        log.info("用户 {} 加入共享知识库成功: {}", userId, knowledgeBase.getName());
    }

    @Override
    @Transactional
    public void leaveKnowledgeBase(Long knowledgeBaseId, Long userId) {
        if (isCreator(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "创建者不能退出自己的知识库");
        }

        // 检查是否为成员
        if (!memberMapper.existsByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "您不是该知识库的成员");
        }

        // 删除成员记录
        LambdaQueryWrapper<KnowledgeBaseMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseMember::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeBaseMember::getUserId, userId);
        memberMapper.delete(wrapper);
        refreshStatistics(knowledgeBaseId);

        log.info("用户 {} 退出共享知识库成功: {}", userId, knowledgeBaseId);
    }

    @Override
    @Transactional
    public void removeMember(Long knowledgeBaseId, Long memberId, Long operatorId) {
        if (!isCreator(knowledgeBaseId, operatorId)) {
            throw new BizException(BizResponseCode.ERR_11004, "只有创建者可以移除成员");
        }

        // 不能移除自己
        if (memberId.equals(operatorId)) {
            throw new BizException(BizResponseCode.ERROR_1, "不能移除自己");
        }

        if (!memberMapper.existsByKnowledgeBaseIdAndUserId(knowledgeBaseId, memberId)) {
            throw new BizException(BizResponseCode.ERROR_1, "该用户不是知识库成员");
        }

        // 删除成员记录
        LambdaQueryWrapper<KnowledgeBaseMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseMember::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeBaseMember::getUserId, memberId);
        memberMapper.delete(wrapper);
        refreshStatistics(knowledgeBaseId);

        log.info("用户 {} 从共享知识库 {} 移除成员 {}", operatorId, knowledgeBaseId, memberId);
    }

    @Override
    public void refreshStatistics(Long knowledgeBaseId) {
        int memberCount = memberMapper.countMembersByKnowledgeBaseId(knowledgeBaseId);
        int fileCount = (int) fileService.count(new LambdaQueryWrapper<KnowledgeBaseFile>()
                .eq(KnowledgeBaseFile::getKnowledgeBaseId, knowledgeBaseId));

        SharedKnowledgeBase update = new SharedKnowledgeBase();
        update.setId(knowledgeBaseId);
        update.setMemberCount(memberCount);
        update.setFileCount(fileCount);
        update.setUpdateTime(LocalDateTime.now());
        this.updateById(update);
    }

    @Override
    public boolean hasPermission(Long knowledgeBaseId, Long userId) {
        return this.baseMapper.checkMembership(knowledgeBaseId, userId);
    }

    @Override
    public boolean isCreator(Long knowledgeBaseId, Long userId) {
        return this.baseMapper.checkCreator(knowledgeBaseId, userId);
    }

    @Override
    public Integer getUserRole(Long knowledgeBaseId, Long userId) {
        return this.baseMapper.getUserRole(knowledgeBaseId, userId);
    }
}