package com.vietkhampha.tourservice.service;

import com.vietkhampha.tourservice.dto.CreateTourGuideRequest;
import com.vietkhampha.tourservice.dto.TourGuideResponse;
import com.vietkhampha.tourservice.entity.TourGuide;
import com.vietkhampha.tourservice.repository.TourGuideRepository;
import org.springframework.stereotype.Service;

@Service
public class TourGuideService {

    private final TourGuideRepository tourGuideRepository;

    public TourGuideService(TourGuideRepository tourGuideRepository) {
        this.tourGuideRepository = tourGuideRepository;
    }

    public TourGuideResponse createTourGuide(CreateTourGuideRequest request) {
        TourGuide guide = new TourGuide(
                request.getFullName().trim(),
                request.getBio(),
                request.getYearsOfExperience(),
                request.getAvatarUrl()
        );
        return TourGuideResponse.from(tourGuideRepository.save(guide));
    }
}
