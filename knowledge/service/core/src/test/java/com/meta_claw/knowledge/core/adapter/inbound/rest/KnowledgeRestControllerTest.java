package com.meta_claw.knowledge.core.adapter.inbound.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/sources 注册来源")
    void registerSource() throws Exception {
        String json = "{"
                + "\"roleName\":\"finance_advisor\","
                + "\"sourceType\":\"git_repository\","
                + "\"location\":\"src/test/resources/samples/sample-repo\","
                + "\"displayName\":\"sample_repo\""
                + "}";

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRecord.sourceId").exists())
                .andExpect(jsonPath("$.snapshotRecord.snapshotId").exists());
    }
}
