package lk.finora.api.smsformat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.StreamSupport;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.common.error.ApiException;
import lk.finora.api.smsformat.dto.SmsFormatsResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
class SmsFormatService {
    // D35 pins the exact audited V9 bundle. Editing a DB row cannot approve new parsing rules.
    private static final String APPROVED_NSB_V1_SHA256 = "be6aeec65f8642d95191b34bf61fc0746e10cc6178bd9b269a8315443e49bf17";

    private final SmsFormatRepository repository;
    private final JsonMapper mapper;

    SmsFormatService(SmsFormatRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    SmsFormatsResponse activeFormats() {
        var rows = repository.activeNsb();
        if (rows.isEmpty()) return new SmsFormatsResponse(1, List.of());
        String pattern = rows.get(0);
        if (!APPROVED_NSB_V1_SHA256.equals(sha256(pattern))) {
            throw new ApiException("SMS_FORMATS_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "SMS formats are unavailable");
        }
        JsonNode templates = mapper.readTree(pattern).get("templates");
        return new SmsFormatsResponse(1, StreamSupport.stream(templates.spliterator(), false).toList());
    }

    @Transactional
    public void setApprovedActive(boolean active) {
        var rows = repository.lockNsbV1();
        if (rows.isEmpty()) throw ApiException.notFound("Approved SMS format unavailable");
        if (active && !APPROVED_NSB_V1_SHA256.equals(sha256(rows.get(0)))) {
            throw new ApiException("CONFLICT", HttpStatus.CONFLICT, "SMS format is not approved");
        }
        if (active) repository.activate(1);
        else repository.deactivate(1);
    }

    List<Revision> revisions() { return repository.revisions(); }

    @Transactional
    public void draft(int revision) {
        if (revision < 2) throw ApiException.badRequest("Deployment revision must be greater than one");
        String definition = approved(repository.lockNsbV1());
        try {
            repository.draftNsb(revision, definition);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new ApiException("CONFLICT", HttpStatus.CONFLICT, "Deployment revision already exists");
        }
    }

    @Transactional
    public void publish(int revision) {
        approved(repository.lockNsbV1());
        approved(repository.revision(revision, false));
        repository.publish(revision);
    }

    @Transactional
    public void activate(int revision) {
        approved(repository.lockNsbV1());
        approved(repository.revision(revision, true));
        repository.activate(revision);
    }

    @Transactional
    public void deactivate(int revision) {
        repository.lockNsbV1();
        if (repository.revision(revision, true).isEmpty()) {
            throw ApiException.notFound("SMS format revision unavailable");
        }
        repository.deactivate(revision);
    }

    private static String approved(List<String> definitions) {
        if (definitions.isEmpty()) throw ApiException.notFound("SMS format revision unavailable");
        String definition = definitions.get(0);
        if (!APPROVED_NSB_V1_SHA256.equals(sha256(definition))) {
            throw new ApiException("CONFLICT", HttpStatus.CONFLICT, "SMS format is not approved");
        }
        return definition;
    }

    record Revision(int version, boolean published, boolean active) {}

    private static String sha256(String value) {
        try {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
