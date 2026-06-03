package com.aidea.aidea.domain.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class BulkInviteRequest {

    @NotEmpty(message = "초대할 이메일을 입력해주세요.")
    private List<@NotBlank(message = "이메일은 빈 값일 수 없습니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") String> emails;
}
