package com.aidea.aidea.domain.aifeedback.entity;

import java.util.List;

//Gemini가 생성하는 질문 한 개
public record Question(
        String id,
        String section,       //빈약하다고 판단된 섹션명
        String text,          //질문 텍스트
        List<String> options  //객관식 선택지 (없으면 null - 직접 입력만)
 ) {}
