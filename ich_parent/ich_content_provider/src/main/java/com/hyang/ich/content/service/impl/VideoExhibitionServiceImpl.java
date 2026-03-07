package com.hyang.ich.content.service.impl;

import com.hyang.ich.content.VideoExhibitionService;
import com.hyang.ich.content.dto.VideoExhibitionDTO;
import com.hyang.ich.content.entity.VideoExhibition;
import com.hyang.ich.content.mapper.content.VideoExhibitionMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

@DubboService
public class VideoExhibitionServiceImpl implements VideoExhibitionService {

    @Autowired
    private VideoExhibitionMapper videoExhibitionMapper;

    @Override
    public List<VideoExhibitionDTO> listAll() {
        return videoExhibitionMapper.selectAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<VideoExhibitionDTO> listByStatus(Integer status) {
        return videoExhibitionMapper.selectByStatus(status).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public VideoExhibitionDTO getById(Long id) {
        VideoExhibition entity = videoExhibitionMapper.selectById(id);
        return entity != null ? toDTO(entity) : null;
    }

    @Override
    public VideoExhibitionDTO add(VideoExhibitionDTO dto) {
        VideoExhibition entity = new VideoExhibition();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getSort() == null) entity.setSort(0);
        if (entity.getViewCount() == null) entity.setViewCount(0);
        if (entity.getLikeCount() == null) entity.setLikeCount(0);
        videoExhibitionMapper.insert(entity);
        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public void update(VideoExhibitionDTO dto) {
        VideoExhibition entity = new VideoExhibition();
        BeanUtils.copyProperties(dto, entity);
        videoExhibitionMapper.update(entity);
    }

    @Override
    public void delete(Long id) {
        videoExhibitionMapper.deleteById(id);
    }

    @Override
    public long count() {
        return videoExhibitionMapper.countAll();
    }

    private VideoExhibitionDTO toDTO(VideoExhibition entity) {
        VideoExhibitionDTO dto = new VideoExhibitionDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
