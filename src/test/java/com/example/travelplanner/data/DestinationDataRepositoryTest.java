package com.example.travelplanner.data;

import com.example.travelplanner.model.destination.Attraction;
import com.example.travelplanner.model.destination.NeighborhoodGroup;
import com.example.travelplanner.model.destination.Region;
import com.example.travelplanner.model.destination.SeasonalInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationDataRepositoryTest {

    private DestinationDataRepository repo;

    @BeforeEach
    void setUp() {
        repo = new DestinationDataRepository();
        repo.load();
    }

    @Test
    void supportedDestinationsContainsFiveEntries() {
        List<String> destinations = repo.supportedDestinations();
        assertThat(destinations).hasSize(5);
        assertThat(destinations).contains("Japan");
    }

    @Test
    void japanRegionsLoaded() {
        List<Region> regions = repo.getRegions("Japan");
        assertThat(regions).hasSizeGreaterThanOrEqualTo(3);
        assertThat(regions).anyMatch(r -> r.name().equals("Tokyo"));
        assertThat(regions).anyMatch(r -> r.name().equals("Kyoto"));
    }

    @Test
    void japanAttractionsIncludeSensoJi() {
        List<Attraction> attractions = repo.getAttractions("Japan");
        assertThat(attractions).isNotEmpty();
        assertThat(attractions).anyMatch(a -> a.name().contains("Senso-ji"));
    }

    @Test
    void mustSeeAttractionsAreSubsetOfAll() {
        List<Attraction> all = repo.getAttractions("Japan");
        List<Attraction> mustSee = repo.getMustSeeAttractions("Japan");
        assertThat(mustSee.size()).isLessThanOrEqualTo(all.size());
        assertThat(mustSee).allMatch(Attraction::mustSee);
    }

    @Test
    void japanAprilSeasonalInfoReturned() {
        Optional<SeasonalInfo> info = repo.getSeasonalInfo("Japan", "april");
        assertThat(info).isPresent();
        assertThat(info.get().crowdLevel()).isEqualTo("VERY_HIGH");
        assertThat(info.get().notableEvents()).anyMatch(e -> e.toLowerCase().contains("cherry"));
    }

    @Test
    void japanCulturalTipsNotEmpty() {
        List<String> tips = repo.getCulturalTips("Japan");
        assertThat(tips).hasSizeGreaterThanOrEqualTo(5);
        assertThat(tips).anyMatch(t -> t.toLowerCase().contains("tip") || t.toLowerCase().contains("shoe"));
    }

    @Test
    void neighbourhoodGroupsLoaded() {
        List<NeighborhoodGroup> groups = repo.getNeighborhoodGroups("Japan");
        assertThat(groups).isNotEmpty();
        assertThat(groups).anyMatch(g -> g.name().contains("Tokyo"));
    }

    @Test
    void fullTextDescriptionContainsDestinationName() {
        String text = repo.getFullTextDescription("Japan");
        assertThat(text).contains("Japan");
        assertThat(text).contains("Attraction");
    }

    @Test
    void parisDataLoadedCorrectly() {
        List<Attraction> attractions = repo.getAttractions("France (Paris)");
        assertThat(attractions).isNotEmpty();
        assertThat(attractions).anyMatch(a -> a.name().contains("Eiffel"));
    }

    @Test
    void caseInsensitiveLookup() {
        List<Region> regions = repo.getRegions("japan");
        assertThat(regions).isNotEmpty();
    }

    @Test
    void unknownDestinationReturnsEmpty() {
        List<Region> regions = repo.getRegions("Antarctica");
        assertThat(regions).isEmpty();
    }

    @Test
    void allFiveDestinationsHaveAttractions() {
        for (String dest : repo.supportedDestinations()) {
            assertThat(repo.getAttractions(dest))
                    .as("Attractions should not be empty for: " + dest)
                    .isNotEmpty();
        }
    }

    @Test
    void allFiveDestinationsHaveCulturalTips() {
        for (String dest : repo.supportedDestinations()) {
            assertThat(repo.getCulturalTips(dest))
                    .as("Cultural tips should not be empty for: " + dest)
                    .hasSizeGreaterThanOrEqualTo(3);
        }
    }
}
