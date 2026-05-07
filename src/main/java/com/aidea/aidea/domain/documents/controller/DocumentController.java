package com.aidea.aidea.domain.documents.controller;

import com.aidea.aidea.domain.documents.dto.*;
import com.aidea.aidea.domain.documents.service.DocumentService;
import com.aidea.aidea.global.dto.GlobalResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    // 팀스페이스 문서 목록 — VIEWER 이상
    // GET /api/documents?teamspaceId=
    @GetMapping
    public GlobalResponse<List<DocumentSummary>> getDocuments(
            @RequestParam String teamspaceId,
            @AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(documentService.getDocuments(teamspaceId, userId));
    }

    // 문서 추가 생성 — MEMBER 이상
    // POST /api/documents  (body에 teamspaceId 포함)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GlobalResponse<DocumentCreateResponse> createDocument(
            @Valid @RequestBody DocumentCreateRequest req,
            @AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(documentService.createDocument(req.getTeamspaceId(), req, userId),
                "문서가 생성되었습니다.");
    }

    // 문서 상세 조회 — VIEWER 이상
    @GetMapping("/{documentId}")
    public GlobalResponse<DocumentDetail> getDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(documentService.getDocument(documentId, userId));
    }

    // 문서 제목 수정 — MEMBER 이상
    @PatchMapping("/{documentId}")
    public GlobalResponse<DocumentUpdateResponse> updateTitle(
            @PathVariable String documentId,
            @RequestBody DocumentUpdateRequest req,
            @AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(documentService.updateTitle(documentId, req, userId));
    }

    // 문서 삭제 — OWNER (IDEA 타입 삭제 불가)
    @DeleteMapping("/{documentId}")
    public GlobalResponse<Void> deleteDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal String userId) {
        documentService.deleteDocument(documentId, userId);
        return GlobalResponse.ok("문서가 삭제되었습니다.");
    }

    // 파일 내보내기 — VIEWER 이상
    @GetMapping("/{documentId}/export")
    public ResponseEntity<byte[]> exportDocument(
            @PathVariable String documentId,
            @RequestParam String format,
            @AuthenticationPrincipal String userId) {
        byte[] file = documentService.exportDocument(documentId, format, userId);
        String contentType = "pdf".equalsIgnoreCase(format) ? "application/pdf" : "application/octet-stream";
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"document." + format + "\"")
                .body(file);
    }
}
