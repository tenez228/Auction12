package vn.edu.auction.ui.model;

import java.util.Objects;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import vn.edu.auction.application.AuctionView;

/** FX-thread model: update properties in place so selection is preserved. */
public final class AuctionRow {
  private AuctionView view;
  private final StringProperty name = new SimpleStringProperty();
  private final StringProperty price = new SimpleStringProperty();
  private final StringProperty status = new SimpleStringProperty();
  private final StringProperty end = new SimpleStringProperty();

  public AuctionRow(AuctionView initial) {
    apply(Objects.requireNonNull(initial));
  }

  public boolean apply(AuctionView next) {
    Objects.requireNonNull(next);
    if (view != null) {
      if (!view.id().equals(next.id())) {
        throw new IllegalArgumentException("Different auction IDs");
      }
      if (next.state().version() <= view.state().version()) {
        return false;
      }
    }
    view = next;
    name.set(next.name());
    price.set(UiFormats.money(next.state().currentPrice()));
    status.set(UiFormats.status(next.state().status()));
    end.set(UiFormats.time(next.endTime()));
    return true;
  }

  public AuctionView view() { return view; }
  public ReadOnlyStringProperty nameProperty() { return name; }
  public ReadOnlyStringProperty priceProperty() { return price; }
  public ReadOnlyStringProperty statusProperty() { return status; }
  public ReadOnlyStringProperty endProperty() { return end; }
}
