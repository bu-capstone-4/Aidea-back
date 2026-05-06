package com.aidea.aidea.domain.aifeedback.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AnswerRequest(

    @NotEmpty(message = "답변 목록은 비어있을 수 없습니다")
    @Valid //각 원소의 검증 어노테이션도 평가
    List<AnswerItem> answer
) {
    //답변 한 개의 모양
    public record AnswerItem(

            @NotBlank(message = "questionId는 필수입니다")
            String questionId,

            @NotBlank(message = "answer 값은 필수입니다")
            String value
    ) {}
}
