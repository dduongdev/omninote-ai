package com.omninote_ai.server.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConversationCreateRequest {
    
    private String title;

    @Size(max = 5, message = "conversation.files.max")
    @NotEmpty(message = "conversation.files.required")
    private List<@NotNull(message = "conversation.file.notnull") MultipartFile> files;
}
