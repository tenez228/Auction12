package vn.edu.auction.application.exception;

import java.util.NoSuchElementException;

/** No auction is visible in this application service for the requested ID. */
public final class AuctionNotFoundException extends NoSuchElementException {
  private static final long serialVersionUID = 1L;

  public AuctionNotFoundException() {
    super("Không tìm thấy phiên đấu giá.");
  }
}
