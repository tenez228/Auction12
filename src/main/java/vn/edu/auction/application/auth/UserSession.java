package vn.edu.auction.application.auth;

import java.util.Objects;
import java.util.UUID;

/** Opaque token; permissions are resolved from the service, never from UI fields. */
public record UserSession(UUID token) {
  public UserSession {
    Objects.requireNonNull(token, "token");
  }
}
