package com.aidea.aidea.domain.documents.service;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.documents.dto.*;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import com.aidea.aidea.domain.documents.entity.DocumentUpdate;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.TeamspaceRoleValidator;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.element.Paragraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentUpdateRepository documentUpdateRepository;
    private final TeamSpaceRepository teamspaceRepository;
    private final UserRepository userRepository;
    private final TeamspaceRoleValidator roleValidator;

    // ───── REST API 메서드 ─────

    public List<DocumentSummary> getDocuments(String teamspaceId, String requestUserId) {
        roleValidator.requireMembership(teamspaceId, parseUserId(requestUserId));
        log.debug("[DOC] getDocuments userId={} teamspaceId={}", requestUserId, teamspaceId);
        return documentRepository.findByTeamspaceId(teamspaceId).stream()
                .map(DocumentSummary::from)
                .toList();
    }

    @Transactional
    public DocumentCreateResponse createDocument(String teamspaceId, DocumentCreateRequest req, String requestUserId) {
        Long userId = parseUserId(requestUserId);
        roleValidator.requireRole(teamspaceId, userId, MemberRole.MEMBER, MemberRole.OWNER);

        var teamspace = teamspaceRepository.findById(teamspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAMSPACE_NOT_FOUND));

        String title = (req.getTitle() == null || req.getTitle().isBlank())
                ? req.getType().name()
                : req.getTitle();

        Document doc = Document.create(UUID.randomUUID().toString(), teamspace, req.getType(), title);
        DocumentCreateResponse response = DocumentCreateResponse.from(documentRepository.save(doc));

        log.info("[DOC] create userId={} teamspaceId={} docId={} type={} title={}",
                userId, teamspaceId, response.getId(), req.getType(), title);
        return response;
    }

    public DocumentDetail getDocument(String docId, String requestUserId) {
        Document doc = findDocument(docId);
        roleValidator.requireMembership(doc.getTeamspace().getTeamspaceId(), parseUserId(requestUserId));
        log.debug("[DOC] getDocument userId={} docId={}", requestUserId, docId);
        return DocumentDetail.from(doc);
    }

    @Transactional
    public DocumentUpdateResponse updateTitle(String docId, DocumentUpdateRequest req, String requestUserId) {
        Long userId = parseUserId(requestUserId);
        Document doc = findDocument(docId);
        roleValidator.requireRole(doc.getTeamspace().getTeamspaceId(), userId, MemberRole.MEMBER, MemberRole.OWNER);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        doc.setTitle(req.getTitle());
        doc.setUpdatedAt(LocalDateTime.now());
        doc.setUpdatedBy(user);

        log.info("[DOC] updateTitle userId={} docId={} title={}", userId, docId, req.getTitle());
        return DocumentUpdateResponse.from(doc);
    }

    @Transactional
    public void deleteDocument(String docId, String requestUserId) {
        Long userId = parseUserId(requestUserId);
        Document doc = findDocument(docId);
        roleValidator.requireRole(doc.getTeamspace().getTeamspaceId(), userId, MemberRole.OWNER);

        if (doc.getType() == DocumentType.IDEA) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        documentRepository.delete(doc);
        log.info("[DOC] delete userId={} docId={}", userId, docId);
    }

    // ───── WebSocket 핸들러에서 위임받는 Yjs 메서드 ─────

    @Transactional
    public void saveUpdate(String docId, byte[] updateBinary, String clientId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
        documentUpdateRepository.save(DocumentUpdate.create(doc, updateBinary, clientId));
        log.info("[DOC] saveUpdate docId={} clientId={} bytes={}", docId, clientId, updateBinary.length);
    }

    public byte[] getSnapshot(String docId) {
        log.debug("[DOC] getSnapshot docId={}", docId);
        return documentRepository.findById(docId)
                .map(Document::getYjsSnapshot)
                .orElse(null);
    }

    public List<byte[]> getPendingUpdates(String docId) {
        log.debug("[DOC] getPendingUpdates docId={}", docId);
        return documentUpdateRepository.findByDocumentIdOrderByIdAsc(docId)
                .stream().map(DocumentUpdate::getUpdateBinary).toList();
    }

    // ───── 파일 내보내기 ─────

    public byte[] exportDocument(String docId, String format, String requestUserId) {
        Document doc = findDocument(docId);
        roleValidator.requireMembership(doc.getTeamspace().getTeamspaceId(), parseUserId(requestUserId));

        log.info("[DOC] export userId={} docId={} format={}", requestUserId, docId, format);
        if ("md".equalsIgnoreCase(format)) return convertToMarkdown(doc);
        if ("pdf".equalsIgnoreCase(format)) return convertToPdf(doc);
        throw new CustomException(ErrorCode.INVALID_INPUT);
    }

    private Document findDocument(String docId) {
        return documentRepository.findById(docId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private Long parseUserId(String userId) {
        return Long.parseLong(userId);
    }

    // ───── 파일 변환 ─────

    private byte[] convertToMarkdown(Document document) {
        String md = """
                # %s

                type: %s

                createdAt: %s
                """.formatted(document.getTitle(), document.getType(), document.getCreatedAt());
        return md.getBytes();
    }

    public byte[] convertToPdf(Document document) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdf);
            doc.add(new Paragraph("Document Title: " + document.getTitle()));
            doc.add(new Paragraph("Type: " + document.getType()));
            doc.add(new Paragraph("Created At: " + document.getCreatedAt()));
            doc.add(new Paragraph("Updated At: " + document.getUpdatedAt()));
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("[DOC] convertToPdf docId={} failed", document.getId(), e);
            throw new RuntimeException("PDF 생성 실패", e);
        }
    }
}
