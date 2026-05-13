package com.aidea.aidea.domain.invitation.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aidea.aidea.domain.invitation.dto.InvitationRequest;
import com.aidea.aidea.domain.invitation.service.InvitationService;
import com.aidea.aidea.global.dto.GlobalResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    public GlobalResponse<Void> invite(@RequestBody InvitationRequest request,
                                        @AuthenticationPrincipal String userId) {
        invitationService.sendInvitation(Long.parseLong(userId), request.getTeamSpaceId(), request.getInviteeEmail());
        return GlobalResponse.ok();
    }

    @GetMapping("/accept")
    public GlobalResponse<Void> accept(@RequestParam String token,
                                        @AuthenticationPrincipal String userId) {
        invitationService.acceptInvitation(token, Long.parseLong(userId));
        return GlobalResponse.ok();
    }
}
