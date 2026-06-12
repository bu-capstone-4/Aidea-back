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
import com.aidea.aidea.global.util.TeamspaceRoleValidator;
import com.aidea.aidea.global.websocket.MemberRoleChangeListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final TeamspaceRoleValidator roleValidator;
    private final List<MemberRoleChangeListener> roleChangeListeners;

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

        return result;
    }

    @Transactional
    public String removeMemberOrCancelInvitation(String teamspaceId, String memberIdOrEmail, Long userId) {
        try {
            Long memberId = Long.parseLong(memberIdOrEmail);
            removeMember(teamspaceId, memberId, userId);
            return "멤버가 추방되었습니다.";
        } catch (NumberFormatException e) {
            // 숫자가 아니면 이메일로 간주 → 초대 취소
            cancelInvitationByEmail(teamspaceId, memberIdOrEmail, userId);
            return "초대가 취소되었습니다.";
        }
    }

    @Transactional
    public void cancelInvitationByEmail(String teamspaceId, String email, Long userId) {
        TeamspaceMember caller = teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));
        if (caller.getRole() != MemberRole.OWNER) {
            throw new CustomException(ErrorCode.NOT_TEAMSPACE_OWNER);
        }

        List<Invitation> invitations = invitationRepository
                .findAllByTeamspaceIdAndInviteeEmailAndStatus(teamspaceId, email, InvitationStatus.PENDING);

        if (invitations.isEmpty()) {
            throw new CustomException(ErrorCode.INVITATION_NOT_FOUND);
        }

        invitationRepository.deleteAll(invitations);
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

        // 팀스페이스의 마지막 OWNER는 추방 불가
        assertNotLastOwner(teamspaceId, target);

        teamspaceMemberRepository.delete(target);
    }

    @Transactional
    public MemberInfoResponse changeMemberRole(String teamspaceId, Long targetUserId, MemberRole newRole, Long userId) {
        // 호출자 OWNER 확인
        roleValidator.requireRole(teamspaceId, userId, MemberRole.OWNER);

        // 대상 멤버 조회
        TeamspaceMember target = teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));

        if (target.getRole() != newRole) {
            // 마지막 OWNER는 강등 불가
            assertNotLastOwner(teamspaceId, target);
            target.changeRole(newRole);

            for (MemberRoleChangeListener listener : roleChangeListeners) {
                listener.onMemberRoleChanged(teamspaceId, targetUserId, newRole);
            }
        }

        User user = userRepository.findById(targetUserId).orElse(null);
        return MemberInfoResponse.builder()
                .userId(target.getUserId())
                .name(user != null ? user.getName() : null)
                .email(user != null ? user.getEmail() : null)
                .role(target.getRole().name())
                .status("ACTIVE")
                .profileImageUrl(user != null ? user.getProfileImageUrl() : null)
                .build();
    }

    private void assertNotLastOwner(String teamspaceId, TeamspaceMember target) {
        if (target.getRole() == MemberRole.OWNER
                && teamspaceMemberRepository.countByTeamspaceIdAndRole(teamspaceId, MemberRole.OWNER) <= 1) {
            throw new CustomException(ErrorCode.TEAMSPACE_LAST_OWNER);
        }
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

        invitationRepository.delete(invitation);
    }
}
