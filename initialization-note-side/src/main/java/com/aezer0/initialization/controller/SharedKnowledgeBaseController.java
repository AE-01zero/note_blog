package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aezer0.initialization.dto.*;
import com.aezer0.initialization.mapper.KnowledgeBaseMemberMapper;
import com.aezer0.initialization.result.PageResult;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.KnowledgeBaseFileService;
import com.aezer0.initialization.service.SharedKnowledgeBaseService;
import com.aezer0.initialization.service.ai.UserConsultantService;
import com.aezer0.initialization.vo.KnowledgeBaseFileVO;
import com.aezer0.initialization.vo.KnowledgeBaseMemberVO;
import com.aezer0.initialization.vo.KnowledgeBaseSquareVO;
import com.aezer0.initialization.vo.SharedKnowledgeBaseVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 鍏变韩鐭ヨ瘑搴撴帶鍒跺櫒
 * 鎻愪緵鍏变韩鐭ヨ瘑搴撶殑鍒涘缓銆佺鐞嗐€佹枃浠朵笂浼犮€丄I瀵硅瘽绛夊姛鑳? */
@RestController
@RequestMapping("/api/shared-knowledge-base")
@Slf4j
public class SharedKnowledgeBaseController {

    @Autowired
    private SharedKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private KnowledgeBaseFileService fileService;

    @Autowired
    private UserConsultantService consultantService;

    @Autowired
    @Qualifier("streamingExecutor")
    private ThreadPoolTaskExecutor streamingExecutor;

    @Autowired
    private KnowledgeBaseMemberMapper knowledgeBaseMemberMapper;

    /**
     * 鍒涘缓鍏变韩鐭ヨ瘑搴?     * 鐢ㄦ埛鍙互鍒涘缓鑷繁鐨勫叡浜煡璇嗗簱锛屾敮鎸佽缃悕绉般€佹弿杩般€佸皝闈€佸瘑鐮佺瓑
     *
     * @param createDTO 鍒涘缓鐭ヨ瘑搴撶殑璇锋眰鍙傛暟
     * @return 鍒涘缓鎴愬姛鐨勭煡璇嗗簱淇℃伅
     */
    @PostMapping
    @SaCheckLogin
    public Result<SharedKnowledgeBaseVO> createKnowledgeBase(@Valid @RequestBody SharedKnowledgeBaseCreateDTO createDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        SharedKnowledgeBaseVO result = knowledgeBaseService.createKnowledgeBase(createDTO, userId);
        return Result.success(result);
    }

    /**
     * 鏇存柊鍏变韩鐭ヨ瘑搴撲俊鎭?     * 鍙湁鐭ヨ瘑搴撳垱寤鸿€呭彲浠ユ搷浣滐紝鏀寔淇敼鍚嶇О銆佹弿杩般€佸皝闈€佸瘑鐮併€佸叕寮€鐘舵€佺瓑
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @param updateDTO       鏇存柊鍙傛暟
     * @return 鏇存柊鍚庣殑鐭ヨ瘑搴撲俊鎭?     */
    @PutMapping("/{knowledgeBaseId}")
    @SaCheckLogin
    public Result<SharedKnowledgeBaseVO> updateKnowledgeBase(@PathVariable Long knowledgeBaseId,
                                                             @Valid @RequestBody SharedKnowledgeBaseUpdateDTO updateDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        SharedKnowledgeBaseVO result = knowledgeBaseService.updateKnowledgeBase(knowledgeBaseId, updateDTO, userId);
        return Result.success(result);
    }

    /**
     * 鍒犻櫎鍏变韩鐭ヨ瘑搴?     * 鍙湁鐭ヨ瘑搴撳垱寤鸿€呭彲浠ユ搷浣滐紝鍒犻櫎鍚庡皢娓呯悊鐩稿叧鐨勬枃浠跺拰鍚戦噺鏁版嵁
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @return 鎿嶄綔缁撴灉
     */
    @DeleteMapping("/{knowledgeBaseId}")
    @SaCheckLogin
    public Result<Void> deleteKnowledgeBase(@PathVariable Long knowledgeBaseId) {
        Long userId = StpUtil.getLoginIdAsLong();
        knowledgeBaseService.deleteKnowledgeBase(knowledgeBaseId, userId);
        return Result.success();
    }

