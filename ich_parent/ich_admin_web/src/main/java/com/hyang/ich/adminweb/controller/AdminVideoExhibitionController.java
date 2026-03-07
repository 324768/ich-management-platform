package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.content.VideoExhibitionService;
import com.hyang.ich.content.dto.VideoExhibitionDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/video/exhibition")
public class AdminVideoExhibitionController {

    @DubboReference
    private VideoExhibitionService videoExhibitionService;

    @GetMapping("/list")
    public Result<List<VideoExhibitionDTO>> list(@RequestParam(required = false) Integer status) {
        List<VideoExhibitionDTO> list = status != null
                ? videoExhibitionService.listByStatus(status)
                : videoExhibitionService.listAll();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<VideoExhibitionDTO> getById(@PathVariable Long id) {
        return Result.success(videoExhibitionService.getById(id));
    }

    @PostMapping("/add")
    public Result<VideoExhibitionDTO> add(@RequestBody VideoExhibitionDTO dto) {
        return Result.success(videoExhibitionService.add(dto));
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody VideoExhibitionDTO dto) {
        videoExhibitionService.update(dto);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        videoExhibitionService.delete(id);
        return Result.success(null);
    }
}
