package com.example.travelplanner.agent;

import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.itinerary.DayPlan;
import com.example.travelplanner.tools.ItineraryTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItineraryBuilderAgent {

    private static final Logger log = LoggerFactory.getLogger(ItineraryBuilderAgent.class);

    private static final String SYSTEM_PROMPT = """
            You are a master travel itinerary builder. Your job is to create detailed, day-by-day
            travel plans that are geographically efficient, well-paced, and budget-appropriate.

            Rules for every itinerary:
            1. Group geographically close attractions on the same day to minimise transit time
            2. Use the neighbourhood groupings tool to plan efficient days
            3. Include 3-4 activities per day — do not over-schedule; leave breathing room
            4. Day 1: account for arrival and jet lag — lighter schedule, nearby hotel area
            5. Final day: end by early afternoon to allow for departure
            6. Match restaurant recommendations to the budget tier
            7. Include must-try local food for at least one meal per day
            8. Provide estimated daily spend per person

            Return a JSON array of DayPlan objects. Each DayPlan must have:
            - dayNumber (int)
            - theme (string — e.g., "Tokyo Highlights & Asakusa")
            - region (string)
            - morningActivities (list of strings, each describing activity + time + cost hint)
            - afternoonActivities (list of strings)
            - eveningActivities (list of strings)
            - lunchRecommendation (string — restaurant name + cuisine + price range)
            - dinnerRecommendation (string — restaurant name + cuisine + price range)
            - accommodation (string — area + hotel tier)
            - estimatedDailySpendPerPerson (double — USD)
            - notes (string — special tips for that day)
            """;

    private final ChatClient chatClient;
    private final ItineraryTools tools;

    public ItineraryBuilderAgent(ChatModel chatModel, ItineraryTools tools) {
        this.tools = tools;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    public List<DayPlan> buildItinerary(String destination, int days, BudgetBreakdown budget,
                                         String destinationResearch) {
        log.debug("ItineraryBuilderAgent building {} day itinerary for {}", days, destination);

        String prompt = """
                Build a %d-day itinerary for %s at the %s budget tier.

                Budget context:
                - Accommodation: $%.0f/night
                - Meals: $%.0f/person/day
                - Activities: ~$%.0f total for group

                Destination research summary:
                %s

                Use the available tools to:
                1. Get neighbourhood groupings for efficient day planning
                2. Get all attraction details with entry fees
                3. Get restaurant recommendations for the %s tier
                4. Get must-try food specialties

                Return a JSON array of %d DayPlan objects following the schema described in your system prompt.
                """.formatted(
                days, destination, budget.tier().name(),
                budget.accommodationPerNight(),
                budget.mealsPerDayPerPerson(),
                budget.activitiesTotal(),
                destinationResearch,
                budget.tier().name(),
                days);

        return chatClient.prompt()
                .user(prompt)
                .tools(tools)
                .call()
                .entity(new org.springframework.core.ParameterizedTypeReference<List<DayPlan>>() {});
    }
}
