package com.aidea.aidea.global.websocket;

import com.aidea.aidea.domain.teamspace.entity.MemberRole;

/**
 * 멤버 역할 변경 시 WebSocket 세션에 캐시된 role 정보를 갱신하고
 * 필요 시 결과를 실시간으로 전파하기 위한 리스너.
 */
public interface MemberRoleChangeListener {
    void onMemberRoleChanged(String teamspaceId, Long userId, MemberRole newRole);
}
