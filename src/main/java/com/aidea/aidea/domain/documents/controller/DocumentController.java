package com.aidea.aidea.domain.documents.controller;


import java.util.List;


import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aidea.aidea.domain.documents.dto.DocumentCreateRequest;
import com.aidea.aidea.domain.documents.dto.DocumentCreateResponse;
import com.aidea.aidea.domain.documents.dto.DocumentDetail;
import com.aidea.aidea.domain.documents.dto.DocumentSummary;
import com.aidea.aidea.domain.documents.dto.DocumentUpdateRequest;
import com.aidea.aidea.domain.documents.dto.DocumentUpdateResponse;
import com.aidea.aidea.domain.documents.service.DocumentService;
import com.aidea.aidea.global.dto.TestGlobalResponseDTO;




@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/test")
        public String runTest() {
            return "/test/document/live-test";
        }

    // 유저의 팀 스페이스 내 문서 목록 조회
    @GetMapping
    public TestGlobalResponseDTO<List<DocumentSummary>> getDocuments(
            @RequestParam("teamspaceId") String teamspaceId) {

        List<DocumentSummary> result = documentService.getDocuments(teamspaceId);


        return TestGlobalResponseDTO.ok(result);
    }
    
    // 워크스페이스id, type, title 작성 후 문서 생성
    @PostMapping
    public TestGlobalResponseDTO<DocumentCreateResponse> createDocument(
            @RequestBody DocumentCreateRequest request) {

        DocumentCreateResponse response = documentService.createDocument(request);

        return TestGlobalResponseDTO.ok(response);
    }

    // 문서 id로(UUID, String) 상세 조회
    @GetMapping("/{documentId}")
    public TestGlobalResponseDTO<DocumentDetail> getDocumentDetail(
            @PathVariable("documentId") String documentId
    ) {
        DocumentDetail result = documentService.getDocumentDetail(documentId);
        return TestGlobalResponseDTO.ok(result);
    }

    // 문서 제목 수정
    @PatchMapping("/{documentId}")
    public TestGlobalResponseDTO<DocumentUpdateResponse> updateDocumentTitle(
            @PathVariable("documentId") String documentId,
            @RequestBody DocumentUpdateRequest request
    ) {

        DocumentUpdateResponse response =
                documentService.updateTitle(documentId, request);

        return TestGlobalResponseDTO.ok("문서 제목이 수정되었습니다.", response);
    }

    // 문서 삭제
    @DeleteMapping("/{documentId}")
    public TestGlobalResponseDTO<Void> deleteDocument(
            @PathVariable("documentId") String documentId
    ) {

        documentService.deleteDocument(documentId);

        return TestGlobalResponseDTO.ok("문서가 삭제되었습니다.");
    }

    // 파일 다운로드
    @GetMapping("/{documentId}/export")
    public ResponseEntity<byte[]> exportDocument(
            @PathVariable("documentId") String documentId,
            @PathVariable("format") String format
    ) {

        byte[] file = documentService.exportDocument(documentId, format);

        String fileName = "document." + format;

        return ResponseEntity.ok()
                .header("Content-Type", format.equals("pdf")
                        ? "application/pdf"
                        : "application/octet-stream")
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .body(file);
    }
}
