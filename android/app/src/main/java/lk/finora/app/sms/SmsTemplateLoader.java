package lk.finora.app.sms;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lk.finora.app.sms.SmsTemplate.DatePattern;
import lk.finora.app.sms.SmsTemplate.DateSource;
import lk.finora.app.sms.SmsTemplate.Field;
import lk.finora.app.sms.SmsTemplate.Markers;
import lk.finora.app.sms.SmsTemplate.Name;
import lk.finora.app.sms.SmsTemplate.NumberStyle;

/** Loader for the Android asset schema only. Callers own the input stream. */
public final class SmsTemplateLoader {
    private static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_TEMPLATES = 32;
    private static final int MAX_SENDERS = 16;
    static final int MAX_LITERAL = 64;

    private SmsTemplateLoader() {}

    public static SmsParser load(InputStream input) throws IOException {
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(readBounded(input))).toString();
            try (JsonReader reader = new JsonReader(new StringReader(json))) {
                reader.setStrictness(Strictness.STRICT);
                Map<String, Object> envelope = object(readValue(reader, 0));
                require(reader.peek() == JsonToken.END_DOCUMENT);
                checkKeys(envelope, "schemaVersion", "templates");
                require(integer(envelope, "schemaVersion") == 1);
                List<?> entries = array(envelope.get("templates"), MAX_TEMPLATES);
                List<SmsTemplate> templates = new ArrayList<>();
                Set<String> ids = new HashSet<>();
                Set<List<?>> mappings = new HashSet<>();
                for (Object entry : entries) {
                    SmsTemplate template = readTemplate(object(entry));
                    require(ids.add(template.id()));
                    List<String> delimiters = new ArrayList<>();
                    for (Field field : template.fields()) delimiters.add(field.delimiter());
                    for (String sender : template.senderIds()) {
                        require(mappings.add(Arrays.asList(sender, template.markers().prefix(), template.markers().suffix(), delimiters)));
                    }
                    templates.add(template);
                }
                return new SmsParser(Collections.unmodifiableList(templates));
            }
        } catch (IOException | RuntimeException invalid) {
            // Do not pass JSON text, paths or decoder details to callers.
            throw new IOException("Invalid SMS template bundle");
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES - output.size() + 1))) != -1) {
            require(!Thread.currentThread().isInterrupted() && count > 0 && count <= MAX_BYTES - output.size());
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static SmsTemplate readTemplate(Map<String, Object> values) throws IOException {
        DateSource source = DateSource.valueOf(literal(values, "dateSource"));
        checkKeys(values, "id", "version", "senderIds", "numberStyle", "dateSource",
                source == DateSource.MESSAGE ? "datePattern" : "receiptZone", "markers", "fields");
        DatePattern pattern = source == DateSource.MESSAGE ? DatePattern.valueOf(literal(values, "datePattern")) : null;
        ZoneId zone = source == DateSource.RECEIPT ? ZoneId.of(literal(values, "receiptZone")) : null;
        var markers = object(values.get("markers"));
        String suffix = "";
        SmsParseResult.Direction requiredDirection = null;
        if (markers.containsKey("suffix")) {
            checkKeys(markers, "prefix", "debit", "credit", "suffix", "requiredDirection");
            suffix = literal(markers, "suffix");
            requiredDirection = SmsParseResult.Direction.valueOf(literal(markers, "requiredDirection"));
        } else {
            checkKeys(markers, "prefix", "debit", "credit");
        }
        Markers rule = new Markers(literal(markers, "prefix"), literal(markers, "debit"),
                literal(markers, "credit"), suffix, requiredDirection);
        require(!rule.debit().equals(rule.credit()));
        List<String> senders = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object raw : array(values.get("senderIds"), MAX_SENDERS)) {
            String sender = literal(raw);
            require(sender.equals(sender.trim()) && seen.add(sender));
            senders.add(sender);
        }
        require(!senders.isEmpty());
        List<Field> fields = new ArrayList<>();
        var names = EnumSet.noneOf(Name.class);
        for (Object raw : array(values.get("fields"), Name.values().length)) {
            var field = object(raw);
            checkKeys(field, "name", "delimiter");
            Name name = Name.valueOf(literal(field, "name"));
            require(names.add(name));
            fields.add(new Field(name, literal(field, "delimiter")));
        }
        require(names.containsAll(EnumSet.of(Name.AMOUNT, Name.CURRENCY, Name.DIRECTION)));
        require(names.contains(Name.DATE) == (source == DateSource.MESSAGE));
        return new SmsTemplate(literal(values, "id"), integer(values, "version"),
                Collections.unmodifiableList(senders), NumberStyle.valueOf(literal(values, "numberStyle")),
                pattern, source, zone, rule, Collections.unmodifiableList(fields));
    }

    private static Object readValue(JsonReader reader, int depth) throws IOException {
        require(depth <= 5 && !Thread.currentThread().isInterrupted());
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                Map<String, Object> values = new HashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    require(values.size() < 8);
                    String name = reader.nextName();
                    require(!values.containsKey(name));
                    values.put(name, readValue(reader, depth + 1));
                }
                reader.endObject();
                return values;
            case BEGIN_ARRAY:
                List<Object> entries = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    require(entries.size() < MAX_TEMPLATES);
                    entries.add(readValue(reader, depth + 1));
                }
                reader.endArray();
                return entries;
            case STRING: return reader.nextString();
            case NUMBER:
                String number = reader.nextString();
                require(number.matches("[1-9][0-9]{0,8}"));
                return Integer.valueOf(number);
            default: throw new IOException("Unexpected template value type");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) throws IOException {
        require(value instanceof Map);
        return (Map<String, Object>) value;
    }

    private static List<?> array(Object value, int max) throws IOException {
        require(value instanceof List && ((List<?>) value).size() <= max);
        return (List<?>) value;
    }

    private static void checkKeys(Map<String, Object> values, String... names) throws IOException {
        require(values.size() == names.length && values.keySet().containsAll(Arrays.asList(names)));
    }

    private static int integer(Map<String, Object> values, String key) throws IOException {
        require(values.get(key) instanceof Integer);
        return (Integer) values.get(key);
    }

    private static String literal(Map<String, Object> values, String key) throws IOException {
        return literal(values.get(key));
    }

    private static String literal(Object value) throws IOException {
        require(value instanceof String);
        String text = (String) value;
        require(!text.isEmpty() && text.length() <= MAX_LITERAL);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            require(!Character.isISOControl(c) || c == '\n' || c == '\r' || c == '\t');
        }
        return text;
    }

    private static void require(boolean valid) throws IOException {
        if (!valid) throw new IOException("Invalid template structure");
    }
}
