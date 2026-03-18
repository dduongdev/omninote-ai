package com.omninote_ai.server.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilvusSoftDeletedSuccessEvent {

    @JsonProperty("doc_id")
    private Long docId;
}
