package com.aidea.aidea.domain.backlog.controller;

import com.aidea.aidea.domain.backlog.dto.request.CreateStoryRequest;
import com.aidea.aidea.domain.backlog.dto.request.ReorderRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateStoryRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateStoryStatusRequest;
import com.aidea.aidea.domain.backlog.dto.response.ReorderResponse;
import com.aidea.aidea.domain.backlog.dto.response.StoryDetailResponse;
import com.aidea.aidea.domain.backlog.dto.response.StorySummaryResponse;
import com.aidea.aidea.domain.backlog.dto.response.StoryStatusResponse;
import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.StoryStatus;
import com.aidea.aidea.domain.backlog.service.StoryService;
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

@Tag(name = "Story", description = "백로그 스토리 API")
@RestController
@RequestMapping("/api/teamspaces/{teamspaceId}/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @Operation(summary = "스토리 목록 조회 (필터링)")
    @GetMapping
    public ResponseEntity<GlobalResponse<List<StorySummaryResponse>>> getStories(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr,
            @RequestParam(required = false) List<StoryStatus> status,
            @RequestParam(required = false) Long epicId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Priority priority) {

        Long userId = Long.parseLong(userIdStr);
        List<StorySummaryResponse> stories =
                storyService.getStories(teamspaceId, userId, status, epicId, assigneeId, priority);
        return ResponseEntity.ok(GlobalResponse.ok(stories));
    }

    @Operation(summary = "스토리 상세 조회")
    @GetMapping("/{storyId}")
    public ResponseEntity<GlobalResponse<StoryDetailResponse>> getStory(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        StoryDetailResponse story = storyService.getStory(teamspaceId, userId, storyId);
        return ResponseEntity.ok(GlobalResponse.ok(story));
    }

    @Operation(summary = "스토리 생성")
    @PostMapping
    public ResponseEntity<GlobalResponse<StoryDetailResponse>> createStory(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody CreateStoryRequest request) {

        Long userId = Long.parseLong(userIdStr);
        StoryDetailResponse story = storyService.createStory(teamspaceId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GlobalResponse.ok(story));
    }

    @Operation(summary = "스토리 수정")
    @PutMapping("/{storyId}")
    public ResponseEntity<GlobalResponse<StoryDetailResponse>> updateStory(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody UpdateStoryRequest request) {

        Long userId = Long.parseLong(userIdStr);
        StoryDetailResponse story = storyService.updateStory(teamspaceId, userId, storyId, request);
        return ResponseEntity.ok(GlobalResponse.ok(story));
    }

    @Operation(summary = "스토리 상태 변경")
    @PatchMapping("/{storyId}/status")
    public ResponseEntity<GlobalResponse<StoryStatusResponse>> changeStatus(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody UpdateStoryStatusRequest request) {

        Long userId = Long.parseLong(userIdStr);
        StoryStatusResponse response = storyService.changeStatus(teamspaceId, userId, storyId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "스토리 순서 변경")
    @PatchMapping("/reorder")
    public ResponseEntity<GlobalResponse<ReorderResponse>> reorder(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody ReorderRequest request) {

        Long userId = Long.parseLong(userIdStr);
        ReorderResponse response = storyService.reorder(teamspaceId, userId, request);
        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "스토리 삭제")
    @DeleteMapping("/{storyId}")
    public ResponseEntity<GlobalResponse<Void>> deleteStory(
            @PathVariable String teamspaceId,
            @PathVariable Long storyId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        storyService.deleteStory(teamspaceId, userId, storyId);
        return ResponseEntity.ok(GlobalResponse.ok());
    }
}
