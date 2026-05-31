package com.aidea.aidea.domain.backlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aidea.aidea.domain.backlog.dto.request.BacklogConfigRequest;
import com.aidea.aidea.domain.backlog.dto.response.BacklogConfigResponse;
import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.repository.BacklogConfigRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class BacklogConfigService {

    private final BacklogConfigRepository backlogConfigRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final BacklogEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BacklogConfigResponse getConfig(String teamspaceId, Long userId) {
        getMemberOrThrow(teamspaceId, userId);
        return backlogConfigRepository.findById(teamspaceId)
                .map(BacklogConfigResponse::from)
                .orElse(BacklogConfigResponse.defaultFor(teamspaceId));
    }

    /** 설정 저장 (없으면 생성, 있으면 덮어쓰기) — OWNER 또는 MEMBER만 가능 */
    public BacklogConfigResponse upsertConfig(String teamspaceId, Long userId, BacklogConfigRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        BacklogConfig config = backlogConfigRepository.findById(teamspaceId)
                .orElse(null);

        if (config == null) {
            config = BacklogConfig.create(teamspaceId,
                    request.feBeEnabled(), request.epicEnabled(), request.storyEnabled(),
                    request.priorityEnabled(), request.sprintEnabled(), request.dueDateEnabled());
        } else {
            config.update(request.feBeEnabled(), request.epicEnabled(), request.storyEnabled(),
                    request.priorityEnabled(), request.sprintEnabled(), request.dueDateEnabled());
        }

        BacklogConfigResponse response = BacklogConfigResponse.from(backlogConfigRepository.save(config));
        broadcastConfigUpdated(teamspaceId, userId, response);
        return response;
    }

    private TeamspaceMember getMemberOrThrow(String teamspaceId, Long userId) {
        return teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));
    }

    private void requireWritePermission(MemberRole role) {
        if (role == MemberRole.VIEWER) {
            throw new CustomException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
    }

    private void broadcastConfigUpdated(String teamspaceId, Long actorId, BacklogConfigResponse config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "backlog:config_updated");
        payload.put("actorId", String.valueOf(actorId));
        payload.put("config", config);
        try {
            eventPublisher.publishToTeamspace(teamspaceId, String.valueOf(actorId),
                    objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
