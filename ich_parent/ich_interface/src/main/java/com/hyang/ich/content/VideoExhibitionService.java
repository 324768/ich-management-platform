package com.hyang.ich.content;

import com.hyang.ich.content.dto.VideoExhibitionDTO;

import java.util.List;

public interface VideoExhibitionService {

    List<VideoExhibitionDTO> listAll();

    List<VideoExhibitionDTO> listByStatus(Integer status);

    VideoExhibitionDTO getById(Long id);

    VideoExhibitionDTO add(VideoExhibitionDTO dto);

    void update(VideoExhibitionDTO dto);

    void delete(Long id);

    long count();
}
