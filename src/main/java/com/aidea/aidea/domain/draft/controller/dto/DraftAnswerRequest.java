package com.aidea.aidea.domain.draft.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

// 질문에 대한 답변 제출 요청. answer를 비워서 보내면 "건너뛰기"로 처리되어
// 원본 아이디어 설명만으로 바로 최종 초안 생성으로 진행한다.
public record DraftAnswerRequest(

    @Valid
    List<AnswerItem> answer
) {
    public record AnswerItem(

            @NotBlank(message = "questionId는 필수입니다")
            String questionId,

            @NotBlank(message = "answer 값은 필수입니다")
            String value
    ) {}
}
