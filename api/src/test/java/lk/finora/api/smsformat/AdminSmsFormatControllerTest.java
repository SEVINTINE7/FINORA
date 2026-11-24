package lk.finora.api.smsformat;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminSmsFormatControllerTest extends IntegrationTest {
    private static final String ADMIN_FORMAT = "/api/v1/admin/sms-formats/NSB/1";
    private static final String REVISIONS = "/api/v1/admin/sms-formats/NSB/revisions";

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void onlyAdminCanToggleApprovedFormatWithoutChangingUserContract() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var user = tokenFor(createUser(Role.USER));
        activate(tokenFor(createUser(Role.STAFF)), false).andExpect(status().isForbidden());
        activate(user, false).andExpect(status().isForbidden());
        activate(admin, false).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.templates.length()").value(0));
        activate(admin, true).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.templates.length()").value(3));
    }

    @Test void publishesAndRollsBackImmutableDeploymentRevisions() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var staff = tokenFor(createUser(Role.STAFF));
        var user = tokenFor(createUser(Role.USER));
        String initial = mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andReturn().getResponse().getContentAsString();
        command(staff, "/2").andExpect(status().isForbidden());
        command(user, "/2/publish").andExpect(status().isForbidden());
        command(admin, "/2").andExpect(status().isCreated());
        command(admin, "/2/activate").andExpect(status().isNotFound());
        command(admin, "/2/validate").andExpect(status().isOk());
        command(admin, "/2/publish").andExpect(status().isNoContent());
        command(admin, "/2/publish").andExpect(status().isNotFound());
        command(admin, "/2/activate").andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(2, activeRevision());
        mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(content().string(initial));
        command(admin, "/1/activate").andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(1, activeRevision());
        mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(content().string(initial));
        command(admin, "/3").andExpect(status().isCreated());
        jdbc.update("UPDATE sms_formats SET pattern = ? WHERE bank_code = 'NSB' AND version = 3", "invalid fixture");
        command(admin, "/3/publish").andExpect(status().isConflict());
    }

    @Test void changedPublishedDefinitionCannotActivateOrReplaceTheCurrentBundle() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var user = tokenFor(createUser(Role.USER));
        var request = get("/api/v1/sms-formats").header("Authorization", "Bearer " + user);
        String initial = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        command(admin, "/2").andExpect(status().isCreated());
        command(admin, "/2/validate").andExpect(status().isOk());
        command(admin, "/2/publish").andExpect(status().isNoContent());
        jdbc.update("UPDATE sms_formats SET pattern = CONCAT(pattern, ' ') WHERE bank_code = 'NSB' AND version = 2");

        command(admin, "/2/activate").andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(1, activeRevision());
        mockMvc.perform(request).andExpect(status().isOk()).andExpect(content().string(initial));
    }

    @Test void draftEditsInvalidateEvidenceAndApprovalRemainsSeparateFromFixtures() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var staff = tokenFor(createUser(Role.STAFF));
        command(admin, "/2").andExpect(status().isCreated());
        command(admin, "/2/publish").andExpect(status().isConflict());
        command(staff, "/2/validate").andExpect(status().isForbidden());
        command(admin, "/2/validate").andExpect(status().isOk())
                .andExpect(jsonPath("$.samples", org.hamcrest.Matchers.contains(true, true, true)));
        String original = jdbc.queryForObject(
                "SELECT pattern FROM sms_formats WHERE bank_code = 'NSB' AND version = 1", String.class);
        edit(staff, original).andExpect(status().isForbidden());
        edit(admin, "{}").andExpect(status().isBadRequest());
        edit(admin, original).andExpect(status().isNoContent());
        command(admin, "/2/publish").andExpect(status().isConflict());
        var changedBoundary = original.replace(". ATM POS Transaction.", ". Synthetic POS boundary.");
        edit(admin, changedBoundary).andExpect(status().isNoContent());
        command(admin, "/2/validate").andExpect(status().isOk())
                .andExpect(jsonPath("$.samples", org.hamcrest.Matchers.contains(false, true, true)));
        command(admin, "/2/publish").andExpect(status().isConflict());
        edit(admin, original + " ").andExpect(status().isNoContent());
        command(admin, "/2/validate").andExpect(status().isOk())
                .andExpect(jsonPath("$.samples", org.hamcrest.Matchers.contains(true, true, true)));
        command(admin, "/2/publish").andExpect(status().isConflict());
        edit(admin, original).andExpect(status().isNoContent());
        command(admin, "/2/validate").andExpect(status().isOk());
        command(admin, "/2/publish").andExpect(status().isNoContent());
        edit(admin, original).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions edit(String token, String definition) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(REVISIONS + "/2")
                .header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json(java.util.Map.of("definition", definition))));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousActivationKeepsOneApprovedRevisionActive() throws Exception {
        var user = createUser(Role.ADMIN);
        var admin = tokenFor(user);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int revision : new int[]{102, 103}) {
                command(admin, "/" + revision).andExpect(status().isCreated());
                command(admin, "/" + revision + "/validate").andExpect(status().isOk());
                command(admin, "/" + revision + "/publish").andExpect(status().isNoContent());
            }
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.function.IntFunction<java.util.concurrent.Callable<Integer>> activate = revision -> () -> {
                ready.countDown();
                org.junit.jupiter.api.Assertions.assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                return command(admin, "/" + revision + "/activate").andReturn().getResponse().getStatus();
            };
            var first = pool.submit(activate.apply(102));
            var second = pool.submit(activate.apply(103));
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            org.junit.jupiter.api.Assertions.assertEquals(204, first.get(15, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertEquals(204, second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertTrue(java.util.Set.of(102, 103).contains(activeRevision()));
        } finally {
            pool.shutdownNow();
            try {
                command(admin, "/1/activate").andExpect(status().isNoContent());
            } finally {
                jdbc.update("DELETE FROM sms_formats WHERE bank_code = 'NSB' AND version IN (102,103)");
                userRepository.deleteById(user.getId());
            }
        }
    }

    private Integer activeRevision() {
        return jdbc.queryForObject("SELECT version FROM sms_formats WHERE bank_code = 'NSB' AND active", Integer.class);
    }

    @Test void onlyAdminCanDiscardUnpublishedDraftsWithoutChangingActiveFormat() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var staff = tokenFor(createUser(Role.STAFF));
        var user = tokenFor(createUser(Role.USER));
        command(admin, "/2").andExpect(status().isCreated());
        discard(staff, 2).andExpect(status().isForbidden());
        discard(user, 2).andExpect(status().isForbidden());
        discard(admin, 1).andExpect(status().isNotFound());
        discard(admin, 2).andExpect(status().isNoContent());
        discard(admin, 2).andExpect(status().isNotFound());
        command(admin, "/2").andExpect(status().isCreated());
        command(admin, "/2/validate").andExpect(status().isOk());
        command(admin, "/2/publish").andExpect(status().isNoContent());
        discard(admin, 2).andExpect(status().isNotFound());
        command(admin, "/2/activate").andExpect(status().isNoContent());
        discard(admin, 2).andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(2, activeRevision());
    }

    private org.springframework.test.web.servlet.ResultActions discard(String token, int revision) throws Exception {
        return mockMvc.perform(delete(REVISIONS + "/" + revision).header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions command(String token, String path) throws Exception {
        return mockMvc.perform(post(REVISIONS + path).header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions activate(String token, boolean active) throws Exception {
        return mockMvc.perform(patch(ADMIN_FORMAT).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json(java.util.Map.of("active", active))));
    }
}
