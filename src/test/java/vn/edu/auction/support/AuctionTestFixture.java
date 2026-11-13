package vn.edu.auction.support;

import java.math.BigDecimal;
import java.time.Instant;
import vn.edu.auction.application.AuctionCreationService;
import vn.edu.auction.application.AuctionView;
import vn.edu.auction.application.DefaultAuctionService;
import vn.edu.auction.application.auth.DemoAuthService;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.application.form.CreateAuctionInput;
import vn.edu.auction.domain.auction.Auction;
import vn.edu.auction.domain.factory.ElectronicsFactory;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.item.ItemType;
import vn.edu.auction.domain.user.Bidder;
import vn.edu.auction.domain.user.Seller;

public final class AuctionTestFixture implements AutoCloseable {
  public static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
  public static final Instant END = START.plusSeconds(60);
  public final MutableClock clock = new MutableClock(START);
  public final InMemoryTestRepository repository = new InMemoryTestRepository();
  public final DemoAuthService auth = new DemoAuthService();
  public final DefaultAuctionService service = new DefaultAuctionService(repository, auth, clock);
  public final AuctionCreationService creation = new AuctionCreationService(service, auth, clock);
  public final UserSession sellerSession = auth.login("seller01", "demo123".toCharArray());
  public final UserSession bidderSession = auth.login("bidder01", "demo123".toCharArray());
  public final UserSession otherSession = auth.login("bidder02", "demo123".toCharArray());
  public final UserSession adminSession = auth.login("admin01", "demo123".toCharArray());
  public final Seller seller = (Seller) auth.requireUser(sellerSession);
  public final Bidder bidder = (Bidder) auth.requireUser(bidderSession);
  public final Bidder other = (Bidder) auth.requireUser(otherSession);
  public final Item item = new ElectronicsFactory("Dell", "Demo", 6)
      .createItem(seller.getId(), "Laptop", "Test description");
  public final Auction auction = new Auction(item, money(100), START, END, clock);

  public static BigDecimal money(long amount) { return BigDecimal.valueOf(amount); }

  public AuctionView create() {
    return service.createAuction(sellerSession, item, money(100), START, END);
  }

  public static CreateAuctionInput input() {
    return new CreateAuctionInput(ItemType.ELECTRONICS, "Laptop", "Demo item", "Dell", "Demo",
        "6", "0", "100", "0", "60");
  }

  @Override
  public void close() {
    service.close();
    auth.close();
  }
}
