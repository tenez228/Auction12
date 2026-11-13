package vn.edu.auction.support;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import vn.edu.auction.application.AuctionRepository;
import vn.edu.auction.domain.auction.Auction;

/** A fresh repository per test. Production AuctionManager keeps its Singleton contract. */
public final class InMemoryTestRepository implements AuctionRepository {
  private final ConcurrentMap<UUID, Auction> auctions = new ConcurrentHashMap<>();

  @Override
  public void registerAuction(Auction auction) {
    if (auctions.putIfAbsent(auction.getId(), auction) != null) {
      throw new IllegalArgumentException("Duplicate ID");
    }
  }

  @Override
  public Auction getAuction(UUID id) {
    Auction result = auctions.get(id);
    if (result == null) { throw new NoSuchElementException("Missing auction"); }
    return result;
  }

  @Override
  public List<Auction> getAuctions() {
    return auctions.values().stream().sorted(Comparator.comparing(Auction::getId)).toList();
  }
}
