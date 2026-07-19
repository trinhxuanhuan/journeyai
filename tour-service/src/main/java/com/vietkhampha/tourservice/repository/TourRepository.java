package com.vietkhampha.tourservice.repository;

import com.vietkhampha.tourservice.entity.Tour;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TourRepository extends MongoRepository<Tour, String> {
}