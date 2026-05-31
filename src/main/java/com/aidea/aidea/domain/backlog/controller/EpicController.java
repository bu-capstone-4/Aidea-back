package com.aidea.aidea.domain.backlog.controller;

import com.aidea.aidea.domain.backlog.dto.request.CreateEpicRequest;
import com.aidea.aidea.domain.backlog.dto.request.ReorderRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateEpicRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateEpicStatusRequest;
import com.aidea.aidea.domain.backlog.dto.response.EpicResponse;
import com.aidea.aidea.domain.backlog.dto.response.ReorderResponse;
import com.aidea.aidea.domain.backlog.service.EpicService;
import com.aidea.aidea.global.dto.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Epic", description = "백로그 에픽 API")
@RestController
@RequestMapping("/api/teamspaces/{teamspaceId}/epics")
@RequiredArgsConstructor
public class EpicController {

    private final EpicService epicService;

    @Operation(summary = "에픽 목록 조회")
    @GetMapping
    public ResponseEntity<GlobalResponse<List<EpicResponse>>> getEpics(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        List<EpicResponse> epics = epicService.getEpics(teamspaceId, userId);
        return ResponseEntity.ok(GlobalResponse.ok(epics));
    }

    @Operation(summary = "에픽 생성")
    @PostMapping
    public ResponseEntity<GlobalResponse<EpicResponse>> createEpic(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody CreateEpicRequest request) {

        Long userId = Long.parseLong(userIdStr);
        EpicResponse response = epicService.createEpic(teamspaceId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GlobalResponse.ok(response));
    }

    @Operation(summary = "에픽 수정")
    @PutMapping("/{epicId}")
    public ResponseEntity<GlobalResponse<EpicResponse>> updateEpic(
            @PathVariable String teamspaceId,
            @PathVariable Long epicId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody UpdateEpicRequest request) {

        Long userId = Long.parseLong(userIdStr);
        EpicResponse response = epicService.updateEpic(teamspaceId, userId, epicId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "에픽 상태 변경")
    @PatchMapping("/{epicId}/status")
    public ResponseEntity<GlobalResponse<EpicResponse>> changeStatus(
            @PathVariable String teamspaceId,
            @PathVariable Long epicId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody UpdateEpicStatusRequest request) {

        Long userId = Long.parseLong(userIdStr);
        EpicResponse response = epicService.changeStatus(teamspaceId, userId, epicId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "에픽 순서 변경")
    @PatchMapping("/reorder")
    public ResponseEntity<GlobalResponse<ReorderResponse>> reorder(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody ReorderRequest request) {

        Long userId = Long.parseLong(userIdStr);
        ReorderResponse response = epicService.reorder(teamspaceId, userId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "에픽 삭제")
    @DeleteMapping("/{epicId}")
    public ResponseEntity<GlobalResponse<Void>> deleteEpic(
            @PathVariable String teamspaceId,
            @PathVariable Long epicId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        epicService.deleteEpic(teamspaceId, userId, epicId);
        return ResponseEntity.ok(GlobalResponse.ok());
    }
}
