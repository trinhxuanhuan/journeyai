package com.vietkhampha.tourservice.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TourSearchServiceQueryTest {

    @Test
    void destinationFilterUsesAnalyzedPhraseQueriesForLegacyAndCurrentIndexMappings() {
        Query query = TourSearchService.buildDestinationQuery("Hội An");

        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(query.bool().should())
                .extracting(item -> item.matchPhrase().field())
                .containsExactly("destinationName", "province");
        assertThat(query.bool().should())
                .extracting(item -> item.matchPhrase().query())
                .containsOnly("Hội An");
    }
}
