package com.aidea.aidea.domain.backlog.service;

public interface BacklogEventPublisher {
    void publishToTeamspace(String teamspaceId, String actorUserId, String jsonEvent);
}
