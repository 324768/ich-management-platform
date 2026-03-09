package com.hyang.ich.omnitrix.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageVO<T> {

    private List<T> list;
    private int total;

    public static <T> PageVO<T> of(List<T> list, int total) {
        PageVO<T> vo = new PageVO<>();
        vo.setList(list);
        vo.setTotal(total);
        return vo;
    }
}
