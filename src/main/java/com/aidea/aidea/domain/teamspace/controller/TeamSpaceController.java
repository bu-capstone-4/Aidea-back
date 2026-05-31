package com.aidea.aidea.domain.teamspace.controller;

import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.service.TeamSpaceService;
import com.aidea.aidea.global.dto.GlobalResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teamspaces")
@RequiredArgsConstructor
public class TeamSpaceController {

    private final TeamSpaceService teamSpaceService;

    // 팀스페이스 생성
    @PostMapping
    public GlobalResponse<TeamSpaceCreateResponse> create(@RequestBody TeamSpaceCreateRequest request,
                                                          @AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(teamSpaceService.create(request, Long.parseLong(userId)));
    }

    // 팀스페이스 단건 조회
    @GetMapping("/{teamspaceId}")
    public GlobalResponse<TeamSpaceDetailResponse> get(@PathVariable String teamspaceId,
                                                       @AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(teamSpaceService.get(teamspaceId, Long.parseLong(userId)));
    }

    // 팀스페이스 목록 조회
    @GetMapping
    public GlobalResponse<TeamSpaceListResponse> getList(@AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(teamSpaceService.getList(Long.parseLong(userId)));
    }

    // 팀스페이스 수정(이름)
    @PutMapping("/{teamspaceId}")
    public GlobalResponse<TeamSpaceCreateResponse> update(@PathVariable String teamspaceId,
                                                          @RequestBody TeamSpaceUpdateRequest request,
                                                          @AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(teamSpaceService.update(teamspaceId, request, Long.parseLong(userId)));
    }

    // 팀스페이스 삭제
    @DeleteMapping("/{teamspaceId}")
    public GlobalResponse<Void> delete(@PathVariable String teamspaceId,
                                       @AuthenticationPrincipal String userId) {
        teamSpaceService.delete(teamspaceId, Long.parseLong(userId));
        return GlobalResponse.ok();
    }
}