package com.grun.calorietracker.service;

import com.grun.calorietracker.config.MediaStorageProperties;
import com.grun.calorietracker.config.ProfileMediaProperties;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.UserAvatarServiceImpl;
import com.grun.calorietracker.service.media.MediaObjectStorage;
import com.grun.calorietracker.service.media.MediaStorageKeyPolicy;
import com.grun.calorietracker.service.support.AvatarUploadInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAvatarServiceImplTest {
    private final ProfileMediaProperties profileProperties = new ProfileMediaProperties();
    private final MediaStorageProperties mediaProperties = new MediaStorageProperties();
    private final UserRepository repository = mock(UserRepository.class);
    private final MediaObjectStorage storage = mock(MediaObjectStorage.class);
    private UserAvatarServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        profileProperties.getAvatar().setPublicBaseUrl("https://api.grun.test");
        MediaStorageKeyPolicy keyPolicy = new MediaStorageKeyPolicy(mediaProperties);
        service = new UserAvatarServiceImpl(profileProperties, repository, storage, keyPolicy,
                new AvatarUploadInspector(profileProperties));
        user = new UserEntity();
        user.setId(42L);
        user.setEmail("user@grun.test");
        when(repository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(repository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void storesAvatarUnderUserNamespaceAndPublishesOpaqueToken() {
        var file = new MockMultipartFile("file", "avatar.png", "image/png", png());

        var profile = service.uploadAvatar(user.getEmail(), file);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), any(byte[].class), anyString(), anyString());
        assertTrue(key.getValue().startsWith("grun/profile/avatars/u42/"));
        assertTrue(profile.getAvatarUrl().matches(
                "https://api\\.grun\\.test/api/v1/users/avatars/u42~[a-f0-9-]{36}\\.png"));
    }

    @Test
    void removesPreviousManagedAvatarOnlyAfterProfileSave() {
        String oldObject = UUID.randomUUID() + ".jpg";
        user.setAvatarUrl("https://api.grun.test/api/v1/users/avatars/u42~" + oldObject);

        service.uploadAvatar(user.getEmail(), new MockMultipartFile("file", "avatar.png", "image/png", png()));

        verify(repository).save(user);
        verify(storage).delete("grun/profile/avatars/u42/" + oldObject);
    }

    @Test
    void deletesNewObjectWhenProfilePersistenceFails() {
        doThrow(new IllegalStateException("db unavailable")).when(repository).save(any(UserEntity.class));

        assertThrows(IllegalStateException.class, () -> service.uploadAvatar(
                user.getEmail(), new MockMultipartFile("file", "avatar.png", "image/png", png())));

        ArgumentCaptor<String> deletedKey = ArgumentCaptor.forClass(String.class);
        verify(storage).delete(deletedKey.capture());
        assertTrue(deletedKey.getValue().startsWith("grun/profile/avatars/u42/"));
    }

    private byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }
}
