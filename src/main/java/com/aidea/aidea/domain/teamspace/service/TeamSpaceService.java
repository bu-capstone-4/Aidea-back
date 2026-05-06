package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.document.entity.Document;
import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.entity.TeamSpaceStatus;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamSpaceService {

    private final TeamSpaceRepository teamSpaceRepository;

    public TeamSpaceCreateResponse create(TeamSpaceCreateRequest request) {
        TeamSpace teamSpace = TeamSpace.builder()
                .teamspaceId(request.getTeamspaceId())
                .name(request.getName())
                .status(TeamSpaceStatus.CREATING)
                .build();

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
            ts = TeamSpace.builder()
                    .teamspaceId(ts.getTeamspaceId())
                    .name(request.getName())
                    .status(ts.getStatus())
                    .documents(ts.getDocuments())
                    .createdAt(ts.getCreatedAt())
                    .build();
        }

        if (request.getStatus() != null) {
            ts = TeamSpace.builder()
                    .teamspaceId(ts.getTeamspaceId())
                    .name(ts.getName())
                    .status(TeamSpaceStatus.valueOf(request.getStatus()))
                    .documents(ts.getDocuments())
                    .createdAt(ts.getCreatedAt())
                    .build();
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
                .id(d.getDocId())
                .type(null)
                .title(null)
                .updatedAt(d.getUpdatedAt())
                .updatedBy(null)
                .build();
    }
}