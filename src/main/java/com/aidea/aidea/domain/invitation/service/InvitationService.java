package com.aidea.aidea.domain.invitation.service;

import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.invitation.entity.Invitation;
import com.aidea.aidea.domain.invitation.entity.InvitationStatus;
import com.aidea.aidea.domain.invitation.repository.InvitationRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final MailService mailService;
    private final TeamSpaceRepository teamSpaceRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final UserRepository userRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendInvitation(Long inviterId, String teamspaceId, String inviteeEmail, MemberRole role) {
        // 팀스페이스 존재 확인
        teamSpaceRepository.findById(teamspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAMSPACE_NOT_FOUND));

        // 초대자 권한 확인 (VIEWER는 초대 불가)
        TeamspaceMember inviterMember = teamspaceMemberRepository
                .findByTeamspaceIdAndUserId(teamspaceId, inviterId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));

        if (inviterMember.getRole() == MemberRole.VIEWER) {
            throw new CustomException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        // 초대 대상이 이미 멤버인지 확인
        userRepository.findByEmail(inviteeEmail).ifPresent(user ->
                teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, user.getId())
                        .ifPresent(m -> { throw new CustomException(ErrorCode.ALREADY_MEMBER); })
        );

        // 중복 초대 방지
        invitationRepository
                .findByTeamspaceIdAndInviteeEmailAndStatus(teamspaceId, inviteeEmail, InvitationStatus.PENDING)
                .ifPresent(i -> { throw new CustomException(ErrorCode.ALREADY_INVITED); });

        Invitation invitation = Invitation.builder()
                .teamspaceId(teamspaceId)
                .inviteeEmail(inviteeEmail)
                .inviterId(inviterId)
                .role(role)
                .build();

        invitationRepository.save(invitation);

        String inviteLink = baseUrl + "/api/invitations/accept?token=" + invitation.getToken();
        mailService.sendInvitationMail(inviteeEmail, inviteLink);
    }

    public void acceptInvitation(String token, Long userId) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));

        if (invitation.isExpired()) {
            throw new CustomException(ErrorCode.INVITATION_EXPIRED);
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new CustomException(ErrorCode.INVITATION_EXPIRED);
        }

        // 유저 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 멤버인지 확인
        teamspaceMemberRepository
                .findByTeamspaceIdAndUserId(invitation.getTeamspaceId(), userId)
                .ifPresent(m -> { throw new CustomException(ErrorCode.ALREADY_MEMBER); });

        invitation.accept();

        MemberRole roleToAssign = invitation.getRole() != null ? invitation.getRole() : MemberRole.MEMBER;
        teamspaceMemberRepository.save(TeamspaceMember.builder()
                .teamspaceId(invitation.getTeamspaceId())
                .userId(userId)
                .role(roleToAssign)
                .build());
    }
}
