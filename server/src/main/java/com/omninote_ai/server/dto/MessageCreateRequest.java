package com.omninote_ai.server.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageCreateRequest {
    @NotNull(message = "conversation.id.required")
    private Long conversationId;

    @NotBlank(message = "message.content.required")
    private String contentQuery;

    private List<Long> documentIds;
}
