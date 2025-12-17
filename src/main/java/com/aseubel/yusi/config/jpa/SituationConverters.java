package com.aseubel.yusi.config.jpa;

import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class SituationConverters {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Converter
    public static class StringSetConverter implements AttributeConverter<Set<String>, String> {
        @Override
        public String convertToDatabaseColumn(Set<String> attribute) {
            if (attribute == null) return null;
            try {
                return objectMapper.writeValueAsString(attribute);
            } catch (Exception e) {
                throw new RuntimeException("Error converting Set to JSON", e);
            }
        }

        @Override
        public Set<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isEmpty()) return Collections.emptySet();
            try {
                return objectMapper.readValue(dbData, new TypeReference<Set<String>>() {});
            } catch (Exception e) {
                throw new RuntimeException("Error converting JSON to Set", e);
            }
        }
    }

    @Converter
    public static class StringMapConverter implements AttributeConverter<Map<String, String>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, String> attribute) {
            if (attribute == null) return null;
            try {
                return objectMapper.writeValueAsString(attribute);
            } catch (Exception e) {
                throw new RuntimeException("Error converting Map to JSON", e);
            }
        }

        @Override
        public Map<String, String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isEmpty()) return Collections.emptyMap();
            try {
                return objectMapper.readValue(dbData, new TypeReference<Map<String, String>>() {});
            } catch (Exception e) {
                throw new RuntimeException("Error converting JSON to Map", e);
            }
        }
    }

    @Converter
    public static class SituationReportConverter implements AttributeConverter<SituationReport, String> {
        @Override
        public String convertToDatabaseColumn(SituationReport attribute) {
            if (attribute == null) return null;
            try {
                return objectMapper.writeValueAsString(attribute);
            } catch (Exception e) {
                throw new RuntimeException("Error converting SituationReport to JSON", e);
            }
        }

        @Override
        public SituationReport convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isEmpty()) return null;
            try {
                return objectMapper.readValue(dbData, SituationReport.class);
            } catch (Exception e) {
                throw new RuntimeException("Error converting JSON to SituationReport", e);
            }
        }
    }
}
