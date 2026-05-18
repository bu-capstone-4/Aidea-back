package com.aidea.aidea.domain.aifeedback.controller.dto;

import jakarta.validation.constraints.Size;

public record FeedbackRequest(

        @Size(max = 500, message = "추가 요청사항은 500자를 초과할 수 없습니다")
        String additionalRequest  //optional - null 허용
) {}
