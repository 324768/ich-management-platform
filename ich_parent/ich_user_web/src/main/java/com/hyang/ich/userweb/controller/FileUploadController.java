package com.hyang.ich.userweb.controller;

import com.hyang.ich.common.vo.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/file")
public class FileUploadController {

    @Value("${file.upload.path:D:/ich-uploads}")
    private String uploadPath;

    @Value("${file.upload.url-prefix:http://localhost:8090/uploads}")
    private String urlPrefix;

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "bizType", defaultValue = "common") String bizType) {
        if (file.isEmpty()) {
            return Result.failed("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";

        String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        String newFilename = UUID.randomUUID().toString().replace("-", "") + ext;
        String relativePath = bizType + "/" + datePath + "/" + newFilename;

        File dest = new File(uploadPath + "/" + relativePath);
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }

        try {
            file.transferTo(dest);
        } catch (IOException e) {
            return Result.failed("文件上传失败: " + e.getMessage());
        }

        Map<String, String> data = new HashMap<>();
        data.put("url", urlPrefix + "/" + relativePath);
        data.put("fileName", originalFilename);
        data.put("fileSize", String.valueOf(file.getSize()));
        data.put("fileType", file.getContentType());

        return Result.success(data);
    }

    @PostMapping("/upload/batch")
    public Result<List<Map<String, String>>> uploadBatch(@RequestParam("files") MultipartFile[] files,
                                                          @RequestParam(value = "bizType", defaultValue = "common") String bizType) {
        List<Map<String, String>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                Result<Map<String, String>> result = upload(file, bizType);
                if (result.getCode() == 200) {
                    results.add(result.getData());
                }
            }
        }
        return Result.success(results);
    }
}
