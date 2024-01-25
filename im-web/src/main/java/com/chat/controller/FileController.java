package com.chat.controller;


import com.chat.result.Result;
import com.chat.result.ResultUtils;
import com.chat.service.thirdparty.FileService;
import com.chat.vo.UploadImageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@Api(tags = "文件上传")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @ApiOperation(value = "上传图片", notes = "上传图片,上传后返回原图和缩略图的url")
    @PostMapping("/image/upload")
    public Result<UploadImageVO> uploadImage(MultipartFile file) {
        return ResultUtils.success(fileService.uploadImage(file));
    }

    @ApiOperation(value = "上传文件", notes = "上传文件，上传后返回文件url")
    @PostMapping("/file/upload")
    public Result<String> uploadFile(MultipartFile file) {
        return ResultUtils.success(fileService.uploadFile(file), "");
    }

}