package com.chat.service.thirdparty;

import com.chat.config.MinIoClientConfig;
import com.chat.config.MinioConfig;
import com.chat.contant.Constant;
import com.chat.enums.FileType;
import com.chat.enums.ResultCode;
import com.chat.exception.GlobalException;
import com.chat.session.SessionContext;
import com.chat.util.FileUtil;
import com.chat.util.ImageUtil;
import com.chat.util.MinioUtil;
import com.chat.vo.UploadImageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.Objects;

/**
 * 通过校验文件MD5实现重复文件秒传
 * 文件上传服务

 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private final MinioUtil minioUtil;

    @Resource
    private MinioConfig minioConfig;



    @PostConstruct
    public void init() {
        if (!minioUtil.bucketExists(minioConfig.getBucketName())) {
            // 创建bucket
            minioUtil.makeBucket(minioConfig.getBucketName());
            // 公开bucket
            minioUtil.setBucketPublic(minioConfig.getBucketName());
        }
    }


    public String uploadFile(MultipartFile file) {
        Long userId = SessionContext.getSession().getUserId();
        // 大小校验
        if (file.getSize() > Constant.MAX_FILE_SIZE) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "文件大小不能超过10M");
        }
        // 上传
        String fileName = minioUtil.upload(minioConfig.getBucketName(), minioConfig.getFilePath(), file);
        if (StringUtils.isEmpty(fileName)) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "文件上传失败");
        }
        String url = generUrl(FileType.FILE, fileName);
        log.info("文件文件成功，用户id:{},url:{}", userId, url);
        return url;
    }

    public UploadImageVO uploadImage(MultipartFile file) {
        try {
            Long userId = SessionContext.getSession().getUserId();
            // 大小校验
            if (file.getSize() > Constant.MAX_IMAGE_SIZE) {
                throw new GlobalException(ResultCode.PROGRAM_ERROR, "图片大小不能超过5M");
            }
            // 图片格式校验
            if (!FileUtil.isImage(file.getOriginalFilename())) {
                throw new GlobalException(ResultCode.PROGRAM_ERROR, "图片格式不合法");
            }
            // 上传原图
            UploadImageVO vo = new UploadImageVO();
            String fileName = minioUtil.upload(minioConfig.getBucketName(), minioConfig.getImagePath(), file);
            if (StringUtils.isEmpty(fileName)) {
                throw new GlobalException(ResultCode.PROGRAM_ERROR, "图片上传失败!");
            }
            vo.setOriginUrl(generUrl(FileType.IMAGE, fileName));
            // 大于30K的文件需上传缩略图
            if (file.getSize() > 30 * 1024) {
                byte[] imageByte = ImageUtil.compressForScale(file.getBytes(), 30);
                fileName = minioUtil.upload(minioConfig.getBucketName(), minioConfig.getImagePath(), Objects.requireNonNull(file.getOriginalFilename()), imageByte, file.getContentType());
                if (StringUtils.isEmpty(fileName)) {
                    throw new GlobalException(ResultCode.PROGRAM_ERROR, "图片上传失败");
                }
            }
            vo.setThumbUrl(generUrl(FileType.IMAGE, fileName));
            log.info("文件图片成功，用户id:{},url:{}", userId, vo.getOriginUrl());
            return vo;
        } catch (IOException e) {
            log.error("上传图片失败，{}", e.getMessage(), e);
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "图片上传失败");
        }
    }


    public String generUrl(FileType fileTypeEnum, String fileName) {
        String url = minioConfig.getUrl() + "/" + minioConfig.getBucketName();
        switch (fileTypeEnum) {
            case FILE:
                url += "/" + minioConfig.getFilePath() + "/";
                break;
            case IMAGE:
                url += "/" + minioConfig.getImagePath() + "/";
                break;
            case VIDEO:
                url += "/" + minioConfig.getVideoPath() + "/";
                break;
            default:
                break;
        }
        url += fileName;
        return url;
    }

}
