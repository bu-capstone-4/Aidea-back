package com.aidea.aidea.domain.backlog.controller;

import com.aidea.aidea.domain.backlog.dto.request.CreateBacklogTaskRequest;
import com.aidea.aidea.domain.backlog.dto.request.LinkStoryRequest;
import com.aidea.aidea.domain.backlog.dto.request.ReorderRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateBacklogTaskRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateBacklogTaskStatusRequest;
import com.aidea.aidea.domain.backlog.dto.response.BacklogTaskResponse;
import com.aidea.aidea.domain.backlog.dto.response.ReorderResponse;
import com.aidea.aidea.domain.backlog.service.BacklogTaskService;
import com.aidea.aidea.global.dto.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "BacklogTask", description = "최상위 태스크 API")
@RestController
@RequestMapping("/api/teamspaces/{teamspaceId}/tasks")
@RequiredArgsConstructor
public class BacklogTaskController {

    private final BacklogTaskService backlogTaskService;

    @Operation(summary = "최상위 태스크 생성")
    @PostMapping
    public ResponseEntity<GlobalResponse<BacklogTaskResponse>> createTask(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody CreateBacklogTaskRequest request) {

        Long userId = Long.parseLong(userIdStr);
        BacklogTaskResponse response = backlogTaskService.createTask(teamspaceId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GlobalResponse.ok(response));
    }

    @Operation(summary = "최상위 태스크 수정")
    @PutMapping("/{taskId}")
    public ResponseEntity<GlobalResponse<BacklogTaskResponse>> updateTask(
            @PathVariable String teamspaceId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody UpdateBacklogTaskRequest request) {

        Long userId = Long.parseLong(userIdStr);
        BacklogTaskResponse response = backlogTaskService.updateTask(teamspaceId, userId, taskId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "최상위 태스크 상태 변경")
    @PatchMapping("/{taskId}/status")
    public ResponseEntity<GlobalResponse<BacklogTaskResponse>> changeStatus(
            @PathVariable String teamspaceId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody UpdateBacklogTaskStatusRequest request) {

        Long userId = Long.parseLong(userIdStr);
        BacklogTaskResponse response = backlogTaskService.changeStatus(teamspaceId, userId, taskId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "최상위 태스크 순서 변경")
    @PatchMapping("/reorder")
    public ResponseEntity<GlobalResponse<ReorderResponse>> reorder(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody ReorderRequest request) {

        Long userId = Long.parseLong(userIdStr);
        ReorderResponse response = backlogTaskService.reorder(teamspaceId, userId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "최상위 태스크 상위 스토리 연결/해제")
    @PatchMapping("/{taskId}/story")
    public ResponseEntity<GlobalResponse<BacklogTaskResponse>> linkStory(
            @PathVariable String teamspaceId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userIdStr,
            @RequestBody LinkStoryRequest request) {

        Long userId = Long.parseLong(userIdStr);
        BacklogTaskResponse response = backlogTaskService.linkStory(teamspaceId, userId, taskId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "최상위 태스크 삭제")
    @DeleteMapping("/{taskId}")
    public ResponseEntity<GlobalResponse<Void>> deleteTask(
            @PathVariable String teamspaceId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        backlogTaskService.deleteTask(teamspaceId, userId, taskId);
        return ResponseEntity.ok(GlobalResponse.ok());
    }
}
