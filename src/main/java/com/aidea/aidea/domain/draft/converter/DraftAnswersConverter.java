package com.aidea.aidea.domain.draft.converter;

import com.aidea.aidea.domain.draft.entity.DraftAnswer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class DraftAnswersConverter implements AttributeConverter<List<DraftAnswer>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<DraftAnswer> answers) {
        if (answers == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DraftAnswer 직렬화 실패", e);
        }
    }

    @Override
    public List<DraftAnswer> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return OBJECT_MAPPER.readValue(dbData, new TypeReference<List<DraftAnswer>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DraftAnswer 역직렬화 실패", e);
        }
    }
}
