package com.omninote_ai.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConversationUpdateNameRequest {
    
    @NotBlank(message = "Title cannot be blank")
    private String title;
}
