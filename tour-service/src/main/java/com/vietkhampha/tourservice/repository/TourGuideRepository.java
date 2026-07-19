package com.vietkhampha.tourservice.repository;

import com.vietkhampha.tourservice.entity.TourGuide;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TourGuideRepository extends MongoRepository<TourGuide, String> {
}