package com.vietkhampha.tourservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.tourservice.entity.TourGuide;
import com.vietkhampha.tourservice.repository.TourGuideRepository;
import com.vietkhampha.tourservice.repository.TourRepository;
import com.vietkhampha.tourservice.repository.TourSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.data.elasticsearch.repositories.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminTourGuideControllerIntegrationTest {

    @Container
    private static final MongoDBContainer MONGODB = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TourGuideRepository tourGuideRepository;

    @Autowired
    private TourRepository tourRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private TourSearchRepository tourSearchRepository;

    @AfterEach
    void cleanDatabase() {
        tourRepository.deleteAll();
        tourGuideRepository.deleteAll();
    }

    @Test
    void adminCreatesGuide_returnsCreatedMongoIdAndPersistsTrimmedName() throws Exception {
        String responseBody = mockMvc.perform(post("/v1/admin/tour-guides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "  Nguyen An  ",
                                  "bio": "Huong dan vien du lieu kiem thu",
                                  "yearsOfExperience": 4,
                                  "avatarUrl": "https://example.test/guide.png"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fullName").value("Nguyen An"))
                .andExpect(jsonPath("$.yearsOfExperience").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        TourGuide savedGuide = tourGuideRepository.findById(response.get("id").asText()).orElseThrow();

        assertThat(tourGuideRepository.count()).isEqualTo(1);
        assertThat(savedGuide.getFullName()).isEqualTo("Nguyen An");
        assertThat(savedGuide.getBio()).isEqualTo("Huong dan vien du lieu kiem thu");
        assertThat(savedGuide.getYearsOfExperience()).isEqualTo(4);
        assertThat(savedGuide.getAvatarUrl()).isEqualTo("https://example.test/guide.png");
        assertThat(savedGuide.getAuthUserId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"fullName\":\"   \"}"
    })
    void missingOrBlankName_returnsBadRequestWithoutSaving(String requestBody) throws Exception {
        mockMvc.perform(post("/v1/admin/tour-guides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());

        assertThat(tourGuideRepository.count()).isZero();
    }

    @Test
    void negativeExperience_returnsBadRequestWithoutSaving() throws Exception {
        mockMvc.perform(post("/v1/admin/tour-guides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Guide Test",
                                  "yearsOfExperience": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.yearsOfExperience").exists());

        assertThat(tourGuideRepository.count()).isZero();
    }

    @Test
    void newlyCreatedGuideId_canBeUsedToCreateTour() throws Exception {
        String guideResponse = mockMvc.perform(post("/v1/admin/tour-guides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Guide for Tour",
                                  "yearsOfExperience": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String guideId = objectMapper.readTree(guideResponse).get("id").asText();

        mockMvc.perform(post("/v1/admin/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTourRequest(guideId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tourGuideId").value(guideId));

        assertThat(tourRepository.count()).isEqualTo(1);
        assertThat(tourRepository.findAll().get(0).getTourGuideId()).isEqualTo(guideId);
    }

    @Test
    void unknownGuideId_isStillRejectedWhenCreatingTour() throws Exception {
        mockMvc.perform(post("/v1/admin/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTourRequest("missing-guide-id")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOUR_GUIDE_NOT_FOUND"));

        assertThat(tourRepository.count()).isZero();
    }

    @Test
    void createsPrivatePackageTourWithStructuredCommercialPolicy() throws Exception {
        mockMvc.perform(post("/v1/admin/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "TP.HCM - Đà Lạt riêng 3N2Đ",
                                  "description": "Hành trình riêng cho gia đình",
                                  "destination": {
                                    "province": "Lâm Đồng",
                                    "geo": { "lat": 11.9404, "lng": 108.4583 }
                                  },
                                  "basePrice": 7800000,
                                  "tourType": "PRIVATE",
                                  "priceModel": "PER_GROUP",
                                  "departureLocation": "TP.HCM",
                                  "meetingPoint": "Nhà Văn hóa Thanh Niên",
                                  "meetingTime": "06:30:00",
                                  "minGroupSize": 2,
                                  "maxGroupSize": 8,
                                  "guideMode": "OPTIONAL",
                                  "optionalGuidePrice": 900000,
                                  "durationDays": 3,
                                  "durationNights": 2,
                                  "included": ["Xe riêng", "Khách sạn 2 đêm"],
                                  "excluded": ["Chi tiêu cá nhân"],
                                  "packageDetails": {
                                    "accommodation": ["Khách sạn 3 sao"],
                                    "transport": ["Xe du lịch riêng"],
                                    "meals": ["2 bữa sáng"],
                                    "tickets": ["Vé điểm tham quan trong chương trình"],
                                    "insurance": ["Bảo hiểm du lịch nội địa"]
                                  },
                                  "childPolicy": {
                                    "description": "Trẻ em tính 60% giá người lớn",
                                    "pricePercentage": 60
                                  },
                                  "singleRoomSupplement": 650000,
                                  "cancellationPolicy": [
                                    {"minimumDaysBeforeDeparture": 0, "refundPercentage": 0},
                                    {"minimumDaysBeforeDeparture": 7, "refundPercentage": 100}
                                  ],
                                  "itinerary": [
                                    {"dayNumber": 1, "title": "Đến Đà Lạt", "activities": [{"time": "06:30", "description": "Khởi hành"}]},
                                    {"dayNumber": 2, "title": "Văn hóa cao nguyên", "activities": [{"time": "08:00", "description": "Tham quan"}]},
                                    {"dayNumber": 3, "title": "Trở về", "activities": [{"time": "09:00", "description": "Mua đặc sản"}]}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tourType").value("PRIVATE"))
                .andExpect(jsonPath("$.priceModel").value("PER_GROUP"))
                .andExpect(jsonPath("$.departureLocation").value("TP.HCM"))
                .andExpect(jsonPath("$.guideMode").value("OPTIONAL"))
                .andExpect(jsonPath("$.durationDays").value(3))
                .andExpect(jsonPath("$.durationNights").value(2))
                .andExpect(jsonPath("$.packageDetails.accommodation[0]").value("Khách sạn 3 sao"))
                .andExpect(jsonPath("$.cancellationPolicy[0].minimumDaysBeforeDeparture").value(7));

        assertThat(tourRepository.count()).isEqualTo(1);
        assertThat(tourRepository.findAll().get(0).getTourGuideId()).isNull();
    }

    @Test
    void rejectsPerGroupPricingForSharedGroupTour() throws Exception {
        String request = validTourRequest(null).replace(
                "\"basePrice\":1500000",
                "\"basePrice\":1500000,\"tourType\":\"GROUP\",\"priceModel\":\"PER_GROUP\""
        );

        mockMvc.perform(post("/v1/admin/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOUR_CONFIGURATION_INVALID"));

        assertThat(tourRepository.count()).isZero();
    }

    private String validTourRequest(String guideId) throws Exception {
        JsonNode request = objectMapper.readTree("""
                {
                  "name": "Tour fixture",
                  "description": "Tour chi dung cho integration test",
                  "destination": {
                    "province": "Da Nang",
                    "geo": { "lat": 16.0544, "lng": 108.2022 }
                  },
                  "basePrice": 1500000,
                  "itinerary": [
                    {
                      "dayNumber": 1,
                      "title": "Ngay thu nhat",
                      "activities": [
                        { "time": "08:00", "description": "Khoi hanh" }
                      ]
                    }
                  ]
                }
                """);
        if (guideId != null) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) request).put("tourGuideId", guideId);
        }
        return objectMapper.writeValueAsString(request);
    }
}
