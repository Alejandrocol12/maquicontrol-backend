package com.maquicontrol.backend.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Converter
public class MapListConverter implements AttributeConverter<List<Map<String, Object>>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
        if (attribute == null) return "[]";
        try {
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
