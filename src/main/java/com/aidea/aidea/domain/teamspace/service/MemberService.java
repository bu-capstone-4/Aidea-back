package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.invitation.entity.Invitation;
import com.aidea.aidea.domain.invitation.entity.InvitationStatus;
import com.aidea.aidea.domain.invitation.repository.InvitationRepository;
import com.aidea.aidea.domain.teamspace.dto.MemberInfoResponse;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MemberInfoResponse> getMembers(String teamspaceId, Long userId) {
        teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));

        List<MemberInfoResponse> result = new ArrayList<>();

        // 활성 멤버 (배치 조회로 N+1 방지)
        List<TeamspaceMember> members = teamspaceMemberRepository.findByTeamspaceId(teamspaceId);
        Set<Long> userIds = members.stream().map(TeamspaceMember::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        for (TeamspaceMember m : members) {
            User u = userMap.get(m.getUserId());
            result.add(MemberInfoResponse.builder()
                    .userId(m.getUserId())
                    .name(u != null ? u.getName() : null)
                    .email(u != null ? u.getEmail() : null)
                    .role(m.getRole().name())
                    .status("ACTIVE")
                    .profileImageUrl(u != null ? u.getProfileImageUrl() : null)
                    .build());
        }

        // 대기 중인 초대 (PENDING)
        List<Invitation> pending = invitationRepository.findByTeamspaceIdAndStatus(teamspaceId, InvitationStatus.PENDING);
        for (Invitation inv : pending) {
            result.add(MemberInfoResponse.builder()
                    .userId(null)
                    .name(null)
                    .email(inv.getInviteeEmail())
                    .role(inv.getRole().name())
                    .status("PENDING")
                    .profileImageUrl(null)
                    .build());
        }

        return result;
    }

    @Transactional
    public void inviteMember(String teamspaceId, String email, Long userId) {
        // 호출자 OWNER 확인
        TeamspaceMember caller = teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));
        if (caller.getRole() != MemberRole.OWNER) {
            throw new CustomException(ErrorCode.NOT_TEAMSPACE_OWNER);
        }

        // 이미 활성 멤버인지 확인
        User invitee = userRepository.findByEmail(email).orElse(null);
        if (invitee != null) {
            if (teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, invitee.getId()).isPresent()) {
                throw new CustomException(ErrorCode.ALREADY_MEMBER);
            }
        }

        // 이미 대기 중인 초대가 있는지 확인
        invitationRepository.findByTeamspaceIdAndInviteeEmailAndStatus(teamspaceId, email, InvitationStatus.PENDING)
                .ifPresent(inv -> { throw new CustomException(ErrorCode.ALREADY_INVITED); });

        invitationRepository.save(Invitation.builder()
                .id(UUID.randomUUID().toString())
                .teamspaceId(teamspaceId)
                .inviterUserId(userId)
                .inviteeEmail(email)
                .token(UUID.randomUUID().toString())
                .status(InvitationStatus.PENDING)
                .role(MemberRole.VIEWER)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());
    }

    @Transactional
    public void removeMember(String teamspaceId, Long memberId, Long userId) {
        // 호출자 OWNER 확인
        TeamspaceMember caller = teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));
        if (caller.getRole() != MemberRole.OWNER) {
            throw new CustomException(ErrorCode.NOT_TEAMSPACE_OWNER);
        }

        // 대상 멤버 조회 (memberId = 대상 유저의 userId)
        TeamspaceMember target = teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));

        // OWNER는 추방 불가
        if (target.getRole() == MemberRole.OWNER) {
            throw new CustomException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        teamspaceMemberRepository.delete(target);
    }

    @Transactional
    public void cancelInvitation(String teamspaceId, String invitationId, Long userId) {
        // 호출자 OWNER 확인
        TeamspaceMember caller = teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));
        if (caller.getRole() != MemberRole.OWNER) {
            throw new CustomException(ErrorCode.NOT_TEAMSPACE_OWNER);
        }

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));

        if (!invitation.getTeamspaceId().equals(teamspaceId)) {
            throw new CustomException(ErrorCode.INVITATION_NOT_FOUND);
        }

        invitation.cancel();
    }
}
