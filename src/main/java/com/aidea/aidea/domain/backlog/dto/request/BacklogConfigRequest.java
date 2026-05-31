package com.aidea.aidea.domain.backlog.dto.request;

public record BacklogConfigRequest(
        boolean feBeEnabled,
        boolean epicEnabled,
        boolean storyEnabled,
        boolean priorityEnabled,
        boolean sprintEnabled,
        boolean dueDateEnabled
) {}
