package com.aidea.aidea.domain.documents.service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentUpdate;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentUpdateRepository documentUpdateRepository;

    public void applyUpdate(String docId, byte[] update) {
        // document 없으면 생성
        if (!documentRepository.existsById(docId)) {
            Document doc = new Document();
            doc.setDocId(docId);
            doc.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(doc);
        }

        // 업데이트 따로 저장
        DocumentUpdate documentUpdate = new DocumentUpdate();
        documentUpdate.setDocId(docId);
        documentUpdate.setUpdate(update);
        documentUpdateRepository.save(documentUpdate);
    }

    @Transactional(readOnly = true)
    public List<String> getSnapshotAsBase64List(String docId) {
        return documentUpdateRepository.findByDocIdOrderById(docId)
                .stream()
                .map(u -> Base64.getEncoder().encodeToString(u.getUpdate()))
                .collect(Collectors.toList());
    }
}