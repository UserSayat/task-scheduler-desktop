package org.example.taskschedulerdesktop.project;


import java.time.Instant;
import java.util.UUID;

/**
 * Участник проекта и его роль в проекте.
 */
public record ProjectMember(UUID userId, MemberRole role, Instant joinedAt) {
}
