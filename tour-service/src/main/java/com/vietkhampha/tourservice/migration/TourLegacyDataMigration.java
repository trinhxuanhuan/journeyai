package com.vietkhampha.tourservice.migration;

import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Additive, idempotent backfill for Mongo documents created before the MVP package model.
 * Legacy tourGuideId is deliberately retained and can be copied to Departures operationally.
 */
@Component
public class TourLegacyDataMigration implements ApplicationRunner {
    private final MongoTemplate mongoTemplate;

    public TourLegacyDataMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Document defaultPackage = new Document()
                .append("accommodation", List.of())
                .append("transport", List.of())
                .append("meals", List.of())
                .append("tickets", List.of())
                .append("insurance", List.of());
        Document defaultChildPolicy = new Document()
                .append("description", "Tre em tinh 75% gia nguoi lon")
                .append("pricePercentage", 75);
        List<Document> defaultCancellation = List.of(
                new Document("minimumDaysBeforeDeparture", 7).append("refundPercentage", 100),
                new Document("minimumDaysBeforeDeparture", 3).append("refundPercentage", 50),
                new Document("minimumDaysBeforeDeparture", 0).append("refundPercentage", 0)
        );

        Document itinerarySize = new Document("$size", new Document("$ifNull", List.of("$itinerary", List.of())));
        Document durationDays = new Document("$max", List.of(1, itinerarySize));

        mongoTemplate.getCollection("tours").updateMany(
                new Document(),
                List.of(
                        new Document("$set", new Document()
                                .append("tourType", new Document("$ifNull", List.of("$tourType", "GROUP")))
                                .append("priceModel", new Document("$ifNull", List.of("$priceModel", "PER_PERSON")))
                                .append("departureLocation", new Document("$ifNull", List.of(
                                        "$departureLocation", "$destination.province"
                                )))
                                .append("minGroupSize", new Document("$ifNull", List.of("$minGroupSize", 1)))
                                .append("maxGroupSize", new Document("$ifNull", List.of("$maxGroupSize", 30)))
                                .append("guideMode", new Document("$ifNull", List.of("$guideMode", "INCLUDED")))
                                .append("optionalGuidePrice", new Document("$ifNull", List.of("$optionalGuidePrice", 0)))
                                .append("durationDays", new Document("$ifNull", List.of("$durationDays", durationDays)))
                                .append("included", new Document("$ifNull", List.of("$included", List.of())))
                                .append("excluded", new Document("$ifNull", List.of("$excluded", List.of())))
                                .append("packageDetails", new Document("$ifNull", List.of("$packageDetails", defaultPackage)))
                                .append("childPolicy", new Document("$ifNull", List.of("$childPolicy", defaultChildPolicy)))
                                .append("singleRoomSupplement", new Document("$ifNull", List.of("$singleRoomSupplement", 0)))
                                .append("cancellationPolicy", new Document("$ifNull", List.of(
                                        "$cancellationPolicy", defaultCancellation
                                )))
                        ),
                        new Document("$set", new Document()
                                .append("meetingPoint", new Document("$ifNull", List.of(
                                        "$meetingPoint", "$departureLocation"
                                )))
                                .append("durationNights", new Document("$ifNull", List.of(
                                        "$durationNights", new Document("$max", List.of(
                                                0, new Document("$subtract", List.of("$durationDays", 1))
                                        ))
                                )))
                        )
                )
        );
    }
}
