package com.example.travelplanner.controller;

import com.example.travelplanner.agent.OrchestratorAgent;
import com.example.travelplanner.data.DestinationDataRepository;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.budget.BudgetTier;
import com.example.travelplanner.model.request.TravelPlanRequest;
import com.example.travelplanner.model.response.TravelPlan;
import com.example.travelplanner.model.response.TravelPlanResponse;
import com.example.travelplanner.model.trace.AgentTrace;
import com.example.travelplanner.model.TravelRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TravelController.class)
class TravelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrchestratorAgent orchestratorAgent;

    @MockitoBean
    private DestinationDataRepository destinationDataRepository;

    @Test
    void postPlanReturns200() throws Exception {
        when(orchestratorAgent.plan(any())).thenReturn(buildMockResponse("conv-123"));
        when(destinationDataRepository.supportedDestinations()).thenReturn(List.of("Japan"));

        mockMvc.perform(post("/api/travel/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TravelPlanRequest("Plan 7 days in Japan", "conv-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-123"))
                .andExpect(jsonPath("$.travelPlan.destination").value("Japan"));
    }

    @Test
    void postFollowupReturns200() throws Exception {
        when(orchestratorAgent.followUp(any())).thenReturn(buildMockResponse("conv-123"));

        mockMvc.perform(post("/api/travel/followup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TravelPlanRequest("Make day 3 a food tour", "conv-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-123"));
    }

    @Test
    void getDestinationsReturns200() throws Exception {
        when(destinationDataRepository.supportedDestinations())
                .thenReturn(List.of("Japan", "France (Paris)", "Thailand"));

        mockMvc.perform(get("/api/travel/destinations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinations").isArray())
                .andExpect(jsonPath("$.destinations.length()").value(3));
    }

    @Test
    void getInfoReturns200WithAgentList() throws Exception {
        when(destinationDataRepository.supportedDestinations()).thenReturn(List.of("Japan"));

        mockMvc.perform(get("/api/travel/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agents").isArray())
                .andExpect(jsonPath("$.features").isArray());
    }

    @Test
    void quickPlanGeneratesConversationId() throws Exception {
        when(orchestratorAgent.plan(any())).thenReturn(buildMockResponse("auto-generated"));

        mockMvc.perform(get("/api/travel/plan/quick")
                        .param("query", "Plan 5 days in Paris"))
                .andExpect(status().isOk());
    }

    @Test
    void missingQueryParamReturns400() throws Exception {
        mockMvc.perform(get("/api/travel/plan/quick"))
                .andExpect(status().is4xxClientError());
    }

    private TravelPlanResponse buildMockResponse(String convId) {
        TravelRequest req = new TravelRequest("Japan", 7, 4, 5000.0, "April", List.of());
        BudgetBreakdown bd = new BudgetBreakdown(
                BudgetTier.MID, 3600, 160, 1120, 60, 1680, 400, 280, 565, 7645, -2645, false,
                "Over budget — retrying with BUDGET tier", "JPY", 1143725, 149.5);
        AgentTrace trace = new AgentTrace(List.of(), 12000L, 3, 1);
        TravelPlan plan = new TravelPlan("Japan", req, List.of(), bd, null,
                List.of("Remove shoes at temples"), List.of("Cherry blossom season"), trace);
        return new TravelPlanResponse(convId, plan, "Plan generated");
    }
}
