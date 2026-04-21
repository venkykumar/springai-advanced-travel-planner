package com.example.travelplanner.agent;

import com.example.travelplanner.tools.LogisticsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class LogisticsAgent {

    private static final Logger log = LoggerFactory.getLogger(LogisticsAgent.class);

    private static final String SYSTEM_PROMPT = """
            You are a travel logistics expert specialising in practical trip planning.

            For any logistics request, use the available tools to gather:
            1. Round-trip flight cost estimates for the given number of travellers and month
            2. All local transportation options with costs and tips
            3. Visa and entry requirements for US passport holders
            4. Airport transfer options and costs

            Always call ALL tools. Provide actionable, practical information.
            Highlight the most important logistics tip for each category.
            Include total estimated flight cost prominently — it is the largest cost item.
            """;

    private final ChatClient chatClient;
    private final LogisticsTools tools;

    public LogisticsAgent(ChatModel chatModel, LogisticsTools tools) {
        this.tools = tools;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    public String analyze(String destination, int travelers, String travelMonth) {
        log.debug("LogisticsAgent analyzing: {} for {} travelers in {}", destination, travelers, travelMonth);
        String prompt = """
                Provide complete logistics analysis for %d traveler(s) visiting %s in %s.
                Include: flight estimates, local transport options, visa requirements, and airport transfers.
                Make sure to call all available tools.
                """.formatted(travelers, destination, travelMonth);

        return chatClient.prompt()
                .user(prompt)
                .tools(tools)
                .call()
                .content();
    }
}
