package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.draft.entity.DraftQuestion;

import java.util.List;

public interface TeamspaceEventPublisher {
    void publishDraftReady(String teamspaceId, String documentId, String draftId, String content);
    void publishDraftError(String teamspaceId, String documentId, String errorCode);
    void publishDraftQuestioning(String teamspaceId, String documentId, String draftId, List<DraftQuestion> questions);
}
