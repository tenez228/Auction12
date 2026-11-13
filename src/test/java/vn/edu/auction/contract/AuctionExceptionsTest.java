package vn.edu.auction.contract;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.auction.application.auth.AuthenticationException;
import vn.edu.auction.domain.auction.AuctionStatus;
import vn.edu.auction.domain.exception.AuctionClosedException;
import vn.edu.auction.domain.exception.InvalidBidException;
import vn.edu.auction.support.AuctionTestFixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small conventional JUnit examples in addition to the shared dynamic contracts. */
class AuctionExceptionsTest {
  private AuctionTestFixture fixture;

  @BeforeEach
  void setUp() { fixture = new AuctionTestFixture(); }

  @AfterEach
  void tearDown() { fixture.close(); }

  @Test
  void acceptedBidUpdatesAllRelatedFields() {
    fixture.auction.placeBid(fixture.bidder, new BigDecimal("150"));
    var state = fixture.auction.snapshot();
    assertAll(
        () -> assertEquals(new BigDecimal("150"), state.currentPrice()),
        () -> assertEquals(fixture.bidder.getId(), state.leadingBidderId().orElseThrow()),
        () -> assertEquals(1, state.bidHistory().size()),
        () -> assertEquals(AuctionStatus.RUNNING, state.status()));
  }

  @ParameterizedTest(name = "Invalid bid amount: {0}")
  @NullSource
  @ValueSource(strings = {"100", "99", "0", "-1", "100.5"})
  void invalidAmountDoesNotMutateAnOpenAuction(String value) {
    fixture.auction.refreshStatus();
    var before = fixture.auction.snapshot();
    BigDecimal amount = value == null ? null : new BigDecimal(value);
    assertThrowsExactly(InvalidBidException.class, () -> fixture.auction.placeBid(fixture.bidder, amount));
    assertEquals(before, fixture.auction.snapshot());
  }

  @Test
  void exactDeadlineClosesEvenWithoutScheduler() {
    fixture.clock.set(AuctionTestFixture.END);
    AuctionClosedException error = assertThrowsExactly(AuctionClosedException.class,
        () -> fixture.auction.placeBid(fixture.bidder, new BigDecimal("150")));
    assertEquals(AuctionClosedException.Reason.CLOSED, error.getReason());
    assertEquals(AuctionStatus.FINISHED, fixture.auction.getStatus());
    assertTrue(fixture.auction.getBidHistory().isEmpty());
  }

  @Test
  void incorrectPasswordUsesAuthenticationException() {
    assertThrowsExactly(AuthenticationException.class,
        () -> fixture.auth.login("bidder01", "wrong".toCharArray()));
  }

  @Test
  void expiredSessionCannotUseApplicationService() {
    fixture.auth.logout(fixture.bidderSession);
    assertThrowsExactly(AuthenticationException.class,
        () -> fixture.service.listAuctions(fixture.bidderSession));
  }

  @Test
  void winnerIsHighestAcceptedBidderAfterEnd() {
    fixture.auction.placeBid(fixture.bidder, new BigDecimal("150"));
    fixture.auction.placeBid(fixture.other, new BigDecimal("200"));
    fixture.clock.set(AuctionTestFixture.END);
    fixture.auction.refreshStatus();
    assertEquals(fixture.other.getId(), fixture.auction.getWinnerId().orElseThrow());
    assertEquals(AuctionStatus.FINISHED, fixture.auction.getStatus());
  }
}
