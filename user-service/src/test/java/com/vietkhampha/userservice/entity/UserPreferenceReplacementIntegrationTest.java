package com.vietkhampha.userservice.entity;

import com.vietkhampha.userservice.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserPreferenceReplacementIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void replacingAnExistingPreferenceUpdatesItWithoutViolatingTheUniqueConstraint() {
        UserProfile profile = new UserProfile(UUID.randomUUID());
        profile.replacePreferenceTags(List.of(
                preference("CULTURE", "1.0"),
                preference("FOOD", "0.8")
        ));
        userProfileRepository.saveAndFlush(profile);

        profile.replacePreferenceTags(List.of(
                preference("CULTURE", "0.6"),
                preference("NATURE", "1.0")
        ));
        userProfileRepository.saveAndFlush(profile);

        assertThat(profile.getPreferenceTags())
                .extracting(UserPreferenceTag::getTagCode)
                .containsExactlyInAnyOrder("CULTURE", "NATURE");
        assertThat(profile.getPreferenceTags())
                .filteredOn(tag -> tag.getTagCode().equals("CULTURE"))
                .extracting(UserPreferenceTag::getWeight)
                .containsExactly(new BigDecimal("0.6"));
    }

    private UserPreferenceTag preference(String code, String weight) {
        return new UserPreferenceTag(code, new BigDecimal(weight));
    }
}
