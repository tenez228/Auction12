package vn.edu.auction.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import vn.edu.auction.application.auth.AuthService;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.application.form.AuctionDraft;
import vn.edu.auction.application.form.AuctionFormParser;
import vn.edu.auction.application.form.CreateAuctionInput;
import vn.edu.auction.domain.exception.AuthorizationException;
import vn.edu.auction.domain.exception.DataValidationException;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.user.Seller;
import vn.edu.auction.domain.user.User;

/** Creates a product and a timed auction on behalf of the authenticated Seller. */
public final class AuctionCreationService {
  private final AuctionService auctions;
  private final AuthService auth;
  private final Clock clock;

  public AuctionCreationService(AuctionService auctions, AuthService auth, Clock clock) {
    this.auctions = Objects.requireNonNull(auctions);
    this.auth = Objects.requireNonNull(auth);
    this.clock = Objects.requireNonNull(clock);
  }

  public AuctionView create(UserSession session, CreateAuctionInput input) {
    requireSeller(session);
    return create(session, AuctionFormParser.parse(input));
  }

  public AuctionView create(UserSession session, AuctionDraft draft) {
    Seller seller = requireSeller(session);
    if (draft == null) {
      throw new DataValidationException("Thiếu thông tin phiên đấu giá.");
    }
    Item item = draft.factory().createItem(seller.getId(), draft.name(), draft.description());
    Instant start = clock.instant().plus(draft.delay());
    return auctions.createAuction(session, item, draft.price(), start, start.plus(draft.duration()));
  }

  private Seller requireSeller(UserSession session) {
    User user = auth.requireUser(session);
    if (!(user instanceof Seller seller)) {
      throw new AuthorizationException("Chỉ Seller được tạo phiên đấu giá.");
    }
    return seller;
  }
}
