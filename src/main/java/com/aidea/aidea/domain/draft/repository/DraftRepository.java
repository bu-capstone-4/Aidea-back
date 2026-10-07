package com.aidea.aidea.domain.draft.repository;

import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // 상태 전이(check-then-act)용 행 잠금 조회 — 동시 답변 제출이 둘 다 QUESTIONING을 읽고 통과하는 것을 막는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Draft d WHERE d.id = :id")
    Optional<Draft> findByIdForUpdate(@Param("id") String id);
}
