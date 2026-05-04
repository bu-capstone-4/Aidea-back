package com.aidea.aidea.domain.documents.repository;

import com.aidea.aidea.domain.documents.entity.DocumentUpdate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentUpdateRepository extends JpaRepository<DocumentUpdate, Long> {

    // 신규 접속자에게 미머지 업데이트 전송용 (id ASC = 수신 순서 보장)
    List<DocumentUpdate> findByDocumentIdOrderByIdAsc(String documentId);

    // 배치 머지 시 가장 오래된 N개 id 조회
    // Pageable로 LIMIT을 제어 — 호출 시 PageRequest.of(0, n) 전달
    @Query("SELECT u.id FROM DocumentUpdate u WHERE u.document.id = :docId ORDER BY u.id ASC")
    List<Long> findTopNIdsByDocumentIdOrderByIdAsc(
            @Param("docId") String docId,
            Pageable pageable
    );

    // 재시작 폴백: 미머지 rows가 존재하는 docId 목록
    @Query("SELECT DISTINCT u.document.id FROM DocumentUpdate u")
    List<String> findDocIdsWithPendingUpdates();
}
