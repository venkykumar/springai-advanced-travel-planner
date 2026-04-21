package com.example.travelplanner.agent;

import com.example.travelplanner.tools.DestinationTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class DestinationResearchAgent {

    private static final Logger log = LoggerFactory.getLogger(DestinationResearchAgent.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert travel researcher with deep knowledge of world destinations.

            For any destination research request, you MUST use the available tools to gather:
            1. The main regions/areas travellers should know about
            2. Must-see attractions with entry fees and visit durations
            3. Seasonal tips for the specified travel month (weather, crowds, key events)
            4. Cultural tips and local etiquette
            5. Neighbourhood groupings for efficient day planning
            6. Any relevant context from the travel knowledge base

            Always call ALL relevant tools — do not skip any. Combine the results into a
            comprehensive research summary. Be specific about costs, times, and practical details.
            Structure your output clearly with headings for each section.
            """;

    private final ChatClient chatClient;
    private final DestinationTools tools;

    public DestinationResearchAgent(ChatModel chatModel, DestinationTools tools) {
        this.tools = tools;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    public String research(String destination, String travelMonth) {
        log.debug("DestinationResearchAgent researching: {} in {}", destination, travelMonth);
        String prompt = """
                Research the destination "%s" for travel in %s.
                Use all available tools to gather regions, attractions, seasonal tips, cultural tips,
                and neighbourhood groupings. Produce a comprehensive research summary.
                """.formatted(destination, travelMonth);

        return chatClient.prompt()
                .user(prompt)
                .tools(tools)
                .call()
                .content();
    }
}
