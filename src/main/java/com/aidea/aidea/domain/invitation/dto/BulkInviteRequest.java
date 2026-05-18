package com.aidea.aidea.domain.invitation.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class BulkInviteRequest {

    @NotEmpty(message = "초대할 이메일을 입력해주세요.")
    private List<String> emails;
}
