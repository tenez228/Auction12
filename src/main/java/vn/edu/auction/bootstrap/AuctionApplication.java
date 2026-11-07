package vn.edu.auction.bootstrap;

import java.time.Clock;
import javafx.application.Application;
import javafx.stage.Stage;
import vn.edu.auction.ui.AppWindow;

public final class AuctionApplication extends Application {
  private AppContext context;

  @Override
  public void start(Stage stage) {
    context = new AppContext(Clock.systemUTC());
    new AppWindow(context, stage).showLogin();
  }

  @Override
  public void stop() {
    if (context != null) {
      context.close();
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
