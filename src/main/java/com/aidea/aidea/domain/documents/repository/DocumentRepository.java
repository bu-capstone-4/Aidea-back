package com.aidea.aidea.domain.documents.repository;

import com.aidea.aidea.domain.documents.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, String> {

    @Query("SELECT d FROM Document d WHERE d.teamspace.teamspaceId = :teamspaceId")
    List<Document> findByTeamspaceId(@Param("teamspaceId") String teamspaceId);
}
