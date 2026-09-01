package com.vietkhampha.tourservice.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vietkhampha.tourservice.dto.TourRequest;
import com.vietkhampha.tourservice.entity.Tour;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VerifiedTourCatalogContractTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final Set<String> OFFICIAL_SOURCE_HOSTS = Set.of(
            "whc.unesco.org", "www.vietnam.travel", "vietnam.travel",
            "datafiles.chinhphu.vn", "xaydungchinhsach.chinhphu.vn"
    );
    private static final Map<String, String> CURRENT_ADMINISTRATIVE_AREAS = Map.of(
            "Huế", "Thành phố Huế",
            "Hà Giang", "Tuyên Quang",
            "Hội An", "Thành phố Đà Nẵng",
            "Phú Quốc", "An Giang"
    );

    @Test
    void catalogContainsValidDetailedAndTraceablePublicTourContent() throws IOException {
        JsonNode catalog = OBJECT_MAPPER.readTree(Files.readString(locateCatalog()));

        assertThat(catalog.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(catalog.path("verifiedAt").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(catalog.path("items").isArray()).isTrue();
        assertThat(catalog.path("items")).hasSize(4);

        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> guideKeys = new HashSet<>();

        for (JsonNode item : catalog.path("items")) {
            assertThat(codes.add(item.path("code").asText())).as("catalog code must be unique").isTrue();
            assertOfficialSources(item.path("sources"));

            TourRequest request = OBJECT_MAPPER.treeToValue(item.path("tour"), TourRequest.class);
            Set<ConstraintViolation<TourRequest>> violations = VALIDATOR.validate(request);
            assertThat(violations)
                    .as(() -> "invalid catalog tour " + item.path("code").asText() + ": " + violations)
                    .isEmpty();
            assertThat(names.add(request.getName())).as("tour name must be unique").isTrue();
            assertThat(request.getDescription()).hasSizeGreaterThan(120);
            assertThat(request.getDepartureLocation()).isNotBlank();
            assertThat(request.getMeetingPoint()).isNotBlank();
            assertThat(request.getDurationDays()).isEqualTo(request.getItinerary().size());
            assertThat(request.getDestination().getProvince())
                    .isEqualTo(CURRENT_ADMINISTRATIVE_AREAS.get(request.getDestination().getName()));

            for (int index = 0; index < request.getItinerary().size(); index++) {
                TourRequest.ItineraryDayDto day = request.getItinerary().get(index);
                assertThat(day.getDayNumber()).isEqualTo(index + 1);
                assertThat(day.getTitle()).hasSizeGreaterThan(15);
                assertThat(day.getActivities()).hasSizeGreaterThanOrEqualTo(4);
                assertThat(day.getActivities()).allSatisfy(activity -> {
                    assertThat(activity.getTime()).matches("([01]\\d|2[0-3]):[0-5]\\d");
                    assertThat(activity.getDescription()).hasSizeGreaterThan(55);
                });
            }

            if (request.getTourType() == Tour.TourType.GROUP) {
                assertThat(item.path("operations").isObject()).isTrue();
                assertThat(item.path("operations").path("publicationStatus").asText()).isEqualTo("DRAFT");
                assertThat(item.path("operations").path("regionKey").asText()).isNotBlank();
                assertThat(guideKeys.add(item.path("operations").path("guideKey").asText()))
                        .as("guideKey must be unique for concurrently published catalog schedules")
                        .isTrue();
                assertThat(request.getPriceModel()).isEqualTo(Tour.PriceModel.PER_PERSON);
                assertThat(request.getGuideMode()).isEqualTo(Tour.GuideMode.INCLUDED);
            } else {
                assertThat(item.path("operations").isNull()).isTrue();
                assertThat(request.getPriceModel()).isEqualTo(Tour.PriceModel.PER_GROUP);
            }
        }
    }

    private void assertOfficialSources(JsonNode sources) {
        assertThat(sources.isArray()).isTrue();
        assertThat(sources.size()).isGreaterThanOrEqualTo(2);
        for (JsonNode source : sources) {
            URI uri = URI.create(source.path("url").asText());
            assertThat(uri.getScheme()).isEqualTo("https");
            assertThat(OFFICIAL_SOURCE_HOSTS).contains(uri.getHost());
            assertThat(source.path("title").asText()).isNotBlank();
        }
    }

    private Path locateCatalog() {
        Path[] candidates = {
                Path.of("..", "catalog", "verified-tour-catalog.v1.json"),
                Path.of("catalog", "verified-tour-catalog.v1.json")
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot locate catalog/verified-tour-catalog.v1.json");
    }
}
