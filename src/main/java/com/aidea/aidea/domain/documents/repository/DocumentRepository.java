package com.aidea.aidea.domain.documents.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidea.aidea.domain.documents.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String>{

     List<Document> findByTeamspaceId(String teamspaceId);

     Optional<Document> findById(String id);
}
