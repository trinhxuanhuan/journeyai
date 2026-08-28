package com.vietkhampha.tourservice.service;

import com.vietkhampha.tourservice.dto.CreateTourGuideRequest;
import com.vietkhampha.tourservice.dto.TourGuideResponse;
import com.vietkhampha.tourservice.entity.TourGuide;
import com.vietkhampha.tourservice.repository.TourGuideRepository;
import com.vietkhampha.tourservice.exception.BusinessException;
import com.vietkhampha.tourservice.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public TourGuideResponse getTourGuide(String id) {
        return TourGuideResponse.from(findById(id));
    }

    public List<TourGuideResponse> listTourGuides() {
        return tourGuideRepository.findAll().stream().map(TourGuideResponse::from).toList();
    }

    public void deactivateTourGuide(String id) {
        TourGuide guide = findById(id);
        guide.deactivate();
        tourGuideRepository.save(guide);
    }

    private TourGuide findById(String id) {
        return tourGuideRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_GUIDE_NOT_FOUND));
    }
}
