package com.omninote_ai.server.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DocumentUploadRequest {
    @NotEmpty(message = "At least one file must be uploaded")
    List<MultipartFile> files;
}
