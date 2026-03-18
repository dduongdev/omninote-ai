package com.omninote_ai.server.mapper;

import java.util.List;
import java.util.stream.Collectors;

import com.omninote_ai.server.dto.CitationResponse;
import com.omninote_ai.server.dto.MessageResponse;
import com.omninote_ai.server.entity.Message;

public class MessageMapper {
    public static MessageResponse toResponse(Message message) {
        if (message == null) return null;

        // Chuyển đổi List<MessageCitation> thành List<CitationResponse>
        List<CitationResponse> citationDTOs = message.getCitations().stream()
                .map(citation -> CitationResponse.builder()
                        .id(citation.getId())
                        .documentId(citation.getDocument().getId())
                        .fileName(citation.getDocument().getFileName())
                        .startIndex(citation.getStartIndex())
                        .endIndex(citation.getEndIndex())
                        .build())
                .collect(Collectors.toList());

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .contentQuery(message.getContentQuery())
                .contentAnswer(message.getContentAnswer())
                // Giả sử bạn đã có DocumentMapper tương tự
                .selectedDocuments(message.getSelectedDocuments().stream()
                        .map(DocumentMapper::toSummary)
                        .collect(Collectors.toList()))
                // Bây giờ truyền list đã convert vào thì sẽ không còn lỗi "not applicable"
                .citationsResponses(citationDTOs) 
                .createdAt(message.getCreatedAt())
                .build();
    }
}