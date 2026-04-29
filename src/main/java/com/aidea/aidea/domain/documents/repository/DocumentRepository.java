package com.aidea.aidea.domain.documents.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidea.aidea.domain.documents.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String>{

}
