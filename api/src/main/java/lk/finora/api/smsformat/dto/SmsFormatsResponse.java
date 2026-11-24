package lk.finora.api.smsformat.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

public record SmsFormatsResponse(int schemaVersion, List<JsonNode> templates) {}
