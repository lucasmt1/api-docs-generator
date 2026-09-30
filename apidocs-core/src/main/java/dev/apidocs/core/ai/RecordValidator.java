package dev.apidocs.core.ai;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/** Finds {@code null} components (fields the LLM left out), recursively through records and lists. */
final class RecordValidator {

    private RecordValidator() {
    }

    static List<String> missingFields(Record record) {
        List<String> missing = new ArrayList<>();
        check(record, "", missing);
        return missing;
    }

    private static void check(Object value, String path, List<String> missing) {
        if (!(value instanceof Record record)) {
            return;
        }
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object fieldValue = read(component, record);
            String fieldPath = path.isEmpty() ? component.getName() : path + "." + component.getName();
            if (fieldValue == null) {
                missing.add(fieldPath);
            } else if (fieldValue instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) == null) {
                        missing.add(fieldPath + "[" + i + "]");
                    } else {
                        check(list.get(i), fieldPath + "[" + i + "]", missing);
                    }
                }
            } else {
                check(fieldValue, fieldPath, missing);
            }
        }
    }

    private static Object read(RecordComponent component, Record record) {
        try {
            return component.getAccessor().invoke(record);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot read " + component.getName(), e);
        }
    }
}
