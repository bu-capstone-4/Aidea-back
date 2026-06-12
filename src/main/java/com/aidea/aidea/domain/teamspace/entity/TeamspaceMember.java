package com.aidea.aidea.domain.teamspace.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teamspace_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TeamspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teamspace_id", nullable = false)
    private String teamspaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    public void changeRole(MemberRole role) {
        this.role = role;
    }
}
