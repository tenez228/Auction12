package vn.edu.auction.ui.controller;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import vn.edu.auction.domain.factory.ArtFactory;
import vn.edu.auction.domain.factory.ElectronicsFactory;
import vn.edu.auction.domain.factory.ItemFactory;
import vn.edu.auction.domain.factory.VehicleFactory;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.item.ItemType;
import vn.edu.auction.ui.model.UiFormats;

/** Small Seller form. Domain and service validate again before registration. */
public final class CreateAuctionDialog {
  private CreateAuctionDialog() {
  }

  public record Request(Item item, BigDecimal price, Duration delay, Duration duration) {
  }

  public static Optional<Request> show(Window owner, UUID sellerId) {
    Dialog<Request> dialog = new Dialog<>();
    dialog.initOwner(owner);
    dialog.setTitle("Tạo phiên đấu giá");
    dialog.setHeaderText("Thông tin sản phẩm và thời gian");
    ButtonType create = new ButtonType("Tạo phiên", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
    GridPane grid = new GridPane();
    grid.setHgap(14);
    grid.setVgap(10);
    grid.setPadding(new Insets(16));
    ComboBox<ItemType> type = new ComboBox<>();
    type.getItems().setAll(ItemType.values());
    type.setValue(ItemType.ELECTRONICS);
    TextField name = new TextField();
    TextField description = new TextField();
    TextField detailOne = new TextField("Dell");
    TextField detailTwo = new TextField("Latitude");
    TextField numberOne = new TextField("6");
    TextField numberTwo = new TextField("0");
    TextField price = new TextField("1000000");
    TextField delay = new TextField("5");
    TextField duration = new TextField("120");
    type.setId("createTypeField");
    name.setId("createNameField");
    description.setId("createDescriptionField");
    price.setId("createPriceField");
    delay.setId("createDelayField");
    duration.setId("createDurationField");
    name.setPrefColumnCount(26);
    name.setPromptText("Tên sản phẩm");
    description.setPromptText("Mô tả ngắn");
    Label detailOneLabel = new Label("Hãng");
    Label detailTwoLabel = new Label("Model");
    Label numberOneLabel = new Label("Bảo hành (tháng)");
    Label numberTwoLabel = new Label("Kilomet đã đi");
    numberTwo.setDisable(true);
    type.valueProperty().addListener((observable, oldValue, selected) -> {
      boolean art = selected == ItemType.ART;
      boolean vehicle = selected == ItemType.VEHICLE;
      detailOneLabel.setText(art ? "Tác giả" : "Hãng");
      detailTwoLabel.setText(art ? "Chất liệu" : "Model");
      numberOneLabel.setText(vehicle ? "Năm sản xuất" : "Bảo hành (tháng)");
      numberOne.setDisable(art);
      numberTwo.setDisable(!vehicle);
      numberOne.setText(vehicle ? "2020" : "6");
      numberTwo.setText(vehicle ? "15000" : "0");
      detailOne.clear();
      detailTwo.clear();
    });
    grid.addRow(0, new Label("Loại sản phẩm"), type);
    grid.addRow(1, new Label("Tên"), name);
    grid.addRow(2, new Label("Mô tả"), description);
    grid.addRow(3, detailOneLabel, detailOne);
    grid.addRow(4, detailTwoLabel, detailTwo);
    grid.addRow(5, numberOneLabel, numberOne);
    grid.addRow(6, numberTwoLabel, numberTwo);
    grid.addRow(7, new Label("Giá khởi điểm (VND)"), price);
    grid.addRow(8, new Label("Bắt đầu sau (giây)"), delay);
    grid.addRow(9, new Label("Thời lượng (giây)"), duration);
    Label error = new Label();
    error.setWrapText(true);
    error.setMaxWidth(440);
    error.setStyle("-fx-text-fill: #b42318;");
    grid.add(error, 0, 10, 2, 1);
    dialog.getDialogPane().setContent(grid);
    AtomicReference<Request> accepted = new AtomicReference<>();
    Button createButton = (Button) dialog.getDialogPane().lookupButton(create);
    createButton.setId("confirmCreateButton");
    createButton.addEventFilter(ActionEvent.ACTION, event -> {
      accepted.set(null);
      try {
        ItemFactory<? extends Item> factory = switch (type.getValue()) {
          case ELECTRONICS -> new ElectronicsFactory(
              detailOne.getText(), detailTwo.getText(), parseInt(numberOne.getText(), "Bảo hành"));
          case ART -> new ArtFactory(detailOne.getText(), detailTwo.getText());
          case VEHICLE -> new VehicleFactory(detailOne.getText(), detailTwo.getText(),
              parseInt(numberOne.getText(), "Năm"), parseInt(numberTwo.getText(), "Kilomet"));
        };
        int delaySeconds = parseInt(delay.getText(), "Thời gian chờ");
        int durationSeconds = parseInt(duration.getText(), "Thời lượng");
        if (delaySeconds < 0 || delaySeconds > 86400
            || durationSeconds < 1 || durationSeconds > 86400) {
          throw new IllegalArgumentException("Chờ: 0..86400 giây; thời lượng: 1..86400 giây.");
        }
        Item item = factory.createItem(sellerId, name.getText(), description.getText());
        accepted.set(new Request(item, UiFormats.parseMoney(price.getText()),
            Duration.ofSeconds(delaySeconds), Duration.ofSeconds(durationSeconds)));
      } catch (RuntimeException ex) {
        error.setText(ex.getMessage());
        event.consume();
      }
    });
    dialog.setResultConverter(button -> button == create ? accepted.get() : null);
    return dialog.showAndWait();
  }

  private static int parseInt(String text, String name) {
    try {
      return Integer.parseInt(text.strip());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(name + " phải là số nguyên hợp lệ.", ex);
    }
  }
}
