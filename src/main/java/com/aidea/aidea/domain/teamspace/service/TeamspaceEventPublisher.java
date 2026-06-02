package com.aidea.aidea.domain.teamspace.service;

public interface TeamspaceEventPublisher {
    void publishDraftReady(String teamspaceId, String documentId, String draftId, String content);
    void publishDraftError(String teamspaceId, String documentId, String errorCode);
}
