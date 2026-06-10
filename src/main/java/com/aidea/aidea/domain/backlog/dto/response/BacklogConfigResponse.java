package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.entity.BacklogDraftStatus;

public record BacklogConfigResponse(
        String teamspaceId,
        boolean feBeEnabled,
        boolean epicEnabled,
        boolean storyEnabled,
        boolean priorityEnabled,
        boolean sprintEnabled,
        boolean dueDateEnabled,
        BacklogDraftStatus draftStatus
) {
    public static BacklogConfigResponse from(BacklogConfig config) {
        return from(config, null);
    }

    public static BacklogConfigResponse from(BacklogConfig config, BacklogDraftStatus draftStatus) {
        return new BacklogConfigResponse(
                config.getTeamspaceId(),
                config.isFeBeEnabled(),
                config.isEpicEnabled(),
                config.isStoryEnabled(),
                config.isPriorityEnabled(),
                config.isSprintEnabled(),
                config.isDueDateEnabled(),
                draftStatus
        );
    }

    /** 설정이 없을 때 반환하는 기본값 — 모든 선택 필드 비활성화 */
    public static BacklogConfigResponse defaultFor(String teamspaceId) {
        return new BacklogConfigResponse(teamspaceId, false, false, false, false, false, false, null);
    }
}
