package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.draft.service.DraftService;
import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.entity.TeamSpaceStatus;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamSpaceService {

    private final TeamSpaceRepository teamSpaceRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DraftService draftService;

    @Transactional
    public TeamSpaceCreateResponse create(TeamSpaceCreateRequest request, Long userId) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException(ErrorCode.INVALID_INPUT.getMessage());
        }

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(ErrorCode.USER_NOT_FOUND.getMessage()));

        TeamSpace teamSpace = TeamSpace.builder()
                .teamspaceId("ts_" + UUID.randomUUID())
                .owner(owner)
                .name(request.getName())
                .status(TeamSpaceStatus.CREATING)
                .build();

        TeamSpace saved = teamSpaceRepository.save(teamSpace);

        teamspaceMemberRepository.save(TeamspaceMember.builder()
                .teamspaceId(saved.getTeamspaceId())
                .userId(owner.getId())
                .role(MemberRole.OWNER)
                .build());

        if (request.getDocumentTypes() != null) {
            List<Document> documents = request.getDocumentTypes().stream()
                    .map(type -> Document.create(UUID.randomUUID().toString(), saved, type, type.name()))
                    .collect(Collectors.toList());
            documentRepository.saveAll(documents);
            documents.forEach(doc -> draftService.triggerDraftGeneration(doc.getId()));
        }

        return TeamSpaceCreateResponse.builder()
                .teamspaceId(saved.getTeamspaceId())
                .name(saved.getName())
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public TeamSpaceDetailResponse get(String id, Long userId) {
        teamspaceMemberRepository.findByTeamspaceIdAndUserId(id, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));

        TeamSpace ts = teamSpaceRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAMSPACE_NOT_FOUND));

        List<TeamSpaceDetailResponse.DocumentSummary> docs =
                ts.getDocuments().stream()
                        .map(this::toDocumentSummary)
                        .collect(Collectors.toList());

        return TeamSpaceDetailResponse.builder()
                .teamspaceId(ts.getTeamspaceId())
                .name(ts.getName())
                .status(ts.getStatus().name())
                .createdAt(ts.getCreatedAt())
                .documents(docs)
                .build();
    }

    @Transactional(readOnly = true)
    public TeamSpaceListResponse getList(Long userId) {
        List<String> teamspaceIds = teamspaceMemberRepository.findByUserId(userId).stream()
                .map(TeamspaceMember::getTeamspaceId)
                .collect(Collectors.toList());

        List<TeamSpaceListResponse.TeamSpaceSummary> list =
                teamSpaceRepository.findAllById(teamspaceIds).stream()
                        .map(ts -> TeamSpaceListResponse.TeamSpaceSummary.builder()
                                .teamspaceId(ts.getTeamspaceId())
                                .name(ts.getName())
                                .status(ts.getStatus().name())
                                .createdAt(ts.getCreatedAt())
                                .build())
                        .collect(Collectors.toList());

        return TeamSpaceListResponse.builder()
                .teamspaces(list)
                .build();
    }

    @Transactional
    public TeamSpaceCreateResponse update(String id, TeamSpaceUpdateRequest request, Long userId) {
        TeamSpace ts = teamSpaceRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAMSPACE_NOT_FOUND));

        if (!ts.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_TEAMSPACE_OWNER);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            ts.setName(request.getName());
        }

        if (request.getStatus() != null) {
            try {
                ts.setStatus(TeamSpaceStatus.valueOf(request.getStatus()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(ErrorCode.INVALID_INPUT.getMessage());
            }
        }

        TeamSpace saved = teamSpaceRepository.save(ts);

        return TeamSpaceCreateResponse.builder()
                .teamspaceId(saved.getTeamspaceId())
                .name(saved.getName())
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public void delete(String id, Long userId) {
        TeamSpace ts = teamSpaceRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAMSPACE_NOT_FOUND));

        if (!ts.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_TEAMSPACE_OWNER);
        }

        teamspaceMemberRepository.deleteAllByTeamspaceId(id);
        teamSpaceRepository.deleteById(id);
    }

    private TeamSpaceDetailResponse.DocumentSummary toDocumentSummary(Document d) {
        return TeamSpaceDetailResponse.DocumentSummary.builder()
                .id(d.getId())
                .type(d.getType() != null ? d.getType().name() : null)
                .title(d.getTitle())
                .updatedAt(d.getUpdatedAt())
                .updatedBy(d.getUpdatedBy() != null ? d.getUpdatedBy().getId().toString() : null)
                .build();
    }
}