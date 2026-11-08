package vn.edu.auction.application.auth;

/** Missing/invalid credentials or a missing, forged or logged-out session. */
public final class AuthenticationException extends SecurityException {
  private static final long serialVersionUID = 1L;

  public AuthenticationException(String message) {
    super(message);
  }
}
