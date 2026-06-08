package com.aidea.aidea.domain.invitation.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

import com.aidea.aidea.domain.invitation.dto.AcceptInvitationRequest;
import com.aidea.aidea.domain.invitation.dto.BulkInviteRequest;
import com.aidea.aidea.domain.invitation.dto.BulkInviteResultItem;
import com.aidea.aidea.domain.invitation.dto.InvitationRequest;
import com.aidea.aidea.domain.invitation.dto.InvitationResponse;
import com.aidea.aidea.domain.invitation.dto.InviteMemberRequest;
import com.aidea.aidea.domain.invitation.service.InvitationService;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.service.MemberService;
import com.aidea.aidea.global.dto.GlobalResponse;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final MemberService memberService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @PostMapping("/api/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public GlobalResponse<InvitationResponse> invite(@Valid @RequestBody InvitationRequest request,
                                                     @AuthenticationPrincipal String userId) {
        InvitationResponse result = new InvitationResponse(
                invitationService.sendInvitation(Long.parseLong(userId), request.getTeamspaceId(), request.getInviteeEmail(), request.getRole()));
        return GlobalResponse.ok("초대 이메일이 발송되었습니다.", result);
    }

    // 이메일 링크 클릭 시 브라우저로 직접 접근 (GET)
    // @RestController에서 RedirectView를 반환하면 JSON 직렬화 시도로 리다이렉트가 동작하지 않으므로
    // HttpServletResponse.sendRedirect()를 직접 사용한다.
    @GetMapping("/api/invitations/accept")
    public void acceptByLink(@RequestParam String token,
                             @AuthenticationPrincipal String userId,
                             HttpServletResponse response) throws IOException {
        // 비로그인 상태 - 프론트엔드 초대 수락 페이지로 이동시키고 프론트가 로그인·수락을 처리
        if (userId == null || "anonymousUser".equals(userId)) {
            response.sendRedirect(frontendUrl + "/invite?token=" + token);
            return;
        }

        try {
            String docId = invitationService.acceptInvitation(token, Long.parseLong(userId));
            String target = (docId != null) ? "/main/" + docId : "/";
            response.sendRedirect(frontendUrl + target);
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.ALREADY_MEMBER) {
                response.sendRedirect(frontendUrl + "/");
            } else {
                response.sendRedirect(frontendUrl + "/?error=" + e.getErrorCode().getCode());
            }
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
    public GlobalResponse<InvitationResponse> inviteMember(
            @PathVariable String teamspaceId,
            @Valid @RequestBody InviteMemberRequest request,
            @AuthenticationPrincipal String userId) {
        InvitationResponse result = new InvitationResponse(
                invitationService.sendInvitation(Long.parseLong(userId), teamspaceId, request.getEmail(), MemberRole.MEMBER));
        return GlobalResponse.ok("초대가 발송되었습니다.", result);
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
