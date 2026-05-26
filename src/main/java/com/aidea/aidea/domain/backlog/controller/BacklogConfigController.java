package com.aidea.aidea.domain.backlog.controller;

import com.aidea.aidea.domain.backlog.dto.request.BacklogConfigRequest;
import com.aidea.aidea.domain.backlog.dto.response.BacklogConfigResponse;
import com.aidea.aidea.domain.backlog.service.BacklogConfigService;
import com.aidea.aidea.global.dto.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "BacklogConfig", description = "백로그 설정 API")
@RestController
@RequestMapping("/api/teamspaces/{teamspaceId}/backlog/config")
@RequiredArgsConstructor
public class BacklogConfigController {

    private final BacklogConfigService backlogConfigService;

    @Operation(summary = "백로그 설정 조회")
    @GetMapping
    public ResponseEntity<GlobalResponse<BacklogConfigResponse>> getConfig(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        return ResponseEntity.ok(GlobalResponse.ok(backlogConfigService.getConfig(teamspaceId, userId)));
    }

    @Operation(summary = "백로그 설정 저장 (없으면 생성, 있으면 덮어쓰기)")
    @PutMapping
    public ResponseEntity<GlobalResponse<BacklogConfigResponse>> upsertConfig(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userIdStr,
            @RequestBody BacklogConfigRequest request) {

        Long userId = Long.parseLong(userIdStr);
        return ResponseEntity.ok(GlobalResponse.ok(backlogConfigService.upsertConfig(teamspaceId, userId, request)));
    }
}
