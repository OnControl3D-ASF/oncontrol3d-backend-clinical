package com.oncontrol.oncontrolbackend.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld.Actor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Thin HTTP client over MockMvc: every call carries the actor's JWT through the real security chain. */
@Component
public class Api {

    private final MockMvc mockMvc;
    private final ObjectMapper json;
    private final ScenarioWorld world;

    public Api(MockMvc mockMvc, ObjectMapper json, ScenarioWorld world) {
        this.mockMvc = mockMvc;
        this.json = json;
        this.world = world;
    }

    public MvcResult get(Actor actor, String uri, Object... vars) {
        return perform(actor, MockMvcRequestBuilders.get(uri, vars));
    }

    public MvcResult post(Actor actor, Object body, String uri, Object... vars) {
        return perform(actor, withJson(MockMvcRequestBuilders.post(uri, vars), body));
    }

    public MvcResult patch(Actor actor, Object body, String uri, Object... vars) {
        return perform(actor, withJson(MockMvcRequestBuilders.patch(uri, vars), body));
    }

    public JsonNode body(MvcResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException("Response body is not JSON", e);
        }
    }

    private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object body) {
        try {
            return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcResult perform(Actor actor, MockHttpServletRequestBuilder request) {
        try {
            MvcResult result = mockMvc.perform(
                    request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.token())).andReturn();
            world.lastResult(result);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
