package com.aidea.aidea.domain.draft.repository;

import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, String> {
    Optional<Draft> findByDocumentId(String documentId);
    boolean existsByDocumentIdAndStatus(String documentId, DraftStatus status);
}
