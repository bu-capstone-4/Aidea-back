package com.aidea.aidea.domain.draft.controller.dto;

import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;

public record DraftAnswerResponse(
        String draftId,
        DraftStatus status
) {
    public static DraftAnswerResponse from(Draft draft) {
        return new DraftAnswerResponse(draft.getId(), draft.getStatus());
    }
}
