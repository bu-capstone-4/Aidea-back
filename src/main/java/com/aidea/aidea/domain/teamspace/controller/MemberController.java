package com.aidea.aidea.domain.teamspace.controller;

import com.aidea.aidea.domain.teamspace.dto.MemberInfoResponse;
import com.aidea.aidea.domain.teamspace.service.MemberService;
import com.aidea.aidea.global.dto.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teamspaces")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // 멤버 목록 조회
    @GetMapping("/{teamspaceId}/members")
    public GlobalResponse<List<MemberInfoResponse>> getMembers(
            @PathVariable String teamspaceId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return GlobalResponse.ok(memberService.getMembers(teamspaceId, userId));
    }

    // 멤버 추가 초대 (멤버 관리 모달)
    @PostMapping("/{teamspaceId}/members/invite")
    public GlobalResponse<Void> inviteMember(
            @PathVariable String teamspaceId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        memberService.inviteMember(teamspaceId, body.get("email"), userId);
        return GlobalResponse.ok("초대가 발송되었습니다.");
    }

    // 멤버 추방
    @DeleteMapping("/{teamspaceId}/members/{memberId}")
    public GlobalResponse<Void> removeMember(
            @PathVariable String teamspaceId,
            @PathVariable Long memberId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        memberService.removeMember(teamspaceId, memberId, userId);
        return GlobalResponse.ok("멤버가 추방되었습니다.");
    }

    // 초대 취소
    @DeleteMapping("/{teamspaceId}/invitations/{invitationId}")
    public GlobalResponse<Void> cancelInvitation(
            @PathVariable String teamspaceId,
            @PathVariable String invitationId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        memberService.cancelInvitation(teamspaceId, invitationId, userId);
        return GlobalResponse.ok("초대가 취소되었습니다.");
    }
}
