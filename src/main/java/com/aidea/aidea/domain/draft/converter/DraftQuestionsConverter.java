package com.aidea.aidea.domain.draft.converter;

import com.aidea.aidea.domain.draft.entity.DraftQuestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class DraftQuestionsConverter implements AttributeConverter<List<DraftQuestion>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<DraftQuestion> questions) {
        if (questions == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DraftQuestion 직렬화 실패", e);
        }
    }

    @Override
    public List<DraftQuestion> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return OBJECT_MAPPER.readValue(dbData, new TypeReference<List<DraftQuestion>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DraftQuestion 역직렬화 실패", e);
        }
    }
}