    /**
     * 鑾峰彇鐭ヨ瘑搴撹鎯?     * 鑾峰彇鎸囧畾鐭ヨ瘑搴撶殑璇︾粏淇℃伅锛屽寘鎷熀鏈俊鎭€佹垚鍛樹俊鎭€佺敤鎴锋潈闄愮瓑
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @return 鐭ヨ瘑搴撹缁嗕俊鎭?     */
    @GetMapping("/{knowledgeBaseId}")
    @SaCheckLogin
    public Result<SharedKnowledgeBaseVO> getKnowledgeBaseDetail(@PathVariable Long knowledgeBaseId) {
        Long userId = StpUtil.getLoginIdAsLong();
        SharedKnowledgeBaseVO result = knowledgeBaseService.getKnowledgeBaseDetail(knowledgeBaseId, userId);
        return Result.success(result);
    }

    /**
     * 鑾峰彇鎴戝垱寤虹殑鐭ヨ瘑搴撳垪琛?     * 杩斿洖褰撳墠鐢ㄦ埛浣滀负鍒涘缓鑰呯殑鎵€鏈夌煡璇嗗簱
     *
     * @return 鐭ヨ瘑搴撳垪琛?     */
    @GetMapping("/my-created")
    @SaCheckLogin
    public Result<List<SharedKnowledgeBaseVO>> getMyCreatedKnowledgeBases() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<SharedKnowledgeBaseVO> result = knowledgeBaseService.getCreatedKnowledgeBases(userId);
        return Result.success(result);
    }

    /**
     * 鑾峰彇鎴戝姞鍏ョ殑鐭ヨ瘑搴撳垪琛?     * 杩斿洖褰撳墠鐢ㄦ埛浣滀负鎴愬憳鍔犲叆鐨勬墍鏈夌煡璇嗗簱
     *
     * @return 鐭ヨ瘑搴撳垪琛?     */
    @GetMapping("/my-joined")
    @SaCheckLogin
    public Result<List<SharedKnowledgeBaseVO>> getMyJoinedKnowledgeBases() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<SharedKnowledgeBaseVO> result = knowledgeBaseService.getJoinedKnowledgeBases(userId);
        return Result.success(result);
    }

    /**
     * 鐭ヨ瘑搴撳箍鍦?     * 鎼滅储鍜屾祻瑙堝叕寮€鐨勭煡璇嗗簱锛屾敮鎸佸叧閿瘝鎼滅储銆佹帓搴忋€佸垎椤?     *
     * @param keyword   鎼滅储鍏抽敭璇嶏紝鍙€?     * @param sortBy    鎺掑簭瀛楁锛岄粯璁や负鍒涘缓鏃堕棿
     * @param sortOrder 鎺掑簭椤哄簭锛岄粯璁や负闄嶅簭
     * @param page      椤电爜锛岄粯璁や负1
     * @param size      姣忛〉澶у皬锛岄粯璁や负10
     * @return 鍒嗛〉鐨勭煡璇嗗簱鍒楄〃
     */
    @GetMapping("/square")
    @SaCheckLogin
    public Result<PageResult<KnowledgeBaseSquareVO>> getKnowledgeBaseSquare(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "create_time") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        Long userId = StpUtil.getLoginIdAsLong();
        KnowledgeBaseSearchDTO searchDTO = new KnowledgeBaseSearchDTO();
        searchDTO.setKeyword(keyword);
        searchDTO.setSortBy(sortBy);
        searchDTO.setSortOrder(sortOrder);
        searchDTO.setPage(page);
        searchDTO.setSize(size);
        searchDTO.setOnlyPublic(true);

        IPage<KnowledgeBaseSquareVO> result = knowledgeBaseService.searchKnowledgeBaseSquare(searchDTO, userId);
        return Result.success(PageResult.convert(result));
    }

    /**
     * 鍔犲叆鐭ヨ瘑搴?     * 鐢ㄦ埛鐢宠鍔犲叆鎸囧畾鐨勭煡璇嗗簱锛屽鏋滅煡璇嗗簱璁剧疆浜嗗瘑鐮佸垯闇€瑕佹彁渚涙纭瘑鐮?     *
     * @param joinDTO 鍔犲叆璇锋眰鍙傛暟锛屽寘鍚煡璇嗗簱ID鍜屽瘑鐮?     * @return 鎿嶄綔缁撴灉
     */
    @PostMapping("/join")
    @SaCheckLogin
    public Result<Void> joinKnowledgeBase(@Valid @RequestBody KnowledgeBaseJoinDTO joinDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        knowledgeBaseService.joinKnowledgeBase(joinDTO, userId);
        return Result.success();
    }

    /**
     * 閫€鍑虹煡璇嗗簱
     * 鐢ㄦ埛涓诲姩閫€鍑烘寚瀹氱殑鐭ヨ瘑搴擄紝鍒涘缓鑰呬笉鑳介€€鍑鸿嚜宸辩殑鐭ヨ瘑搴?     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @return 鎿嶄綔缁撴灉
     */
    @PostMapping("/{knowledgeBaseId}/leave")
    @SaCheckLogin
    public Result<Void> leaveKnowledgeBase(@PathVariable Long knowledgeBaseId) {
        Long userId = StpUtil.getLoginIdAsLong();
        knowledgeBaseService.leaveKnowledgeBase(knowledgeBaseId, userId);
        return Result.success();
    }

    /**
     * 绉婚櫎鎴愬憳
     * 鐭ヨ瘑搴撳垱寤鸿€呭彲浠ョЩ闄ゆ寚瀹氱殑鎴愬憳锛岃绉婚櫎鐨勬垚鍛樺皢澶卞幓璁块棶鏉冮檺
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @param memberId        瑕佺Щ闄ょ殑鎴愬憳ID
     * @return 鎿嶄綔缁撴灉
     */
    @DeleteMapping("/{knowledgeBaseId}/members/{memberId}")
    @SaCheckLogin
    public Result<Void> removeMember(@PathVariable Long knowledgeBaseId,
                                     @PathVariable Long memberId) {
        Long userId = StpUtil.getLoginIdAsLong();
        knowledgeBaseService.removeMember(knowledgeBaseId, memberId, userId);
        return Result.success();
    }

    // ==================== 鏂囦欢绠＄悊鐩稿叧鎺ュ彛 ====================

    /**
     * 涓婁紶鏂囦欢鍒扮煡璇嗗簱
     * 灏嗘湰鍦版枃浠朵笂浼犲埌鎸囧畾鐨勭煡璇嗗簱涓紝鏂囦欢浼氳嚜鍔ㄨ繘琛屽悜閲忓寲澶勭悊浠ユ敮鎸丄I妫€绱?     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @param file            瑕佷笂浼犵殑鏂囦欢
     * @return 涓婁紶鎴愬姛鐨勬枃浠朵俊鎭?     */
    @PostMapping("/{knowledgeBaseId}/files/upload")
    @SaCheckLogin
    public Result<KnowledgeBaseFileVO> uploadFile(@PathVariable Long knowledgeBaseId,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "category", required = false) String category) {
        Long userId = StpUtil.getLoginIdAsLong();
        KnowledgeBaseFileVO result = fileService.uploadFile(knowledgeBaseId, file, userId, category);
        return Result.success(result);
    }

    /**
     * 澶嶅埗鏂囦欢鍒扮煡璇嗗簱
     * 浠庣敤鎴风殑涓汉鐭ヨ瘑搴撳鍒跺凡鏈夋枃浠跺埌鍏变韩鐭ヨ瘑搴撲腑
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @param uploadDTO       澶嶅埗璇锋眰鍙傛暟锛屽寘鍚澶嶅埗鐨勬枃浠禝D鍒楄〃
     * @return 澶嶅埗鎿嶄綔缁撴灉锛屽寘鍚垚鍔熷拰澶辫触鐨勬枃浠剁粺璁?     */
    @PostMapping("/{knowledgeBaseId}/files/copy")
    @SaCheckLogin
    public Result<Map<String, Object>> copyFiles(@PathVariable Long knowledgeBaseId,
                                                 @Valid @RequestBody KnowledgeBaseFileUploadDTO uploadDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        uploadDTO.setKnowledgeBaseId(knowledgeBaseId);
        Map<String, Object> result = fileService.copyFilesToKnowledgeBase(uploadDTO, userId);
        return Result.success(result);
    }

    /**
     * 鏇存柊鐭ヨ瘑搴撴枃浠跺垎绫?     */
    @PutMapping("/{knowledgeBaseId}/files/{fileId}/category")
    @SaCheckLogin
    public Result<Void> updateFileCategory(@PathVariable Long knowledgeBaseId,
                                           @PathVariable Long fileId,
                                           @RequestParam(value = "category", required = false) String category) {
        Long userId = StpUtil.getLoginIdAsLong();
        fileService.updateFileCategory(knowledgeBaseId, fileId, category, userId);
        return Result.success();
    }

    /**
     * 鍒犻櫎鐭ヨ瘑搴撴枃浠?     * 浠庣煡璇嗗簱涓垹闄ゆ寚瀹氱殑鏂囦欢锛屽悓鏃朵細娓呯悊鐩稿叧鐨勫悜閲忔暟鎹?     * 鍒涘缓鑰呭彲浠ュ垹闄や换浣曟枃浠讹紝鏅€氭垚鍛樺彧鑳藉垹闄よ嚜宸变笂浼犵殑鏂囦欢
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @param fileId          鏂囦欢ID
     * @return 鎿嶄綔缁撴灉
     */
    @DeleteMapping("/{knowledgeBaseId}/files/{fileId}")
    @SaCheckLogin
    public Result<Void> deleteFile(@PathVariable Long knowledgeBaseId,
                                   @PathVariable Long fileId) {
        Long userId = StpUtil.getLoginIdAsLong();
        fileService.deleteFile(knowledgeBaseId, fileId, userId);
        return Result.success();
    }

    /**
     * 鑾峰彇鐭ヨ瘑搴撴枃浠跺垪琛?     * 鍒嗛〉鑾峰彇鐭ヨ瘑搴撲腑鐨勬墍鏈夋枃浠讹紝鏀寔鎸夋枃浠跺悕鎼滅储
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D
     * @param page            椤电爜锛岄粯璁や负1
     * @param size            姣忛〉澶у皬锛岄粯璁や负10
     * @param keyword         鎼滅储鍏抽敭璇嶏紝鍙€?     * @return 鍒嗛〉鐨勬枃浠跺垪琛?     */
    @GetMapping("/{knowledgeBaseId}/files")
    @SaCheckLogin
    public Result<PageResult<KnowledgeBaseFileVO>> getKnowledgeBaseFiles(
            @PathVariable Long knowledgeBaseId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Long userId = StpUtil.getLoginIdAsLong();
        IPage<KnowledgeBaseFileVO> result = fileService.getKnowledgeBaseFiles(knowledgeBaseId, page, size, keyword, userId);
        return Result.success(PageResult.convert(result));
    }

    @GetMapping("/{knowledgeBaseId}/categories")
    @SaCheckLogin
    public Result<List<String>> getKnowledgeBaseCategories(@PathVariable Long knowledgeBaseId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(fileService.getKnowledgeBaseCategories(knowledgeBaseId, userId));
    }

    /**
     * 鑾峰彇涓汉鏂囦欢鍒楄〃
     * 鑾峰彇鐢ㄦ埛涓汉鐭ヨ瘑搴撲腑鐨勬墍鏈夋枃浠讹紝鐢ㄤ簬澶嶅埗鍒板叡浜煡璇嗗簱鏃堕€夋嫨
     *
     * @return 鐢ㄦ埛鐨勪釜浜烘枃浠跺垪琛?     */
    @GetMapping("/personal-files")
    @SaCheckLogin
    public Result<List<KnowledgeBaseFileVO>> getPersonalFiles() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<KnowledgeBaseFileVO> result = fileService.getPersonalFiles(userId);
        return Result.success(result);
    }

    // ==================== AI 瀵硅瘽鐩稿叧鎺ュ彛 ====================

    /**
     * 鍏变韩鐭ヨ瘑搴揂I瀵硅瘽锛堟祦寮忥級
     * 鍩轰簬鎸囧畾鍏变韩鐭ヨ瘑搴撹繘琛孉I瀵硅瘽锛屾敮鎸佹祦寮忓搷搴斾互鎻愪緵鏇村ソ鐨勭敤鎴蜂綋楠?     *
     * @param chatDTO 瀵硅瘽璇锋眰鍙傛暟
     * @return 娴佸紡AI鍥炲
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckLogin
    public Flux<String> chatWithKnowledgeBaseStream(
            @Valid @RequestBody SharedKnowledgeBaseChatDTO chatDTO) {
        Long userId = StpUtil.getLoginIdAsLong();

        // Verify whether the user has permission to access this knowledge base.
        if (!knowledgeBaseService.hasPermission(chatDTO.getKnowledgeBaseId(), userId)) {
            return Flux.error(new RuntimeException("No permission to access this knowledge base"));
        }

        chatDTO.setKnowledgeBaseId(chatDTO.getKnowledgeBaseId());
        return consultantService.chatWithSharedKnowledgeBaseStream(chatDTO, userId)
                .subscribeOn(Schedulers.fromExecutor(streamingExecutor))
                .timeout(Duration.ofSeconds(60), Flux.just("抱歉，共享知识库AI响应超时，请稍后重试。"))
                .map(chunk -> chunk.replace("\n", "\\n"))
                .onErrorResume(throwable -> {
                    log.error("共享知识库流式对话失败: {}", throwable.getMessage(), throwable);
                    return Flux.just("抱歉，共享知识库AI助手暂时不可用，请稍后重试。");
                });
    }

    /**
     * 鏌ヨ鐭ヨ瘑搴撶殑瀹屾暣鎴愬憳鍒楄〃
     * 瀵瑰簲 Mapper 鐨?selectMembersByKnowledgeBaseId 鏂规硶
     * 鏉冮檺锛氶€氬父鐭ヨ瘑搴撶殑鎵€鏈夋垚鍛橀兘鍙互鏌ョ湅
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D (浠嶶RL璺緞涓幏鍙?
     * @return 鎴愬憳VO鍒楄〃
     */
    @GetMapping("/members/{knowledgeBaseId}")
    @SaCheckLogin
    public Result<List<KnowledgeBaseMemberVO>> listMembers(@PathVariable Long knowledgeBaseId) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeBaseService.hasPermission(knowledgeBaseId, userId)) {
            return Result.error(new RuntimeException("No permission to access this knowledge base"));
        }
        List<KnowledgeBaseMemberVO> members = knowledgeBaseMemberMapper.selectMembersByKnowledgeBaseId(knowledgeBaseId);
        return Result.success(members);
    }

    /**
     * 鑾峰彇鎸囧畾鐨勫崟涓垚鍛樹俊鎭?     * 瀵瑰簲 Mapper 鐨?selectMemberInfo 鏂规硶
     * 鏉冮檺锛氶€氬父鐭ヨ瘑搴撶殑鎵€鏈夋垚鍛橀兘鍙互鏌ョ湅
     *
     * @param knowledgeBaseId 鐭ヨ瘑搴揑D (浠嶶RL璺緞涓幏鍙?
     * @param userId          瑕佹煡璇㈢殑鎴愬憳鐢ㄦ埛ID (浠嶶RL璺緞涓幏鍙?
     * @return 鎸囧畾鎴愬憳鐨刅O淇℃伅
     */
    @GetMapping("/member/{userId}/{knowledgeBaseId}")
    @SaCheckLogin
    public Result<KnowledgeBaseMemberVO> getMemberInfo(@PathVariable Long knowledgeBaseId, @PathVariable Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (!knowledgeBaseService.hasPermission(knowledgeBaseId, currentUserId)) {
            return Result.error(new RuntimeException("No permission to access this knowledge base"));
        }
        KnowledgeBaseMemberVO memberInfo = knowledgeBaseMemberMapper.selectMemberInfo(knowledgeBaseId, userId);
        return Result.success(memberInfo);
    }

    /**
     * 鍒犻櫎鎸囧畾鎴愬憳
     */
    @DeleteMapping("/members/{deleteUserId}/{knowledgeBaseId}")
    @SaCheckLogin
    public Result<Void> deleteMember(@PathVariable Long knowledgeBaseId, @PathVariable Long deleteUserId) {
        Long userId = StpUtil.getLoginIdAsLong();
        knowledgeBaseService.removeMember(knowledgeBaseId, deleteUserId, userId);
        return Result.success();
    }

} 
