package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.draft.service.DraftService;
import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamSpaceService {

    private final TeamSpaceRepository teamSpaceRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final DocumentRepository documentRepository;
    private final DocumentUpdateRepository documentUpdateRepository;
    private final FeedbackRepository feedbackRepository;
    private final DraftRepository draftRepository;
    private final UserRepository userRepository;
    private final DraftService draftService;

    @Transactional
    public TeamSpaceCreateResponse create(TeamSpaceCreateRequest request, Long userId) {
        log.warn("[TS] create request userId={} name={} ideaPresent={} docTypes={}",
                userId, request.getName(),
                request.getIdea() != null && !request.getIdea().isBlank(),
                request.getDocumentTypes());

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException(ErrorCode.INVALID_INPUT.getMessage());
        }

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(ErrorCode.USER_NOT_FOUND.getMessage()));

        TeamSpace teamSpace = TeamSpace.builder()
                .teamspaceId("ts_" + UUID.randomUUID())
                .owner(owner)
                .name(request.getName())
                .build();

        TeamSpace saved = teamSpaceRepository.save(teamSpace);
        log.warn("[TS] saved teamspaceId={} userId={}", saved.getTeamspaceId(), userId);

        teamspaceMemberRepository.save(TeamspaceMember.builder()
                .teamspaceId(saved.getTeamspaceId())
                .userId(owner.getId())
                .role(MemberRole.OWNER)
                .build());

        List<Document> savedDocuments = List.of();
        if (request.getDocumentTypes() != null && !request.getDocumentTypes().isEmpty()) {
            List<Document> documents = request.getDocumentTypes().stream()
                    .map(type -> Document.create(UUID.randomUUID().toString(), saved, type, type.name()))
                    .collect(Collectors.toList());
            savedDocuments = documentRepository.saveAll(documents);
            log.warn("[TS] documents saved teamspaceId={} count={}", saved.getTeamspaceId(), savedDocuments.size());

            String ideaContext = request.getIdea();
            String teamspaceName = saved.getName();

            Document ideaDoc = savedDocuments.stream()
                    .filter(doc -> doc.getType() == com.aidea.aidea.domain.documents.entity.DocumentType.IDEA)
                    .findFirst()
                    .orElse(null);

            savedDocuments.forEach(doc -> {
                if (ideaDoc != null && doc.getId().equals(ideaDoc.getId())) {
                    log.warn("[TS] triggering idea draft docId={} teamspaceId={}", doc.getId(), saved.getTeamspaceId());
                    draftService.triggerDraftGeneration(doc.getId(), ideaContext, teamspaceName);
                } else {
                    log.warn("[TS] saving pending draft docId={} teamspaceId={}", doc.getId(), saved.getTeamspaceId());
                    draftService.savePendingDraft(doc.getId(), ideaContext);
                }
            });
        }

        List<TeamSpaceCreateResponse.DocumentInfo> docInfos = savedDocuments.stream()
                .map(doc -> TeamSpaceCreateResponse.DocumentInfo.builder()
                        .id(doc.getId())
                        .type(doc.getType().name())
                        .title(doc.getTitle())
                        .aiStatus(doc.getStatus() != null ? doc.getStatus().name() : "DRAFT")
                        .build())
                .collect(Collectors.toList());

        TeamSpaceCreateResponse response = TeamSpaceCreateResponse.builder()
                .teamspaceId(saved.getTeamspaceId())
                .name(saved.getName())
                .createdAt(saved.getCreatedAt())
                .documents(docInfos)
                .build();
        log.warn("[TS] create complete teamspaceId={} userId={} docCount={}", saved.getTeamspaceId(), userId, docInfos.size());
        return response;
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

        TeamSpace saved = teamSpaceRepository.save(ts);

        return TeamSpaceCreateResponse.builder()
                .teamspaceId(saved.getTeamspaceId())
                .name(saved.getName())
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

        List<Document> documents = documentRepository.findByTeamspaceId(id);
        for (Document doc : documents) {
            documentUpdateRepository.deleteByDocumentId(doc.getId());
            feedbackRepository.deleteByDocumentId(doc.getId());
            draftRepository.deleteByDocumentId(doc.getId());
        }
        documentRepository.deleteAll(documents);

        teamspaceMemberRepository.deleteAllByTeamspaceId(id);
        teamSpaceRepository.deleteById(id);
    }

    private TeamSpaceDetailResponse.DocumentSummary toDocumentSummary(Document d) {
        return TeamSpaceDetailResponse.DocumentSummary.builder()
                .id(d.getId())
                .type(d.getType() != null ? d.getType().name() : null)
                .title(d.getTitle())
                .aiStatus(d.getStatus() != null ? d.getStatus().name() : "IDLE")
                .updatedAt(d.getUpdatedAt())
                .updatedBy(d.getUpdatedBy() != null ? d.getUpdatedBy().getId().toString() : null)
                .build();
    }
}
