package com.aidea.aidea.domain.draft.controller;

import com.aidea.aidea.domain.draft.controller.dto.DraftAnswerRequest;
import com.aidea.aidea.domain.draft.controller.dto.DraftAnswerResponse;
import com.aidea.aidea.domain.draft.service.DraftService;
import com.aidea.aidea.global.dto.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Draft")
@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    @Operation(summary = "IDEA 초안 구체화 질문에 대한 답변 제출 (비워서 보내면 건너뛰기)")
    @PostMapping("/{draftId}/answers")
    public ResponseEntity<GlobalResponse<DraftAnswerResponse>> submitAnswer(
            @PathVariable String draftId,
            @Valid @RequestBody DraftAnswerRequest request,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        DraftAnswerResponse response = draftService.submitDraftAnswer(draftId, request, userId);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(GlobalResponse.ok("답변을 처리하고 있습니다.", response));
    }
}
