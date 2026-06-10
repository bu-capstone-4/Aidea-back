package com.aidea.aidea.domain.backlog.repository;

import com.aidea.aidea.domain.backlog.entity.BacklogDraft;
import com.aidea.aidea.domain.backlog.entity.BacklogDraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BacklogDraftRepository extends JpaRepository<BacklogDraft, String> {
    Optional<BacklogDraft> findByTeamspaceId(String teamspaceId);
    boolean existsByTeamspaceIdAndStatus(String teamspaceId, BacklogDraftStatus status);
}
