package com.aezer0.initialization.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 包装分页数据
 */
@Data
public class PageResult<T> {

    private List<T> data;

    private Long total;


    /**
     * mpPage转成Page
     *
     * @param mpPage mp的分页结果
     * @param <T>    类型
     * @return page
     */
    public static <T> PageResult<T> convert(IPage<T> mpPage) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setData(mpPage.getRecords());
        pageResult.setTotal(mpPage.getTotal());
        return pageResult;
    }
}
