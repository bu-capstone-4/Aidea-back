package com.aidea.aidea.domain.invitation.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.servlet.view.RedirectView;

import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.domain.invitation.dto.AcceptInvitationRequest;
import com.aidea.aidea.domain.invitation.dto.BulkInviteRequest;
import com.aidea.aidea.domain.invitation.dto.BulkInviteResultItem;
import com.aidea.aidea.domain.invitation.dto.InvitationRequest;
import com.aidea.aidea.domain.invitation.service.InvitationService;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.service.MemberService;
import com.aidea.aidea.global.dto.GlobalResponse;
import com.aidea.aidea.global.util.CookieUtils;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final MemberService memberService;
    private final CookieUtils cookieUtils;

    @Value("${frontend.url}")
    private String frontendUrl;

    @PostMapping("/api/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public GlobalResponse<Void> invite(@Valid @RequestBody InvitationRequest request,
                                        @AuthenticationPrincipal String userId) {
        invitationService.sendInvitation(Long.parseLong(userId), request.getTeamspaceId(), request.getInviteeEmail(), request.getRole());
        return GlobalResponse.ok("초대 이메일이 발송되었습니다.");
    }

    // 이메일 링크 클릭 시 브라우저로 직접 접근 (GET)
    @GetMapping("/api/invitations/accept")
    public RedirectView acceptByLink(@RequestParam String token,
                                     @AuthenticationPrincipal String userId,
                                     HttpServletResponse response) {
        // 비로그인 상태 - 초대 토큰을 쿠키에 저장하고 GitHub OAuth 로그인으로 리다이렉트
        if (userId == null || "anonymousUser".equals(userId)) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.createPendingInviteCookie(token).toString());
            return new RedirectView("/oauth2/authorization/github");
        }

        try {
            String docId = invitationService.acceptInvitation(token, Long.parseLong(userId));
            String target = (docId != null) ? "/main/" + docId : "/";
            return new RedirectView(frontendUrl + target);
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.ALREADY_MEMBER) {
                return new RedirectView(frontendUrl + "/");
            }
            return new RedirectView(frontendUrl + "/?error=" + e.getErrorCode().getCode());
        }
    }

    // 프론트엔드 SPA에서 fetch 호출 시 (POST)
    @PostMapping("/api/invitations/accept")
    public GlobalResponse<Map<String, String>> accept(@Valid @RequestBody AcceptInvitationRequest request,
                                                      @AuthenticationPrincipal String userId) {
        String docId = invitationService.acceptInvitation(request.getToken(), Long.parseLong(userId));
        return GlobalResponse.ok("팀스페이스에 참여하였습니다.", Map.of("docId", docId != null ? docId : ""));
    }

    @PostMapping("/api/teamspaces/{teamspaceId}/members/invite")
    public GlobalResponse<Void> inviteMember(
            @PathVariable String teamspaceId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        invitationService.sendInvitation(Long.parseLong(userId), teamspaceId, body.get("email"), MemberRole.MEMBER);
        return GlobalResponse.ok("초대가 발송되었습니다.");
    }

    @PostMapping("/api/teamspaces/{teamspaceId}/invitations")
    public GlobalResponse<List<BulkInviteResultItem>> bulkInvite(
            @PathVariable String teamspaceId,
            @Valid @RequestBody BulkInviteRequest request,
            @AuthenticationPrincipal String userId) {
        List<BulkInviteResultItem> results = invitationService.sendBulkInvitations(
                Long.parseLong(userId), teamspaceId, request.getEmails());
        return GlobalResponse.ok("초대가 발송되었습니다.", results);
    }

    @DeleteMapping("/api/teamspaces/{teamspaceId}/members/{memberId}")
    public GlobalResponse<Void> removeMember(
            @PathVariable String teamspaceId,
            @PathVariable String memberId,
            @AuthenticationPrincipal String userId) {
        String message = memberService.removeMemberOrCancelInvitation(teamspaceId, memberId, Long.parseLong(userId));
        return GlobalResponse.ok(message);
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
