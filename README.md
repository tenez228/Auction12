<div align="center">

# Hệ thống Đấu giá Trực tuyến AuctionUET

[![Java CI with Maven](https://github.com/iTCuong090/AuctionUET/actions/workflows/ci.yml/badge.svg)](https://github.com/iTCuong090/AuctionUET/actions/workflows/ci.yml)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-green.svg)](https://openjfx.io/)

**Bài tập lớn môn Lập trình Nâng cao - Trường Đại học Công nghệ - ĐHQGHN**

[Đề bài](#đề-bài-bài-tập-lớn) • [Cài đặt](#cài-đặt-và-yêu-cầu-hệ-thống) • [Chạy ứng dụng](#hướng-dẫn-chạy-ứng-dụng) • [Tính năng](#danh-sách-chức-năng) • [Báo cáo](#báo-cáo-và-demo)

</div>

---

## Mô tả Hệ thống

**AuctionUET** là một hệ thống đấu giá trực tuyến (online bidding platform) được phát triển theo kiến trúc **Client-Server**, cho phép nhiều người dùng cùng tham gia cạnh tranh giá để mua sản phẩm trong thời gian thực. Hệ thống mô phỏng các nền tảng đấu giá thực tế như eBay Auctions với đầy đủ các tính năng cốt lõi và nâng cao.

### Phạm vi Hệ thống

- **Quản lý người dùng đa vai trò**: Bidder (người đấu giá), Seller (người bán), Admin (quản trị viên)
- **Quản lý sản phẩm & phiên đấu giá**: Tạo, sửa, xóa sản phẩm với đầy đủ thông tin và trạng thái
- **Đấu giá thời gian thực**: Cập nhật giá đấu tức thời cho tất cả người dùng đang theo dõi
- **Đấu giá qua giọng nói**: Tích hợp API AI của Google Gemini để nhận diện số tiền đấu giá qua giọng nói.
- **Hệ thống tài chính**: Ví điện tử, nạp/rút tiền, đặt cọc, đóng băng số dư
- **Đấu giá tự động (Auto-Bidding)**: Thuật toán đấu giá tự động giúp bảo vệ người dẫn đầu
- **Chống đấu giá phút chót (Anti-Sniping)**: Tự động gia hạn phiên khi có bid cuối
- **Trực quan hóa dữ liệu**: Biểu đồ giá đấu theo thời gian thực (Real-time Line Chart)

---

## Công nghệ Sử dụng

### Backend (Server)
- **Java 21** - Ngôn ngữ lập trình chính
- **Socket Programming** - Giao tiếp Client-Server qua TCP/IP
- **Gson 2.10.1** - Serialization/Deserialization JSON
- **SLF4J + Logback** - Logging framework
- **JUnit 5** - Unit testing và Integration testing
- **Concurrency API** - ReentrantLock, ConcurrentHashMap, ScheduledExecutorService

### Frontend (Client)
- **JavaFX 21** - Framework giao diện đồ họa
- **FXML** - Declarative UI layout
- **JavaFX Charts** - Biểu đồ trực quan hóa dữ liệu

### Build & CI/CD
- **Maven 3.8+** - Build tool và dependency management
- **GitHub Actions** - Continuous Integration (CI)
- **Maven Shade Plugin** - Đóng gói JAR executable

### Design Patterns
- **Singleton Pattern** - AuctionManager, DataManager, SessionManager
- **Observer Pattern** - Real-time updates cho phiên đấu giá
- **Factory Pattern** - GsonFactory
- **MVC Pattern** - Tách biệt Model-View-Controller

---

## Cài đặt và Yêu cầu Hệ thống

### Yêu cầu
- **JDK 21** trở lên ([Download](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html))
- **Maven 3.8+** ([Download](https://maven.apache.org/download.cgi))
- **Git** (để clone repository)
- **Hệ điều hành**: Windows 10/11, macOS, Linux

### Cài đặt

```bash
# Clone repository
git clone https://github.com/iTCuong090/AuctionUET.git
cd AuctionUET

# Build toàn bộ project (bao gồm cả 3 modules)
mvn clean package

# Hoặc build từng module riêng lẻ
mvn clean package -pl auction-protocol
mvn clean package -pl auction-server
mvn clean package -pl auction-client
```

### Chạy Unit Tests

```bash
# Chạy tất cả tests
mvn test

# Chạy tests cho module cụ thể
mvn test -pl auction-server
```

---

## Cấu trúc Thư mục

```
AuctionUET/
├── auction-protocol/          # Module chứa DTOs, Enums, Message Protocol
│   └── src/main/java/com/auctionuet/protocol/
│       ├── dto/              # Data Transfer Objects
│       ├── enums/            # AuctionStatus, UserRole, ItemType, etc.
│       ├── Request.java      # Request message wrapper
│       ├── Response.java     # Response message wrapper
│       └── PushMessage.java  # Server push notification
│
├── auction-server/           # Module Server
│   ├── src/main/java/com/auctionuet/server/
│   │   ├── ServerApp.java   # Main entry point
│   │   ├── domain/
│   │   │   ├── model/       # User, Bidder, Seller, Admin, LiveAuction
│   │   │   ├── service/     # Business logic layer
│   │   │   └── manager/     # AuctionManager, DataManager, SessionManager
│   │   ├── network/
│   │   │   ├── server/      # AuctionServer, ClientHandler, RequestRouter
│   │   │   └── controller/  # AuthController, BidController, AuctionController, etc.
│   │   ├── persistence/
│   │   │   ├── dao/         # Data Access Objects (JSON-based)
│   │   │   └── schema/      # Schema definitions
│   │   ├── exception/       # Custom exceptions
│   │   └── util/            # Utilities (PasswordUtils, ValidationUtils, etc.)
│   ├── data/                # JSON database files
│   ├── logs/                # Server logs
│   └── target/
│       └── auction-server-1.0-SNAPSHOT-shaded.jar  # Server JAR
│
├── auction-client/           # Module Client
│   ├── src/main/java/com/auctionuet/client/
│   │   ├── ClientApp.java   # Main JavaFX Application
│   │   ├── Launcher.java    # JAR launcher
│   │   ├── network/         # Client-side network communication
│   │   └── view/            # Controllers (22 FXML controllers)
│   ├── src/main/resources/
│   │   ├── fxml/            # 22 FXML layout files
│   │   ├── css/             # Stylesheets
│   │   └── images/          # Icons and images
│   └── target/
│       └── auction-client-1.0-SNAPSHOT-shaded.jar  # Client JAR
│
├── docs/                     # Tài liệu và báo cáo
├── .github/workflows/        # GitHub Actions CI/CD
├── pom.xml                   # Parent POM
└── TASK.md                   # Đề bài bài tập lớn
```

---

## Hướng dẫn Chạy Ứng dụng

### Vị trí các file JAR

Sau khi build thành công, các file JAR sẽ nằm tại:

- **Server JAR**: `auction-server/target/auction-server-1.0-SNAPSHOT-shaded.jar`
- **Client JAR**: `auction-client/target/auction-client-1.0-SNAPSHOT-shaded.jar`

### Bước 1: Khởi động Server

**Quan trọng**: Phải chạy Server trước khi chạy Client!

```bash
# Chạy Server với port mặc định (8888)
java -jar auction-server/target/auction-server-1.0-SNAPSHOT-shaded.jar

# Hoặc chỉ định port tùy chỉnh
java -jar auction-server/target/auction-server-1.0-SNAPSHOT-shaded.jar 9999
```

Server sẽ hiển thị banner khởi động và lắng nghe kết nối từ Client:



### Bước 2: Khởi động Client

Sau khi Server đã chạy, mở terminal/command prompt mới và chạy Client:

```bash
java -jar auction-client/target/auction-client-1.0-SNAPSHOT-shaded.jar
```

Giao diện JavaFX sẽ hiển thị màn hình đăng nhập.

### Bước 3: Đăng nhập hoặc Đăng ký

- **Đăng ký tài khoản mới**: Chọn vai trò (Bidder/Seller) và điền thông tin
- **Đăng nhập**: Sử dụng username và password đã đăng ký
- **Admin**: Tài khoản admin được tạo sẵn trong hệ thống

### Chạy nhiều Client đồng thời

Để test tính năng real-time và concurrent bidding, bạn có thể chạy nhiều Client cùng lúc:

```bash
# Terminal 1
java -jar auction-client/target/auction-client-1.0-SNAPSHOT-shaded.jar

# Terminal 2
java -jar auction-client/target/auction-client-1.0-SNAPSHOT-shaded.jar

# Terminal 3
java -jar auction-client/target/auction-client-1.0-SNAPSHOT-shaded.jar
```

---

## Danh sách Chức năng

### Chức năng Bắt buộc (9/9 điểm)

#### 1. Quản lý Người dùng
- [x] Đăng ký tài khoản với vai trò: Bidder, Seller, Admin
- [x] Đăng nhập/Đăng xuất với xác thực mật khẩu (BCrypt hashing)
- [x] Quản lý profile người dùng
- [x] Phân quyền theo vai trò (Role-based Access Control)

#### 2. Quản lý Sản phẩm Đấu giá
- [x] Thêm/Sửa/Xóa sản phẩm (Seller)
- [x] Thông tin đầy đủ: Tên, mô tả, giá khởi điểm, hình ảnh
- [x] Phân loại sản phẩm: Electronics, Art, Vehicle, Fashion, etc.
- [x] Trạng thái sản phẩm: ItemCondition (NEW, LIKE_NEW, GOOD, FAIR, POOR)
- [x] Hệ thống phê duyệt sản phẩm (Admin approval)

#### 3. Tham gia Đấu giá
- [x] Đặt giá cao hơn giá hiện tại
- [x] Kiểm tra tính hợp lệ của giá đấu (validation)
- [x] Cập nhật người dẫn đầu phiên đấu giá
- [x] Hiển thị lịch sử đấu giá (bid history)

#### 4. Kết thúc Phiên Đấu giá
- [x] Tự động đóng phiên khi hết thời gian
- [x] Xác định người thắng cuộc
- [x] Chuyển trạng thái: OPEN → RUNNING → FINISHED → PAID/CANCELED
- [x] Xử lý thanh toán và hoàn tiền cọc

#### 5. Xử lý Lỗi & Ngoại lệ
- [x] Custom exceptions: InvalidBidException, AuctionClosedException, InsufficientBalanceException
- [x] Global exception handler
- [x] Validation utils cho input
- [x] Error logging với SLF4J

#### 6. Giao diện Người dùng (JavaFX)
- [x] 22 FXML views với controllers
- [x] Danh sách phiên đấu giá với filter và search
- [x] Chi tiết sản phẩm với hình ảnh
- [x] Màn hình đấu giá trực tiếp (real-time bidding)
- [x] Dashboard quản lý cho Seller và Admin
- [x] Theme manager (Light/Dark mode support)

#### 7. Thiết kế OOP
- [x] Encapsulation: private fields + getters/setters
- [x] Inheritance: User → Bidder/Seller/Admin
- [x] Polymorphism: Override methods
- [x] Abstraction: Abstract classes và interfaces

#### 8. Kiến trúc Client-Server & MVC
- [x] Socket-based communication (TCP/IP)
- [x] JSON message protocol
- [x] MVC pattern trên cả Client và Server
- [x] Separation of concerns: Controller → Service → DAO

#### 9. Xử lý Đấu giá Đồng thời (Concurrency)
- [x] ReentrantLock cho thread-safe bidding
- [x] ConcurrentHashMap cho shared data structures
- [x] Tránh lost update, race condition
- [x] Atomic operations cho critical sections

#### 10. Real-time Update (Observer Pattern)
- [x] Observer pattern implementation
- [x] Server push notifications
- [x] Tất cả client được cập nhật ngay lập tức khi có bid mới
- [x] Thread-safe notification mechanism

### Chức năng Nâng cao (1.5/1.5 điểm)

#### 1. Auto-Bidding (Đấu giá Tự động)
- [x] Người dùng đặt maxBid và increment
- [x] Hệ thống tự động trả giá thay người dùng
- [x] Không vượt quá maxBid

#### 2. Anti-Sniping Algorithm (Gia hạn Phiên)
- [x] Tự động gia hạn khi có bid trong X giây cuối
- [x] Configurable extension time
- [x] Ngăn chặn chiến thuật "đấu giá phút chót"

#### 3. Bid History Visualization (Biểu đồ Giá)
- [x] JavaFX LineChart hiển thị giá đấu theo thời gian
- [x] Trục X: Timestamp, Trục Y: Giá đấu
- [x] Cập nhật real-time khi có bid mới
- [x] Smooth animation

### Chức năng Sáng tạo Thêm (Creative Features)

#### 1. Hệ thống Tài chính & Ví điện tử
- [x] Wallet system với balance và frozenBalance
- [x] Nạp tiền/Rút tiền (Mock payment với VietQR API)
- [x] Transaction history
- [x] Đặt cọc 10% giá khởi điểm để tham gia đấu giá
- [x] Tự động đóng băng/giải phóng tiền cọc

#### 2. Hệ thống Admin Nâng cao
- [x] Quản lý người dùng: Xem, khóa/mở khóa tài khoản
- [x] Quản lý sản phẩm: Phê duyệt/Từ chối sản phẩm
- [x] Financial audit: Xem toàn bộ giao dịch hệ thống
- [x] System monitor: Thống kê real-time (users, auctions, revenue)
- [x] Hủy phiên đấu giá vi phạm

#### 3. Voice Input (Nhập giá bằng Giọng nói)
- [x] Tích hợp voice recognition
- [x] Đọc số tiền bằng tiếng Việt
- [x] Currency input reader

### Tích hợp & Chất lượng Mã (1.5/1.5 điểm)

#### Build Tool & Convention
- [x] Maven multi-module project
- [x] Clean code: Refactoring, loại bỏ code trùng
- [x] Naming convention: Google Java Style Guide
- [x] Proper package structure

#### Testing
- [x] 29 JUnit test files
- [x] Unit tests cho Service layer
- [x] Integration tests cho Auction flow
- [x] DAO tests với mock data

#### CI/CD
- [x] GitHub Actions workflow
- [x] Automated testing on PR
- [x] Maven cache optimization

#### Git Workflow
- [x] Conventional Commits
- [x] Frequent commits (không chỉ 1 commit cuối)
- [x] Branch strategy
- [x] Pull Request reviews

---

## Design Patterns Áp dụng

| Pattern | Vị trí | Mục đích |
|---------|--------|----------|
| **Singleton** | `AuctionManager`, `DataManager`, `SessionManager` | Đảm bảo chỉ có 1 instance duy nhất quản lý state toàn cục |
| **Observer** | `LiveAuction`, `AuctionObserver`, `SystemMonitorObserver` | Real-time updates cho tất cả clients đang theo dõi |
| **Factory** | `GsonFactory` | Tạo Gson instance với custom adapters |
| **MVC** | Toàn bộ Client & Server | Tách biệt Model-View-Controller |
| **State / Lock** | `AutoBidConfig` kết hợp `PendingAutoBid` | Lưu cấu hình tự động đấu giá của người dẫn đầu và phản ứng thời gian thực khi có người đặt giá mới |

---

## Thống kê Dự án

- **Tổng số dòng code**: ~15,000+ LOC
- **Số lượng classes**: 157 files Java
  - Server: 64 files
  - Client: 43 files
  - Protocol: 50 files
- **Số lượng tests**: 29 test files
- **FXML views**: 22 views
- **Controllers**: 22 controllers
- **Services**: 10 service classes
- **DAOs**: 6 DAO classes
- **Design Patterns**: 6 patterns

---

## Đề bài Bài tập Lớn

Xem file đầy đủ tại: [TASK.md](TASK.md)

### Tóm tắt Yêu cầu

**Mục tiêu**: Phát triển hệ thống đấu giá trực tuyến áp dụng lập trình hướng đối tượng và các kỹ thuật nâng cao.

**Quy định**:
- Làm việc nhóm 3-4 người
- Sử dụng Java, JavaFX, Maven
- Commit thường xuyên lên GitHub
- Áp dụng OOP, Design Patterns, MVC
- Unit testing với JUnit
- CI/CD với GitHub Actions

**Điểm số**: 10 điểm bắt buộc + 1.5 điểm nâng cao

---

## Báo cáo và Demo

### Báo cáo PDF
- **Link**: [AuctionUET_Report.pdf](https://drive.google.com/file/d/1I8kn-tHSeL0e8ji8r_qH9RtA9QPxbw1z/view?usp=sharing)
- Nội dung: Phân tích thiết kế, kiến trúc hệ thống, design patterns, kết quả testing

### Video Demo
- **Link**: [Xem Video Demo trên YouTube](https://www.youtube.com/watch?v=mM9AWSJR8LM)
- Nội dung: Demo đầy đủ các chức năng, concurrent bidding, real-time updates

---

## Thành viên Nhóm

| Họ và Tên | MSSV | Vai trò | Đóng góp chính |
|-----------|------|---------|----------------|
| Tạ Hữu Cường | 25020053 | Team Leader & Core Architect | Phát triển tầng dịch vụ (Auction, Wallet, Bid), tái cấu trúc sản phẩm (flat ItemSchema), tích hợp Gemini Voice Input & Bidding, kiểm soát concurrency và luồng thanh toán. |
| Ngô Duy Anh | 25020015 | Lead Backend & Quality | Thiết kế OOP Domain models (User, LiveAuction), hệ thống Admin quản trị nâng cao, viết bộ Integration Tests toàn diện và tài liệu ôn tập Viva Guide. |
| Nguyễn Cao Công | 25020046 | Frontend Lead & Client Dev | Phát triển toàn bộ 22 giao diện JavaFX (FXML & CSS), quản lý Client Network connection, Theme Manager (Light/Dark mode) và luồng xử lý UI luân phiên (Platform.runLater). |
| Đào Đình Khánh | 25020211 | Network & DevOps Lead | Thiết kế giao thức Protocol truyền tin (Request/Response), triển khai Socket Server & Request Routing, thuật toán Auto-Bidding bảo vệ người dẫn đầu (Pending Auto-Bid) và thiết lập CI/CD GitHub Actions. |

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- **Giảng viên hướng dẫn**: Phạm Bảo Phúc - Trần Ngọc Trúc Linh
- **Môn học**: Lập trình Nâng cao
- **Trường**: Trường Đại học Công nghệ - ĐHQGHN
- **Học kỳ**: 2/2025-2026

---

<div align="center">

Nếu bạn thấy project hữu ích, hãy cho chúng mình một star!

Made by AuctionUET Team 2026.

</div>
