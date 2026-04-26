package com.aezer0.initialization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aezer0.initialization.domain.Note;
import com.aezer0.initialization.vo.NoteBriefVO;
import com.aezer0.initialization.vo.NoteDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 笔记Mapper接口
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 分页查询笔记列表（简要信息）
     * @param page 分页对象
     * @param notebookId 笔记本ID
     * @param userId 用户ID
     * @param keyword 搜索关键词
     * @return 笔记列表
     */
    @Select("<script>" +
            "SELECT n.*, nb.name as notebook_name " +
            "FROM t_note n " +
            "LEFT JOIN t_notebook nb ON n.notebook_id = nb.id " +
            "WHERE n.user_id = #{userId} " +
            "<if test='notebookId != null'> AND n.notebook_id = #{notebookId} </if> " +
            "<if test='keyword != null and keyword != \"\"'> AND (n.title LIKE CONCAT('%', #{keyword}, '%') OR n.content_md LIKE CONCAT('%', #{keyword}, '%')) </if> " +
            "ORDER BY n.is_pinned DESC, n.update_time DESC" +
            "</script>")
    IPage<NoteBriefVO> selectNotesBriefByPage(Page<NoteBriefVO> page, @Param("notebookId") Long notebookId, @Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 查询笔记详情
     * @param noteId 笔记ID
     * @param userId 用户ID
     * @return 笔记详情
     */
    @Select("SELECT n.*, nb.name as notebook_name " +
            "FROM t_note n " +
            "LEFT JOIN t_notebook nb ON n.notebook_id = nb.id " +
            "WHERE n.id = #{noteId} AND n.user_id = #{userId}")
    NoteDetailVO selectNoteDetailById(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /**
     * 增加笔记查看次数
     * @param noteId 笔记ID
     */
    @Update("UPDATE t_note SET view_count = view_count + 1 WHERE id = #{noteId}")
    void incrementViewCount(@Param("noteId") Long noteId);
} 