package com.cherrycore.shared.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StorageConfigTest {

    @Test
    void publicPolicyCoversCompanyAndApplicationLogosOnly() {
        JsonNode policy = JsonMapper.builder().build().readTree(StorageConfig.publicReadPolicy("media"));
        JsonNode statement = policy.get("Statement").get(0);

        assertThat(statement.get("Action").get(0).asString()).isEqualTo("s3:GetObject");
        assertThat(statement.get("Resource")).extracting(JsonNode::asString).containsExactlyInAnyOrder(
                "arn:aws:s3:::media/companies/*/public/*",
                "arn:aws:s3:::media/applications/*/public/*");
    }
}
