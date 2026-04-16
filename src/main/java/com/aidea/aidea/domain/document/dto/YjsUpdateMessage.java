package com.aidea.aidea.domain.document.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // getter, setter, toString, equals, hashCode 함수 자동 생성
@NoArgsConstructor // 파라미터 없는 기본 생성자 생성, 객체를 생성하기 위해 사용됨
@AllArgsConstructor // 모든 필드를 받는 생성사 생성
public class YjsUpdateMessage {
    private String docId;
    private String update;
    private List<String> updates; // 다중 업데이트 (초기 sync용)
}

