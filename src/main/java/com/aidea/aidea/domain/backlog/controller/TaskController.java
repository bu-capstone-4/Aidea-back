package com.aidea.aidea.domain.backlog.controller;

import com.aidea.aidea.domain.backlog.dto.request.CreateTaskRequest;
import com.aidea.aidea.domain.backlog.dto.request.ReorderRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateTaskRequest;
import com.aidea.aidea.domain.backlog.dto.response.ReorderResponse;
import com.aidea.aidea.domain.backlog.dto.response.TaskResponse;
import com.aidea.aidea.domain.backlog.service.TaskService;
import com.aidea.aidea.global.dto.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Task", description = "백로그 태스크 API")
@RestController
@RequestMapping("/api/teamspaces/{teamspaceId}/stories/{storyId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "태스크 생성")
    @PostMapping
    public ResponseEntity<GlobalResponse<TaskResponse>> createTask(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody CreateTaskRequest request) {

        Long userId = Long.parseLong(userIdStr);
        TaskResponse task = taskService.createTask(teamspaceId, userId, storyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GlobalResponse.ok(task));
    }

    @Operation(summary = "태스크 수정")
    @PutMapping("/{taskId}")
    public ResponseEntity<GlobalResponse<TaskResponse>> updateTask(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody UpdateTaskRequest request) {

        Long userId = Long.parseLong(userIdStr);
        TaskResponse task = taskService.updateTask(teamspaceId, userId, storyId, taskId, request);
        return ResponseEntity.ok(GlobalResponse.ok(task));
    }

    @Operation(summary = "태스크 완료 토글")
    @PatchMapping("/{taskId}/complete")
    public ResponseEntity<GlobalResponse<Map<String, Object>>> toggleComplete(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        Map<String, Object> result = taskService.toggleComplete(teamspaceId, userId, storyId, taskId);
        return ResponseEntity.ok(GlobalResponse.ok(result));
    }

    @Operation(summary = "태스크 순서 변경")
    @PatchMapping("/reorder")
    public ResponseEntity<GlobalResponse<ReorderResponse>> reorder(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody ReorderRequest request) {

        Long userId = Long.parseLong(userIdStr);
        ReorderResponse response = taskService.reorder(teamspaceId, userId, storyId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "태스크 삭제")
    @DeleteMapping("/{taskId}")
    public ResponseEntity<GlobalResponse<Void>> deleteTask(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        taskService.deleteTask(teamspaceId, userId, storyId, taskId);
        return ResponseEntity.ok(GlobalResponse.ok());
    }
}
