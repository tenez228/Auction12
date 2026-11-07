package vn.edu.auction.application.auth;

import java.util.UUID;
import vn.edu.auction.domain.user.UserRole;

/** Read-only information for displaying the signed-in user. */
public record UserProfile(UUID id, String username, String displayName, UserRole role) {
}
