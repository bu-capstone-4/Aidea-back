package com.aidea.aidea.domain.aifeedback.converter;

import com.aidea.aidea.domain.aifeedback.entity.Question;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class QuestionsConverter implements AttributeConverter<List<Question>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    //Java 객체 -> DB 컬럼
    @Override
    public String convertToDatabaseColumn(List<Question> questions) {
        if (questions == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Question 직렬화 실패", e);
        }
    }

    //DB 컬럼 -> Java 객체
    @Override
    public List<Question> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return OBJECT_MAPPER.readValue(dbData, new TypeReference<List<Question>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Question 역직렬화 실패", e);
        }
    }
}
