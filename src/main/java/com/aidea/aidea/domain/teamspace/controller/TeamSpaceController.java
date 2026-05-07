package com.aidea.aidea.domain.teamspace.controller;

import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.service.TeamSpaceService;
import com.aidea.aidea.global.dto.GlobalResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teamspaces")
@RequiredArgsConstructor
public class TeamSpaceController {

    private final TeamSpaceService teamSpaceService;

    // 팀스페이스 생성
    @PostMapping
    public GlobalResponse<TeamSpaceCreateResponse> create(@RequestBody TeamSpaceCreateRequest request,
                                                          Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return GlobalResponse.ok(teamSpaceService.create(request, userId));
    }

    // 팀스페이스 단건 조회
    @GetMapping("/{teamspaceId}")
    public GlobalResponse<TeamSpaceDetailResponse> get(@PathVariable String teamspaceId,
                                                       Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return GlobalResponse.ok(teamSpaceService.get(teamspaceId, userId));
    }

    // 팀스페이스 목록 조회
    @GetMapping
    public GlobalResponse<TeamSpaceListResponse> getList(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return GlobalResponse.ok(teamSpaceService.getList(userId));
    }

    // 팀스페이스 수정(이름)
    @PutMapping("/{teamspaceId}")
    public GlobalResponse<TeamSpaceCreateResponse> update(@PathVariable String teamspaceId,
                                                          @RequestBody TeamSpaceUpdateRequest request,
                                                          Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return GlobalResponse.ok(teamSpaceService.update(teamspaceId, request, userId));
    }

    // 팀스페이스 삭제
    @DeleteMapping("/{teamspaceId}")
    public GlobalResponse<Void> delete(@PathVariable String teamspaceId,
                                       Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        teamSpaceService.delete(teamspaceId, userId);
        return GlobalResponse.ok();
    }
}