package vn.edu.auction.ui.controller;

import java.util.Arrays;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.bootstrap.AppContext;
import vn.edu.auction.ui.AppWindow;

public final class LoginController implements AutoCloseable {
  @FXML private TextField usernameField;
  @FXML private PasswordField passwordField;
  @FXML private Button loginButton;
  @FXML private Label errorLabel;
  @FXML private ProgressIndicator progressIndicator;
  private AppContext context;
  private AppWindow window;
  private boolean disposed;
  private boolean busy;

  public void configure(AppContext context, AppWindow window) {
    this.context = context;
    this.window = window;
    usernameField.setText("bidder01");
    errorLabel.setText("");
    setBusy(false);
  }

  @FXML
  private void handleLogin() {
    if (disposed || busy) { return; }
    String username = usernameField.getText();
    char[] password = passwordField.getText().toCharArray();
    passwordField.clear();
    setBusy(true);
    errorLabel.setText("");
    Task<UserSession> task = new Task<>() {
      @Override
      protected UserSession call() {
        try {
          return context.auth().login(username, password);
        } finally {
          Arrays.fill(password, '\0');
        }
      }
    };
    task.setOnSucceeded(event -> {
      if (disposed) {
        context.auth().logout(task.getValue());
        return;
      }
      setBusy(false);
      try {
        window.showAuctions(task.getValue());
      } catch (RuntimeException ex) {
        context.auth().logout(task.getValue());
        errorLabel.setText("Không tải được màn hình: " + ex.getMessage());
        ex.printStackTrace();
      }
    });
    task.setOnFailed(event -> {
      if (!disposed) {
        setBusy(false);
        errorLabel.setText(task.getException().getMessage());
      }
    });
    try {
      context.workers().execute(task);
    } catch (RuntimeException ex) {
      Arrays.fill(password, '\0');
      setBusy(false);
      errorLabel.setText(ex.getMessage());
    }
  }

  private void setBusy(boolean value) {
    busy = value;
    loginButton.setDisable(value);
    usernameField.setDisable(value);
    passwordField.setDisable(value);
    progressIndicator.setVisible(value);
  }

  @Override
  public void close() {
    disposed = true;
    passwordField.clear();
  }
}
