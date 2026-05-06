package com.aidea.aidea.domain.aifeedback.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(

        @NotBlank(message = "문서 본문은 필수입니다")
        @Size(max = 50_000, message = "문서 본문은 50,000자를 초과할 수 없습니다")
        String originalMarkdown,

        @Size(max = 500, message = "추가 요청사항은 500자를 초과할 수 없습니다")
        String additionalRequest  //optional - null 허용
) {}
