<div align="center">

# Hệ thống Đấu giá Trực tuyến Auction12

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-green.svg)](https://openjfx.io/)

**Bài tập lớn môn Lập trình Nâng cao - Trường Đại học Công nghệ - ĐHQGHN**  
**Giai đoạn Tuần 7: Đa luồng, Observer Pattern & Giao diện JavaFX**

[Mô tả](#mô-tả-hệ-thống) • [Công nghệ](#công-nghệ-sử-dụng) • [Cài đặt](#cài-đặt-và-yêu-cầu-hệ-thống) • [Chạy ứng dụng](#hướng-dẫn-chạy-ứng-dụng) • [Tính năng](#danh-sách-chức-năng-hiện-tại-tuần-7) • [Design Patterns](#design-patterns-áp-dụng)

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

## Cài đặt và Yêu cầu Hệ thống

### Yêu cầu
- **JDK 21** trở lên ([Tải JDK 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html))
- **Maven 3.8+** ([Tải Maven](https://maven.apache.org/download.cgi))
- **Hệ điều hành**: Windows 10/11, macOS, Linux

### Kiểm tra nhanh (Không cần cài Maven)

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

### Chạy Unit / Self-Test với Maven

```bash
cd Auction12
mvn clean test
# Hoặc chỉ định rõ file pom của Tuần 7:
mvn -f pom-week7.xml clean test
```

---

## Cấu trúc Thư mục

```
Auction12/
├── src/
│   ├── main/
│   │   ├── java/vn/edu/auction/
│   │   │   ├── application/        # Tầng dịch vụ ứng dụng (Service Layer)
│   │   │   │   ├── auth/           # Xác thực phiên (DemoAuthService, UserSession)
│   │   │   │   ├── AuctionManager.java    # Registry quản lý danh mục phiên đấu giá
│   │   │   │   ├── AuctionScheduler.java  # Bộ lập lịch cập nhật trạng thái tự động
│   │   │   │   ├── AuctionService.java    # Interface nghiệp vụ cho UI
│   │   │   │   ├── DefaultAuctionService.java # Triển khai nghiệp vụ với synchronized & observer
│   │   │   │   └── AuctionView.java       # DTO bất biến cung cấp cho UI
│   │   │   ├── bootstrap/          # Khởi tạo ứng dụng & dữ liệu mẫu
│   │   │   │   ├── AppContext.java        # Khởi tạo DI container và dịch vụ hệ thống
│   │   │   │   ├── AuctionApplication.java# Điểm khởi chạy JavaFX UI
│   │   │   │   └── Week7Demo.java         # Bản demo console tương tác thời gian thực
│   │   │   ├── domain/             # Tầng mô hình nghiệp vụ cốt lõi (Domain Model)
│   │   │   │   ├── auction/        # Auction, BidTransaction, AuctionStatus
│   │   │   │   │   └── event/      # Observer Pattern: AuctionObserver, AuctionEvent, Subscription
│   │   │   │   ├── factory/        # Factory Method: ItemFactory, Art/Electronics/VehicleFactory
│   │   │   │   ├── item/           # Thực thể sản phẩm: Item, Art, Electronics, Vehicle
│   │   │   │   └── user/           # Thực thể người dùng: User, Bidder, Seller, Admin
│   │   │   └── ui/                 # Tầng hiển thị giao diện đồ họa JavaFX
│   │   │       ├── controller/     # LoginController, AuctionListController, CreateAuctionDialog
│   │   │       └── model/          # AuctionRow (chống hiển thị lùi version), UiFormats
│   │   └── resources/
│   │       ├── fxml/               # login-view.fxml, auction-list-view.fxml
│   │       └── css/                # app.css
│   └── test/java/vn/edu/auction/   # Bộ kiểm thử tự động độc lập (Standalone Self-Tests)
│       ├── application/            # AuctionManagerSelfTest, Week7SelfTest
│       ├── domain/                 # DomainModelSelfTest, UserModelSelfTest
│       └── ui/                     # Week7UiSmokeTest (kiểm thử GUI tự động)
├── docs/week7/                     # Tài liệu thiết kế, quyết định kiến trúc & đối chiếu
├── check-week7.cmd / .ps1 / .sh    # Script chạy kiểm thử nhanh
├── pom.xml                         # Cấu hình Maven chuẩn của dự án
└── README.md                       # Tài liệu hướng dẫn dự án
```

---

## Hướng dẫn Chạy Ứng dụng

### Khởi động Giao diện JavaFX

Mở terminal tại thư mục `Auction12` và thực thi lệnh sau:

```bash
cd Auction12
mvn -f pom-week7.xml javafx:run
```
*(Nếu dùng `pom.xml` mặc định, bạn có thể chạy trực tiếp `mvn javafx:run`)*

---

### Tài khoản Demo Thử nghiệm

Hệ thống được nạp sẵn các tài khoản demo để thử nghiệm đầy đủ các vai trò:

| Tên đăng nhập | Mật khẩu | Vai trò | Quyền hạn và Thao tác chính |
|:---|:---|:---|:---|
| `bidder01` | `demo123` | **Bidder** (Người đấu giá) | Xem danh sách, theo dõi thời gian và đặt giá cao hơn giá hiện tại. |
| `bidder02` | `demo123` | **Bidder** (Người đấu giá) | Dùng để mở song song cạnh tranh giá với `bidder01`. |
| `seller01` | `demo123` | **Seller** (Người bán) | Tạo phiên đấu giá mới; xác nhận thanh toán hoặc hủy phiên của mình khi đã kết thúc. |
| `admin01`  | `demo123` | **Admin** (Quản trị viên) | Xác nhận thanh toán hoặc hủy các phiên đấu giá đã kết thúc. |

---

### Trải nghiệm Tính năng Real-time với Nhiều Người dùng

1. Khởi động ứng dụng và đăng nhập bằng tài khoản `bidder01`.
2. Trên màn hình chính, nhấn nút **"Mở cửa sổ khác"**.
3. Tại cửa sổ mới xuất hiện, đăng nhập bằng tài khoản `bidder02`.
4. Cả hai cửa sổ cùng chọn một phiên đấu giá đang diễn ra (`RUNNING`).
5. Dùng `bidder01` đặt một mức giá cao hơn: **ngay lập tức màn hình của `bidder02` sẽ tự động nhảy số tiền và người dẫn đầu mới** nhờ Observer Pattern mà không cần bấm làm mới.

---

## Danh sách Chức năng Hiện tại (Tuần 7)

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

## Kế hoạch Phát triển Tiếp theo (Tuần sau)

- [ ] Chuyển đổi kiến trúc sang **Client-Server phân tán qua TCP/IP Socket**.
- [ ] Xây dựng hệ thống giao thức mạng JSON (Message Protocol) giữa Client và Server.
- [ ] Tích hợp cơ sở dữ liệu để lưu trữ lâu dài (Persistence Layer).
- [ ] Bổ sung các tính năng nâng cao: Đấu giá tự động (Auto-Bidding), Chống đấu giá phút chót (Anti-Sniping).

---

<div align="center">

Made for **Lập trình Nâng cao - UET 2026**

</div>
