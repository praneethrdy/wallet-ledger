package com.example.wallet.testsupport;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.example.wallet.testsupport.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Thin MockMvc helper: authenticated JSON calls with an optional Idempotency-Key. */
public class Api {

    private final MockMvc mvc;
    private final ObjectMapper json;

    public Api(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public ResultActions get(TestUser user, String path, Object... vars) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.get(path, vars), user));
    }

    /** POST with a fresh random Idempotency-Key. */
    public ResultActions post(TestUser user, String path, Object body) throws Exception {
        return post(user, path, body, UUID.randomUUID().toString());
    }

    /** POST with the given Idempotency-Key (null = header omitted). */
    public ResultActions post(TestUser user, String path, Object body, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder req = auth(MockMvcRequestBuilders.post(path), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : json.writeValueAsString(body));
        if (idempotencyKey != null) {
            req.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(req);
    }

    public JsonNode body(ResultActions result) throws Exception {
        String content = result.andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? null : json.readTree(content);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, TestUser user) {
        return user == null ? req : req.header("Authorization", user.bearer());
    }
}
