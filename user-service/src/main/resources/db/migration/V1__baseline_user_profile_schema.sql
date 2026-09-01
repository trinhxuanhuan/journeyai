CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    auth_user_id UUID NOT NULL,
    avatar_url VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    phone VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_profiles_auth_user_id UNIQUE (auth_user_id)
);

CREATE TABLE user_preference_tags (
    id UUID PRIMARY KEY,
    tag_code VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    weight NUMERIC(38, 2) NOT NULL,
    user_profile_id UUID NOT NULL,
    CONSTRAINT fk_user_preference_tags_profile
        FOREIGN KEY (user_profile_id) REFERENCES user_profiles(id)
);
