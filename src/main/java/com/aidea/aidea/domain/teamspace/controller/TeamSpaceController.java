package com.aidea.aidea.domain.teamspace.controller;

import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.service.TeamSpaceService;
import com.aidea.aidea.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teamspaces")
@RequiredArgsConstructor
public class TeamSpaceController {

    private final TeamSpaceService teamSpaceService;

    // 팀스페이스 생성
    @PostMapping
    public ApiResponse<TeamSpaceCreateResponse> create(@RequestBody TeamSpaceCreateRequest request) {
        return ApiResponse.ok(teamSpaceService.create(request));
    }

    // 팀스페이스 단건 조회
    @GetMapping("/{teamspaceId}")
    public ApiResponse<TeamSpaceDetailResponse> get(@PathVariable String teamspaceId) {
        return ApiResponse.ok(teamSpaceService.get(teamspaceId));
    }

    // 팀스페이스 목록 조회
    @GetMapping
    public ApiResponse<TeamSpaceListResponse> getList() {
        return ApiResponse.ok(teamSpaceService.getList());
    }

    // 팀스페이스 수정(이름)
    @PutMapping("/{teamspaceId}")
    public ApiResponse<TeamSpaceCreateResponse> update(@PathVariable String teamspaceId,
                                                       @RequestBody TeamSpaceUpdateRequest request) {
        return ApiResponse.ok(teamSpaceService.update(teamspaceId, request));
    }

    // 팀스페이스 삭제
    @DeleteMapping("/{teamspaceId}")
    public ApiResponse<Void> delete(@PathVariable String teamspaceId) {
        teamSpaceService.delete(teamspaceId);
        return ApiResponse.ok();
    }
}