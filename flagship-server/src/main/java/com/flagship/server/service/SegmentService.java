package com.flagship.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flagship.server.api.SegmentRequest;
import com.flagship.server.api.SegmentResponse;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.FlagEnvironmentConfig;
import com.flagship.server.entity.Project;
import com.flagship.server.entity.Segment;
import com.flagship.server.push.FlagsChangedEvent;
import com.flagship.server.repository.FlagEnvironmentConfigRepository;
import com.flagship.server.repository.FlagRepository;
import com.flagship.server.repository.ProjectRepository;
import com.flagship.server.repository.SegmentRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Named, reusable rule conditions. Flags store a reference ({"type":"segment","key":"..."});
 * before evaluation the reference is replaced by the segment's condition (the "expand" methods),
 * so core, codec and starter need no knowledge of segments.
 */
@Service
public class SegmentService {

    private static final Pattern KEY = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final FlagRepository flags;
    private final FlagEnvironmentConfigRepository configs;
    private final FlagConverter converter;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public SegmentService(ProjectRepository projects, SegmentRepository segments, FlagRepository flags,
                          FlagEnvironmentConfigRepository configs, FlagConverter converter,
                          ObjectMapper mapper, ApplicationEventPublisher events) {
        this.projects = projects;
        this.segments = segments;
        this.flags = flags;
        this.configs = configs;
        this.converter = converter;
        this.mapper = mapper;
        this.events = events;
    }

    // ---------- API ----------

    @Transactional(readOnly = true)
    public List<SegmentResponse> list(String projectKey) {
        Project project = findProject(projectKey);
        return segments.findByProjectOrderByKeyAsc(project).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SegmentResponse get(String projectKey, String key) {
        return toResponse(find(findProject(projectKey), key));
    }

    /** Creates the segment or replaces it. Apps are told, because every flag using it may change. */
    @Transactional
    public SegmentResponse put(String projectKey, String key, SegmentRequest req) {
        Project project = findProject(projectKey);
        if (key.length() > 100 || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "segment key must be lowercase letters, digits and single dashes, e.g. south-asia");
        }
        JsonNode condition = req.condition();
        if (condition == null || !condition.isObject()) {
            throw new IllegalArgumentException("condition must be a JSON object, e.g. "
                    + "{\"type\":\"condition\",\"attribute\":\"country\",\"operator\":\"IN\",\"values\":[\"BD\"]}");
        }
        if (containsReference(condition)) {
            throw new IllegalArgumentException("A segment cannot use another segment");
        }
        // Check it the way a flag rule is checked: the same codec parses it.
        ObjectNode rule = mapper.createObjectNode();
        rule.put("id", "segment");
        rule.put("variation", "x");
        rule.set("condition", condition);
        converter.rulesFromJson(mapper.createArrayNode().add(rule).toString());

        Segment segment = segments.findByProjectAndKey(project, key).orElseGet(() -> {
            Segment created = new Segment();
            created.setProject(project);
            created.setKey(key);
            return created;
        });
        segment.setName(req.name().trim());
        segment.setConditionJson(condition.toString());
        segment.setUpdatedAt(Instant.now());
        segments.save(segment);

        events.publishEvent(new FlagsChangedEvent(projectKey, "segment:" + key, null));
        return toResponse(segment);
    }

    @Transactional
    public void delete(String projectKey, String key) {
        Project project = findProject(projectKey);
        Segment segment = find(project, key);

        List<FlagEntity> all = flags.findByProjectOrderByKeyAsc(project);
        if (!all.isEmpty()) {
            for (FlagEnvironmentConfig config : configs.findByFlagIn(all)) {
                String rules = config.getRulesJson();
                if (rules != null && rules.contains("\"segment\"") && references(parse(rules), key)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Segment '" + key + "' is still used by flag '" + config.getFlag().getKey()
                                    + "' in " + config.getEnvironment());
                }
            }
        }
        segments.delete(segment);
        events.publishEvent(new FlagsChangedEvent(projectKey, "segment:" + key, null));
    }

    // ---------- expanding references ----------

    /** key -> condition tree, for every segment of the project. */
    @Transactional(readOnly = true)
    public Map<String, JsonNode> loadConditions(String projectKey) {
        Project project = findProject(projectKey);
        Map<String, JsonNode> conditions = new HashMap<>();
        for (Segment s : segments.findByProjectOrderByKeyAsc(project)) {
            conditions.put(s.getKey(), parse(s.getConditionJson()));
        }
        return conditions;
    }

    /**
     * Rules JSON text with every segment reference replaced by the segment's condition.
     * strict: an unknown segment is an error (saving); otherwise it never matches (reading).
     */
    @Transactional(readOnly = true)
    public String expandText(String projectKey, String rulesJson, boolean strict) {
        if (rulesJson == null || !rulesJson.contains("\"segment\"")) {
            return rulesJson;
        }
        return expandTree(parse(rulesJson), loadConditions(projectKey), strict).toString();
    }

    public JsonNode expandTree(JsonNode node, Map<String, JsonNode> conditions, boolean strict) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            if ("segment".equals(node.path("type").asText())) {
                String key = node.path("key").asText();
                JsonNode condition = conditions.get(key);
                if (condition == null) {
                    if (strict) {
                        throw new IllegalArgumentException("Unknown segment '" + key + "'");
                    }
                    return neverMatches();
                }
                return condition.deepCopy();
            }
            ObjectNode copy = mapper.createObjectNode();
            node.fields().forEachRemaining(e -> copy.set(e.getKey(), expandTree(e.getValue(), conditions, strict)));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = mapper.createArrayNode();
            for (JsonNode child : node) {
                copy.add(expandTree(child, conditions, strict));
            }
            return copy;
        }
        return node;
    }

    /** True if the tree uses any segment reference. */
    public boolean containsReference(JsonNode node) {
        return references(node, null);
    }

    // ---------- helpers ----------

    /** key == null: any reference; otherwise only references to that segment. */
    private static boolean references(JsonNode node, String key) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            if ("segment".equals(node.path("type").asText())
                    && (key == null || key.equals(node.path("key").asText()))) {
                return true;
            }
            for (JsonNode child : node) {
                if (references(child, key)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (references(child, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A condition no user matches (a missing attribute never matches). */
    private JsonNode neverMatches() {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "condition");
        node.put("attribute", "__missing_segment__");
        node.put("operator", "EQUALS");
        node.putArray("values").add("x");
        return node;
    }

    private Project findProject(String key) {
        return projects.findByKey(key).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + key + "'"));
    }

    private Segment find(Project project, String key) {
        return segments.findByProjectAndKey(project, key).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No segment with key '" + key + "'"));
    }

    private SegmentResponse toResponse(Segment s) {
        return new SegmentResponse(s.getKey(), s.getName(), parse(s.getConditionJson()), s.getUpdatedAt());
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is invalid", e);
        }
    }
}