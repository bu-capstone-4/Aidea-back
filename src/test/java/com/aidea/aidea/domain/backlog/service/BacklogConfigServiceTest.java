package com.aidea.aidea.domain.backlog.service;

import com.aidea.aidea.domain.backlog.dto.request.BacklogConfigRequest;
import com.aidea.aidea.domain.backlog.dto.response.BacklogConfigResponse;
import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.entity.BacklogDraftStatus;
import com.aidea.aidea.domain.backlog.repository.BacklogConfigRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacklogConfigServiceTest {

    @Mock
    private BacklogConfigRepository backlogConfigRepository;
    @Mock
    private TeamspaceMemberRepository teamspaceMemberRepository;
    @Mock
    private BacklogEventPublisher eventPublisher;
    @Mock
    private BacklogDraftService backlogDraftService;

    private BacklogConfigService service;

    private static final String TEAMSPACE_ID = "ts-1";
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new BacklogConfigService(backlogConfigRepository, teamspaceMemberRepository,
                eventPublisher, backlogDraftService, new ObjectMapper());

        TeamspaceMember member = TeamspaceMember.builder()
                .id(1L)
                .teamspaceId(TEAMSPACE_ID)
                .userId(USER_ID)
                .role(MemberRole.OWNER)
                .build();
        when(teamspaceMemberRepository.findByTeamspaceIdAndUserId(TEAMSPACE_ID, USER_ID))
                .thenReturn(Optional.of(member));
    }

    @Test
    void firstCreation_withGenerateDraft_triggersDraftGeneration() {
        when(backlogConfigRepository.findById(TEAMSPACE_ID)).thenReturn(Optional.empty());
        when(backlogConfigRepository.save(any(BacklogConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(backlogDraftService.triggerBacklogDraftGeneration(eq(TEAMSPACE_ID), eq(USER_ID), any(BacklogConfig.class)))
                .thenReturn(BacklogDraftStatus.PENDING);

        BacklogConfigRequest request = new BacklogConfigRequest(true, true, true, true, true, true, true);

        BacklogConfigResponse response = service.upsertConfig(TEAMSPACE_ID, USER_ID, request);

        assertThat(response.draftStatus()).isEqualTo(BacklogDraftStatus.PENDING);
        verify(backlogDraftService).triggerBacklogDraftGeneration(eq(TEAMSPACE_ID), eq(USER_ID), any(BacklogConfig.class));
        // backlog:config_updated + backlog:draft_started
        verify(eventPublisher, times(2)).publishToTeamspace(eq(TEAMSPACE_ID), eq(String.valueOf(USER_ID)), anyString());
    }

    @Test
    void existingConfig_withGenerateDraft_throwsNotFirstCreation() {
        BacklogConfig existing = BacklogConfig.create(TEAMSPACE_ID, false, false, false, false, false, false);
        when(backlogConfigRepository.findById(TEAMSPACE_ID)).thenReturn(Optional.of(existing));

        BacklogConfigRequest request = new BacklogConfigRequest(true, true, true, true, true, true, true);

        assertThatThrownBy(() -> service.upsertConfig(TEAMSPACE_ID, USER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BACKLOG_DRAFT_NOT_FIRST_CREATION);

        verify(backlogDraftService, never()).triggerBacklogDraftGeneration(anyString(), any(), any());
        verify(backlogConfigRepository, never()).save(any());
    }
}
