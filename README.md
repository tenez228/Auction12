<div align="center">

# Hệ thống Đấu giá Trực tuyến Auction12

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-green.svg)](https://openjfx.io/)

**Bài tập lớn môn Lập trình Nâng cao - Trường Đại học Công nghệ - ĐHQGHN**  
**Giai đoạn Tuần 7: Đa luồng, Observer Pattern & Giao diện JavaFX**

[Mô tả](#mô-tả-hệ-thống) • [Công nghệ](#công-nghệ-sử-dụng) • [Cấu trúc Thư mục](#cấu-trúc-thư-mục) • [Cài đặt & Chạy](#cài-đặt-và-hướng-dẫn-chạy) • [Chức năng Đã làm](#danh-sách-chức-năng-đã-hoàn-thành-tuần-7) • [Design Patterns](#design-patterns-áp-dụng)

</div>

---

## Mô tả Hệ thống

**Auction12** là hệ thống đấu giá trực tuyến được phát triển bằng ngôn ngữ **Java 21**, áp dụng các nguyên lý lập trình hướng đối tượng (OOP), mô hình thiết kế **Observer Pattern**, cơ chế xử lý đa luồng và đồng thời (**Concurrency**) để đảm bảo an toàn dữ liệu khi có nhiều người cùng đặt giá, cùng với giao diện đồ họa xây dựng trên nền tảng **JavaFX**.

### Phạm vi Hệ thống Hiện tại (Mục tiêu Tuần 7)

- **Quản lý người dùng đa vai trò**: Phân quyền giữa `Bidder` (người đấu giá), `Seller` (người bán) và `Admin` (quản trị viên).
- **Quản lý sản phẩm & tạo phiên**: Hỗ trợ nhiều dòng sản phẩm (`Electronics`, `Art`, `Vehicle`) thông qua Factory Method; người bán thiết lập giá khởi điểm và thời gian đấu giá.
- **Đấu giá đa luồng thời gian thực**: Cơ chế đặt giá an toàn luồng với `ReentrantLock` độc lập cho từng phiên, ngăn ngừa triệt để lỗi *Race Condition* và *Lost Update*.
- **Vòng đời & Quản lý trạng thái tự động**: Tự động chuyển đổi `OPEN` ➔ `RUNNING` ➔ `FINISHED` ➔ `PAID` / `CANCELED` theo thời gian thực nhờ bộ lập lịch `AuctionScheduler`.
- **Cập nhật tức thời qua Observer Pattern**: Tự động phát sự kiện (`AuctionEvent`) khi có lượt đặt giá mới hoặc chuyển trạng thái, giúp giao diện người dùng tự làm mới không cần tải lại thủ công.
- **Giao diện người dùng JavaFX**: Màn hình Đăng nhập và Danh sách phiên đấu giá trực quan, hỗ trợ mở nhiều cửa sổ đồng thời trong cùng phiên để kiểm thử tương tác thời gian thực giữa nhiều tài khoản.

---

## Công nghệ Sử dụng

### Core & Nghiệp vụ (Domain & Application)
- **Java 21** - Ngôn ngữ lập trình chính (sử dụng Sealed classes, Records, Pattern Matching, Switch expressions).
- **Concurrency API** - `ReentrantLock`, `ScheduledExecutorService`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, `ConcurrentLinkedQueue`, `AtomicBoolean`.
- **Observer Pattern** - Hệ thống phân phối sự kiện bất đồng bộ an toàn luồng (`AuctionObserver`, `AuctionEvent`, `Subscription`).

### Giao diện Người dùng (UI)
- **JavaFX 21** (`javafx-controls`, `javafx-fxml`) - Framework giao diện đồ họa.
- **FXML & CSS** - Thiết kế bố cục giao diện khai báo và định dạng kiểu dáng.
- **JavaFX Concurrency** - Tách rời các tác vụ tính toán sang `Task` / Background Thread và cập nhật UI an toàn bằng `Platform.runLater()`.

### Build & Kiểm thử
- **Maven 3.8+** - Quản lý phụ thuộc và quy trình build (`maven-compiler-plugin`, `exec-maven-plugin`, `javafx-maven-plugin`).
- **Standalone Self-Test Suite** - Bộ kiểm thử tự động độc lập chuyên sâu gồm 156+ test cases (không phụ thuộc vào thư viện ngoài).

---

## Cấu trúc Thư mục

Dưới đây là cây thư mục thực tế đầy đủ của dự án:

```
Auction12/
├── docs/
│   └── week7/                      # Tài liệu thiết kế, quyết định kỹ thuật & đối chiếu
│       ├── ARCHITECTURE.md         # Luồng hoạt động, xử lý đa luồng & Observer
│       ├── CHANGES.md              # Chi tiết thay đổi mã nguồn Tuần 7
│       ├── DECISIONS.md            # Các quyết định thiết kế hệ thống
│       ├── MANUAL_TESTS.md         # Hướng dẫn và checklist kiểm thử thủ công
│       ├── POM-MERGE.md            # Hướng dẫn tích hợp cấu hình Maven & JavaFX
│       ├── TASKS.md                # Bảng đối chiếu các nhiệm vụ Tuần 7
│       └── VERIFICATION.md         # Báo cáo kết quả kiểm thử và độ bao phủ
│
├── src/
│   ├── main/
│   │   ├── java/vn/edu/auction/
│   │   │   ├── application/        # Tầng nghiệp vụ ứng dụng & dịch vụ
│   │   │   │   ├── auth/           # Xác thực tài khoản demo & quản lý phiên
│   │   │   │   │   ├── DemoAuthService.java
│   │   │   │   │   ├── UserProfile.java
│   │   │   │   │   └── UserSession.java
│   │   │   │   ├── AuctionManager.java          # Registry quản lý danh mục phiên đấu giá
│   │   │   │   ├── AuctionScheduler.java        # Bộ lập lịch cập nhật trạng thái tự động
│   │   │   │   ├── AuctionService.java          # Interface nghiệp vụ cho UI
│   │   │   │   ├── AuctionView.java             # DTO bất biến trả về cho tầng hiển thị
│   │   │   │   └── DefaultAuctionService.java   # Triển khai dịch vụ với synchronized & observer
│   │   │   │
│   │   │   ├── bootstrap/          # Khởi tạo DI, ứng dụng JavaFX & Demo console
│   │   │   │   ├── AppContext.java              # Quản lý vòng đời dịch vụ & nạp dữ liệu mẫu
│   │   │   │   ├── AuctionApplication.java      # Điểm khởi chạy chính của JavaFX GUI
│   │   │   │   ├── AuctionLauncher.java         # Launcher phụ
│   │   │   │   ├── AuctionManagerDemo.java      # Demo quản lý phiên
│   │   │   │   ├── AuctionServiceDemo.java      # Demo dịch vụ đấu giá
│   │   │   │   ├── DomainDemo.java              # Demo domain model
│   │   │   │   └── UserDemo.java                # Demo user
│   │   │   │
│   │   │   ├── domain/             # Mô hình nghiệp vụ cốt lõi (Domain Model)
│   │   │   │   ├── Entity.java                  # Lớp trừu tượng cơ sở quản lý ID và equals
│   │   │   │   ├── auction/                     # Nghiệp vụ phiên đấu giá & Observer
│   │   │   │   │   ├── event/                   # Hệ thống phân phối sự kiện (Observer Pattern)
│   │   │   │   │   │   ├── AuctionEvent.java
│   │   │   │   │   │   ├── AuctionEventType.java
│   │   │   │   │   │   ├── AuctionObserver.java
│   │   │   │   │   │   └── Subscription.java
│   │   │   │   │   ├── Auction.java             # Quản lý phiên đấu giá, ReentrantLock, đặt giá
│   │   │   │   │   ├── AuctionSnapshot.java     # Bản sao bất biến trạng thái phiên
│   │   │   │   │   ├── AuctionStatus.java       # Enum: OPEN, RUNNING, FINISHED, PAID, CANCELED
│   │   │   │   │   └── BidTransaction.java      # Giao dịch đặt giá hợp lệ
│   │   │   │   ├── factory/                     # Factory Method khởi tạo sản phẩm
│   │   │   │   │   ├── ArtFactory.java
│   │   │   │   │   ├── ElectronicsFactory.java
│   │   │   │   │   ├── ItemFactory.java
│   │   │   │   │   └── VehicleFactory.java
│   │   │   │   ├── item/                        # Thực thể sản phẩm đấu giá
│   │   │   │   │   ├── Art.java
│   │   │   │   │   ├── Electronics.java
│   │   │   │   │   ├── Item.java
│   │   │   │   │   ├── ItemType.java
│   │   │   │   │   └── Vehicle.java
│   │   │   │   └── user/                        # Thực thể người dùng & phân quyền
│   │   │   │       ├── Admin.java
│   │   │   │       ├── Bidder.java
│   │   │   │       ├── Seller.java
│   │   │   │       ├── User.java
│   │   │   │       └── UserRole.java
│   │   │   │
│   │   │   └── ui/                 # Giao diện đồ họa người dùng JavaFX (MVC View & Controller)
│   │   │       ├── controller/     # Điều khiển giao diện
│   │   │       │   ├── AuctionListController.java
│   │   │       │   ├── CreateAuctionDialog.java
│   │   │       │   └── LoginController.java
│   │   │       ├── model/          # Mô hình hiển thị cho UI
│   │   │       │   ├── AuctionRow.java          # Wrapper dòng bảng kèm version check
│   │   │       │   └── UiFormats.java           # Định dạng hiển thị tiền VND & thời gian
│   │   │       └── AppWindow.java               # Quản lý cửa sổ và chuyển đổi Scene
│   │   │
│   │   └── resources/
│   │       ├── css/
│   │       │   └── app.css                      # Định dạng stylesheet giao diện
│   │       └── fxml/
│   │           ├── auction-list-view.fxml       # Layout màn hình danh sách & đặt giá
│   │           └── login-view.fxml              # Layout màn hình đăng nhập
│   │
│   └── test/java/vn/edu/auction/   # Bộ kiểm thử tự động độc lập (Standalone Self-Tests)
│       ├── application/
│       │   ├── AuctionManagerSelfTest.java      # Kiểm thử Registry
│       │   ├── AuctionServiceSelfTest.java      # Kiểm thử Service
│       │   └── Week7SelfTest.java               # Kiểm thử đa luồng, concurrency & observer
│       ├── domain/
│       │   ├── user/
│       │   │   └── UserModelSelfTest.java       # Kiểm thử người dùng & phân quyền
│       │   └── DomainModelSelfTest.java         # Kiểm thử toàn diện logic đặt giá & trạng thái
│       └── ui/
│           ├── AuctionUiSmokeTest.java          # Kiểm thử cơ bản UI
│           └── Week7UiSmokeTest.java            # Kiểm thử luồng tự động giao diện JavaFX
│
├── check-week7.cmd                 # Script kiểm tra nhanh trên Windows CMD
├── check-week7.ps1                 # Script kiểm tra nhanh trên PowerShell
├── check-week7.sh                  # Script kiểm tra nhanh trên Linux / macOS
├── pom.xml                         # Cấu hình Maven chuẩn (build, test, javafx:run)
├── pom-week7.xml                   # Cấu hình Maven Tuần 7 (dự phòng, đồng nhất với pom.xml)
├── sources-domain.txt              # Danh sách file nguồn Domain phục vụ compile nhanh
├── sources-manager.txt             # Danh sách file nguồn Manager
├── sources-week7.txt               # Danh sách toàn bộ file nguồn Tuần 7
└── README.md                       # Tài liệu hướng dẫn dự án duy nhất
```

---

## Cài đặt và Hướng dẫn Chạy

### Yêu cầu Hệ thống
- **JDK 21** trở lên ([Tải JDK 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html))
- **Maven 3.8+** ([Tải Maven](https://maven.apache.org/download.cgi))
- **Hệ điều hành**: Windows 10/11, macOS, Linux

### 1. Kiểm tra nhanh (Không cần cài Maven)

Dự án cung cấp sẵn kịch bản biên dịch và chạy trọn vẹn bộ kiểm thử Java độc lập:

- **Trên Windows PowerShell:**
  ```powershell
  cd Auction12
  .\check-week7.ps1
  ```
- **Trên Windows Command Prompt (CMD):**
  ```cmd
  cd Auction12
  .\check-week7.cmd
  ```
- **Trên Linux / macOS:**
  ```bash
  cd Auction12
  sh check-week7.sh
  ```

---

### 2. Chạy Kiểm thử với Maven

```bash
cd Auction12
mvn clean test
```

---

### 3. Khởi động Giao diện JavaFX

```bash
cd Auction12
mvn javafx:run
```

---

### 4. Tài khoản Demo Thử nghiệm

Hệ thống được nạp sẵn các tài khoản demo để thử nghiệm đầy đủ các vai trò:

| Tên đăng nhập | Mật khẩu | Vai trò | Quyền hạn và Thao tác chính |
|:---|:---|:---|:---|
| `bidder01` | `demo123` | **Bidder** (Người đấu giá) | Xem danh sách, theo dõi thời gian và đặt giá cao hơn giá hiện tại. |
| `bidder02` | `demo123` | **Bidder** (Người đấu giá) | Dùng để mở song song cạnh tranh giá với `bidder01`. |
| `seller01` | `demo123` | **Seller** (Người bán) | Tạo phiên đấu giá mới; xác nhận thanh toán hoặc hủy phiên của mình khi đã kết thúc. |
| `admin01`  | `demo123` | **Admin** (Quản trị viên) | Xác nhận thanh toán hoặc hủy các phiên đấu giá đã kết thúc. |

---

### 5. Thử nghiệm Tính năng Real-time với Nhiều Người dùng

1. Khởi động ứng dụng bằng lệnh `mvn javafx:run` và đăng nhập tài khoản `bidder01`.
2. Trên màn hình chính, nhấn nút **"Mở cửa sổ khác"**.
3. Tại cửa sổ mới xuất hiện, đăng nhập bằng tài khoản `bidder02`.
4. Cả hai cửa sổ cùng chọn một phiên đấu giá đang diễn ra (`RUNNING`).
5. Dùng `bidder01` đặt một mức giá cao hơn: **ngay lập tức màn hình của `bidder02` sẽ tự động nhảy số tiền và người dẫn đầu mới** nhờ Observer Pattern mà không cần bấm làm mới.

---

## Danh sách Chức năng Đã hoàn thành (Tuần 7)

Dưới đây là các tính năng **đã hoàn thành thực tế** trong mã nguồn dự án:

### 1. Quản lý Người dùng & Phân quyền (OOP & Security)
- [x] Mô hình kế thừa phân quyền: lớp cha `User` và các lớp con `Bidder`, `Seller`, `Admin`.
- [x] Xác thực người dùng qua dịch vụ `DemoAuthService` và quản lý phiên làm việc bằng `UserSession`.
- [x] Kiểm tra quyền hạn chặt chẽ: Bidder chỉ được đặt giá, Seller chỉ được tạo phiên/quản lý phiên của mình, Admin quản trị hệ thống.

### 2. Quản lý Sản phẩm & Phiên Đấu giá
- [x] Áp dụng **Factory Method Pattern** để khởi tạo các dòng sản phẩm: Điện tử (`Electronics`), Mỹ thuật (`Art`), Phương tiện (`Vehicle`).
- [x] Tạo phiên đấu giá với đầy đủ thuộc tính: Sản phẩm, giá khởi điểm, thời gian bắt đầu và kết thúc.
- [x] Kiểm tra hợp lệ dữ liệu (Validation): Giờ kết thúc phải sau giờ bắt đầu, giá khởi điểm phải là số nguyên dương VND, người bán sở hữu sản phẩm mới được tạo phiên.

### 3. Nghiệp vụ Đặt giá & Xử lý Đa luồng (Concurrency)
- [x] **Xử lý Đấu giá Đồng thời**: Sử dụng `ReentrantLock` độc lập cho từng phiên đấu giá.
- [x] **Chống Race Condition & Lost Update**: Khóa bao bọc toàn bộ quá trình đọc giờ, kiểm tra giá, ghi lịch sử `BidTransaction`, cập nhật người dẫn đầu và tăng `version`.
- [x] Kiểm tra nghiêm ngặt: Giá đặt mới phải cao hơn giá hiện tại, người bán không được tự đặt giá trên sản phẩm của mình.
- [x] Lưu trữ lịch sử đấu giá đầy đủ và trả về danh sách bất biến (defensive copy).

### 4. Vòng đời & Tự động Chuyển trạng thái Phiên
- [x] Các trạng thái phiên: `OPEN` ➔ `RUNNING` ➔ `FINISHED` ➔ `PAID` / `CANCELED`.
- [x] **Bộ lập lịch `AuctionScheduler`**: Dùng `ScheduledExecutorService` quét định kỳ (mỗi 200ms) để tự động cập nhật trạng thái theo đồng hồ hệ thống.
- [x] Tự động chốt người chiến thắng (`winnerId`) ngay khi phiên chuyển sang `FINISHED`.
- [x] Cơ chế thanh toán (`markPaid`) và hủy phiên có lý do (`cancel`) dành cho Seller và Admin.

### 5. Cập nhật Thời gian thực (Observer Pattern)
- [x] Thiết kế Observer Pattern với `AuctionObserver`, `AuctionEvent` và `Subscription`.
- [x] Bắn thông báo ngay khi có sự kiện `BID_PLACED` hoặc `STATUS_CHANGED`.
- [x] Cơ chế phát thông báo ngoài vùng Lock theo hàng đợi FIFO, cách ly hoàn toàn ngoại lệ của Observer (nếu một observer bị lỗi, phiên đấu giá vẫn toàn vẹn).
- [x] Quản lý vòng đời đăng ký: hỗ trợ hủy theo dõi (`unsubscribe`) an toàn.

### 6. Giao diện Đồ họa JavaFX (UI)
- [x] Màn hình Đăng nhập (`login-view.fxml`) xử lý nhập liệu và thông báo lỗi đăng nhập.
- [x] Màn hình Danh sách Đấu giá (`auction-list-view.fxml`) hiển thị bảng TableView, đồng hồ đếm ngược, chi tiết giá và người dẫn đầu.
- [x] Hộp thoại Tạo phiên mới (`CreateAuctionDialog`) dành riêng cho tài khoản người bán.
- [x] Tách biệt luồng UI: Tác vụ gọi nghiệp vụ đặt giá chạy trên background `Task`, kết quả đẩy về giao diện bằng `Platform.runLater()`.
- [x] Cơ chế chống giật lùi giao diện: `AuctionRow` kiểm tra `version` để loại bỏ các gói tin cũ đến muộn do độ trễ luồng.

### 7. Kiểm thử Tự động (Self-Test Suite)
- [x] **156+ bài kiểm tra tự động đã pass 100%**:
  - `UserModelSelfTest`: Kiểm tra khởi tạo User, chuẩn hóa dữ liệu, phân quyền.
  - `DomainModelSelfTest`: Kiểm tra thực thể Domain, logic đặt giá, chuyển trạng thái.
  - `AuctionManagerSelfTest`: Kiểm tra Registry và an toàn bộ nhớ.
  - `Week7SelfTest`: Kiểm tra đa luồng đồng thời, Observer, Authentication và Scheduler.
  - `Week7UiSmokeTest`: Kiểm tra tự động luồng giao diện JavaFX.

---

## Design Patterns Áp dụng

| Pattern | Thành phần / Vị trí áp dụng | Mục đích giải quyết |
|:---|:---|:---|
| **Observer** | `Auction`, `AuctionObserver`, `AuctionEvent`, `DefaultAuctionService` | Phát thông báo cập nhật giá và trạng thái tức thì đến các màn hình UI đang theo dõi theo thời gian thực mà không cần cơ chế kéo (polling). |
| **Factory Method** | `ItemFactory`, `ArtFactory`, `ElectronicsFactory`, `VehicleFactory` | Đóng gói và chuẩn hóa quy trình khởi tạo các đối tượng sản phẩm thuộc nhiều danh mục khác nhau. |
| **Registry / Singleton** | `AuctionManager` | Quản lý định danh và tập trung lưu trữ trạng thái các phiên đấu giá toàn ứng dụng. |
| **MVC** | `domain` (Model), `fxml` (View), `controller` (Controller) | Tách biệt hoàn toàn tầng dữ liệu nghiệp vụ, giao diện người dùng và tầng điều hướng logic. |

---

## Thống kê Dự án

- **Ngôn ngữ**: Java 21
- **Tổng số lớp Java**: ~30 classes phân chia theo đúng kiến trúc Clean Architecture
- **Màn hình giao diện**: 2 FXML views (`login-view`, `auction-list-view`) + 1 Dialog FXML
- **Bộ kiểm thử tự động**: 5 bộ Self-Test chuyên sâu (156 checks pass 100%)
- **Mức độ hoàn thành Tuần 7**: 6/6 yêu cầu đề bài.

---

<div align="center">

Made for **Lập trình Nâng cao - UET 2026**

</div>
