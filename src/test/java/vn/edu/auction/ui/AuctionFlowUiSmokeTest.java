package vn.edu.auction.ui;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.edu.auction.application.AuctionView;
import vn.edu.auction.bootstrap.AppContext;
import vn.edu.auction.domain.auction.AuctionStatus;
import vn.edu.auction.ui.model.AuctionRow;

/**
 * Opt-in real JavaFX smoke test. Requires a graphical desktop or Xvfb.
 * NOT part of the offline javac suite; do not report it as passed without running.
 */
public final class AuctionFlowUiSmokeTest {
  private AuctionFlowUiSmokeTest() {
  }

  public static void main(String[] args) throws Exception {
    CountDownLatch ready = new CountDownLatch(1);
    Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
    check(ready.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
    AppContext context = new AppContext(Clock.systemUTC());
    List<Stage> stages = new ArrayList<>();
    try {
      Stage first = open(context, stages);
      screenshot(first, "login.png");
      fx(() -> {
        field(first, "usernameField").setText("bidder01");
        field(first, "passwordField").setText("wrong");
        button(first, "loginButton").fire();
        return null;
      });
      waitFor(() -> !((Label) first.getScene().lookup("#errorLabel")).getText().isBlank());
      System.out.println("PASS - Wrong password is displayed without leaving login");
      login(first, "bidder01");
      Stage second = open(context, stages);
      login(second, "bidder02");
      UUID chosen = fx(() -> {
        AuctionRow row = table(first).getItems().stream()
            .filter(value -> value.view().state().status() == AuctionStatus.RUNNING)
            .findFirst().orElseThrow();
        table(first).getSelectionModel().select(row);
        return row.view().id();
      });
      select(second, chosen);
      AuctionView before = fx(() -> table(first).getSelectionModel().getSelectedItem().view());
      BigDecimal offer = before.state().currentPrice().add(BigDecimal.valueOf(10000));
      bid(first, offer);
      waitFor(() -> price(first).compareTo(offer) == 0 && price(second).compareTo(offer) == 0);
      System.out.println("PASS - Accepted bid updates two open windows without manual refresh");
      fx(() -> {
        AuctionRow row = table(first).getSelectionModel().getSelectedItem();
        check(!row.apply(before), "Older snapshot overwrote new row");
        return null;
      });
      System.out.println("PASS - Old snapshot is rejected by UI row version check");
      bid(second, offer);
      waitFor(() -> ((Label) second.getScene().lookup("#messageLabel")).getText()
          .contains("cao h"));
      System.out.println("PASS - Equal bid shows a validation error");
      screenshot(first, "auctions.png");
      Stage seller = open(context, stages);
      login(seller, "seller01");
      Platform.runLater(() -> button(seller, "createButton").fire());
      waitFor(() -> findCreateDialog() != null);
      fx(() -> {
        Window dialog = findCreateDialog();
        text(dialog, "createNameField").setText("UI smoke item");
        text(dialog, "createDescriptionField").setText("Created by the UI test");
        text(dialog, "createPriceField").setText("1000");
        text(dialog, "createDelayField").setText("0");
        text(dialog, "createDurationField").setText("10");
        ((Button) dialog.getScene().lookup("#confirmCreateButton")).fire();
        return null;
      });
      waitFor(() -> table(first).getItems().stream()
          .anyMatch(row -> row.view().name().equals("UI smoke item")));
      UUID newId = fx(() -> table(first).getItems().stream()
          .filter(row -> row.view().name().equals("UI smoke item"))
          .findFirst().orElseThrow().view().id());
      select(first, newId);
      select(seller, newId);
      bid(first, BigDecimal.valueOf(1500));
      waitFor(() -> price(first).compareTo(BigDecimal.valueOf(1500)) == 0);
      waitFor(() -> table(seller).getSelectionModel().getSelectedItem().view().state().status()
          == AuctionStatus.FINISHED);
      fx(() -> { button(seller, "paidButton").fire(); return null; });
      waitFor(() -> table(first).getSelectionModel().getSelectedItem().view().state().status()
          == AuctionStatus.PAID);
      System.out.println("PASS - Seller creates item through Factory, scheduler closes, payment updates UI");
      fx(() -> {
        // Uses the same navigation action as the logout button without a private-controller call.
        Node logout = seller.getScene().getRoot().lookupAll(".button").stream()
            .filter(node -> node instanceof Button b && b.getText().equals("\u0110\u0103ng xu\u1ea5t"))
            .findFirst().orElseThrow();
        ((Button) logout).fire();
        return null;
      });
      waitFor(() -> seller.getScene().lookup("#loginButton") != null);
      System.out.println("PASS - Logout returns to login");
      System.out.println("RESULT: JavaFX smoke flow passed; screenshots are in target/ui-flow-smoke.");
    } finally {
      fx(() -> { stages.forEach(Stage::close); return null; });
      context.close();
      check(context.awaitStopped(Duration.ofSeconds(5)), "Application workers did not stop");
      Platform.exit();
    }
  }

  private static Stage open(AppContext context, List<Stage> stages) throws Exception {
    return fx(() -> {
      Stage stage = new Stage();
      new AppWindow(context, stage).showLogin();
      stages.add(stage);
      return stage;
    });
  }

  private static void login(Stage stage, String username) throws Exception {
    fx(() -> {
      field(stage, "usernameField").setText(username);
      field(stage, "passwordField").setText("demo123");
      button(stage, "loginButton").fire();
      return null;
    });
    waitFor(() -> stage.getScene().lookup("#auctionTable") != null
        && !table(stage).getItems().isEmpty());
  }

  private static void select(Stage stage, UUID id) throws Exception {
    waitFor(() -> table(stage).getItems().stream().anyMatch(row -> row.view().id().equals(id)));
    fx(() -> {
      AuctionRow selected = table(stage).getItems().stream()
          .filter(row -> row.view().id().equals(id)).findFirst().orElseThrow();
      table(stage).getSelectionModel().select(selected);
      return null;
    });
  }

  private static void bid(Stage stage, BigDecimal amount) throws Exception {
    waitFor(() -> !button(stage, "bidButton").isDisabled());
    fx(() -> {
      field(stage, "amountField").setText(amount.toPlainString());
      button(stage, "bidButton").fire();
      return null;
    });
  }

  private static BigDecimal price(Stage stage) {
    return table(stage).getSelectionModel().getSelectedItem().view().state().currentPrice();
  }

  private static Window findCreateDialog() {
    return Window.getWindows().stream()
        .filter(window -> window.getScene() != null
            && window.getScene().lookup("#createNameField") != null)
        .findFirst().orElse(null);
  }

  private static TextField field(Stage stage, String id) {
    return text(stage, id);
  }

  private static TextField text(Window window, String id) {
    return (TextField) window.getScene().lookup("#" + id);
  }

  private static Button button(Stage stage, String id) {
    return (Button) stage.getScene().lookup("#" + id);
  }

  @SuppressWarnings("unchecked")
  private static TableView<AuctionRow> table(Stage stage) {
    return (TableView<AuctionRow>) stage.getScene().lookup("#auctionTable");
  }

  private static <T> T fx(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    Platform.runLater(task);
    return task.get(12, TimeUnit.SECONDS);
  }

  private static void waitFor(Callable<Boolean> condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < deadline) {
      if (fx(condition)) { return; }
      Thread.sleep(25); // Test thread only, never the JavaFX Application Thread.
    }
    throw new AssertionError("Timed out waiting for UI condition");
  }

  private static void screenshot(Stage stage, String name) throws Exception {
    BufferedImage image = fx(() -> {
      WritableImage snapshot = stage.getScene().snapshot(null);
      int width = (int) snapshot.getWidth();
      int height = (int) snapshot.getHeight();
      int[] pixels = new int[width * height];
      snapshot.getPixelReader().getPixels(0, 0, width, height,
          PixelFormat.getIntArgbInstance(), pixels, 0, width);
      BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      result.setRGB(0, 0, width, height, pixels, 0, width);
      return result;
    });
    Path directory = Path.of("target", "ui-flow-smoke");
    Files.createDirectories(directory);
    ImageIO.write(image, "png", directory.resolve(name).toFile());
  }

  private static void check(boolean condition, String message) {
    if (!condition) { throw new AssertionError(message); }
  }
}
