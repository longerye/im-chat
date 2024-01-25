package com.chat.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
public class MinIoClientConfig {

    @Resource
    private MinioConfig minioConfig;



    @Bean
    public MinioClient minioClient() {
        // 注入minio 客户端
        return MinioClient.builder()
                .endpoint(minioConfig.getExternalUrl())
                .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                .build();
    }
}