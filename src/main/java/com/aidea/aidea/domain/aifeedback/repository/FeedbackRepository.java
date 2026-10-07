package com.aidea.aidea.domain.aifeedback.repository;

import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    boolean existsByDocumentIdAndStatusIn(String documentId, Collection<FeedbackStatus> statuses);

    Optional<Feedback> findTopByDocumentIdAndStatusNotInOrderByCreatedAtDesc(
            String documentId, Collection<FeedbackStatus> statuses);

    void deleteByDocumentId(String documentId);

    // 상태 전이(check-then-act)용 행 잠금 조회 — 동시 답변 제출이 둘 다 QUESTIONING을 읽고 통과하는 것을 막는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Feedback f WHERE f.id = :id")
    Optional<Feedback> findByIdForUpdate(@Param("id") String id);
}
