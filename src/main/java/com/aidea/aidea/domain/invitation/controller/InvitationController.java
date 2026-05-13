package com.aidea.aidea.domain.invitation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidea.aidea.domain.invitation.dto.InvitationRequest;
import com.aidea.aidea.domain.invitation.service.InvitationService;
import com.aidea.aidea.global.dto.GlobalResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GlobalResponse<Void> invite(@Valid @RequestBody InvitationRequest request,
                                        @AuthenticationPrincipal String userId) {
        invitationService.sendInvitation(Long.parseLong(userId), request.getTeamspaceId(), request.getInviteeEmail(), request.getRole());
        return GlobalResponse.ok("초대 이메일이 발송되었습니다.");
    }

    @GetMapping("/accept")
    public GlobalResponse<Void> accept(@RequestParam String token,
                                        @AuthenticationPrincipal String userId) {
        invitationService.acceptInvitation(token, Long.parseLong(userId));
        return GlobalResponse.ok("초대가 수락되었습니다.");
    }
}
