package com.aidea.aidea.domain.aifeedback.entity.converter;

import com.aidea.aidea.domain.aifeedback.entity.Answer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class AnswersConverter implements AttributeConverter<List<Answer>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Answer> answers) {
        if (answers == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Answer 직렬화 실패", e);
        }
    }

    @Override
    public List<Answer> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return OBJECT_MAPPER.readValue(dbData, new TypeReference<List<Answer>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Answer 역직렬화 실패", e);
        }
    }
}
