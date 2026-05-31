package com.ldd.initialization.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ldd.initialization.config.exception.BizException;
import com.ldd.initialization.domain.KnowledgeBaseFile;
import com.ldd.initialization.domain.KnowledgeBaseMember;
import com.ldd.initialization.domain.SharedKnowledgeBase;
import com.ldd.initialization.dto.*;
import com.ldd.initialization.enums.KnowledgeBaseRoleEnum;
import com.ldd.initialization.mapper.KnowledgeBaseMemberMapper;
import com.ldd.initialization.mapper.SharedKnowledgeBaseMapper;
import com.ldd.initialization.result.BizResponseCode;
import com.ldd.initialization.service.KnowledgeBaseFileService;
import com.ldd.initialization.service.SharedKnowledgeBaseService;
import com.ldd.initialization.service.ai.SharedKnowledgeBaseVectorService;
import com.ldd.initialization.vo.KnowledgeBaseSquareVO;
import com.ldd.initialization.vo.SharedKnowledgeBaseVO;
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
 * 鍏变韩鐭ヨ瘑搴揝ervice瀹炵幇绫? */
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
        // 妫€鏌ュ悓鍒嗙被涓嬫槸鍚﹀凡鏈夊悓鍚嶇煡璇嗗簱
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
        // 濡傛灉璁剧疆浜嗗瘑鐮侊紝杩涜鍔犲瘑
        if (StrUtil.isNotBlank(createDTO.getPassword())) {
            knowledgeBase.setPassword(DigestUtil.sha256Hex(createDTO.getPassword()));
        }

        this.save(knowledgeBase);

        // 娣诲姞鍒涘缓鑰呬负鎴愬憳
        KnowledgeBaseMember member = new KnowledgeBaseMember();
        member.setKnowledgeBaseId(knowledgeBase.getId());
        member.setUserId(userId);
        member.setRole(KnowledgeBaseRoleEnum.CREATOR.getValue());
        member.setJoinTime(LocalDateTimeUtil.now());
        memberMapper.insert(member);

        // 濡傛灉鏈夊垵濮嬫枃浠讹紝澶嶅埗鍒扮煡璇嗗簱
        if (!CollectionUtils.isEmpty(createDTO.getInitialFileIds())) {
            try {
                KnowledgeBaseFileUploadDTO uploadDTO = new KnowledgeBaseFileUploadDTO();
                uploadDTO.setKnowledgeBaseId(knowledgeBase.getId());
                uploadDTO.setFileIds(createDTO.getInitialFileIds());
                uploadDTO.setSourceType(2);
                fileService.copyFilesToKnowledgeBase(uploadDTO, userId);
            } catch (Exception e) {
                log.warn("澶嶅埗鍒濆鏂囦欢鍒扮煡璇嗗簱澶辫触: {}", e.getMessage());
                throw new RuntimeException("澶嶅埗鍒濆鏂囦欢鍒扮煡璇嗗簱澶辫触", e);
            }
        }

        log.info("鐢ㄦ埛 {} 鍒涘缓鍏变韩鐭ヨ瘑搴撴垚鍔? {}", userId, knowledgeBase.getName());
        refreshStatistics(knowledgeBase.getId());
        return getKnowledgeBaseDetail(knowledgeBase.getId(), userId);
    }

    @Override
    @Transactional
    public SharedKnowledgeBaseVO updateKnowledgeBase(Long knowledgeBaseId, SharedKnowledgeBaseUpdateDTO updateDTO, Long userId) {
        // 楠岃瘉鏉冮檺
        if (!isCreator(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11004, "鍙湁鍒涘缓鑰呭彲浠ヤ慨鏀圭煡璇嗗簱淇℃伅");
        }

        SharedKnowledgeBase knowledgeBase = this.getById(knowledgeBaseId);
        if (knowledgeBase == null || knowledgeBase.getStatus() != 1) {
            throw new BizException(BizResponseCode.ERROR_1, "鐭ヨ瘑搴撲笉瀛樺湪");
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
            knowledgeBase.setPassword(null); // 娓呯┖瀵嗙爜
        }

        this.updateById(knowledgeBase);

        log.info("鐢ㄦ埛 {} 鏇存柊鍏变韩鐭ヨ瘑搴撴垚鍔? {}", userId, knowledgeBase.getName());
        return getKnowledgeBaseDetail(knowledgeBaseId, userId);
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(Long knowledgeBaseId, Long userId) {
        // 楠岃瘉鏉冮檺
        if (!isCreator(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERR_11004, "鍙湁鍒涘缓鑰呭彲浠ュ垹闄ょ煡璇嗗簱");
        }

        SharedKnowledgeBase knowledgeBase = this.getById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BizException(BizResponseCode.ERROR_1, "鐭ヨ瘑搴撲笉瀛樺湪");
        }

        // 删除知识库
        baseMapper.deleteById(knowledgeBaseId);
        // 鍒犻櫎鍚戦噺鏁版嵁
        try {
            vectorService.deleteSharedKnowledgeBaseVectors(knowledgeBaseId);
        } catch (Exception e) {
            log.error("鍒犻櫎鐭ヨ瘑搴?{} 鍚戦噺鏁版嵁澶辫触: {}", knowledgeBaseId, e.getMessage());
        }
        //鍒犻櫎鏂囦欢
        fileService.remove(new LambdaQueryWrapper<KnowledgeBaseFile>().eq(KnowledgeBaseFile::getKnowledgeBaseId, knowledgeBaseId));
        //鍒犻櫎鎴愬憳
        memberMapper.delete(new LambdaQueryWrapper<KnowledgeBaseMember>().eq(KnowledgeBaseMember::getKnowledgeBaseId, knowledgeBaseId));
        log.info("鐢ㄦ埛 {} 鍒犻櫎鍏变韩鐭ヨ瘑搴撴垚鍔? {}", userId, knowledgeBase.getName());
    }

    @Override
    public SharedKnowledgeBaseVO getKnowledgeBaseDetail(Long knowledgeBaseId, Long userId) {
        SharedKnowledgeBaseVO detail = this.baseMapper.selectDetailById(knowledgeBaseId, userId);
        if (detail == null) {
            throw new BizException(BizResponseCode.ERROR_1, "鐭ヨ瘑搴撲笉瀛樺湪");
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
            throw new BizException(BizResponseCode.ERROR_1, "鐭ヨ瘑搴撲笉瀛樺湪");
        }

        // 妫€鏌ユ槸鍚﹀凡缁忔槸鎴愬憳
        if (memberMapper.existsByKnowledgeBaseIdAndUserId(joinDTO.getKnowledgeBaseId(), userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "您已经是该知识库成员");
        }

        // 楠岃瘉瀵嗙爜
        if (StrUtil.isNotBlank(knowledgeBase.getPassword())) {
            if (StrUtil.isBlank(joinDTO.getPassword())) {
                throw new BizException(BizResponseCode.ERROR_1, "该知识库需要密码才能加入");
            }
            
            String encryptedPassword = DigestUtil.sha256Hex(joinDTO.getPassword());
            if (!encryptedPassword.equals(knowledgeBase.getPassword())) {
                throw new BizException(BizResponseCode.ERROR_1, "瀵嗙爜閿欒");
            }
        }

        // 娣诲姞鎴愬憳
        KnowledgeBaseMember member = new KnowledgeBaseMember();
        member.setKnowledgeBaseId(joinDTO.getKnowledgeBaseId());
        member.setUserId(userId);
        member.setRole(KnowledgeBaseRoleEnum.MEMBER.getValue());
        member.setJoinTime(LocalDateTime.now());
        memberMapper.insert(member);
        refreshStatistics(joinDTO.getKnowledgeBaseId());

        log.info("鐢ㄦ埛 {} 鍔犲叆鍏变韩鐭ヨ瘑搴撴垚鍔? {}", userId, knowledgeBase.getName());
    }

    @Override
    @Transactional
    public void leaveKnowledgeBase(Long knowledgeBaseId, Long userId) {
        if (isCreator(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "创建者不能退出自己的知识库");
        }

        // 妫€鏌ユ槸鍚︿负鎴愬憳
        if (!memberMapper.existsByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "鎮ㄤ笉鏄鐭ヨ瘑搴撶殑鎴愬憳");
        }

        // 鍒犻櫎鎴愬憳璁板綍
        LambdaQueryWrapper<KnowledgeBaseMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseMember::getKnowledgeBaseId, knowledgeBaseId)
               .eq(KnowledgeBaseMember::getUserId, userId);
        memberMapper.delete(wrapper);
        refreshStatistics(knowledgeBaseId);

        log.info("鐢ㄦ埛 {} 閫€鍑哄叡浜煡璇嗗簱鎴愬姛: {}", userId, knowledgeBaseId);
    }

    @Override
    @Transactional
    public void removeMember(Long knowledgeBaseId, Long memberId, Long operatorId) {
        if (!isCreator(knowledgeBaseId, operatorId)) {
            throw new BizException(BizResponseCode.ERR_11004, "只有创建者可以移除成员");
        }

        // 涓嶈兘绉婚櫎鑷繁
        if (memberId.equals(operatorId)) {
            throw new BizException(BizResponseCode.ERROR_1, "涓嶈兘绉婚櫎鑷繁");
        }

        if (!memberMapper.existsByKnowledgeBaseIdAndUserId(knowledgeBaseId, memberId)) {
            throw new BizException(BizResponseCode.ERROR_1, "璇ョ敤鎴蜂笉鏄煡璇嗗簱鎴愬憳");
        }

        // 鍒犻櫎鎴愬憳璁板綍
        LambdaQueryWrapper<KnowledgeBaseMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseMember::getKnowledgeBaseId, knowledgeBaseId)
               .eq(KnowledgeBaseMember::getUserId, memberId);
        memberMapper.delete(wrapper);
        refreshStatistics(knowledgeBaseId);

        log.info("鐢ㄦ埛 {} 浠庡叡浜煡璇嗗簱 {} 绉婚櫎鎴愬憳 {}", operatorId, knowledgeBaseId, memberId);
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

