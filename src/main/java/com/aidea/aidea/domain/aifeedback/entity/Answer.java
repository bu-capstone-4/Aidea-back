package com.aidea.aidea.domain.aifeedback.entity;

//유저가 제출하는 답변 한 개
public record Answer(
        String questionId, //Question.id 참조
        String value       //선택했거나 직접 입력한 답변
) {}
