package com.aidea.aidea.domain.teamspace.controller;

import com.aidea.aidea.domain.teamspace.dto.MemberInfoResponse;
import com.aidea.aidea.domain.teamspace.service.MemberService;
import com.aidea.aidea.global.dto.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teamspaces")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{teamspaceId}/members")
    public GlobalResponse<List<MemberInfoResponse>> getMembers(
            @PathVariable String teamspaceId,
            @AuthenticationPrincipal String userId) {
        return GlobalResponse.ok(memberService.getMembers(teamspaceId, Long.parseLong(userId)));
    }
}
