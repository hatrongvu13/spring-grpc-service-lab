# FleetPulse

Nền tảng điều phối và theo dõi giao hàng thời gian thực trên Spring Boot và gRPC, xây dựng
theo mô hình **contract-first** với chuỗi gọi phân tầng — nơi một service vừa là server cho
tầng trên, vừa là client của tầng dưới.

```
Web client ──REST──▶ edge-gateway ──gRPC──▶ dispatch-service ──gRPC──▶ pricing-service
                                                                              │
                                                                              ▼
                                                                     telemetry-service
```

## Chạy thử trong 5 phút

```bash
./mvnw clean install -DskipTests

# 4 terminal, khởi động từ tầng dưới lên
make run-telemetry
make run-pricing
make run-dispatch
make run-gateway

make ping
```

Kết quả mong đợi:

```json
{
  "ok": true,
  "answeredBy": "dispatch-service",
  "hops": 3,
  "trace": [
    "telemetry-service <- pricing-service (1ms)",
    "pricing-service <- dispatch-service (14ms)",
    "dispatch-service <- edge-gateway (31ms)"
  ]
}
```

Một lệnh `curl` đi xuyên bốn tiến trình và quay ngược lại. Nếu lệnh này xanh, toàn bộ đường
dây đã đúng: hợp đồng proto, cấu hình client/server, và ngữ cảnh truyền xuyên chuỗi.

## Cấu trúc module

| Module | Vai trò |
|---|---|
| `proto-contracts` | Nguồn sự thật duy nhất cho mọi hợp đồng API. Không chứa logic. |
| `grpc-commons` | Interceptor chain, mô hình lỗi, deadline, metrics. Không biết gì về nghiệp vụ. |
| `security-commons` | Xác thực và phân quyền dùng chung. Service chỉ khai 3 dòng cấu hình. |
| `dispatch-service` | Tầng giữa — vừa server vừa client. Trọng tâm kiến trúc. |
| `pricing-service` | Tầng cuối, nơi mô phỏng sự cố để kiểm chứng cô lập lỗi. |
| `telemetry-service` | Luồng dữ liệu streaming từ thiết bị. |
| `edge-gateway` | Biên hệ thống. Điểm duy nhất chấp nhận token bên ngoài và phát internal token. |

## Đặc trưng kỹ thuật

- **Contract-First** — API định nghĩa trong `.proto` trước, CI chặn thay đổi phá vỡ tương thích
- **Đủ 4 kiểu RPC** — unary, server streaming, client streaming, bidi streaming
- **Interceptor Chain** — thứ tự kiểm soát bằng `@Order`, hoạt động đúng trên từng message của
  stream chứ không chỉ khi mở kết nối
- **Bảo mật hai vành đai** — JWT tại biên cho người dùng, mTLS giữa các service nội bộ với danh
  tính lấy từ chứng chỉ
- **Failure Isolation** — deadline propagation xuyên 3 hop, circuit breaker, bulkhead, fallback
  được đánh dấu rõ ràng
- **Backpressure** — streaming kiểm soát bằng `setOnReadyHandler` thay vì đẩy dữ liệu mù

## Ngăn xếp công nghệ

Java 17 · Spring Boot 3 · Maven · gRPC · Protocol Buffers · protovalidate · Resilience4j ·
PostgreSQL · Redis · Micrometer / Prometheus / Grafana · Testcontainers · JUnit 5 · buf

## Tài liệu

- [`ROADMAP.md`](ROADMAP.md) — lộ trình 9 giai đoạn kèm checklist chi tiết
- [`docs/`](fleetpulse/docs) — kiến trúc, mô hình lỗi, mô hình bảo mật

## Giấy phép

MIT
