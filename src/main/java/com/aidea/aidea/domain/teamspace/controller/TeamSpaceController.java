package com.aidea.aidea.domain.teamspace.controller;

import com.aidea.aidea.domain.teamspace.dto.*;
import com.aidea.aidea.domain.teamspace.service.TeamSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teamspaces")
@RequiredArgsConstructor
public class TeamSpaceController {

    private final TeamSpaceService teamSpaceService;

    @PostMapping
    public TeamSpaceCreateResponse create(@RequestBody TeamSpaceCreateRequest request) {
        return teamSpaceService.create(request);
    }

    @GetMapping("/{id}")
    public TeamSpaceDetailResponse get(@PathVariable String id) {
        return teamSpaceService.get(id);
    }

    @GetMapping
    public TeamSpaceListResponse getList() {
        return teamSpaceService.getList();
    }

    @PutMapping("/{id}")
    public TeamSpaceCreateResponse update(@PathVariable String id,
                                          @RequestBody TeamSpaceUpdateRequest request) {
        return teamSpaceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        teamSpaceService.delete(id);
    }
}