package com.ecom.search.index;

import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pulls the words a person typed out of a form's {@code json_data}.
 *
 * <p>This is the largest body of text in the application and none of it was
 * searchable: {@code academic_document.json_data} and
 * {@code position_document.json_data} hold the answers to roughly 400 distinct
 * form fields — course names, committee members, memo numbers, free-text
 * comments — and the old search looked only at request codes and applicant
 * names.
 *
 * <p><b>Values only, never keys.</b> {@code DocumentGenerationService.flattenMap}
 * keeps both because it is building {@code {{placeholder}}} substitutions. Here
 * the keys are field names like {@code applicant_current_pos}, which nobody
 * searches for and which would pollute the trigram index with English
 * identifiers that match far too much.
 *
 * <p>Arrays are walked too — the other flattener stops at maps, so repeated
 * groups such as the list of publications on form 1 would otherwise be dropped
 * entirely.
 */
@Component
public class JsonFormTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonFormTextExtractor.class);

    /**
     * Ceiling on the extracted text.
     *
     * <p>A filled form runs to a few kilobytes, so this is generous. It exists
     * because {@code json_data} is free text from a browser and nothing stops
     * someone pasting a thesis into a comment box; without a cap that lands in
     * the trigram index and every search pays for it.
     */
    static final int MAX_CHARS = 20_000;

    /** Below this, a value carries no search signal: "1", "-", "x", checkbox marks. */
    private static final int MIN_VALUE_LENGTH = 2;

    /**
     * Built here rather than injected, matching the rest of the application —
     * there is no {@code ObjectMapper} bean in this context, and the controllers
     * that read {@code json_data} each construct their own. Reading is
     * thread-safe once configured, so one instance serves every indexing thread.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param json a form's stored {@code json_data}, or null
     * @return the distinct text values joined by spaces, or null when there is
     *         nothing worth indexing
     */
    public String extract(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            // A form that cannot be parsed must not stop the document being
            // indexed on its label and code alone — a half-indexed row is far
            // better than a missing one.
            log.warn("ข้าม json_data ที่ parse ไม่ได้ ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }

        // Ordered so the result is stable, which keeps content_hash stable and
        // stops the reconciler rewriting rows that have not actually changed.
        Set<String> values = new LinkedHashSet<>();
        collect(root, values);

        if (values.isEmpty()) {
            return null;
        }

        String joined = String.join(" ", values);
        return joined.length() > MAX_CHARS ? joined.substring(0, MAX_CHARS) : joined;
    }

    private void collect(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject() || node.isArray()) {
            node.forEach(child -> collect(child, out));
            return;
        }
        // Booleans say nothing a search could use, and every form has dozens.
        if (node.isBoolean()) {
            return;
        }

        String value = node.asText("").trim().replaceAll("\\s+", " ");
        if (value.length() < MIN_VALUE_LENGTH) {
            return;
        }
        // Checkbox glyphs are stored as values on several forms.
        if ("☑".equals(value) || "☐".equals(value)) {
            return;
        }
        out.add(value);
    }
}
