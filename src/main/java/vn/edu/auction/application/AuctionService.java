package vn.edu.auction.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.domain.auction.BidTransaction;
import vn.edu.auction.domain.auction.event.Subscription;
import vn.edu.auction.domain.item.Item;

public interface AuctionService extends AutoCloseable {
  AuctionView createAuction(
      UserSession session, Item item, BigDecimal startingPrice, Instant start, Instant end);

  List<AuctionView> listAuctions(UserSession session);

  AuctionView getAuction(UserSession session, UUID auctionId);

  BidTransaction placeBid(UserSession session, UUID auctionId, BigDecimal amount);

  void markPaid(UserSession session, UUID auctionId);

  void cancel(UserSession session, UUID auctionId, String reason);

  Subscription subscribe(UserSession session, Consumer<AuctionView> observer);

  int refreshAuctions();

  @Override
  void close();
}
