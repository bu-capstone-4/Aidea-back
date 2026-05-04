package com.aidea.aidea.domain.documents.repository;

import com.aidea.aidea.domain.documents.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, String> {

    // teamspace.id = :teamspaceId 로 Spring Data JPA가 자동 변환
    List<Document> findByTeamspaceId(String teamspaceId);
}
