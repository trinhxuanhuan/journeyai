package com.vietkhampha.tourservice.repository;

import com.vietkhampha.tourservice.document.TourSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TourSearchRepository extends ElasticsearchRepository<TourSearchDocument, String> {
}