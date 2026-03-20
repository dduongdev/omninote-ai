package com.omninote_ai.server.services;

import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.omninote_ai.server.exception.UploadFileException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {
    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @PostConstruct
    public void initBucket() {
        try {
            // 1. Check if the bucket already exists
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());

            if (!found) {
                // 2. Create the bucket if it doesn't exist
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO: Bucket '{}' created successfully.", bucketName);
            } else {
                log.info("MinIO: Bucket '{}' already exists.", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO: Error occurred while initializing bucket '{}'", bucketName, e);
            throw new RuntimeException("Could not initialize MinIO bucket: " + e.getMessage());
        }
    }

    public String uploadFile(MultipartFile file) {
        try {
            String fileName = Paths.get(file.getOriginalFilename()).getFileName().toString();
            String objectName = UUID.randomUUID() + "_" + fileName;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            return objectName;
        } catch (Exception e) {
            throw new UploadFileException("Upload file to MinIO failed", e);
        }
    }

    public String getFileContent(String objectName) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Get file content from MinIO failed: " + objectName, e);
        }
    }

    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Delete file from MinIO failed", e);
        }
    }

}
