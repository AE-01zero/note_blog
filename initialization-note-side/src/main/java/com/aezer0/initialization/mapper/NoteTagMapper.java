package com.aezer0.initialization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aezer0.initialization.domain.NoteTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 笔记标签Mapper接口
 */
@Mapper
public interface NoteTagMapper extends BaseMapper<NoteTag> {

    /**
     * 根据笔记ID查询标签列表
     * @param noteId 笔记ID
     * @return 标签列表
     */
    @Select("SELECT tag_name FROM t_note_tag WHERE note_id = #{noteId}")
    List<String> selectTagsByNoteId(@Param("noteId") Long noteId);

    /**
     * 删除笔记的所有标签
     * @param noteId 笔记ID
     */
    @Delete("DELETE FROM t_note_tag WHERE note_id = #{noteId}")
    void deleteTagsByNoteId(@Param("noteId") Long noteId);

    /**
     * 获取用户的所有标签（去重）
     * @param userId 用户ID
     * @return 标签列表
     */
    @Select("SELECT DISTINCT nt.tag_name FROM t_note_tag nt " +
            "INNER JOIN t_note n ON nt.note_id = n.id " +
            "WHERE n.user_id = #{userId} " +
            "ORDER BY nt.tag_name")
    List<String> selectDistinctTagsByUserId(@Param("userId") Long userId);
} 