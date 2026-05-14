package com.aidea.aidea.domain.invitation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidea.aidea.domain.invitation.dto.InvitationRequest;
import com.aidea.aidea.domain.invitation.service.InvitationService;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.service.MemberService;
import com.aidea.aidea.global.dto.GlobalResponse;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final MemberService memberService;

    @PostMapping("/api/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public GlobalResponse<Void> invite(@Valid @RequestBody InvitationRequest request,
                                        @AuthenticationPrincipal String userId) {
        invitationService.sendInvitation(Long.parseLong(userId), request.getTeamspaceId(), request.getInviteeEmail(), request.getRole());
        return GlobalResponse.ok("초대 이메일이 발송되었습니다.");
    }

    @GetMapping("/api/invitations/accept")
    public GlobalResponse<Void> accept(@RequestParam String token,
                                        @AuthenticationPrincipal String userId) {
        invitationService.acceptInvitation(token, Long.parseLong(userId));
        return GlobalResponse.ok("초대가 수락되었습니다.");
    }

    @PostMapping("/api/teamspaces/{teamspaceId}/members/invite")
    public GlobalResponse<Void> inviteMember(
            @PathVariable String teamspaceId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        invitationService.sendInvitation(Long.parseLong(userId), teamspaceId, body.get("email"), MemberRole.MEMBER);
        return GlobalResponse.ok("초대가 발송되었습니다.");
    }

    @DeleteMapping("/api/teamspaces/{teamspaceId}/members/{memberId}")
    public GlobalResponse<Void> removeMember(
            @PathVariable String teamspaceId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal String userId) {
        memberService.removeMember(teamspaceId, memberId, Long.parseLong(userId));
        return GlobalResponse.ok("멤버가 추방되었습니다.");
    }

    @DeleteMapping("/api/teamspaces/{teamspaceId}/invitations/{invitationId}")
    public GlobalResponse<Void> cancelInvitation(
            @PathVariable String teamspaceId,
            @PathVariable String invitationId,
            @AuthenticationPrincipal String userId) {
        memberService.cancelInvitation(teamspaceId, invitationId, Long.parseLong(userId));
        return GlobalResponse.ok("초대가 취소되었습니다.");
    }
}
