package vn.edu.auction.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import vn.edu.auction.application.auth.AuthService;
import vn.edu.auction.application.exception.AuctionNotFoundException;
import vn.edu.auction.domain.exception.AuthorizationException;
import vn.edu.auction.domain.exception.DataValidationException;
import vn.edu.auction.domain.exception.InvalidAuctionStateException;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.domain.auction.Auction;
import vn.edu.auction.domain.auction.AuctionSnapshot;
import vn.edu.auction.domain.auction.AuctionStatus;
import vn.edu.auction.domain.auction.BidTransaction;
import vn.edu.auction.domain.auction.event.Subscription;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.user.Bidder;
import vn.edu.auction.domain.user.Seller;
import vn.edu.auction.domain.user.User;

/** Application operations. UI receives views, never mutable Auction instances. */
public final class DefaultAuctionService implements AuctionService {
  private static final Logger LOGGER = Logger.getLogger(DefaultAuctionService.class.getName());
  private final AuctionRepository manager;
  private final AuthService auth;
  private final Clock clock;
  private final ConcurrentMap<UUID, Item> itemsByAuction = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<ViewObserver> observers = new CopyOnWriteArrayList<>();
  private volatile boolean closed;

  public DefaultAuctionService(AuctionRepository manager, AuthService auth, Clock clock) {
    this.manager = Objects.requireNonNull(manager, "manager");
    this.auth = Objects.requireNonNull(auth, "auth");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public AuctionView createAuction(
      UserSession session, Item item, BigDecimal price, Instant start, Instant end) {
    User actor = requireUser(session);
    if (item == null || price == null || start == null || end == null) {
      throw new DataValidationException("Thiếu sản phẩm, giá hoặc thời gian phiên.");
    }
    if (!(actor instanceof Seller) || !actor.getId().equals(item.getSellerId())) {
      throw new AuthorizationException("Chỉ người bán sở hữu sản phẩm được tạo phiên.");
    }
    Auction auction;
    // Creation checks and insertion share one monitor across service instances.
    // Auction operations still use each Auction's own ReentrantLock.
    synchronized (manager) {
      requireOpen();
      Instant now = clock.instant();
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
      if (!end.isAfter(now)) {
        throw new DataValidationException("Giờ kết thúc phải ở tương lai.");
      }
      boolean active = manager.getAuctions().stream().anyMatch(existing -> {
        AuctionStatus status = existing.getStatus();
        return existing.getItemId().equals(item.getId())
            && (status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING)
            && now.isBefore(existing.getEndTime());
      });
      if (active) {
        throw new InvalidAuctionStateException("Sản phẩm đã có một phiên chưa kết thúc.");
      }
      auction = new Auction(item, price, start, end, clock);
      Subscription subscription = auction.addObserver(event -> publish(
          view(auction, item, event.snapshot())));
      try {
        manager.registerAuction(auction);
      } catch (RuntimeException ex) {
        subscription.close();
        throw ex;
      }
      itemsByAuction.put(auction.getId(), item);
      subscriptions.put(auction.getId(), subscription);
    }
    // Never notify arbitrary callbacks while holding the creation monitor.
    auction.refreshStatus();
    AuctionView result = view(auction, item, auction.snapshot());
    publish(result);
    return result;
  }

  @Override
  public List<AuctionView> listAuctions(UserSession session) {
    requireUser(session);
    List<AuctionView> result = new ArrayList<>();
    for (Auction auction : manager.getAuctions()) {
      Item item = itemsByAuction.get(auction.getId());
      if (item != null) {
        result.add(view(auction, item, auction.snapshot()));
      }
    }
    return List.copyOf(result);
  }

  @Override
  public AuctionView getAuction(UserSession session, UUID auctionId) {
    requireUser(session);
    Auction auction = ownedAuction(auctionId);
    return view(auction, itemsByAuction.get(auctionId), auction.snapshot());
  }

  @Override
  public BidTransaction placeBid(UserSession session, UUID auctionId, BigDecimal amount) {
    User actor = requireUser(session);
    if (!(actor instanceof Bidder bidder)) {
      throw new AuthorizationException("Chỉ Bidder được đặt giá.");
    }
    return ownedAuction(auctionId).placeBid(bidder, amount);
  }

  @Override
  public void markPaid(UserSession session, UUID auctionId) {
    User actor = requireUser(session);
    ownedAuction(auctionId).markPaid(actor);
  }

  @Override
  public void cancel(UserSession session, UUID auctionId, String reason) {
    User actor = requireUser(session);
    ownedAuction(auctionId).cancel(actor, reason);
  }

  @Override
  public Subscription subscribe(UserSession session, Consumer<AuctionView> observer) {
    requireUser(session);
    ViewObserver registration = new ViewObserver(session, Objects.requireNonNull(observer));
    synchronized (manager) {
      requireOpen();
      observers.add(registration);
    }
    return () -> {
      registration.active.set(false);
      observers.remove(registration);
    };
  }

  @Override
  public int refreshAuctions() {
    requireOpen();
    int changed = 0;
    for (UUID id : itemsByAuction.keySet()) {
      try {
        if (manager.getAuction(id).refreshStatus()) {
          changed++;
        }
      } catch (RuntimeException ex) {
        LOGGER.log(Level.WARNING, "Cannot refresh auction " + id, ex);
      }
    }
    return changed;
  }

  private void publish(AuctionView view) {
    if (closed) {
      return;
    }
    for (ViewObserver registration : observers) {
      if (!registration.active.get()) {
        continue;
      }
      if (!auth.isActive(registration.session)) {
        registration.active.set(false);
        observers.remove(registration);
        continue;
      }
      try {
        registration.observer.accept(view);
      } catch (RuntimeException ex) {
        LOGGER.log(Level.WARNING, "View observer failed.", ex);
      }
    }
  }

  private AuctionView view(Auction auction, Item item, AuctionSnapshot snapshot) {
    return new AuctionView(auction.getId(), item.getId(), item.getName(),
        item.getDescription() + " | " + item.describeDetails(), item.getType(),
        item.getSellerId(), auth.displayName(item.getSellerId()), auction.getStartingPrice(),
        auction.getStartTime(), auction.getEndTime(),
        snapshot.leadingBidderId().map(auth::displayName).orElse("Chưa có"),
        snapshot.winnerId().map(auth::displayName).orElse("Chưa có"), snapshot);
  }

  private Auction ownedAuction(UUID id) {
    if (id == null) {
      throw new DataValidationException("Vui lòng chọn một phiên đấu giá.");
    }
    if (!itemsByAuction.containsKey(id)) {
      throw new AuctionNotFoundException();
    }
    return manager.getAuction(id);
  }

  private User requireUser(UserSession session) {
    requireOpen();
    return auth.requireUser(session);
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("Auction service is closed.");
    }
  }

  @Override
  public void close() {
    synchronized (manager) {
      if (closed) {
        return;
      }
      closed = true;
      subscriptions.values().forEach(Subscription::close);
      subscriptions.clear();
      observers.forEach(registration -> registration.active.set(false));
      observers.clear();
    }
  }

  private static final class ViewObserver {
    private final UserSession session;
    private final Consumer<AuctionView> observer;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private ViewObserver(UserSession session, Consumer<AuctionView> observer) {
      this.session = session;
      this.observer = observer;
    }
  }
}
