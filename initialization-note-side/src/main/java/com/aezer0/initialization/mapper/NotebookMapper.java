package com.aezer0.initialization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aezer0.initialization.domain.Notebook;
import com.aezer0.initialization.vo.NotebookVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 笔记本Mapper接口
 */
@Mapper
public interface NotebookMapper extends BaseMapper<Notebook> {

    /**
     * 查询用户的笔记本列表（包含笔记数量）
     * @param userId 用户ID
     * @return 笔记本列表
     */
    @Select("SELECT n.*, " +
            "COALESCE(note_count.count, 0) as note_count, " +
            "bc.name as category_name " +
            "FROM t_notebook n " +
            "LEFT JOIN (SELECT notebook_id, COUNT(*) as count FROM t_note GROUP BY notebook_id) note_count " +
            "ON n.id = note_count.notebook_id " +
            "LEFT JOIN t_blog_category bc ON n.category_id = bc.id " +
            "WHERE n.user_id = #{userId} " +
            "ORDER BY n.sort_order ASC, n.create_time DESC")
    List<NotebookVO> selectNotebooksByUserId(Long userId);
} 