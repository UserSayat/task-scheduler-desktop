package org.example.taskschedulerdesktop.models;

import org.example.taskschedulerdesktop.utils.MemberRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Участник проекта и его роль в проекте.
 */
public record ProjectMember(UUID userId, MemberRole role, Instant joinedAt) {
}
