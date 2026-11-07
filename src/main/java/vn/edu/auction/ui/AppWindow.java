package vn.edu.auction.ui;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Screen;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.bootstrap.AppContext;
import vn.edu.auction.ui.controller.AuctionListController;
import vn.edu.auction.ui.controller.LoginController;

/** Owns navigation and the current screen's subscription lifecycle. FX thread only. */
public final class AppWindow {
  private final AppContext context;
  private final Stage stage;
  private AutoCloseable currentController;

  public AppWindow(AppContext context, Stage stage) {
    this.context = Objects.requireNonNull(context);
    this.stage = Objects.requireNonNull(stage);
    stage.setTitle("Auction12 | Tuần 7");
    stage.setOnHidden(event -> disposeController());
  }

  public void showLogin() {
    FXMLLoader loader = loader("/fxml/login-view.fxml");
    Parent root = load(loader);
    disposeController();
    LoginController controller = loader.getController();
    currentController = controller;
    controller.configure(context, this);
    setScene(root, 740, 660, 680, 650);
  }

  public void showAuctions(UserSession session) {
    FXMLLoader loader = loader("/fxml/auction-list-view.fxml");
    Parent root = load(loader);
    disposeController();
    AuctionListController controller = loader.getController();
    currentController = controller;
    controller.configure(context, this, session);
    setScene(root, 1160, 780, 1020, 700);
  }

  public void openAnotherWindow() {
    new AppWindow(context, new Stage()).showLogin();
  }

  public Stage stage() { return stage; }

  private void setScene(Parent root, double width, double height, double minWidth, double minHeight) {
    Scene scene = new Scene(root, width, height);
    URL stylesheet = Objects.requireNonNull(getClass().getResource("/css/app.css"), "app.css");
    scene.getStylesheets().add(stylesheet.toExternalForm());
    var bounds = Screen.getPrimary().getVisualBounds();
    stage.setMinWidth(Math.min(minWidth, bounds.getWidth() - 32));
    stage.setMinHeight(Math.min(minHeight, bounds.getHeight() - 32));
    stage.setScene(scene);
    stage.sizeToScene();
    stage.show();
    // Dimensions are defined after showing the first native window.
    stage.setWidth(Math.min(stage.getWidth(), bounds.getWidth() - 32));
    stage.setHeight(Math.min(stage.getHeight(), bounds.getHeight() - 32));
  }

  private static FXMLLoader loader(String path) {
    return new FXMLLoader(Objects.requireNonNull(AppWindow.class.getResource(path), path));
  }

  private static Parent load(FXMLLoader loader) {
    try {
      return loader.load();
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot load FXML: " + loader.getLocation(), ex);
    }
  }

  private void disposeController() {
    AutoCloseable old = currentController;
    currentController = null;
    if (old != null) {
      try {
        old.close();
      } catch (Exception ex) {
        throw new IllegalStateException("Cannot dispose screen", ex);
      }
    }
  }
}
