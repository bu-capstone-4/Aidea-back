package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.invitation.repository.InvitationRepository;
import com.aidea.aidea.domain.teamspace.dto.MemberInfoResponse;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.TeamspaceRoleValidator;
import com.aidea.aidea.global.websocket.MemberRoleChangeListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private TeamspaceMemberRepository teamspaceMemberRepository;
    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MemberRoleChangeListener teamspaceListener;
    @Mock
    private MemberRoleChangeListener documentListener;

    private MemberService memberService;

    private static final String TEAMSPACE_ID = "ts-1";
    private static final Long OWNER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @BeforeEach
    void setUp() {
        TeamspaceRoleValidator roleValidator = new TeamspaceRoleValidator(teamspaceMemberRepository);
        memberService = new MemberService(
                teamspaceMemberRepository,
                invitationRepository,
                userRepository,
                roleValidator,
                List.of(teamspaceListener, documentListener)
        );
    }

    private TeamspaceMember member(Long userId, MemberRole role) {
        return TeamspaceMember.builder()
                .id(userId)
                .teamspaceId(TEAMSPACE_ID)
                .userId(userId)
                .role(role)
                .build();
    }

    private User user(Long userId) {
        return User.builder()
                .id(userId)
                .name("user" + userId)
                .email("user" + userId + "@example.com")
                .build();
    }

    @Test
    void changeMemberRole_promotesViewerToMember() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER);
        TeamspaceMember target = member(TARGET_ID, MemberRole.VIEWER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID)));

        MemberInfoResponse response = memberService.changeMemberRole(TEAMSPACE_ID, TARGET_ID, MemberRole.MEMBER, OWNER_ID);

        assertThat(response.getRole()).isEqualTo("MEMBER");
        assertThat(target.getRole()).isEqualTo(MemberRole.MEMBER);
        verify(teamspaceListener).onMemberRoleChanged(TEAMSPACE_ID, TARGET_ID, MemberRole.MEMBER);
        verify(documentListener).onMemberRoleChanged(TEAMSPACE_ID, TARGET_ID, MemberRole.MEMBER);
    }

    @Test
    void changeMemberRole_demotesMemberToViewer() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER);
        TeamspaceMember target = member(TARGET_ID, MemberRole.MEMBER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID)));

        MemberInfoResponse response = memberService.changeMemberRole(TEAMSPACE_ID, TARGET_ID, MemberRole.VIEWER, OWNER_ID);

        assertThat(response.getRole()).isEqualTo("VIEWER");
        assertThat(target.getRole()).isEqualTo(MemberRole.VIEWER);
        verify(teamspaceListener).onMemberRoleChanged(TEAMSPACE_ID, TARGET_ID, MemberRole.VIEWER);
    }

    @Test
    void changeMemberRole_promotesMemberToCoOwner() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER);
        TeamspaceMember target = member(TARGET_ID, MemberRole.MEMBER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID)));

        MemberInfoResponse response = memberService.changeMemberRole(TEAMSPACE_ID, TARGET_ID, MemberRole.OWNER, OWNER_ID);

        assertThat(response.getRole()).isEqualTo("OWNER");
        assertThat(target.getRole()).isEqualTo(MemberRole.OWNER);
    }

    @Test
    void changeMemberRole_ownerCanDemoteCoOwner_whenMultipleOwnersExist() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER);
        TeamspaceMember target = member(TARGET_ID, MemberRole.OWNER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, TARGET_ID)).thenReturn(Optional.of(target));
        when(teamspaceMemberRepository.countByTeamspaceIdAndRole(TEAMSPACE_ID, MemberRole.OWNER)).thenReturn(2L);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID)));

        MemberInfoResponse response = memberService.changeMemberRole(TEAMSPACE_ID, TARGET_ID, MemberRole.MEMBER, OWNER_ID);

        assertThat(response.getRole()).isEqualTo("MEMBER");
        assertThat(target.getRole()).isEqualTo(MemberRole.MEMBER);
    }

    @Test
    void changeMemberRole_lastOwnerCannotBeDemoted() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER);
        TeamspaceMember target = caller; // 본인이 마지막 OWNER

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(teamspaceMemberRepository.countByTeamspaceIdAndRole(TEAMSPACE_ID, MemberRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> memberService.changeMemberRole(TEAMSPACE_ID, OWNER_ID, MemberRole.MEMBER, OWNER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEAMSPACE_LAST_OWNER);

        assertThat(target.getRole()).isEqualTo(MemberRole.OWNER);
        verify(teamspaceListener, never()).onMemberRoleChanged(any(), any(), any());
        verify(documentListener, never()).onMemberRoleChanged(any(), any(), any());
    }

    @Test
    void changeMemberRole_sameRole_isIdempotentAndDoesNotNotify() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER); // 본인이 마지막 OWNER, role 변경 없음

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID)));

        MemberInfoResponse response = memberService.changeMemberRole(TEAMSPACE_ID, OWNER_ID, MemberRole.OWNER, OWNER_ID);

        assertThat(response.getRole()).isEqualTo("OWNER");
        verify(teamspaceMemberRepository, never()).countByTeamspaceIdAndRole(any(), any());
        verify(teamspaceListener, never()).onMemberRoleChanged(any(), any(), any());
        verify(documentListener, never()).onMemberRoleChanged(any(), any(), any());
    }

    @Test
    void changeMemberRole_callerNotOwner_throwsInsufficientPermission() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.MEMBER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> memberService.changeMemberRole(TEAMSPACE_ID, TARGET_ID, MemberRole.MEMBER, OWNER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_PERMISSION);

        verify(teamspaceMemberRepository, never()).findByTeamspaceIdAndUserId(TEAMSPACE_ID, TARGET_ID);
    }

    @Test
    void changeMemberRole_callerNotTeamspaceMember_throwsNotTeamspaceMember() {
        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.changeMemberRole(TEAMSPACE_ID, TARGET_ID, MemberRole.MEMBER, OWNER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_TEAMSPACE_MEMBER);
    }

    @Test
    void changeMemberRole_targetNotTeamspaceMember_throwsNotTeamspaceMember() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.changeMemberRole(TEAMSPACE_ID, TARGET_ID, MemberRole.MEMBER, OWNER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_TEAMSPACE_MEMBER);
    }

    @Test
    void removeMember_lastOwner_throwsTeamspaceLastOwner() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(teamspaceMemberRepository.countByTeamspaceIdAndRole(TEAMSPACE_ID, MemberRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> memberService.removeMember(TEAMSPACE_ID, OWNER_ID, OWNER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEAMSPACE_LAST_OWNER);

        verify(teamspaceMemberRepository, never()).delete(any());
    }

    @Test
    void removeMember_ownerWithMultipleOwners_succeeds() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.OWNER);
        TeamspaceMember target = member(TARGET_ID, MemberRole.OWNER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));
        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, TARGET_ID)).thenReturn(Optional.of(target));
        when(teamspaceMemberRepository.countByTeamspaceIdAndRole(TEAMSPACE_ID, MemberRole.OWNER)).thenReturn(2L);

        memberService.removeMember(TEAMSPACE_ID, TARGET_ID, OWNER_ID);

        verify(teamspaceMemberRepository).delete(target);
    }

    @Test
    void removeMember_callerNotOwner_throwsNotTeamspaceOwner() {
        TeamspaceMember caller = member(OWNER_ID, MemberRole.MEMBER);

        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, OWNER_ID)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> memberService.removeMember(TEAMSPACE_ID, TARGET_ID, OWNER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_TEAMSPACE_OWNER);
    }
}
