package com.hyang.ich.common;

import com.hyang.ich.common.dto.BrowseHistoryDTO;

import java.util.List;

public interface BrowseHistoryService {

    /** 记录浏览历史 */
    void record(BrowseHistoryDTO dto);

    /** 查询用户某一天的浏览历史 */
    List<BrowseHistoryDTO> listByDate(Long userId, String date);

    /** 查询用户最近N天的浏览历史（按天分组返回） */
    List<BrowseHistoryDTO> listRecent(Long userId, int days);

    /** 查询用户对某类目标的浏览历史 */
    List<BrowseHistoryDTO> listByType(Long userId, String targetType, int limit);

    /** 统计用户某一天的浏览次数 */
    int countByDate(Long userId, String date);

    /** 查询用户浏览过的日期列表（最近N天） */
    List<String> listBrowseDates(Long userId, int days);
}
