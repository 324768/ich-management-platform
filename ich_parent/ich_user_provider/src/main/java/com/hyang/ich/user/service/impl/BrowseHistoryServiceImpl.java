package com.hyang.ich.user.service.impl;

import com.hyang.ich.common.BrowseHistoryService;
import com.hyang.ich.common.dto.BrowseHistoryDTO;
import com.hyang.ich.user.entity.BrowseHistory;
import com.hyang.ich.user.mapper.common.BrowseHistoryMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@DubboService
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    private static final Logger log = LoggerFactory.getLogger(BrowseHistoryServiceImpl.class);

    @Autowired
    private BrowseHistoryMapper browseHistoryMapper;

    @Override
    public void record(BrowseHistoryDTO dto) {
        BrowseHistory entity = new BrowseHistory();
        entity.setUserId(dto.getUserId());
        entity.setTargetType(dto.getTargetType());
        entity.setTargetId(dto.getTargetId());
        entity.setTargetTitle(dto.getTargetTitle());
        entity.setDurationSeconds(dto.getDurationSeconds() != null ? dto.getDurationSeconds() : 0);
        entity.setSource(dto.getSource() != null ? dto.getSource() : "web");
        browseHistoryMapper.insert(entity);
        log.debug("浏览历史记录: userId={}, type={}, targetId={}", dto.getUserId(), dto.getTargetType(), dto.getTargetId());
    }

    @Override
    public List<BrowseHistoryDTO> listByDate(Long userId, String date) {
        List<BrowseHistory> list = browseHistoryMapper.selectByDate(userId, date);
        return toDTOList(list);
    }

    @Override
    public List<BrowseHistoryDTO> listRecent(Long userId, int days) {
        List<BrowseHistory> list = browseHistoryMapper.selectRecent(userId, days);
        return toDTOList(list);
    }

    @Override
    public List<BrowseHistoryDTO> listByType(Long userId, String targetType, int limit) {
        List<BrowseHistory> list = browseHistoryMapper.selectByType(userId, targetType, limit);
        return toDTOList(list);
    }

    @Override
    public int countByDate(Long userId, String date) {
        return browseHistoryMapper.countByDate(userId, date);
    }

    @Override
    public List<String> listBrowseDates(Long userId, int days) {
        return browseHistoryMapper.selectBrowseDates(userId, days);
    }

    private List<BrowseHistoryDTO> toDTOList(List<BrowseHistory> entities) {
        List<BrowseHistoryDTO> dtoList = new ArrayList<>();
        if (entities != null) {
            for (BrowseHistory e : entities) {
                dtoList.add(toDTO(e));
            }
        }
        return dtoList;
    }

    private BrowseHistoryDTO toDTO(BrowseHistory e) {
        BrowseHistoryDTO dto = new BrowseHistoryDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setTargetType(e.getTargetType());
        dto.setTargetId(e.getTargetId());
        dto.setTargetTitle(e.getTargetTitle());
        dto.setBrowseDate(e.getBrowseDate());
        dto.setBrowseTime(e.getBrowseTime());
        dto.setDurationSeconds(e.getDurationSeconds());
        dto.setSource(e.getSource());
        dto.setCreateTime(e.getCreateTime());
        return dto;
    }
}
