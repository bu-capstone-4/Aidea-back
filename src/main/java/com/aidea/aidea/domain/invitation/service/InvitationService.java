package com.aidea.aidea.domain.invitation.service;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.invitation.dto.BulkInviteResultItem;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final MailService mailService;
    private final TeamSpaceRepository teamSpaceRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    public Invitation sendInvitation(Long inviterId, String teamspaceId, String inviteeEmail, MemberRole role) {
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

        String inviteLink = frontendUrl + "/invite?token=" + invitation.getToken();
        try {
            mailService.sendInvitationMail(inviteeEmail, inviteLink);
        } catch (MailException e) {
            log.warn("초대 메일 발송 실패 - email: {}, cause: {}", inviteeEmail, e.getMessage());
        }

        return invitation;
    }

    public String acceptInvitation(String token, Long userId) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));

        if (invitation.isExpired()) {
            throw new CustomException(ErrorCode.INVITATION_EXPIRED);
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new CustomException(ErrorCode.INVITATION_EXPIRED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 초대받은 이메일과 로그인된 유저 이메일 검증
        if (!invitation.getInviteeEmail().equalsIgnoreCase(user.getEmail())) {
            throw new CustomException(ErrorCode.INVITATION_EMAIL_MISMATCH);
        }

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

        List<com.aidea.aidea.domain.documents.entity.Document> docs =
                documentRepository.findByTeamspaceId(invitation.getTeamspaceId());
        return docs.isEmpty() ? null : docs.get(0).getId();
    }

    @Transactional(readOnly = true)
    public String getTeamspaceIdByToken(String token) {
        return invitationRepository.findByToken(token)
                .map(Invitation::getTeamspaceId)
                .orElse(null);
    }

    public List<BulkInviteResultItem> sendBulkInvitations(Long inviterId, String teamspaceId, List<String> emails) {
        if (emails.size() > 8) {
            throw new CustomException(ErrorCode.INVITATION_LIMIT_EXCEEDED);
        }

        teamSpaceRepository.findById(teamspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAMSPACE_NOT_FOUND));

        TeamspaceMember inviterMember = teamspaceMemberRepository
                .findByTeamspaceIdAndUserId(teamspaceId, inviterId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));

        if (inviterMember.getRole() != MemberRole.OWNER) {
            throw new CustomException(ErrorCode.NOT_TEAMSPACE_OWNER);
        }

        List<BulkInviteResultItem> results = new ArrayList<>();

        for (String email : emails) {
            boolean isAlreadyMember = userRepository.findByEmail(email)
                    .map(user -> teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, user.getId()).isPresent())
                    .orElse(false);

            if (isAlreadyMember) {
                results.add(new BulkInviteResultItem(email, "ALREADY_MEMBER", null));
                continue;
            }

            Invitation existingInvitation = invitationRepository
                    .findByTeamspaceIdAndInviteeEmailAndStatus(teamspaceId, email, InvitationStatus.PENDING)
                    .orElse(null);

            if (existingInvitation != null) {
                results.add(new BulkInviteResultItem(email, "SENT", existingInvitation.getId()));
                continue;
            }

            Invitation invitation = Invitation.builder()
                    .teamspaceId(teamspaceId)
                    .inviteeEmail(email)
                    .inviterId(inviterId)
                    .role(MemberRole.MEMBER)
                    .build();

            invitationRepository.save(invitation);

            String inviteLink = frontendUrl + "/invite?token=" + invitation.getToken();
            try {
                mailService.sendInvitationMail(email, inviteLink);
            } catch (MailException e) {
                log.warn("초대 메일 발송 실패 - email: {}, cause: {}", email, e.getMessage());
            }

            results.add(new BulkInviteResultItem(email, "SENT", invitation.getId()));
        }

        return results;
    }
}
