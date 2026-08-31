package com.vietkhampha.tourservice.document;

import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TourSearchDocumentMappingTest {

    @Test
    void readsNestedDepartureStartDateStoredAsEpochMillis() {
        Instant startDate = Instant.parse("2026-10-12T00:00:00Z");
        Document source = Document.create();
        source.put("id", "tour-da-lat-3n2d");
        source.put("availableDepartures", List.of(Map.of(
                "departureId", "11111111-1111-4111-8111-111111111111",
                "startDate", startDate.toEpochMilli()
        )));

        ElasticsearchCustomConversions conversions = new ElasticsearchCustomConversions(List.of());
        SimpleElasticsearchMappingContext mappingContext = new SimpleElasticsearchMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.setInitialEntitySet(Set.of(
                TourSearchDocument.class,
                TourSearchDocument.DepartureAvailability.class
        ));
        mappingContext.afterPropertiesSet();
        MappingElasticsearchConverter converter = new MappingElasticsearchConverter(mappingContext);
        converter.setConversions(conversions);
        converter.afterPropertiesSet();

        TourSearchDocument document = converter.read(TourSearchDocument.class, source);

        assertThat(document.getAvailableDepartures()).singleElement()
                .satisfies(departure -> {
                    assertThat(departure.getDepartureId())
                            .isEqualTo("11111111-1111-4111-8111-111111111111");
                    assertThat(departure.getStartDate()).isEqualTo(startDate);
                });

        Document target = Document.create();
        converter.write(document, target);

        assertThat(target.get("availableDepartures"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("startDate", startDate.toEpochMilli());
    }
}
