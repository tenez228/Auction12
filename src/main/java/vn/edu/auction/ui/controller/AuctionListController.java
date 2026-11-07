package vn.edu.auction.ui.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import vn.edu.auction.application.AuctionView;
import vn.edu.auction.application.auth.UserProfile;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.bootstrap.AppContext;
import vn.edu.auction.domain.auction.AuctionStatus;
import vn.edu.auction.domain.auction.event.Subscription;
import vn.edu.auction.domain.user.UserRole;
import vn.edu.auction.ui.AppWindow;
import vn.edu.auction.ui.model.AuctionRow;
import vn.edu.auction.ui.model.UiFormats;

/** View orchestration only. Every business mutation goes through AuctionService. */
public final class AuctionListController implements AutoCloseable {
  @FXML private Label userLabel;
  @FXML private Label countLabel;
  @FXML private Label selectionLabel;
  @FXML private Label priceLabel;
  @FXML private Label detailsLabel;
  @FXML private Label ownerLabel;
  @FXML private Label startLabel;
  @FXML private Label endLabel;
  @FXML private Label stateLabel;
  @FXML private Label leaderLabel;
  @FXML private Label winnerLabel;
  @FXML private Label remainingLabel;
  @FXML private Label messageLabel;
  @FXML private Label updatedLabel;
  @FXML private TextField searchField;
  @FXML private TextField amountField;
  @FXML private TextField reasonField;
  @FXML private TextArea historyArea;
  @FXML private Button bidButton;
  @FXML private Button createButton;
  @FXML private Button paidButton;
  @FXML private Button cancelButton;
  @FXML private TableView<AuctionRow> auctionTable;
  @FXML private TableColumn<AuctionRow, String> nameColumn;
  @FXML private TableColumn<AuctionRow, String> priceColumn;
  @FXML private TableColumn<AuctionRow, String> stateColumn;
  @FXML private TableColumn<AuctionRow, String> endColumn;
  private final ObservableList<AuctionRow> rows = FXCollections.observableArrayList();
  private final Map<UUID, AuctionRow> byId = new HashMap<>();
  private final ConcurrentHashMap<UUID, AuctionView> pending = new ConcurrentHashMap<>();
  private final AtomicBoolean flushScheduled = new AtomicBoolean();
  private AppContext context;
  private AppWindow window;
  private UserSession session;
  private UserProfile profile;
  private Subscription subscription;
  private Timeline countdown;
  private volatile boolean disposed;
  private boolean busy;

  @FXML
  private void initialize() {
    nameColumn.setCellValueFactory(cell -> cell.getValue().nameProperty());
    priceColumn.setCellValueFactory(cell -> cell.getValue().priceProperty());
    stateColumn.setCellValueFactory(cell -> cell.getValue().statusProperty());
    endColumn.setCellValueFactory(cell -> cell.getValue().endProperty());
    auctionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    FilteredList<AuctionRow> filtered = new FilteredList<>(rows, row -> true);
    SortedList<AuctionRow> sorted = new SortedList<>(filtered);
    sorted.comparatorProperty().bind(auctionTable.comparatorProperty());
    auctionTable.setItems(sorted);
    priceColumn.setComparator((left, right) ->
        new BigDecimal(left.replaceAll("[^0-9]", ""))
            .compareTo(new BigDecimal(right.replaceAll("[^0-9]", ""))));
    endColumn.setSortable(false);
    auctionTable.setPlaceholder(new Label("Chưa có phiên phù hợp."));
    searchField.textProperty().addListener((observable, old, value) -> {
      String query = value.strip().toLowerCase(Locale.ROOT);
      filtered.setPredicate(row -> row.view().name().toLowerCase(Locale.ROOT).contains(query));
    });
    auctionTable.getSelectionModel().selectedItemProperty()
        .addListener((observable, old, selected) -> updateDetails());
  }

