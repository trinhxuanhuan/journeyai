package com.vietkhampha.bookingservice.service;

import com.vietkhampha.bookingservice.dto.CreateBookingRequest;
import com.vietkhampha.bookingservice.dto.ParticipantDto;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class BookingRequestHasher {

    public static final String HASH_VERSION = "SHA256_V1";
    private static final String OPERATION = "POST:/v1/bookings";

    public String hash(UUID customerId, CreateBookingRequest request) {
        MessageDigest digest = sha256();

        append(digest, HASH_VERSION);
        append(digest, OPERATION);
        append(digest, customerId.toString());
        append(digest, request.getTourSlotId() == null ? null : request.getTourSlotId().toString());
        append(digest, normalize(request.getGeneratedItineraryId()));

        List<CanonicalParticipant> participants = canonicalParticipants(request.getParticipants());
        append(digest, Integer.toString(participants.size()));
        for (CanonicalParticipant participant : participants) {
            append(digest, participant.fullName());
            append(digest, participant.phone());
            append(digest, Boolean.toString(participant.primaryContact()));
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private List<CanonicalParticipant> canonicalParticipants(List<ParticipantDto> participants) {
        if (participants == null) {
            return List.of();
        }

        List<CanonicalParticipant> canonicalParticipants = new ArrayList<>(participants.size());
        for (ParticipantDto participant : participants) {
            canonicalParticipants.add(new CanonicalParticipant(
                    normalize(participant.getFullName()),
                    normalize(participant.getPhone()),
                    participant.isPrimaryContact()
            ));
        }

        canonicalParticipants.sort(
                Comparator.comparing(CanonicalParticipant::fullName, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(CanonicalParticipant::phone, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(CanonicalParticipant::primaryContact)
        );
        return canonicalParticipants;
    }

    private void append(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String normalize(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record CanonicalParticipant(String fullName, String phone, boolean primaryContact) {
    }
}
