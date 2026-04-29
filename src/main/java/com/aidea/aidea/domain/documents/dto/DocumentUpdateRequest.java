package com.aidea.aidea.domain.documents.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;

/*
    문서 업데이트(수정) 요청 dto
*/


@Getter
@NoArgsConstructor
public class DocumentUpdateRequest {

    private String title;
}