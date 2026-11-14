package by.findsport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JSON-снимки версий карточек мероприятий и профилей поставщиков для журнала модерации
 * и сравнение версий (Diff-режим, ТЗ п. 4.14.3). Версия передается в каноническом виде
 * ({@code normalized()}), чтобы порядок элементов не давал ложных отличий.
 */
@Component
@RequiredArgsConstructor
class CardSnapshotService {

    private final ObjectMapper objectMapper;

    String snapshot(Object version) {
        try {
            return objectMapper.writeValueAsString(version);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сформировать снимок карточки", e);
        }
    }

    <T> T read(String snapshot, Class<T> type) {
        try {
            return objectMapper.readValue(snapshot, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Поврежден снимок карточки", e);
        }
    }

    /** Имена полей верхнего уровня, отличающихся между версиями; без предыдущей версии — все поля. */
    List<String> changedFields(String previous, String current) {
        JsonNode currentNode = readTree(current);
        if (previous == null) {
            List<String> all = new ArrayList<>();
            currentNode.fieldNames().forEachRemaining(all::add);
            return all;
        }
        JsonNode previousNode = readTree(previous);
        Set<String> names = new LinkedHashSet<>();
        previousNode.fieldNames().forEachRemaining(names::add);
        currentNode.fieldNames().forEachRemaining(names::add);
        List<String> changed = new ArrayList<>();
        for (String name : names) {
            if (!previousNode.path(name).equals(currentNode.path(name))) {
                changed.add(name);
            }
        }
        return changed;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Поврежден снимок карточки", e);
        }
    }
}
