package com.aidea.aidea.global.util;

import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class TeamspaceRoleValidator {

    private final TeamspaceMemberRepository teamspaceMemberRepository;

    public void requireMembership(String teamspaceId, Long userId) {
        teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));
    }

    public void requireRole(String teamspaceId, Long userId, MemberRole... allowedRoles) {
        TeamspaceMember member = teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));

        if (Arrays.stream(allowedRoles).noneMatch(r -> r == member.getRole())) {
            throw new CustomException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
    }
}