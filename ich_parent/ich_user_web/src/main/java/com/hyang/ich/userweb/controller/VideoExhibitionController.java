package com.hyang.ich.userweb.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.content.VideoExhibitionService;
import com.hyang.ich.content.dto.VideoExhibitionDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/video")
public class VideoExhibitionController {

    @DubboReference(check = false)
    private VideoExhibitionService videoExhibitionService;

    @GetMapping("/exhibition/list")
    public Result<List<VideoExhibitionDTO>> list() {
        return Result.success(videoExhibitionService.listByStatus(1));
    }

    @GetMapping("/exhibition/{id}")
    public Result<VideoExhibitionDTO> get(@PathVariable Long id) {
        return Result.success(videoExhibitionService.getById(id));
    }
}
