package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.draft.entity.DraftQuestion;
import com.aidea.aidea.domain.draft.entity.DraftStatus;

import java.util.List;

public record ActiveDraftInfo(
        String draftId,
        DraftStatus status,
        String content,
        List<DraftQuestion> questions
) {}