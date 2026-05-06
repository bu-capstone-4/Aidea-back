package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.entity.TeamSpaceStatus;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamSpaceService {

    private final TeamSpaceRepository teamSpaceRepository;

    public TeamSpaceCreateResponse create(TeamSpaceCreateRequest request) {

        // 팀스페이스 생성
        TeamSpace teamSpace = TeamSpace.builder()
                .teamspaceId("ts_" + UUID.randomUUID())
                .name(request.getName())
                .status(TeamSpaceStatus.CREATING)
                .build();

        // Document 생성 (요청받은 타입 기준)
        if (request.getDocumentTypes() != null) {
            List<Document> documents = request.getDocumentTypes().stream()
                    .map(type -> Document.builder()
                            .id(UUID.randomUUID().toString())
                            .teamspaceId(teamSpace.getTeamspaceId())
                            .type(type)
                            .title(type.name())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build())
                    .collect(Collectors.toList());

            teamSpace.getDocuments().addAll(documents);
        }

        TeamSpace saved = teamSpaceRepository.save(teamSpace);

        return TeamSpaceCreateResponse.builder()
                .teamspaceId(saved.getTeamspaceId())
                .name(saved.getName())
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public TeamSpaceDetailResponse get(String id) {
        TeamSpace ts = teamSpaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TeamSpace not found"));

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
                .orElseThrow(() -> new RuntimeException("TeamSpace not found"));

        if (request.getName() != null) {
            ts.setName(request.getName());
        }

        if (request.getStatus() != null) {
            ts.setStatus(TeamSpaceStatus.valueOf(request.getStatus()));
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
        teamSpaceRepository.deleteById(id);
    }

    private TeamSpaceDetailResponse.DocumentSummary toDocumentSummary(Document d) {
        return TeamSpaceDetailResponse.DocumentSummary.builder()
                .id(d.getId())
                .type(d.getType() != null ? d.getType().name() : null)
                .title(d.getTitle())
                .updatedAt(d.getUpdatedAt())
                .updatedBy(d.getUpdatedBy())
                .build();
    }
}