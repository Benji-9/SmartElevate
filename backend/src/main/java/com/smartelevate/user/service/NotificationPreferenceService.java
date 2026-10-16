package com.smartelevate.user.service;

import com.smartelevate.user.dto.NotificationPreferencesDto;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Preferencias de aviso por email del usuario de la sesión (#221). */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public NotificationPreferencesDto get(UUID userId) {
        return toDto(find(userId));
    }

    @Transactional
    public NotificationPreferencesDto update(UUID userId, NotificationPreferencesDto preferences) {
        User user = find(userId);
        user.setNotificationPreferences(preferences.departureReminder(), preferences.elevatorOutOfService());
        return toDto(user);
    }

    private User find(UUID userId) {
        // Cuenta borrada después de autenticar: sesión inválida (igual que UserService.me).
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido"));
    }

    private static NotificationPreferencesDto toDto(User user) {
        return new NotificationPreferencesDto(user.isNotifyDepartureReminder(), user.isNotifyElevatorOutOfService());
    }
}
