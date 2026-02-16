package server;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class JsonUtil {
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Collection<?>) obj) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first) sb.append(",");
                sb.append(toJson(entry.getKey().toString())).append(":").append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        
        // POJO reflection
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        try {
            for (Field field : obj.getClass().getFields()) {
                if (!first) sb.append(",");
                sb.append("\"").append(field.getName()).append("\":");
                sb.append(toJson(field.get(obj)));
                first = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sb.append("}");
        return sb.toString();
    }
    
    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static Map<String, Object> fromJson(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null) return map;
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim();
                    if (value.matches("-?\\d+")) {
                        map.put(key, Integer.parseInt(value));
                    } else {
                        map.put(key, value.replace("\"", ""));
                    }
                }
            }
        }
        return map;
    }
}