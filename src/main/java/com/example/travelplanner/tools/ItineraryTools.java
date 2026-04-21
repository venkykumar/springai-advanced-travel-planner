package com.example.travelplanner.tools;

import com.example.travelplanner.data.DestinationDataRepository;
import com.example.travelplanner.model.destination.Attraction;
import com.example.travelplanner.model.destination.NeighborhoodGroup;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ItineraryTools {

    private final DestinationDataRepository destinationRepo;

    public ItineraryTools(DestinationDataRepository destinationRepo) {
        this.destinationRepo = destinationRepo;
    }

    @Tool(description = "Get attractions grouped by neighbourhood for efficient day planning — reduces travel time between sites")
    public String getAttractionsByNeighbourhood(String destination) {
        List<NeighborhoodGroup> groups = destinationRepo.getNeighborhoodGroups(destination);
        if (groups.isEmpty()) return "No neighbourhood groupings found for: " + destination;
        return groups.stream()
                .map(g -> "📍 " + g.name() + ":\n" + g.attractions().stream()
                        .map(a -> "  - " + a)
                        .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));
    }

    @Tool(description = "Get full attraction details including entry fees and visit duration for a destination — needed for day planning")
    public String getAllAttractionDetails(String destination) {
        List<Attraction> attractions = destinationRepo.getAttractions(destination);
        if (attractions.isEmpty()) return "No attraction data for: " + destination;
        return attractions.stream()
                .map(a -> "• %s (%s, %s)\n  %s\n  Entry: $%.0f | Duration: %d min | Must-see: %s".formatted(
                        a.name(), a.neighborhood(), a.region(),
                        a.description(),
                        a.entryFeeUSD(), a.durationMinutes(),
                        a.mustSee() ? "Yes" : "No"))
                .collect(Collectors.joining("\n\n"));
    }

    @Tool(description = "Get restaurant recommendations for a destination and neighbourhood at a specific budget tier (BUDGET/MID/LUXURY)")
    public String getRestaurantRecommendations(String destination, String neighbourhood, String tier) {
        return switch (destination.toLowerCase()) {
            case "japan" -> getJapanRestaurants(neighbourhood, tier);
            case "france (paris)" -> getParisRestaurants(neighbourhood, tier);
            case "italy (rome & florence)" -> getItalyRestaurants(neighbourhood, tier);
            case "thailand" -> getThailandRestaurants(neighbourhood, tier);
            case "spain (barcelona)" -> getBarcelonaRestaurants(neighbourhood, tier);
            default -> "Generic restaurant recommendations: look for places with high local footfall and menus in the local language.";
        };
    }

    @Tool(description = "Get estimated travel time in minutes between two neighbourhoods or areas in a destination")
    public String estimateTravelTime(String destination, String fromArea, String toArea) {
        // Simplified estimates based on destination knowledge
        int minutes = switch (destination.toLowerCase()) {
            case "japan" -> estimateJapanTravel(fromArea, toArea);
            case "france (paris)" -> estimateParisTravel(fromArea, toArea);
            default -> 30;
        };
        return "Estimated travel time from " + fromArea + " to " + toArea + " in " + destination
                + ": approximately " + minutes + " minutes by public transport.";
    }

    @Tool(description = "Get local food specialties and must-try dishes for a destination")
    public String getMustTryFood(String destination) {
        return switch (destination.toLowerCase()) {
            case "japan" -> """
                Must-try food in Japan:
                • Ramen — regional varieties (Sapporo miso, Tokyo shoyu, Hakata tonkotsu)
                • Sushi/Sashimi — Tsukiji Outer Market for breakfast sushi
                • Yakitori — grilled chicken skewers at izakayas
                • Ramen at convenience stores (7-Eleven, Lawson) is surprisingly excellent
                • Matcha everything — ice cream, lattes, Kit Kats
                • Takoyaki (Osaka) — octopus balls from street stalls
                • Kaiseki — multi-course traditional Japanese dinner (worth splurging once)""";
            case "france (paris)" -> """
                Must-try food in France (Paris):
                • Croissant and café au lait at a traditional boulangerie
                • Steak frites with a glass of Bordeaux at a classic brasserie
                • French onion soup (gratinée) — warming and iconic
                • Crêpes at a street stand near the Eiffel Tower
                • Macarons from Ladurée or Pierre Hermé
                • Wine by the carafe (pichet) — ask for house wine (vin de la maison)
                • Cheese platter (plateau de fromages) after dinner""";
            case "italy (rome & florence)" -> """
                Must-try food in Italy:
                • Cacio e Pepe — Rome's simplest, most perfect pasta
                • Supplì (Rome) — fried rice balls with mozzarella
                • Ribollita (Florence) — hearty Tuscan bread and vegetable soup
                • Bistecca alla Fiorentina — Florence's legendary T-bone steak
                • Gelato from a gelateria with covered tubs (not fluorescent piled-up mounds)
                • Espresso standing at the bar — the authentic Roman way
                • Tiramisù — invented in Italy, best eaten here""";
            case "thailand" -> """
                Must-try food in Thailand:
                • Pad Thai — stir-fried noodles from a street wok (best from market stalls)
                • Tom Yum Goong — hot and sour shrimp soup
                • Mango Sticky Rice — simple, perfect, everywhere
                • Som Tam (green papaya salad) — choose your spice level carefully
                • Khao Man Gai — poached chicken on rice, humble and delicious
                • Thai iced tea (Cha Yen) — sweet, milky, essential
                • Massaman curry — rich, slow-cooked, and unique to Thailand""";
            case "spain (barcelona)" -> """
                Must-try food in Spain (Barcelona):
                • Pan con Tomate — bread rubbed with tomato and olive oil, the Catalan staple
                • Patatas Bravas — fried potatoes with spicy or aioli sauce
                • Jamón Ibérico — cured ham, order a raciones plate at any tapas bar
                • Paella Valenciana — go to the port area for genuine versions
                • Crema Catalana — Spain's answer to crème brûlée
                • Cava — Catalan sparkling wine, excellent value
                • Pintxos (from the Basque Country, popular in Barcelona bars)""";
            default -> "Try local street food, visit covered markets, and eat where locals eat.";
        };
    }

    private String getJapanRestaurants(String neighbourhood, String tier) {
        return switch (tier.toUpperCase()) {
            case "BUDGET" -> """
                Budget restaurants in Japan (%s):
                • Ichiran Ramen — solo ramen booth experience, ~$10/bowl
                • Yoshinoya / Sukiya — gyudon (beef bowl) for $4-6
                • Any convenience store (7-Eleven, Lawson) — quality bento ~$5
                • Gyoza no Ohsho — dumplings and rice sets for $7-9
                • Standing soba/udon shops at train stations — $5-8""".formatted(neighbourhood);
            case "MID" -> """
                Mid-range restaurants in Japan (%s):
                • Tempura Kondo — excellent tempura set lunch ~$30
                • Tonki (Tokyo) — legendary tonkatsu since 1939, ~$20
                • Kyubey (Ginza) — classic sushi, lunch omakase ~$60
                • Izakaya evening — grilled skewers and sake, ~$25-35/person""".formatted(neighbourhood);
            default -> """
                Luxury restaurants in Japan (%s):
                • Sukiyabashi Jiro Honten — world-famous sushi, reserve 2+ months ahead, $300+
                • Kikunoi (Kyoto) — 3-star Michelin kaiseki, $200+
                • Ryugin — modern Japanese cuisine, $250+""".formatted(neighbourhood);
        };
    }

    private String getParisRestaurants(String neighbourhood, String tier) {
        return switch (tier.toUpperCase()) {
            case "BUDGET" -> """
                Budget restaurants in Paris (%s):
                • Bouillon Chartier — classic French brasserie, ~$15 for full meal
                • L'As du Fallafel (Marais) — best falafel in Paris, ~$8
                • Any neighbourhood boulangerie — sandwich jambon-beurre ~$5
                • Daily specials (plat du jour) at local cafés — $12-15 including drink""".formatted(neighbourhood);
            case "MID" -> """
                Mid-range restaurants in Paris (%s):
                • Chez Janou (Marais) — Provençal cuisine, ~$35/person
                • Le Comptoir du Relais — French bistro classics, ~$40
                • Clown Bar — natural wine bar with creative dishes, ~$45
                • Septime — book 3+ weeks ahead, modern bistro, ~$60""".formatted(neighbourhood);
            default -> """
                Luxury restaurants in Paris (%s):
                • Guy Savoy — 3-Michelin-star, $400+
                • Le Grand Véfour — historic, Napoleon's favourite, $300+
                • Alain Ducasse at Plaza Athénée — $450+""".formatted(neighbourhood);
        };
    }

    private String getItalyRestaurants(String neighbourhood, String tier) {
        return switch (tier.toUpperCase()) {
            case "BUDGET" -> """
                Budget restaurants in Italy (%s):
                • Supplì Roma — fried rice balls and pizza al taglio, ~$5-8
                • Trattoria da Enzo al 29 (Trastevere) — classic Roman, ~$20
                • Mercato Centrale (Florence) — fresh market food hall, ~$10-15
                • Any bar for standing espresso + cornetto, ~$2""".formatted(neighbourhood);
            case "MID" -> """
                Mid-range restaurants in Italy (%s):
                • Da Enzo al 29 (Rome) — cacio e pepe and carbonara, ~$30
                • Trattoria Mario (Florence) — communal tables, Florentine classics, ~$25
                • Enoteca Corsi (Rome) — wine bar lunch, ~$20
                • Buca Mario (Florence) — historic restaurant since 1886, ~$45""".formatted(neighbourhood);
            default -> """
                Luxury restaurants in Italy (%s):
                • La Pergola (Rome) — 3-Michelin-star with panoramic views, $300+
                • Enoteca Pinchiorri (Florence) — legendary wine cellar, $250+
                • Il Pagliaccio (Rome) — 2-Michelin-star, $200+""".formatted(neighbourhood);
        };
    }

    private String getThailandRestaurants(String neighbourhood, String tier) {
        return switch (tier.toUpperCase()) {
            case "BUDGET" -> """
                Budget restaurants in Thailand (%s):
                • Or Tor Kor Market (Bangkok) — premium street food, ~$3-6
                • Jay Fai (Bangkok) — Michelin-star street food, ~$25 (worth it once)
                • Night Bazaar stalls (Chiang Mai) — pad thai and som tam, ~$2-4
                • 7-Eleven for breakfast — surprisingly tasty, ~$2""".formatted(neighbourhood);
            case "MID" -> """
                Mid-range restaurants in Thailand (%s):
                • Bo.lan (Bangkok) — authentic Thai cuisine, ~$35
                • Gaggan Anand (Bangkok) — progressive Indian-Thai, ~$80 (book ahead)
                • Khao Soi Islam (Chiang Mai) — best khao soi in the north, ~$5
                • Blue Elephant (Bangkok) — royal Thai cuisine, ~$50""".formatted(neighbourhood);
            default -> """
                Luxury restaurants in Thailand (%s):
                • Gaggan Anand — top-50 world restaurant, $150+
                • Mandarin Oriental Riverside dining — $100+
                • Nahm (Bangkok) — classic Thai fine dining, $80+""".formatted(neighbourhood);
        };
    }

    private String getBarcelonaRestaurants(String neighbourhood, String tier) {
        return switch (tier.toUpperCase()) {
            case "BUDGET" -> """
                Budget restaurants in Barcelona (%s):
                • Bar Cañete — tapas and vermouth, ~$15/person
                • Menú del día anywhere — 3 courses + wine for €12-15 at lunch
                • La Cova Fumada (Barceloneta) — birthplace of the bombas, ~$15
                • Any neighbourhood bar for pintxos — €1.50/piece""".formatted(neighbourhood);
            case "MID" -> """
                Mid-range restaurants in Barcelona (%s):
                • El Xampanyet (El Born) — classic Catalan tapas and cava, ~$30
                • Bar Mut (Eixample) — excellent vermouth and tapas, ~$40
                • Cervecería Catalana — popular tapas bar, ~$35
                • La Mar Salada (Barceloneta) — fresh seafood, ~$50""".formatted(neighbourhood);
            default -> """
                Luxury restaurants in Barcelona (%s):
                • Disfrutar — 3-Michelin-star creative cuisine, $250+ (book 2+ months ahead)
                • Tickets (Albert Adrià) — avant-garde tapas, $150+
                • ABaC — 2-Michelin-star, $200+""".formatted(neighbourhood);
        };
    }

    private int estimateJapanTravel(String from, String to) {
        boolean crossCity = (from.contains("Tokyo") && to.contains("Kyoto"))
                || (from.contains("Kyoto") && to.contains("Tokyo"))
                || (from.contains("Osaka") && to.contains("Tokyo"));
        if (crossCity) return 160;
        boolean crossDistrict = !from.split(",")[0].equals(to.split(",")[0]);
        return crossDistrict ? 45 : 20;
    }

    private int estimateParisTravel(String from, String to) {
        if (from.toLowerCase().contains("versailles") || to.toLowerCase().contains("versailles")) return 50;
        return 25;
    }
}
