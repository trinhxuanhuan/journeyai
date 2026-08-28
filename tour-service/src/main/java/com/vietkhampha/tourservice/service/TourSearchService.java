package com.vietkhampha.tourservice.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.vietkhampha.tourservice.document.TourSearchDocument;
import com.vietkhampha.tourservice.dto.TourSearchItemDto;
import com.vietkhampha.tourservice.dto.TourSearchResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TourSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public TourSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public TourSearchResponse search(String q, String destination, BigDecimal minPrice, BigDecimal maxPrice,
                                     String fromDate, String toDate, String tourType,
                                     Double lat, Double lng, Double radiusKm,
                                     String sortBy, int page, int size) {

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        boolQuery.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));

        if (q != null && !q.isBlank()) {
            boolQuery.must(m -> m.multiMatch(mm -> mm
                    .query(q)
                    .fields("name", "description")
            ));
        }

        if (destination != null && !destination.isBlank()) {
            boolQuery.filter(f -> f.term(t -> t.field("province").value(destination)));
        }

        if (tourType != null && !tourType.isBlank()) {
            boolQuery.filter(f -> f.term(t -> t.field("tourType").value(tourType.toUpperCase())));
        }

        if (fromDate != null || toDate != null) {
            try {
                LocalDate parsedFromDate = fromDate == null ? null : LocalDate.parse(fromDate);
                LocalDate parsedToDate = toDate == null ? null : LocalDate.parse(toDate);
                if (parsedFromDate != null && parsedToDate != null
                        && parsedFromDate.isAfter(parsedToDate)) {
                    throw new java.time.format.DateTimeParseException(
                            "fromDate must not be after toDate", fromDate, 0
                    );
                }
                boolQuery.filter(f -> f.range(range -> {
                    range.field("availableDepartureDates");
                    if (parsedFromDate != null) {
                        range.gte(co.elastic.clients.json.JsonData.of(
                                parsedFromDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString()
                        ));
                    }
                    if (parsedToDate != null) {
                        range.lte(co.elastic.clients.json.JsonData.of(
                                parsedToDate.plusDays(1).atStartOfDay()
                                        .toInstant(ZoneOffset.UTC).minusNanos(1).toString()
                        ));
                    }
                    return range;
                }));
            } catch (java.time.format.DateTimeParseException exception) {
                throw new com.vietkhampha.tourservice.exception.BusinessException(
                        com.vietkhampha.tourservice.exception.ErrorCode.VALIDATION_FAILED
                );
            }
        }

        if (minPrice != null || maxPrice != null) {
            boolQuery.filter(f -> f.range(r -> {
                r.field("basePrice");
                if (minPrice != null) r.gte(co.elastic.clients.json.JsonData.of(minPrice));
                if (maxPrice != null) r.lte(co.elastic.clients.json.JsonData.of(maxPrice));
                return r;
            }));
        }

        if (lat != null && lng != null && radiusKm != null) {
            boolQuery.filter(f -> f.geoDistance(gd -> gd
                    .field("location")
                    .distance(radiusKm + "km")
                    .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lng)))
            ));
        }


        Query finalQuery = new Query.Builder().bool(boolQuery.build()).build();

        Sort sort = resolveSort(sortBy);
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(finalQuery)
                .withPageable(PageRequest.of(page, size, sort))
                .build();

        SearchHits<TourSearchDocument> hits = elasticsearchOperations.search(nativeQuery, TourSearchDocument.class);

        List<TourSearchItemDto> items = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(TourSearchItemDto::from)
                .collect(Collectors.toList());

        return new TourSearchResponse(items, hits.getTotalHits(), page);
    }

    private Sort resolveSort(String sortBy) {
        if (sortBy == null) return Sort.unsorted();
        return switch (sortBy) {
            case "priceAsc" -> Sort.by(Sort.Direction.ASC, "basePrice");
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "basePrice");
            case "ratingDesc" -> Sort.by(Sort.Direction.DESC, "avgRating");
            default -> Sort.unsorted(); // relevance mặc định của Elasticsearch (điểm khớp keyword)
        };
    }
}
