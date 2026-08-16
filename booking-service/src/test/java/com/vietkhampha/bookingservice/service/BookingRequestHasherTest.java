package com.vietkhampha.bookingservice.service;

import com.vietkhampha.bookingservice.dto.CreateBookingRequest;
import com.vietkhampha.bookingservice.dto.ParticipantDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingRequestHasherTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TOUR_SLOT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final BookingRequestHasher hasher = new BookingRequestHasher();

    @Test
    void unicodeNfcCanonicalEquivalentsProduceSameHash() {
        String composed = "Caf\u00e9 Fixture";
        String decomposed = "Cafe\u0301 Fixture";

        assertNotEquals(composed, decomposed, "The fixture must use different Unicode representations");
        assertEquals(
                hash(request("fixture-itinerary", participant(composed, "0000000001", true))),
                hash(request("fixture-itinerary", participant(decomposed, "0000000001", true)))
        );
    }

    @Test
    void participantOrderDoesNotAffectHash() {
        ParticipantDto alpha = participant("Synthetic Traveler Alpha", "0000000001", true);
        ParticipantDto beta = participant("Synthetic Traveler Beta", "0000000002", false);

        assertEquals(
                hash(request("fixture-itinerary", alpha, beta)),
                hash(request("fixture-itinerary", beta, alpha))
        );
    }

    @Test
    void nullAndEmptyStringsProduceDifferentHashes() {
        CreateBookingRequest nullItinerary = request(null, participant("Synthetic Traveler", null, true));
        CreateBookingRequest emptyItinerary = request("", participant("Synthetic Traveler", null, true));
        CreateBookingRequest nullPhone = request("fixture-itinerary", participant("Synthetic Traveler", null, true));
        CreateBookingRequest emptyPhone = request("fixture-itinerary", participant("Synthetic Traveler", "", true));

        assertAll(
                () -> assertNotEquals(hash(nullItinerary), hash(emptyItinerary)),
                () -> assertNotEquals(hash(nullPhone), hash(emptyPhone))
        );
    }

    @Test
    void duplicateParticipantsPreserveMultiplicity() {
        ParticipantDto firstCopy = participant("Synthetic Duplicate", "0000000003", false);
        ParticipantDto secondCopy = participant("Synthetic Duplicate", "0000000003", false);

        assertNotEquals(
                hash(request("fixture-itinerary", firstCopy)),
                hash(request("fixture-itinerary", firstCopy, secondCopy))
        );
    }

    @Test
    void changingEachBookingRelevantFieldChangesHash() {
        CreateBookingRequest baselineRequest = request(
                "fixture-itinerary",
                participant("Synthetic Traveler", "0000000004", true)
        );
        String baselineHash = hash(baselineRequest);

        assertAll(
                () -> assertNotEquals(
                        baselineHash,
                        hasher.hash(UUID.fromString("33333333-3333-3333-3333-333333333333"), baselineRequest)
                ),
                () -> assertNotEquals(
                        baselineHash,
                        hash(request(
                                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                                "fixture-itinerary",
                                participant("Synthetic Traveler", "0000000004", true)
                        ))
                ),
                () -> assertNotEquals(
                        baselineHash,
                        hash(request("changed-itinerary", participant("Synthetic Traveler", "0000000004", true)))
                ),
                () -> assertNotEquals(
                        baselineHash,
                        hash(request("fixture-itinerary", participant("Changed Synthetic Traveler", "0000000004", true)))
                ),
                () -> assertNotEquals(
                        baselineHash,
                        hash(request("fixture-itinerary", participant("Synthetic Traveler", "0000000099", true)))
                ),
                () -> assertNotEquals(
                        baselineHash,
                        hash(request("fixture-itinerary", participant("Synthetic Traveler", "0000000004", false)))
                )
        );
    }

    @Test
    void sameRequestProducesDeterministicLowercaseSha256Hex() {
        CreateBookingRequest request = request(
                "fixture-itinerary",
                participant("Synthetic Traveler", "0000000005", true)
        );

        String firstHash = hash(request);
        String secondHash = hash(request);

        assertEquals(firstHash, secondHash);
        assertTrue(firstHash.matches("[0-9a-f]{64}"));
    }

    @Test
    void fixedInputMatchesSha256V1GoldenVector() {
        CreateBookingRequest request = request(
                "fixture-itinerary-01",
                participant("Synthetic Traveler Beta", null, false),
                participant("Synthetic Traveler Alpha", "0000000001", true)
        );

        assertEquals("SHA256_V1", BookingRequestHasher.HASH_VERSION);
        assertEquals(
                "b121b50a65f7a2a85f14b46257dda625ca6b5bd34c9914a69d604af87125cd47",
                hash(request)
        );
    }

    private String hash(CreateBookingRequest request) {
        return hasher.hash(CUSTOMER_ID, request);
    }

    private CreateBookingRequest request(String generatedItineraryId, ParticipantDto... participants) {
        return request(TOUR_SLOT_ID, generatedItineraryId, participants);
    }

    private CreateBookingRequest request(
            UUID tourSlotId,
            String generatedItineraryId,
            ParticipantDto... participants
    ) {
        return new TestCreateBookingRequest(tourSlotId, generatedItineraryId, List.of(participants));
    }

    private ParticipantDto participant(String fullName, String phone, boolean primaryContact) {
        return new TestParticipantDto(fullName, phone, primaryContact);
    }

    private static final class TestCreateBookingRequest extends CreateBookingRequest {
        private TestCreateBookingRequest(
                UUID tourSlotId,
                String generatedItineraryId,
                List<ParticipantDto> participants
        ) {
            setTourSlotId(tourSlotId);
            setGeneratedItineraryId(generatedItineraryId);
            setParticipants(participants);
        }
    }

    private static final class TestParticipantDto extends ParticipantDto {
        private TestParticipantDto(String fullName, String phone, boolean primaryContact) {
            setFullName(fullName);
            setPhone(phone);
            setPrimaryContact(primaryContact);
        }
    }
}
