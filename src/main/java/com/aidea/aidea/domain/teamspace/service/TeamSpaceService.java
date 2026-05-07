package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.entity.TeamSpaceStatus;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamSpaceService {

    private final TeamSpaceRepository teamSpaceRepository;
    private final DocumentRepository documentRepository;

    public TeamSpaceCreateResponse create(TeamSpaceCreateRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException(ErrorCode.INVALID_INPUT.getMessage());
        }

        TeamSpace teamSpace = TeamSpace.builder()
                .teamspaceId("ts_" + UUID.randomUUID())
                .name(request.getName())
                .status(TeamSpaceStatus.CREATING)
                .build();

        TeamSpace saved = teamSpaceRepository.save(teamSpace);

        if (request.getDocumentTypes() != null) {
            List<Document> documents = request.getDocumentTypes().stream()
                    .map(type -> Document.create(UUID.randomUUID().toString(), saved, type, type.name()))
                    .collect(Collectors.toList());
            documentRepository.saveAll(documents);
        }

        return TeamSpaceCreateResponse.builder()
                .teamspaceId(saved.getTeamspaceId())
                .name(saved.getName())
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public TeamSpaceDetailResponse get(String id) {
        TeamSpace ts = teamSpaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(ErrorCode.TEAMSPACE_NOT_FOUND.getMessage()));

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

    public TeamSpaceListResponse getList() {
        List<TeamSpaceListResponse.TeamSpaceSummary> list =
                teamSpaceRepository.findAll().stream()
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

    public TeamSpaceCreateResponse update(String id, TeamSpaceUpdateRequest request) {
        TeamSpace ts = teamSpaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(ErrorCode.TEAMSPACE_NOT_FOUND.getMessage()));

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

    public void delete(String id) {
        if (!teamSpaceRepository.existsById(id)) {
            throw new RuntimeException(ErrorCode.TEAMSPACE_NOT_FOUND.getMessage());
        }
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