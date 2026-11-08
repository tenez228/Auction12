package vn.edu.auction.application;

import java.util.List;
import java.util.UUID;
import vn.edu.auction.domain.auction.Auction;

/** Minimal registry contract used by the service; test registries need no Singleton reset. */
public interface AuctionRepository {
  void registerAuction(Auction auction);

  Auction getAuction(UUID id);

  List<Auction> getAuctions();
}
