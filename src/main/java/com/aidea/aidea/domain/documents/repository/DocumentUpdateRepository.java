package com.aidea.aidea.domain.documents.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidea.aidea.domain.documents.entity.DocumentUpdate;

public interface DocumentUpdateRepository extends JpaRepository<DocumentUpdate, Long> {
    List<DocumentUpdate> findByDocIdOrderById(String docId);
}