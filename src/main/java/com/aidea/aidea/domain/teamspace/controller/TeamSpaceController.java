package com.aidea.aidea.domain.teamspace.controller;

import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.service.TeamSpaceService;
import com.aidea.aidea.global.dto.TestGlobalResponseDTO;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teamspaces")
@RequiredArgsConstructor
public class TeamSpaceController {

    private final TeamSpaceService teamSpaceService;

    // 팀스페이스 생성
    @PostMapping
    public TestGlobalResponseDTO<TeamSpaceCreateResponse> create(@RequestBody TeamSpaceCreateRequest request) {
        return TestGlobalResponseDTO.ok(teamSpaceService.create(request));
    }

    // 팀스페이스 단건 조회
    @GetMapping("/{teamspaceId}")
    public TestGlobalResponseDTO<TeamSpaceDetailResponse> get(@PathVariable String teamspaceId) {
        return TestGlobalResponseDTO.ok(teamSpaceService.get(teamspaceId));
    }

    // 팀스페이스 목록 조회
    @GetMapping
    public TestGlobalResponseDTO<TeamSpaceListResponse> getList() {
        return TestGlobalResponseDTO.ok(teamSpaceService.getList());
    }

    // 팀스페이스 수정(이름)
    @PutMapping("/{teamspaceId}")
    public TestGlobalResponseDTO<TeamSpaceCreateResponse> update(@PathVariable String teamspaceId,
                                                       @RequestBody TeamSpaceUpdateRequest request) {
        return TestGlobalResponseDTO.ok(teamSpaceService.update(teamspaceId, request));
    }

    // 팀스페이스 삭제
    @DeleteMapping("/{teamspaceId}")
    public TestGlobalResponseDTO<Void> delete(@PathVariable String teamspaceId) {
        teamSpaceService.delete(teamspaceId);
        return TestGlobalResponseDTO.ok();
    }
}