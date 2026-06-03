package com.aidea.aidea.domain.draft.repository;

import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, String> {
    Optional<Draft> findByDocumentId(String documentId);
    boolean existsByDocumentIdAndStatus(String documentId, DraftStatus status);
    void deleteByDocumentId(String documentId);

    @Query("SELECT d FROM Draft d JOIN d.document doc WHERE doc.teamspace.teamspaceId = :teamspaceId AND d.status = :status")
    List<Draft> findByTeamspaceIdAndStatus(@Param("teamspaceId") String teamspaceId, @Param("status") DraftStatus status);
}
