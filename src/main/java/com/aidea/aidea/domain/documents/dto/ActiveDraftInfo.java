package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.draft.entity.DraftStatus;

public record ActiveDraftInfo(
        String draftId,
        DraftStatus status,
        String content
) {}