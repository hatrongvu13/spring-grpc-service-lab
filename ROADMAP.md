# FleetPulse — Lộ trình triển khai

Dự án đi từ **chưa có gì** đến một hệ thống gRPC vận hành được, qua 9 giai đoạn. Mỗi giai đoạn
chạy được end-to-end và demo được; dừng ở bất kỳ giai đoạn nào vẫn có repo hoàn chỉnh.

**Nguyên tắc xuyên suốt:** khung sườn được dựng theo kiến trúc cuối ngay từ đầu. Module
`security-commons`, chỗ đặt interceptor chain, và cấu trúc hợp đồng đều có mặt từ Giai đoạn 0
— chỉ khác ở chỗ chúng còn rỗng. Các giai đoạn sau **chỉ thêm vào, không bao giờ di dời hay
xoá bỏ** thứ đã có.

---

## Mục lục

- [Vì sao Maven](#vì-sao-maven)
- [Bản đồ giai đoạn](#bản-đồ-giai-đoạn)
- [Thiết kế nền tảng đọc một lần](#thiết-kế-nền-tảng-đọc-một-lần)
- [GĐ 0 — Khung Maven, bốn tiến trình khởi động](#gđ-0--khung-maven-bốn-tiến-trình-khởi-động)
- [GĐ 1 — Hợp đồng đầu tiên và ping-pong chéo](#gđ-1--hợp-đồng-đầu-tiên-và-ping-pong-chéo)
- [GĐ 2 — Service nghiệp vụ đầu tiên](#gđ-2--service-nghiệp-vụ-đầu-tiên)
- [GĐ 3 — Tương tác service với service](#gđ-3--tương-tác-service-với-service)
- [GĐ 4 — Module bảo mật dùng chung](#gđ-4--module-bảo-mật-dùng-chung)
- [GĐ 5 — Interceptor chain đầy đủ và quan trắc](#gđ-5--interceptor-chain-đầy-đủ-và-quan-trắc)
- [GĐ 6 — Streaming và backpressure](#gđ-6--streaming-và-backpressure)
- [GĐ 7 — Failure isolation](#gđ-7--failure-isolation)
- [GĐ 8 — Sẵn sàng vận hành](#gđ-8--sẵn-sàng-vận-hành)
- [GĐ 9 — Cổng GraphQL (dự án kế tiếp)](#gđ-9--cổng-graphql-dự-án-kế-tiếp)
- [Phụ lục](#phụ-lục)

---

## Vì sao Maven

Khuyến nghị: **chỉ dùng Maven**, không duy trì song song với Gradle.

Ba lý do. Thứ nhất, `protobuf-maven-plugin` sinh mã ổn định và không cần plugin phụ trợ dò hệ
điều hành như phía Gradle. Thứ hai, `dependencyManagement` với BOM import cho bạn một chỗ duy
nhất quản lý phiên bản của Spring Boot, gRPC, Protobuf và Resilience4j — và trong dự án nhiều
module với nhiều thư viện dễ xung đột như thế này, đó là điểm cộng lớn. Thứ ba, người review
portfolio nhìn thấy cả `pom.xml` lẫn `build.gradle.kts` sẽ nghĩ bạn chưa quyết được, chứ không
nghĩ bạn thạo cả hai.

Nếu vẫn muốn có Gradle, hãy để nó ở một nhánh riêng tên `build/gradle` như một bài tập đối
chiếu, và ghi rõ trong README rằng `main` dùng Maven.

**Bản đồ đối chiếu nếu bạn quen Gradle:**

| Gradle | Maven |
|---|---|
| `settings.gradle.kts` → `include(...)` | `pom.xml` cha → `<modules>` |
| `gradle.properties` | `<properties>` trong pom cha |
| `platform(...)` / BOM | `<dependencyManagement>` + `<scope>import</scope>` |
| `api` | `<dependency>` thường (Maven không có `implementation`/`api`) |
| `implementation` | `<dependency>` + cân nhắc `<optional>` |
| `./gradlew build` | `./mvnw verify` |
| `./gradlew :dispatch-service:bootRun` | `./mvnw -pl dispatch-service spring-boot:run` |

---

## Bản đồ giai đoạn

| GĐ | Tên | Thời lượng | Điều kiện nghiệm thu |
|----|-----|-----------|----------------------|
| 0 | Khung Maven, 4 tiến trình khởi động | 2 ngày | `mvn verify` xanh, 4 app lên, `/actuator/health` UP |
| 1 | Hợp đồng đầu tiên, ping-pong chéo | 2–3 ngày | `curl /api/v1/ping` trả trace 3 chặng |
| 2 | Service nghiệp vụ đầu tiên | 4–5 ngày | Tạo đơn hàng, lưu Postgres, idempotent |
| 3 | Tương tác service với service | 3–4 ngày | dispatch gọi pricing thật, deadline trừ dần |
| 4 | Module bảo mật dùng chung | 6–8 ngày | mTLS + internal token, service khai 3 dòng |
| 5 | Interceptor chain + quan trắc | 4–5 ngày | Trace một request xuyên 3 service trên Jaeger |
| 6 | Streaming và backpressure | 5–6 ngày | Xem vị trí xe realtime, consumer chậm không tràn heap |
| 7 | Failure isolation | 4–5 ngày | Tắt pricing, hệ thống vẫn phục vụ bằng fallback |
| 8 | Sẵn sàng vận hành | 4–6 ngày | Deploy k8s, load test, tài liệu, video demo |
| 9 | Cổng GraphQL | — | Dự án riêng, không triển khai ở đây |

Tổng khoảng 7–9 tuần nếu làm ngoài giờ. **Từ Giai đoạn 5 trở đi repo đã đủ mạnh để đưa vào
portfolio**; các giai đoạn sau là phần nâng cấp.

---

## Thiết kế nền tảng đọc một lần

Bốn quyết định dưới đây chi phối toàn bộ lộ trình. Nắm trước sẽ giúp bạn hiểu vì sao từng
giai đoạn làm như vậy.

### 1. Ba module chung, ba ranh giới rõ ràng

```
proto-contracts     — chỉ hợp đồng. Không import gì của FleetPulse.
grpc-commons        — cross-cutting kỹ thuật. Phụ thuộc proto-contracts.
security-commons    — xác thực, phân quyền. Phụ thuộc grpc-commons.
```

Quy tắc kiểm chứng: nếu một class trong `grpc-commons` cần biết "đơn hàng" là gì, nó đang nằm
sai chỗ. Sự tách bạch này là điều làm nên khác biệt giữa "ba service" và "một monolith bị cắt
làm ba".

### 2. Module chung nạp qua auto-configuration, không qua component scan

`@SpringBootApplication` của các service **không** quét `io.fleetpulse.commons`. Thay vào đó,
mỗi module chung có file:

```
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Lý do: component scan sẽ nạp *tất cả* bean trong package, kể cả những thứ nội bộ mà module
không có ý định phơi ra. Auto-configuration cho phép module tự quyết định phơi cái gì, và
quan trọng hơn — cho phép bật/tắt cả khối bằng `@ConditionalOnProperty`. Đây chính là cơ chế
khiến Giai đoạn 1 chạy được mà không cần chứng chỉ nào.

### 3. Thứ tự interceptor được quản lý tập trung

Toàn bộ thứ tự chain nằm trong một dải số thống nhất giữa hai module:

| Order | Interceptor | Module | Giai đoạn |
|---|---|---|---|
| 10 | Correlation ID | grpc-commons | 1 |
| 20 | Deadline guard | grpc-commons | 1 |
| 25 | Peer identity (mTLS) | security-commons | 4 |
| 30 | Internal token | security-commons | 4 |
| 40 | Authorization | security-commons | 4 |
| 50 | Validation | grpc-commons | 2 |
| 60 | Metrics | grpc-commons | 5 |
| 70 | Error mapping | grpc-commons | 5 |

Thứ tự này không tuỳ tiện. Correlation ID phải chạy đầu để mọi log sau đó có ID. Deadline guard
đứng trước xác thực để từ chối sớm mà không tốn công verify chữ ký. Validation đứng **sau**
phân quyền vì thông báo lỗi validation chi tiết có thể rò rỉ thông tin cho người chưa có quyền.
Error mapping nằm trong cùng để bắt được mọi thứ lọt ra từ các lớp trên.

### 4. Bảo mật hai vành đai, gateway là người phát hành duy nhất

**Vành đai ngoài** (client → gateway): JWT RS256, verify bằng JWKS. Hoặc không có token gì cả
— khách ẩn danh vẫn được phục vụ.

**Vành đai trong** (service → service): hai lớp chồng nhau.
- mTLS trả lời câu hỏi *service nào đang gọi tới* — danh tính lấy từ SAN của client cert
- Internal token trả lời câu hỏi *thay mặt người dùng nào* — JWT Ed25519 sống ~60 giây, do
  gateway ký

Cả hai đều cần. Chỉ có mTLS thì không biết đơn hàng thuộc về ai. Chỉ có token thì kẻ tấn công
đã vào được mạng nội bộ có thể mạo danh.

**Khách ẩn danh cũng được cấp internal token**, chỉ khác `tier` và `scopes`. Đây là chi tiết
thiết kế quan trọng: nếu tách thành hai nhánh code riêng cho "có đăng nhập" và "không đăng
nhập", bạn sẽ có hai đường đi khác nhau, và một trong hai sẽ bị quên khi thêm tính năng mới.
Một đường đi, khác nhau ở dữ liệu.

---

## GĐ 0 — Khung Maven, bốn tiến trình khởi động

### Mục tiêu
Bảy module Maven biên dịch được, bốn ứng dụng Spring Boot khởi động được. Chưa có gRPC.

### Giải thích

**Vì sao dựng đủ bảy module ngay dù năm trong số đó còn rỗng?** Vì việc thêm một module vào cây
Maven đã chạy thì dễ, nhưng việc *di chuyển code* giữa các module sau khi đã viết thì tốn thời
gian và làm bẩn lịch sử git. Khung sườn dựng đúng ngay từ đầu nghĩa là mỗi dòng code bạn viết
về sau đều rơi đúng chỗ của nó.

**Pom cha là BOM.** Không service nào được viết `<version>` cho dependency. Mọi phiên bản nằm
trong `<dependencyManagement>` của pom cha, phần lớn đến từ BOM import (`spring-boot-dependencies`,
`grpc-bom`, `protobuf-bom`, `resilience4j-bom`). Khi nâng cấp gRPC, bạn sửa một số.

**Maven wrapper.** Chạy `mvn wrapper:wrapper -Dmaven=3.9.9` để sinh `mvnw`. Nhờ nó, người clone
repo không cần cài Maven — một chi tiết nhỏ nhưng người review sẽ nhận ra.

### Checklist

- [ ] `git init`, tạo repo GitHub public, viết mô tả và topics
- [ ] Pom cha `packaging=pom`, khai đủ 7 module
- [ ] `<properties>` tập trung mọi số phiên bản
- [ ] `<dependencyManagement>` import 4 BOM
- [ ] `mvn wrapper:wrapper`, commit `mvnw` và `.mvn/`
- [ ] `<pluginManagement>` cho `spring-boot-maven-plugin` và `protobuf-maven-plugin`
- [ ] Bốn module service có `Application` class và `spring-boot-maven-plugin` với goal `repackage`
- [ ] Cấp phát cổng dứt khoát: HTTP 8080/8091/8092/8093, gRPC 9091/9092/9093
- [ ] `application.yml` mỗi service với `spring.application.name` và actuator
- [ ] `docker-compose.yml`: postgres, redis, prometheus, grafana + healthcheck
- [ ] `Makefile` với `build`, `test`, `up`, `down`, `run-*`
- [ ] `.gitignore` (chặn `target/`, `*.pem`)
- [ ] GitHub Actions: `mvn -B verify`
- [ ] Tag `v0.0-skeleton`

### Nghiệm thu
```bash
./mvnw clean install -DskipTests     # BUILD SUCCESS, 7 module
make run-telemetry && curl localhost:8093/actuator/health   # {"status":"UP"}
```

---

## GĐ 1 — Hợp đồng đầu tiên và ping-pong chéo

### Mục tiêu
`ping.proto` là hợp đồng đầu tiên. Một lệnh `curl` ở gateway đi xuyên cả bốn tiến trình rồi
quay ngược lại, mang theo trace của từng chặng.

### Giải thích

**Đây không phải hello world.** `PingService` được thiết kế để chứng minh bốn thứ cùng lúc:

1. Bốn tiến trình đang sống và nói chuyện được với nhau
2. `dispatch-service` **vừa là server vừa là client** — nó nhận ping từ gateway và gọi tiếp
   sang pricing
3. Hợp đồng proto sinh mã đúng ở cả phía server lẫn phía client
4. Correlation ID và deadline đi xuyên suốt chuỗi

Bạn sẽ giữ endpoint này đến cuối dự án. Mỗi lần bật mTLS, đổi service discovery, hay deploy lên
Kubernetes, đây là thứ chạy đầu tiên để biết đường dây còn nguyên.

**`hops_remaining` là chốt chặn vòng lặp.** Nếu bạn vô tình cấu hình `telemetry.next-hop=dispatch`,
chuỗi sẽ thành chu trình vô hạn. Trường này giảm dần mỗi chặng và dừng ở 0. Chi tiết nhỏ nhưng
nó là thói quen đúng: mọi cơ chế chuyển tiếp đều cần một giới hạn.

**`ChainedPingService` nằm trong `grpc-commons`, không nằm trong từng service.** Đây là ví dụ
mẫu cho toàn bộ triết lý module chung: viết một lần, mọi service có ngay, cấu hình bằng một
property duy nhất:

```yaml
fleetpulse.grpc.ping.next-hop: pricing    # dispatch → pricing
fleetpulse.grpc.ping.next-hop: telemetry  # pricing → telemetry
fleetpulse.grpc.ping.next-hop:            # telemetry là điểm cuối
```

**`CorrelationIdInterceptor` viết đầy đủ ngay từ giai đoạn này** — không phải stub. Lý do: nó
là khuôn mẫu cho cách bọc `ServerCall.Listener`, và nếu bạn nắm pattern này ở Giai đoạn 1 thì
Giai đoạn 5 sẽ nhanh gấp đôi. Điểm mấu chốt nằm ở chỗ MDC là `ThreadLocal`, nên với streaming,
các callback `onMessage` chạy trên thread khác và sẽ **mất** correlation ID nếu bạn chỉ set một
lần trong `interceptCall`. Đây là lỗi phổ biến nhất khi viết interceptor cho gRPC.

**`CorrelationIdClientInterceptor` cũng cần thiết ngang.** Không có nó, ID chết ở hop đầu tiên:
dispatch nhận được ID nhưng khi gọi tiếp sang pricing lại sinh ID mới. Đây là lý do interceptor
chain phải có cả hai phía, không chỉ server.

### Checklist

- [ ] Viết `ping.proto` với `from`, `nonce`, `hops_remaining`, `trace`
- [ ] Cấu hình `protobuf-maven-plugin`, xác nhận mã sinh ra trong `target/generated-sources`
- [ ] `PingNextHop` (interface) + `GrpcPingNextHop` (cài đặt gRPC) + `PingNextHop.none()`
- [ ] `ChainedPingService` trong `grpc-commons`, đánh dấu `@GrpcService`
- [ ] `FleetPulseGrpcProperties` với `service-name`, `default-deadline-ms`, `ping.next-hop`
- [ ] `FleetPulseGrpcAutoConfiguration` + file `AutoConfiguration.imports`
- [ ] `CorrelationIdInterceptor` — bọc `ServerCall.Listener` đủ 5 callback
- [ ] `CorrelationIdClientInterceptor` — đọc từ Context, ghi vào metadata
- [ ] `DeadlineGuardInterceptor` — deadline mặc định + từ chối sớm
- [ ] Cấu hình `grpc.client.*` cho từng chặng
- [ ] `PingController` ở gateway, trả về trace
- [ ] Bật gRPC reflection ở profile `dev` để dùng `grpcurl`
- [ ] Test: `ChainedPingService` với `PingNextHop` giả, không cần network
- [ ] Test: ping với `hops_remaining=0` dừng đúng chỗ
- [ ] Test: correlation ID giống nhau ở cả ba service (kiểm bằng log)
- [ ] Tag `v0.1-ping-pong`

### Nghiệm thu
```bash
curl -s localhost:8080/api/v1/ping | jq .
```
Trả về `"ok": true` với 3 dòng trace. Grep log của cả ba service thấy cùng một `correlationId`.

### Bẫy thường gặp

`@GrpcClient` chỉ hoạt động trên bean được Spring quản lý — không dùng được với `new`. Nếu cần
tạo channel động (như `PingNextHop` làm), hãy inject `GrpcChannelFactory` và gọi
`createChannel(name)`.

Nếu gateway báo `UNAVAILABLE`, kiểm tra thứ tự khởi động: tầng dưới phải lên trước. Từ Giai
đoạn 3 bạn sẽ thêm health check để không còn phụ thuộc vào thứ tự nữa.

---

## GĐ 2 — Service nghiệp vụ đầu tiên

### Mục tiêu
`CreateDelivery` chạy thật: nhận request, validate, ghi Postgres, trả `Delivery`. Giá tạm thời
tính bằng công thức nội bộ — chưa gọi pricing qua mạng.

### Giải thích

**Vì sao chưa gọi pricing thật ở giai đoạn này?** Nguyên tắc "mỗi giai đoạn thêm đúng một loại
rủi ro mới". Giai đoạn 2 chỉ có rủi ro nghiệp vụ và dữ liệu. Giai đoạn 3 mới thêm rủi ro mạng.
Khi hỏng, bạn biết ngay hỏng ở đâu.

**Idempotency key là bắt buộc, không phải tuỳ chọn.** gRPC tự retry khi gặp `UNAVAILABLE`. Không
có khoá idempotent, một lần retry sẽ tạo trùng đơn. Đặt unique index trên cột này trong Postgres
và xử lý `DataIntegrityViolationException` thành "trả về đơn đã có" chứ không phải lỗi.

**Đừng biến entity JPA thành message protobuf.** Viết mapper thủ công. Message protobuf là lớp
vỏ ngoài của hệ thống; entity là chuyện nội bộ. Trộn hai thứ lại là con đường nhanh nhất biến
mọi thay đổi schema DB thành một breaking change của API.

**protovalidate thay cho validate bằng tay.** Ràng buộc viết ngay trong `.proto`:

```proto
string idempotency_key = 1 [(buf.validate.field).string.min_len = 8];
double weight_kg = 4 [(buf.validate.field).double = {gt: 0, lte: 500}];
```

Ưu điểm so với Bean Validation: ràng buộc nằm cùng chỗ với hợp đồng, nên client sinh từ cùng
file `.proto` cũng thấy được. Lưu ý import `buf/validate/validate.proto` cần thêm dependency
`build.buf:protovalidate` và cấu hình đường dẫn import cho plugin.

**Bật `buf breaking` trong CI từ giai đoạn này.** Đây là phần biến "contract-first" từ khẩu
hiệu thành ràng buộc kỹ thuật: xoá field, đổi số field, đổi kiểu — CI chặn trước khi merge.

### Checklist

- [ ] Bổ sung ràng buộc protovalidate vào `dispatch.proto` và `pricing.proto`
- [ ] Thêm dependency `build.buf:protovalidate`, cấu hình import path cho plugin
- [ ] `ValidationInterceptor` (order 50) trong `grpc-commons`, chạy protovalidate
- [ ] Bật `buf lint` và `buf breaking` trong CI
- [ ] Flyway migration `V1__create_delivery.sql` + unique index trên `idempotency_key`
- [ ] `DeliveryEntity`, `DeliveryRepository`
- [ ] `DeliveryMapper` viết tay giữa entity và message
- [ ] `PricingCalculator` nội bộ (Haversine × đơn giá) — tạm thời, giai đoạn 3 sẽ thay
- [ ] `DispatchServiceImpl.createDelivery`
- [ ] Xử lý trùng idempotency key → trả đơn cũ, không báo lỗi
- [ ] `DeliveryController` ở gateway với `POST /api/v1/deliveries`
- [ ] `GrpcStatusToHttpMapper` (bảng ở phụ lục)
- [ ] `@RestControllerAdvice` trả `ProblemDetail` (RFC 7807)
- [ ] Test unit với in-process gRPC server
- [ ] Test tích hợp với Testcontainers Postgres
- [ ] Test idempotency: gọi hai lần trả cùng một đơn
- [ ] Test validation: `weightKg = -1` trả `INVALID_ARGUMENT` kèm tên field
- [ ] Tag `v0.2-first-service`

### Nghiệm thu
```bash
curl -X POST localhost:8080/api/v1/deliveries \
  -H 'content-type: application/json' \
  -d '{"idempotencyKey":"abc12345","origin":{"latitude":21.03,"longitude":105.85},
       "destination":{"latitude":21.01,"longitude":105.80},"weightKg":12.5}'
```
Trả 201 với đơn có giá. Gọi lại lần hai trả đúng đơn đó. Gửi `weightKg: -1` trả 400 kèm tên
field sai.

---

## GĐ 3 — Tương tác service với service

### Mục tiêu
`dispatch-service` gọi `pricing-service` qua mạng thật. Deadline trừ dần qua ba chặng.
Health check thay cho việc phụ thuộc thứ tự khởi động.

### Giải thích

**Thay đổi duy nhất là nơi lấy giá.** `PricingCalculator` nội bộ được thay bằng một stub gRPC.
Nhấn mạnh điểm này trong README: nhờ contract-first, business logic không đổi một dòng — bạn
chỉ đổi cài đặt phía sau một interface.

**Deadline propagation.** Khi dispatch gọi pricing, **đừng** đặt một deadline tuyệt đối mới.
gRPC Java tự truyền phần deadline còn lại sang call con nếu call đó được tạo trong cùng
`Context`. Việc của bạn là chừa dự trữ cho phần xử lý còn lại:

```
gateway đặt 2000ms
  → dispatch nhận, còn ~1950ms
    → gọi pricing với min(1500ms, còn lại − 300ms dự trữ)
      → còn 300ms để ghi DB và trả về
```

`DeadlineGuardInterceptor` từ chối sớm khi thời gian còn lại dưới ngưỡng — trả lại tài nguyên
ngay thay vì tiêu tốn cho việc chắc chắn sẽ trễ hạn.

**Health check.** Mỗi service đăng ký `grpc.health.v1.Health`. Readiness probe của dispatch
phản ánh sức khoẻ của pricing — nhờ vậy không cần khởi động theo thứ tự nữa, và Kubernetes ở
Giai đoạn 8 sẽ dùng chính cơ chế này.

**Graceful shutdown.** Khi nhận SIGTERM, server phải ngừng nhận call mới nhưng chờ call đang
chạy kết thúc. Với streaming, đây là chuyện nghiêm túc: một stream đang mở có thể còn sống
hàng phút. Đặt `grpc.server.shutdown-grace-period` và trả `NOT_SERVING` trên health check ngay
khi bắt đầu tắt.

### Checklist

- [ ] `PricingPort` (interface) + `GrpcPricingAdapter` thay `PricingCalculator` nội bộ
- [ ] Cấu hình `grpc.client.pricing` trong `dispatch-service`
- [ ] Tính deadline con từ `Context.current().getDeadline()`, có dự trữ
- [ ] `DeadlineGuardInterceptor` hoạt động ở cả ba service
- [ ] Đăng ký `grpc.health.v1.Health` ở cả ba service
- [ ] Readiness probe của dispatch phản ánh sức khoẻ downstream
- [ ] Graceful shutdown + `NOT_SERVING` khi đang tắt
- [ ] Test: pricing chậm 3s → gateway trả 504 sau ~2s, không treo
- [ ] Test: correlation ID xuất hiện trong log của cả ba service
- [ ] Test tích hợp ba tiến trình thật bằng Testcontainers
- [ ] `docs/architecture.md` — sơ đồ chuỗi gọi
- [ ] Tag `v0.3-service-mesh`

### Nghiệm thu
Chèn `Thread.sleep(3000)` vào pricing; request từ gateway trả `504` sau khoảng 2 giây chứ không
treo, và cả ba service log cùng một `correlationId`.

---

## GĐ 4 — Module bảo mật dùng chung

### Mục tiêu
Bật `fleetpulse.security.enabled: true` và toàn bộ hệ thống chuyển sang mTLS + internal token
— mà không service nào phải viết code bảo mật.

### Giải thích

**Đây là giai đoạn nâng dự án từ "lab" lên "có thể đặt ra ngoài mạng".** Cũng là giai đoạn có
nhiều nội dung đáng viết vào README nhất.

#### Vai trò của edge-gateway

Gateway làm bốn việc, theo đúng thứ tự:

1. **Lọc token bên ngoài.** Có `Authorization: Bearer` thì verify JWT bằng JWKS, kiểm `iss`,
   `aud`, `exp`. Không có thì không sao — chuyển sang tier ẩn danh.
2. **Phân hạng.** `AUTHENTICATED` với scope theo claim của người dùng, hoặc `ANONYMOUS` với
   một bộ scope tối thiểu (ví dụ `quote:read`).
3. **Phát internal token.** JWT Ed25519, sống 60 giây, `aud` trỏ đích danh service sẽ nhận,
   `jti` duy nhất để chống replay.
4. **Mở kênh mTLS.** Trình client cert của gateway; đính internal token vào metadata.

Gateway là **người phát hành duy nhất**. Không service nội bộ nào được cài `InternalTokenIssuer`
— một service vừa phát vừa verify nghĩa là nó có thể tự nâng quyền cho chính mình.

#### Vì sao khách ẩn danh cũng được cấp token

Nếu không cấp, bạn sẽ phải viết hai nhánh xử lý: một cho request có danh tính, một cho request
không có. Hai đường đi nghĩa là khi thêm tính năng mới, một trong hai sẽ bị quên — và nhánh bị
quên thường là nhánh ẩn danh, nơi kiểm soát lỏng nhất. Một đường đi duy nhất, khác nhau ở dữ
liệu (`tier` và `scopes`), là thiết kế an toàn hơn hẳn.

#### Tin có điều kiện

`dispatch-service` cần biết đơn hàng thuộc về ai. Danh tính đó đến từ metadata do gateway đặt.
Nhưng metadata thì ai cũng đặt được.

Quy tắc: **chỉ tin internal token khi peer certificate là của gateway**. `PeerIdentityInterceptor`
(order 25) trích tên service từ SAN của client cert; `InternalTokenInterceptor` (order 30) chỉ
verify token khi peer nằm trong `trusted-issuers`. Một service nội bộ khác gửi token hợp lệ vẫn
bị từ chối — vì nó không có quyền đại diện người dùng.

Đây là chi tiết rất đáng viết vào README: nó cho thấy bạn hiểu rằng zero-trust không phải là
"bật TLS lên" mà là "không tin ai chỉ vì họ ở trong mạng".

#### Phân quyền theo từng RPC, mặc định từ chối

Khai báo trong `application.yml`:

```yaml
fleetpulse.security.authorization:
  "fleetpulse.ping.v1.PingService/Ping": [PUBLIC]
  "fleetpulse.dispatch.v1.DispatchService/CreateDelivery": [delivery:write]
  "fleetpulse.dispatch.v1.DispatchService/WatchDelivery": [delivery:read]
```

Một RPC mới chưa khai báo sẽ **bị chặn**. Nếu làm ngược lại (mặc định cho qua), mọi RPC bạn
thêm sau này đều là lỗ hổng tiềm tàng mà không ai nhận ra cho đến khi quá muộn.

#### Tăng cứng transport

- Tắt gRPC reflection ở profile `prod` — nó phơi toàn bộ schema API
- `maxInboundMessageSize` — chặn decompression bomb
- `maxConcurrentCallsPerConnection`, `maxConnectionAge` — chặn cạn tài nguyên
- `permitKeepAliveTime` tối thiểu — chặn lạm dụng keepalive, kiểu tấn công từng làm sập nhiều
  hệ gRPC
- TLS 1.3, tắt cipher yếu

#### Bề mặt cấu hình mà một service phải khai

Mục tiêu thiết kế: **ba dòng**.

```yaml
fleetpulse.security:
  enabled: true
  audience: pricing-service
  verification-key-location: file:/etc/fleetpulse/gateway-token-pub.pem
```

Mọi thứ khác — interceptor, thứ tự, cách trích SAN, cách kiểm `jti` — nằm trong
`security-commons`. Nếu bạn thấy mình phải copy-paste cấu hình giữa các service, đó là dấu hiệu
module chung thiếu một thứ gì đó.

### Checklist

**Hạ tầng khoá**
- [ ] Script sinh CA nội bộ + cert cho 4 thành phần, SAN đúng
- [ ] Sinh cặp khoá Ed25519 cho internal token
- [ ] `.gitignore` chặn mọi `*.pem`, `*.p12`
- [ ] Bí mật đọc từ biến môi trường / Docker secret, không có key trong git

**mTLS**
- [ ] Bật TLS phía server ở cả ba service nội bộ
- [ ] Bật `clientAuth: REQUIRE` (mTLS)
- [ ] Cấu hình client trình cert ở dispatch và gateway
- [ ] `PeerIdentityInterceptor` (order 25) trích SAN → Context
- [ ] Test: client không có cert bị từ chối handshake
- [ ] Test: cert ký bởi CA khác bị từ chối

**Internal token**
- [ ] `InternalToken` record với `subject`, `tier`, `scopes`, `audience`, `jti`
- [ ] `InternalTokenIssuer` — cài đặt **chỉ** ở gateway
- [ ] `InternalTokenVerifier` — cài đặt ở `security-commons`, dùng chung
- [ ] Verify: chữ ký, `exp`, `iat`, `aud` khớp đúng service
- [ ] Chống replay bằng `jti` lưu Redis đến khi hết hạn
- [ ] `InternalTokenInterceptor` (order 30), tin có điều kiện theo `trusted-issuers`
- [ ] Test: token đúng nhưng `aud` sai service → từ chối
- [ ] Test: token hợp lệ nhưng peer không phải gateway → từ chối
- [ ] Test: token quá hạn → `UNAUTHENTICATED`
- [ ] Test: dùng lại `jti` → từ chối

**Vành đai ngoài**
- [ ] JWT resource server ở gateway (JWKS, kiểm `iss`/`aud`/`exp`)
- [ ] Đường ẩn danh: không có token vẫn phát internal token tier `ANONYMOUS`
- [ ] Rate limit theo principal (Bucket4j + Redis), trả 429 kèm `Retry-After`
- [ ] Test: khách ẩn danh gọi được RPC công khai, bị chặn ở RPC cần quyền

**Phân quyền**
- [ ] `AuthorizationInterceptor` (order 40), bảng method → scope
- [ ] Mặc định từ chối với RPC chưa khai báo
- [ ] Test: RPC mới chưa khai báo bị chặn
- [ ] Test: thiếu scope → `PERMISSION_DENIED`

**Tăng cứng và kiểm toán**
- [ ] Tắt reflection ở profile `prod`
- [ ] `maxInboundMessageSize`, `maxConnectionAge`, `permitKeepAliveTime`
- [ ] `AuditLogInterceptor` — ai, RPC nào, khi nào, status gì. **Không ghi payload.**
- [ ] Quét phụ thuộc: OWASP Dependency-Check hoặc Trivy trong CI
- [ ] `docs/security.md` — mô hình mối đe doạ, sơ đồ hai vành đai
- [ ] Tag `v0.4-secure`

### Nghiệm thu
```bash
grpcurl -plaintext localhost:9091 list          # bị từ chối
make ping                                        # vẫn xanh, giờ đi qua mTLS
```
Gọi một RPC cần quyền bằng token ẩn danh nhận `PERMISSION_DENIED`, và sự kiện đó xuất hiện
trong audit log với đầy đủ correlation ID.

---

## GĐ 5 — Interceptor chain đầy đủ và quan trắc

### Mục tiêu
Hoàn thiện chain, chuẩn hoá mô hình lỗi, và nhìn thấy được một request đi qua ba service.

### Giải thích

**Bẫy lớn nhất vẫn là streaming.** Một `ServerInterceptor` viết kiểu ngây thơ chỉ chạy một lần
khi call mở. Với `PushLocation` nhận 10.000 message, việc validate hay đếm metric phải nằm trong
`onMessage` của một `ForwardingServerCallListener`. Viết test riêng khẳng định điều này — nó là
chi tiết cho thấy bạn thực sự hiểu gRPC chứ không sao chép tutorial.

**Viết test cho thứ tự chain.** Đừng tin trí nhớ về việc gRPC áp interceptor theo thứ tự nào.
Một test đăng ký các interceptor giả ghi tên mình vào một list, rồi khẳng định thứ tự — rẻ và
cứu bạn nhiều giờ debug.

**Mô hình lỗi có cấu trúc.** Dùng `google.rpc.Status` với `ErrorInfo` và `BadRequest` trong
detail, gắn qua `StatusProto.toStatusRuntimeException`. Client đọc được lỗi có cấu trúc thay vì
parse chuỗi. Nguyên tắc tuyệt đối: **không bao giờ để stack trace lọt vào `Status.getDescription()`**
— lỗi hệ thống trả `INTERNAL` với một `errorId`, chi tiết nằm trong log.

**Bốn chỉ số tối thiểu:** tỷ lệ request theo method, tỷ lệ lỗi theo `Status`, latency p50/p95/p99,
số stream đang mở. Thêm exemplar để nhảy từ biểu đồ Prometheus sang trace tương ứng.

### Checklist

- [ ] `MetricsInterceptor` (order 60) — đếm cả message của stream, không chỉ call
- [ ] `ExceptionTranslatingInterceptor` (order 70)
- [ ] `GrpcExceptionAdvice` ánh xạ exception nghiệp vụ → `Status` + `ErrorInfo`
- [ ] Rà soát: không có stack trace nào lọt vào `Status.description`
- [ ] Bộ interceptor client song song (correlation, metrics, deadline)
- [ ] `ValidationInterceptor` chạy trên **mọi** message của stream
- [ ] Test khẳng định thứ tự thực thi của chain
- [ ] Test interceptor với client streaming nhiều message
- [ ] OpenTelemetry agent + exporter OTLP; Jaeger hoặc Tempo trong compose
- [ ] Dashboard Grafana lưu vào `deploy/compose/grafana/`
- [ ] Log JSON có `correlationId` và `traceId`
- [ ] `docs/error-model.md`
- [ ] Tag `v0.5-observability`

### Nghiệm thu
Một `curl` duy nhất tạo ra một trace liền mạch gateway → dispatch → pricing trên Jaeger, và
dashboard Grafana hiển thị p99 cho từng RPC.

---

## GĐ 6 — Streaming và backpressure

### Mục tiêu
Hiện thực hoá ba RPC streaming đã chốt hợp đồng từ Giai đoạn 1.

### Giải thích

**Đây là phần khó nhất về kỹ thuật và đáng giá nhất trong portfolio.** Hầu hết dự án cá nhân
dùng gRPC chỉ làm unary; làm streaming đúng cách là điểm khác biệt rõ ràng.

**Backpressure.** Mặc định, khi bạn gọi `onNext()` trong vòng lặp mà client tiêu thụ chậm, gRPC
đệm dữ liệu trong bộ nhớ đến khi hết RAM. Cách làm đúng:

```java
var obs = (ServerCallStreamObserver<LocationSample>) responseObserver;
obs.setOnReadyHandler(() -> {
    while (obs.isReady() && source.hasNext()) {
        obs.onNext(source.next());
    }
});
obs.setOnCancelHandler(source::close);
```

Chỉ đẩy khi `isReady()`. Với client streaming, gọi `disableAutoRequest()` rồi `request(1)` sau
mỗi message xử lý xong để kiểm soát tốc độ nhận.

**Huỷ.** Client đóng tab, stream bị huỷ. Không đăng ký `setOnCancelHandler` thì luồng đọc DB
vẫn chạy và rò rỉ tài nguyên. Mọi stream phải có handler huỷ.

**`oneof` và tương thích tiến.** `DispatchChannel` dùng `oneof` cả hai chiều. Xử lý bằng `switch`
trên `getPayloadCase()`, và **luôn có nhánh `PAYLOAD_NOT_SET`** — để không vỡ khi client mới gửi
loại message mà server cũ chưa biết.

**Bảo mật cho stream.** Internal token sống 60 giây nhưng stream có thể sống hàng giờ. Giải
pháp: verify token một lần khi mở stream, ghi nhận danh tính vào Context cho cả phiên, và đặt
`maxConnectionAge` để buộc thiết lập lại định kỳ. Ghi rõ lựa chọn này trong `docs/security.md`
— đây là loại đánh đổi mà người phỏng vấn thích hỏi.

### Checklist

- [ ] `PushLocation` client streaming + `disableAutoRequest` + `request(1)` theo nhịp
- [ ] `WatchVehicle` server streaming + `setOnReadyHandler` + `setOnCancelHandler`
- [ ] `WatchDelivery` đọc từ `WatchVehicle`, bổ sung trạng thái đơn
- [ ] `DispatchChannel` bidi, xử lý `oneof` đủ nhánh kể cả `PAYLOAD_NOT_SET`
- [ ] Redis pub/sub làm cầu nối giữa telemetry và dispatch
- [ ] Keepalive hai phía + `permitKeepAliveWithoutCalls`
- [ ] Xác thực stream: verify một lần khi mở + `maxConnectionAge`
- [ ] SSE endpoint ở gateway cho `WatchDelivery`
- [ ] `tools/driver-simulator` — CLI giả lập tài xế bắn GPS
- [ ] Test: consumer chậm → `isReady()` chuyển false, heap không tăng
- [ ] Test: huỷ stream giải phóng tài nguyên
- [ ] Test: interceptor chạy đúng trên từng message
- [ ] Tag `v0.6-streaming`

### Nghiệm thu
Chạy simulator bắn 50 msg/s; mở SSE ở terminal khác thấy vị trí cập nhật liên tục; `Ctrl-C`
phía nhận không để lại thread treo.

---

## GĐ 7 — Failure isolation

### Mục tiêu
Một service chết không kéo theo cả hệ thống.

### Giải thích

**Circuit breaker chỉ dành cho unary.** Đây là hiểu lầm phổ biến. Circuit breaker đếm tỷ lệ thất
bại trên các call ngắn; một stream sống 30 phút không hợp với mô hình đó. Với stream, cơ chế
đúng là **reconnect có exponential backoff + jitter**, cộng keepalive để phát hiện đứt kết nối.
Viết rõ điều này trong README — rất ít người phân biệt được.

**Bulkhead.** Giới hạn số call đồng thời tới pricing. Nếu pricing chậm, tối đa 25 luồng của
dispatch bị kẹt, phần còn lại vẫn phục vụ các RPC khác. Không có bulkhead, toàn bộ thread pool
bị hút cạn — đây chính là kiểu lỗi lan truyền mà "failure isolation" nói đến.

**Fallback phải trung thực.** `QuotePriceResponse` có `from_fallback`. Khi pricing sập, trả giá
ước lượng nhưng **đánh dấu rõ**, và gateway hiển thị cảnh báo. Fallback im lặng trả dữ liệu sai
tệ hơn báo lỗi.

**Phân loại lỗi để retry.** Chỉ retry `UNAVAILABLE` và `DEADLINE_EXCEEDED`, và chỉ trên RPC
idempotent. Không bao giờ retry `INVALID_ARGUMENT` hay `PERMISSION_DENIED`.

**Chaos có kiểm soát.** `pricing-service` có flag chèn độ trễ và lỗi theo tỷ lệ. Bật lên, quay
màn hình circuit breaker chuyển `CLOSED → OPEN → HALF_OPEN → CLOSED` trên Grafana. Đoạn video
này chính là thứ đáng đưa vào portfolio.

### Checklist

- [ ] Resilience4j CircuitBreaker trên stub pricing, cấu hình qua YAML
- [ ] Bulkhead giới hạn call đồng thời
- [ ] TimeLimiter đồng bộ với deadline gRPC, không đặt hai giá trị mâu thuẫn
- [ ] Fallback tính giá xấp xỉ, đặt `from_fallback = true`
- [ ] Gateway hiển thị cảnh báo khi giá đến từ fallback
- [ ] gRPC service config: retry policy cho RPC idempotent
- [ ] Reconnect có backoff + jitter cho stream (không dùng circuit breaker)
- [ ] Chaos flag trong pricing, mặc định tắt
- [ ] Metric circuit breaker xuất ra Prometheus + panel Grafana
- [ ] Test: pricing sập → `CreateDelivery` vẫn thành công với fallback
- [ ] Test: circuit breaker mở sau N lỗi liên tiếp
- [ ] Test: bulkhead đầy → `RESOURCE_EXHAUSTED`, không treo
- [ ] Load test bằng `ghz`, ghi kết quả vào `docs/benchmarks.md`
- [ ] Ghi màn hình vòng đời circuit breaker
- [ ] Tag `v0.7-resilient`

### Nghiệm thu
`docker stop pricing-service` trong lúc `ghz` đang chạy: tỷ lệ thành công không tụt về 0, circuit
breaker mở trong vài giây, và tự đóng lại sau khi service quay lại.

---

## GĐ 8 — Sẵn sàng vận hành

### Mục tiêu
Đóng gói, triển khai, và trình bày.

### Giải thích

**Load balancing là vấn đề riêng của gRPC.** gRPC dùng kết nối HTTP/2 lâu dài, nên L4 load
balancer sẽ ghim toàn bộ traffic vào một pod. Giải pháp: headless Service + `dns:///` resolver
+ `round_robin` policy phía client. Đây là điểm kỹ thuật rất cụ thể và rất đáng nhắc tới.

**Trình bày quan trọng ngang code.** Một repo tốt mà README kém thì không ai đọc tới dòng code
nào. Cần: sơ đồ kiến trúc, GIF demo, bảng đánh đổi thiết kế, và một mục **"những gì tôi sẽ làm
khác đi"** — mục này cho thấy sự trưởng thành kỹ thuật nhiều hơn bất cứ thứ gì khác.

### Checklist

- [ ] Dockerfile multi-stage cho từng service, chạy bằng user không phải root
- [ ] Base image distroless hoặc alpine, quét Trivy sạch
- [ ] Manifest k8s: Deployment, headless Service, ConfigMap, Secret
- [ ] Client dùng `dns:///` + `round_robin`
- [ ] Liveness/readiness probe kiểu gRPC
- [ ] `PodDisruptionBudget` và `preStop` hook cho graceful shutdown
- [ ] HPA theo CPU hoặc số stream đang mở
- [ ] `docs/architecture.md` có sơ đồ Mermaid
- [ ] `docs/decisions/` — vài ADR ngắn cho các quyết định lớn
- [ ] `docs/benchmarks.md` — kết quả `ghz`, so sánh với REST tương đương
- [ ] README hoàn chỉnh: kiến trúc, cách chạy, GIF demo, đánh đổi, hướng phát triển
- [ ] Video demo 2–3 phút
- [ ] Tag `v1.0`

### Nghiệm thu
Người lạ clone repo, chạy `make up && make build`, có hệ thống hoạt động trong 10 phút mà không
cần hỏi bạn.

---

## GĐ 9 — Cổng GraphQL (dự án kế tiếp)

**Không triển khai trong FleetPulse.** Phần này là bản thiết kế để bạn tách thành một dự án
portfolio thứ hai, dùng lại `proto-contracts` của FleetPulse làm backend.

### Vì sao tách ra thay vì thêm vào

FleetPulse có một luận điểm rõ ràng: *giao tiếp backend hiệu năng cao với gRPC*. Thêm GraphQL
vào sẽ làm loãng luận điểm đó và biến repo thành một đống công nghệ chồng lên nhau. Tách ra
thành dự án riêng cho bạn **hai** mục portfolio với hai luận điểm khác nhau, và dự án thứ hai
tự nhiên có một câu chuyện hay: *"tôi xây một cổng GraphQL trên nền một hệ gRPC có sẵn"*.

### Phác thảo kiến trúc

```
Web/Mobile ──GraphQL──▶ graphql-gateway ──gRPC──▶ [FleetPulse services]
                              │
                              └── schema sinh từ .proto
```

`graphql-gateway` thay thế vai trò của `edge-gateway`, hoặc đứng cạnh nó. Nó nhập
`proto-contracts` như một dependency Maven bình thường — đây chính là phần thưởng của việc tách
hợp đồng thành module riêng từ Giai đoạn 0.

### Những vấn đề kỹ thuật đáng giải

**Ánh xạ schema.** Protobuf và GraphQL không tương ứng một-một. `oneof` → union type;
`google.protobuf.Timestamp` → custom scalar; `map<K,V>` → không có tương đương trực tiếp, phải
chuyển thành list of pairs. Viết một generator sinh `.graphqls` từ `.proto` là một dự án con
thú vị và rất dễ trình bày.

**Bài toán N+1.** Một query GraphQL lấy 50 đơn hàng, mỗi đơn cần thông tin xe → 50 call gRPC.
Giải bằng DataLoader gom thành một batch call. Điều này đòi hỏi thêm RPC `BatchGetVehicles` vào
hợp đồng — và vì có `buf breaking` trong CI, bạn sẽ thực hành được quy trình mở rộng hợp đồng
mà không phá vỡ tương thích.

**Subscription trên nền streaming.** GraphQL subscription qua WebSocket, phía dưới là
`WatchDelivery` server streaming. Đây là chỗ hai công nghệ gặp nhau đẹp nhất, và cũng là chỗ
backpressure lại xuất hiện — lần này giữa WebSocket và gRPC stream.

**Bảo mật.** GraphQL có bề mặt tấn công riêng: query depth limiting, complexity analysis, và
chặn introspection ở production. Field-level authorization phải ánh xạ về bộ scope trong
internal token của FleetPulse — nghĩa là `security-commons` được dùng lại gần như nguyên vẹn.

**Persisted queries.** Chỉ chấp nhận query đã đăng ký trước, tra theo hash. Vừa chặn query độc
hại, vừa giảm băng thông. Đây là một câu trả lời rất tốt cho câu hỏi "GraphQL không nguy hiểm sao".

### Lộ trình gợi ý cho dự án thứ hai

| GĐ | Nội dung |
|---|---|
| 1 | Schema thủ công cho 2–3 query, gọi gRPC, chạy được |
| 2 | Generator sinh `.graphqls` từ `.proto` |
| 3 | DataLoader giải N+1, thêm RPC batch vào hợp đồng |
| 4 | Subscription trên nền server streaming |
| 5 | Bảo mật: depth/complexity limit, persisted queries, field authorization |
| 6 | Federation nếu muốn đi xa hơn — gộp nhiều subgraph |

---

## Phụ lục

### A. Bảng ánh xạ gRPC Status sang HTTP

| gRPC Status | HTTP | Ghi chú |
|---|---|---|
| `OK` | 200 / 201 | |
| `INVALID_ARGUMENT` | 400 | Kèm chi tiết field từ `BadRequest` detail |
| `UNAUTHENTICATED` | 401 | |
| `PERMISSION_DENIED` | 403 | |
| `NOT_FOUND` | 404 | |
| `ALREADY_EXISTS` | 409 | |
| `FAILED_PRECONDITION` | 422 | |
| `RESOURCE_EXHAUSTED` | 429 | Kèm `Retry-After` |
| `INTERNAL`, `UNKNOWN` | 500 | Chỉ trả `errorId`; chi tiết nằm trong log |
| `UNIMPLEMENTED` | 501 | |
| `UNAVAILABLE` | 503 | |
| `DEADLINE_EXCEEDED` | 504 | Không phải 500 |

### B. Sơ đồ cổng

| Thành phần | HTTP | gRPC |
|---|---|---|
| edge-gateway | 8080 | — |
| dispatch-service | 8091 | 9091 |
| pricing-service | 8092 | 9092 |
| telemetry-service | 8093 | 9093 |

Quy ước: cổng gRPC = cổng HTTP + 1000. Dễ nhớ, dễ mở rộng.

### C. Mười sai lầm thường gặp

1. Interceptor chỉ chạy một lần với streaming call — phải bọc `ServerCall.Listener`
2. Đẩy `onNext` trong vòng lặp mà không kiểm tra `isReady()` — tràn bộ nhớ khi consumer chậm
3. Quên `setOnCancelHandler` — rò rỉ tài nguyên khi client ngắt kết nối
4. Dùng circuit breaker cho long-lived stream thay vì reconnect có backoff
5. Để stack trace lọt vào `Status.getDescription()` và trả ra client
6. Dùng chung entity JPA làm message protobuf
7. Đặt deadline tuyệt đối mới ở mỗi hop thay vì trừ dần từ deadline còn lại
8. Retry trên RPC không idempotent, gây tạo trùng dữ liệu
9. Bật gRPC reflection ở production — phơi toàn bộ schema API
10. Tin metadata từ upstream mà không xác minh danh tính peer

### D. Mô tả cho trang portfolio

> **FleetPulse** — *Backend Communication*
>
> Nền tảng điều phối giao hàng thời gian thực trên Spring Boot và gRPC, với chuỗi gọi phân tầng
> nơi một service vừa là server vừa là client. Bao gồm đủ 4 kiểu RPC, interceptor chain hoạt
> động đúng với streaming, bảo mật hai vành đai với mTLS zero-trust nội bộ, và cô lập lỗi qua
> deadline propagation cùng circuit breaker.
>
> `Java 17` `Spring Boot 3` `Maven` `gRPC` `Protocol Buffers` `Resilience4j` `PostgreSQL`
> `Redis` `Prometheus` `Testcontainers` `JUnit 5`
>
> **Architecture patterns:** Contract-First · Interceptor Chain · Failure Isolation ·
> Deadline Propagation · Backpressure Control · Two-Perimeter Security · Shared Auto-Configuration

### E. Quy ước git

Mỗi giai đoạn là một nhánh, merge vào `main` qua PR — kể cả khi bạn làm một mình. Lịch sử PR là
một phần của portfolio.

```
feat(commons): add correlation-id interceptor with streaming support
feat(dispatch): implement CreateDelivery with idempotency key
feat(security): verify internal token only from trusted peer
fix(telemetry): honour isReady before pushing samples
docs(readme): add two-perimeter security diagram
chore(ci): enable buf breaking check against main
```

Mỗi PR mô tả: vấn đề, cách giải, đánh đổi. Đây là chỗ thể hiện tư duy kỹ thuật cho người đọc
repo sau này.
