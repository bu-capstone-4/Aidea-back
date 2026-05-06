package com.aidea.aidea.domain.aifeedback.repository;

import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    //진행 중 상태로 간주하는 것 : PENDING, QUESTIONING, ANSWERING, DONE
    //종료 상태 : ACCEPTED, REJECTED
    //검사할 문서 ID, 진행 중으로 간주할 상태 목록, 하나라고 있으면 true 리턴
    boolean existsByDocumentIdAndStatusIn(String documentId, Collection<FeedbackStatus> statuses);
}