  public void configure(AppContext context, AppWindow window, UserSession session) {
    this.context = context;
    this.window = window;
    this.session = session;
    profile = context.auth().profile(session);
    userLabel.setText(profile.displayName() + " | " + profile.role().getLabel());
    boolean seller = profile.role() == UserRole.SELLER;
    createButton.setVisible(seller);
    createButton.setManaged(seller);
    boolean settlement = profile.role() != UserRole.BIDDER;
    paidButton.setVisible(settlement);
    paidButton.setManaged(settlement);
    cancelButton.setVisible(settlement);
    cancelButton.setManaged(settlement);
    reasonField.setVisible(settlement);
    reasonField.setManaged(settlement);
    subscription = context.auctions().subscribe(session, this::queueView);
    countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateCountdown()));
    countdown.setCycleCount(Timeline.INDEFINITE);
    countdown.play();
    updateDetails();
    // Subscribe BEFORE reading the initial list. Version checks reject stale initial rows.
    submit(() -> context.auctions().listAuctions(session), views -> {
      views.forEach(this::queueView);
      showMessage("Đã tải danh sách. Thay đổi sẽ tự cập nhật.", false);
    });
  }

  private void queueView(AuctionView view) {
    if (disposed) { return; }
    pending.merge(view.id(), view, (old, next) ->
        next.state().version() > old.state().version() ? next : old);
    scheduleFlush();
  }

  private void scheduleFlush() {
    if (!disposed && flushScheduled.compareAndSet(false, true)) {
      Platform.runLater(this::flushViews);
    }
  }

  private void flushViews() {
    try {
      if (disposed || !context.auth().isActive(session)) {
        pending.clear();
        return;
      }
      Map<UUID, AuctionView> batch = new HashMap<>(pending);
      batch.forEach((id, view) -> {
        if (pending.remove(id, view)) {
          AuctionRow row = byId.get(id);
          if (row == null) {
            row = new AuctionRow(view);
            byId.put(id, row);
            rows.add(row);
          } else {
            row.apply(view);
          }
        }
      });
      countLabel.setText(rows.size() + " phiên");
      if (auctionTable.getSelectionModel().getSelectedItem() == null && !rows.isEmpty()) {
        auctionTable.getSelectionModel().selectFirst();
      }
      updatedLabel.setText("Cập nhật: " + UiFormats.time(context.clock().instant()));
      updateDetails();
    } finally {
      flushScheduled.set(false);
      if (!pending.isEmpty() && !disposed) { scheduleFlush(); }
    }
  }

  @FXML
  private void handleBid() {
    AuctionView selected = selection();
    if (selected == null || busy) { return; }
    try {
      BigDecimal amount = UiFormats.parseMoney(amountField.getText());
      submit(() -> context.auctions().placeBid(session, selected.id(), amount), bid -> {
        amountField.clear();
        showMessage("Đã chấp nhận giá " + UiFormats.money(bid.getAmount()), false);
      });
    } catch (IllegalArgumentException ex) {
      showMessage(ex.getMessage(), true);
    }
  }

  @FXML
  private void handleCreate() {
    if (busy) { return; }
    CreateAuctionDialog.show(window.stage(), profile.id()).ifPresent(request ->
        submit(() -> {
          Instant start = context.clock().instant().plus(request.delay());
          return context.auctions().createAuction(session, request.item(), request.price(),
              start, start.plus(request.duration()));
        }, view -> showMessage("Đã tạo phiên: " + view.name(), false)));
  }

  @FXML
  private void handlePaid() {
    AuctionView selected = selection();
    if (selected != null && !busy) {
      submit(() -> {
        context.auctions().markPaid(session, selected.id());
        return true;
      }, ignored -> showMessage("Đã ghi nhận thanh toán (demo, không chuyển tiền).", false));
    }
  }

  @FXML
  private void handleCancel() {
    AuctionView selected = selection();
    if (selected != null && !busy) {
      String reason = reasonField.getText();
      submit(() -> {
        context.auctions().cancel(session, selected.id(), reason);
        return true;
      }, ignored -> showMessage("Đã hủy phiên.", false));
    }
  }

  @FXML
  private void handleLogout() { window.showLogin(); }

  @FXML
  private void handleNewWindow() { window.openAnotherWindow(); }

  private <T> void submit(Callable<T> operation, Consumer<T> success) {
    if (disposed) { return; }
    busy = true;
    updateButtons();
    showMessage("Đang xử lý...", false);
    Task<T> task = new Task<>() {
      @Override
      protected T call() throws Exception { return operation.call(); }
    };
    task.setOnSucceeded(event -> {
      if (!disposed) {
        busy = false;
        updateButtons();
        success.accept(task.getValue());
      }
    });
    task.setOnFailed(event -> {
      if (!disposed) {
        busy = false;
        updateButtons();
        String message = task.getException().getMessage();
        showMessage(message == null ? "Không thực hiện được thao tác." : message, true);
      }
    });
    try {
      context.workers().execute(task);
    } catch (RuntimeException ex) {
      busy = false;
      updateButtons();
      showMessage(ex.getMessage(), true);
    }
  }

  private AuctionView selection() {
    AuctionRow row = auctionTable.getSelectionModel().getSelectedItem();
    return row == null ? null : row.view();
  }

  private void updateDetails() {
    if (context == null) { return; }
    AuctionView selected = selection();
    if (selected == null) {
      selectionLabel.setText("Chọn một phiên");
      priceLabel.setText("--");
      detailsLabel.setText("");
      ownerLabel.setText("");
      startLabel.setText("");
      endLabel.setText("");
      stateLabel.setText("");
      leaderLabel.setText("");
      winnerLabel.setText("");
      historyArea.clear();
    } else {
      selectionLabel.setText(selected.name());
      priceLabel.setText(UiFormats.money(selected.state().currentPrice()));
      detailsLabel.setText(selected.details());
      ownerLabel.setText("Người bán: " + selected.sellerName());
      startLabel.setText("Bắt đầu: " + UiFormats.time(selected.startTime()));
      endLabel.setText("Kết thúc: " + UiFormats.time(selected.endTime()));
      stateLabel.setText(UiFormats.status(selected.state().status()));
      leaderLabel.setText("Dẫn đầu: " + selected.leaderName());
      winnerLabel.setText("Kết quả: " + selected.winnerName()
          + selected.state().cancellationReason().map(reason -> " | " + reason).orElse(""));
      var history = selected.state().bidHistory();
      String text = history.stream().skip(Math.max(0, history.size() - 30))
          .map(bid -> UiFormats.time(bid.getPlacedAt()) + " | "
              + context.auth().displayName(bid.getBidderId()) + " | " + UiFormats.money(bid.getAmount()))
          .collect(Collectors.joining("\n"));
      historyArea.setText(text.isEmpty() ? "Chưa có lượt đặt giá." : text);
    }
    updateCountdown();
    updateButtons();
  }

  private void updateCountdown() {
    AuctionView selected = selection();
    if (selected == null || context == null) {
      remainingLabel.setText("");
      return;
    }
    Instant now = context.clock().instant();
    if (selected.state().status() == AuctionStatus.OPEN) {
      long seconds = Math.max(0, java.time.Duration.between(now, selected.startTime()).toSeconds());
      remainingLabel.setText("Bắt đầu sau: " + seconds + " giây");
    } else if (selected.state().status() == AuctionStatus.RUNNING) {
      long seconds = Math.max(0, java.time.Duration.between(now, selected.endTime()).toSeconds());
      remainingLabel.setText("Còn: " + seconds + " giây");
    } else {
      remainingLabel.setText("Đã đóng nhận giá");
    }
  }

  private void updateButtons() {
    if (profile == null) { return; }
    AuctionView selected = selection();
    boolean running = selected != null && selected.state().status() == AuctionStatus.RUNNING;
    boolean finished = selected != null && selected.state().status() == AuctionStatus.FINISHED;
    boolean manager = selected != null && (profile.role() == UserRole.ADMIN
        || (profile.role() == UserRole.SELLER && profile.id().equals(selected.sellerId())));
    bidButton.setDisable(busy || !running || profile.role() != UserRole.BIDDER);
    amountField.setDisable(busy || !running || profile.role() != UserRole.BIDDER);
    createButton.setDisable(busy || profile.role() != UserRole.SELLER);
    paidButton.setDisable(busy || !finished || !manager || selected.state().winnerId().isEmpty());
    cancelButton.setDisable(busy || !finished || !manager);
    reasonField.setDisable(busy || !finished || !manager);
  }

  private void showMessage(String message, boolean error) {
    messageLabel.setText(message);
    messageLabel.setStyle(error ? "-fx-text-fill: #b42318;" : "-fx-text-fill: #16624f;");
  }

  @Override
  public void close() {
    disposed = true;
    if (subscription != null) { subscription.close(); }
    if (countdown != null) { countdown.stop(); }
    pending.clear();
    if (context != null) { context.auth().logout(session); }
  }
}
