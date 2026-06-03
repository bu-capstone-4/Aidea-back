package com.aidea.aidea.domain.aifeedback.repository;

import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    boolean existsByDocumentIdAndStatusIn(String documentId, Collection<FeedbackStatus> statuses);

    Optional<Feedback> findTopByDocumentIdAndStatusNotInOrderByCreatedAtDesc(
            String documentId, Collection<FeedbackStatus> statuses);

    void deleteByDocumentId(String documentId);
}
