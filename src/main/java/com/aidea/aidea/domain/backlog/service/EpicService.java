package com.aidea.aidea.domain.backlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.backlog.dto.request.CreateEpicRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateEpicRequest;
import com.aidea.aidea.domain.backlog.dto.response.EpicResponse;
import com.aidea.aidea.domain.backlog.entity.Epic;
import com.aidea.aidea.domain.backlog.repository.EpicRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class EpicService {

    private final EpicRepository epicRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final UserRepository userRepository;
    private final BacklogEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<EpicResponse> getEpics(String teamspaceId, Long userId) {
        getMemberOrThrow(teamspaceId, userId);
        return epicRepository.findAllWithCreatorByTeamspaceId(teamspaceId)
                .stream().map(EpicResponse::from).toList();
    }

    public EpicResponse createEpic(String teamspaceId, Long userId, CreateEpicRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Epic epic = Epic.create(teamspaceId, request.name(), request.color(), request.description(), creator);
        Epic saved = epicRepository.save(epic);

        EpicResponse response = EpicResponse.from(saved);
        broadcast(teamspaceId, userId, "epic:created", Map.of("epic", response));
        return response;
    }

    public EpicResponse updateEpic(String teamspaceId, Long userId, Long epicId, UpdateEpicRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Epic epic = epicRepository.findById(epicId)
                .orElseThrow(() -> new CustomException(ErrorCode.EPIC_NOT_FOUND));
        validateBelongsToTeamspace(epic, teamspaceId);

        epic.update(request.name(), request.color(), request.description());

        EpicResponse response = EpicResponse.from(epic);
        broadcast(teamspaceId, userId, "epic:updated", Map.of("epic", response));
        return response;
    }

    public void deleteEpic(String teamspaceId, Long userId, Long epicId) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Epic epic = epicRepository.findById(epicId)
                .orElseThrow(() -> new CustomException(ErrorCode.EPIC_NOT_FOUND));
        validateBelongsToTeamspace(epic, teamspaceId);

        epicRepository.delete(epic);
        broadcast(teamspaceId, userId, "epic:deleted", Map.of("epicId", epicId));
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

    private void validateBelongsToTeamspace(Epic epic, String teamspaceId) {
        if (!epic.getTeamspaceId().equals(teamspaceId)) {
            throw new CustomException(ErrorCode.EPIC_NOT_FOUND);
        }
    }

    private void broadcast(String teamspaceId, Long actorId, String type, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("actorId", String.valueOf(actorId));
        payload.putAll(extra);
        try {
            eventPublisher.publishToTeamspace(teamspaceId, String.valueOf(actorId),
                    objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
