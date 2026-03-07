package com.hyang.ich.common.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码 */
    private int pageNum;

    /** 每页数量 */
    private int pageSize;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private int totalPages;

    /** 数据列表 */
    private List<T> list;

    public PageResult() {}

    public PageResult(int pageNum, int pageSize, long total, List<T> list) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        this.list = list;
    }

    public static <T> PageResult<T> of(int pageNum, int pageSize, long total, List<T> list) {
        return new PageResult<>(pageNum, pageSize, total, list);
    }
}
