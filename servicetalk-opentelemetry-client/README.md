# ServiceTalk OpenTelemetry Client

ServiceTalk-based transport implementations for OpenTelemetry SDK exporters.

## Overview

This module provides ServiceTalk-based implementations of OpenTelemetry's `GrpcSender` and `HttpSender` interfaces, enabling you to use ServiceTalk's high-performance HTTP and gRPC clients as the underlying transport for OpenTelemetry telemetry data (traces, metrics, and logs).

## Benefits

- **Zero Configuration**: Automatic discovery via Java SPI - just use standard OpenTelemetry APIs
- **Universal Transport**: Single implementation works for traces, metrics, and logs
- **Simplified API**: SDK handles serialization; senders only handle transport
- **ServiceTalk Integration**: Leverages ServiceTalk's async, non-blocking networking
- **Performance**: Benefits from ServiceTalk's optimized HTTP/2 and connection management
- **Flexibility**: Advanced users can provide custom HttpClient configuration

## Quick Start (Automatic Discovery via SPI)

**Recommended approach** - ServiceTalk transport is automatically discovered and used by OpenTelemetry SDK:

### HTTP Traces

```java
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

// Just configure the standard OpenTelemetry exporter
// ServiceTalk transport is automatically discovered via SPI
SpanExporter spanExporter = OtlpHttpSpanExporter.builder()
    .setEndpoint("http://localhost:4318/v1/traces")
    .setTimeout(Duration.ofSeconds(30))
    .addHeader("api-key", "secret-key")
    .setCompression("gzip")
    .build();

// Register with OpenTelemetry SDK
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
    .build();
```

### gRPC Traces

```java
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

// ServiceTalk gRPC transport automatically discovered
SpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
    .setEndpoint("http://localhost:4317")
    .setTimeout(Duration.ofSeconds(30))
    .addHeader("authorization", "Bearer " + token)
    .build();

SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
    .build();
```

### HTTP Metrics

The same pattern works for metrics:

```java
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;

MetricExporter metricExporter = OtlpHttpMetricExporter.builder()
    .setEndpoint("http://localhost:4318/v1/metrics")
    .setTimeout(Duration.ofSeconds(30))
    .build();

SdkMeterProvider meterProvider = SdkMeterProvider.builder()
    .registerMetricReader(
        PeriodicMetricReader.builder(metricExporter)
            .setInterval(Duration.ofSeconds(60))
            .build()
    )
    .build();
```

### HTTP Logs

And with log exporters:

```java
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;

LogRecordExporter logExporter = OtlpHttpLogRecordExporter.builder()
    .setEndpoint("http://localhost:4318/v1/logs")
    .build();

SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
    .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
    .build();
```

## How It Works

This module implements OpenTelemetry's **SPI (Service Provider Interface)** for automatic transport discovery:

1. **You configure** standard OpenTelemetry exporters (OtlpGrpcSpanExporter, OtlpHttpMetricExporter, etc.)
2. **SDK discovers** ServiceTalk providers via Java ServiceLoader mechanism
3. **SDK creates** ServiceTalk-based senders with your configuration
4. **Data flows** through ServiceTalk's high-performance HTTP clients

```
┌─────────────────────────────────┐
│    Your Application Code        │
│  (Standard OpenTelemetry API)   │
└────────────┬────────────────────┘
             │ OtlpGrpcSpanExporter.builder()...
             ▼
┌─────────────────────────────────┐
│   OpenTelemetry SDK 1.59.0+     │
│  - ServiceLoader discovers SPI  │
│  - Calls GrpcSenderProvider     │
│  - Gets ServiceTalkGrpcSender   │
└────────────┬────────────────────┘
             │ Pre-serialized protobuf bytes
             ▼
┌─────────────────────────────────┐
│   ServiceTalkGrpcSender         │
│  - HTTP/2 with gRPC protocol    │
│  - Async, non-blocking I/O      │
└────────────┬────────────────────┘
             │ HTTP/2 POST
             ▼
┌─────────────────────────────────┐
│      OTLP Collector             │
│  (Jaeger, Grafana, Prometheus)  │
└─────────────────────────────────┘
```

## Advanced Configuration

### Custom ServiceTalk HttpClient

For advanced scenarios, you can directly construct senders with custom HttpClient configuration:

```java
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.opentelemetry.client.ServiceTalkHttpSender;

// Create a fully customized ServiceTalk HTTP client
HttpClient customClient = HttpClients.forSingleAddress("otlp-collector", 4318)
    .ioExecutor(customIoExecutor)
    .executor(customExecutor)
    .executionStrategy(customStrategy)
    .protocols(HttpProtocolConfigs.h1Default())
    .appendClientFilter(myCustomFilter)
    .build();

// Create sender directly with custom client
ServiceTalkHttpSender httpSender = new ServiceTalkHttpSender(
    customClient,
    null,  // No compression
    "application/x-protobuf",
    "/v1/traces",
    () -> Map.of("Authorization", List.of("Bearer " + token))
);

// Use with OpenTelemetry exporter
SpanExporter spanExporter = OtlpHttpSpanExporter.builder()
    .setHttpSender(httpSender)
    .build();
```

### Custom gRPC Client

```java
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.opentelemetry.client.ServiceTalkGrpcSender;

// Create custom HTTP/2 client for gRPC
HttpClient grpcClient = HttpClients.forSingleAddress("otlp-collector", 4317)
    .protocols(HttpProtocolConfigs.h2Default())
    .sslConfig(customSslConfig)
    .build();

// Create gRPC sender with full method name
ServiceTalkGrpcSender grpcSender = new ServiceTalkGrpcSender(
    grpcClient,
    CompressorEncoding.getInstance("gzip"),
    "/opentelemetry.proto.collector.trace.v1.TraceService/Export",
    () -> Map.of("api-key", List.of("secret-123"))
);

// Use with OpenTelemetry exporter
SpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
    .setGrpcSender(grpcSender)
    .build();
```

### SSL/TLS Configuration

Configure HTTPS endpoints with custom SSL:

```java
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ClientSslConfig;

// Build SSL configuration
ClientSslConfig sslConfig = new ClientSslConfigBuilder(
    DefaultTrustManagerFactory.INSTANCE
).build();

// Create HTTPS client
HttpClient httpsClient = HttpClients.forSingleAddress("otlp-collector", 4318)
    .sslConfig(sslConfig)
    .build();

ServiceTalkHttpSender httpSender = new ServiceTalkHttpSender(
    httpsClient,
    null,
    "application/x-protobuf",
    "/v1/traces",
    () -> Map.of()
);
```

## Architecture

The sender implementations follow OpenTelemetry's new Sender pattern introduced in SDK 1.59.0:

1. **SDK Handles Serialization**: The OpenTelemetry SDK serializes spans/metrics/logs to protobuf
2. **Sender Handles Transport**: The sender receives pre-serialized data and transmits it
3. **Universal Design**: Same sender works for all telemetry types (traces, metrics, logs)

```
┌─────────────────────────┐
│  OpenTelemetry SDK      │
│  - Span Collection      │
│  - Protobuf Encoding    │
└──────────┬──────────────┘
           │ MessageWriter (opaque bytes)
           ▼
┌─────────────────────────┐
│  ServiceTalkHttpSender  │
│  - HTTP Transport       │
│  - Async Callbacks      │
│  - Compression          │
└──────────┬──────────────┘
           │ HTTP POST
           ▼
┌─────────────────────────┐
│  OTLP Collector         │
│  (Jaeger, Prometheus,   │
│   Grafana, etc.)        │
└─────────────────────────┘
```

## Migration from Old SpanExporter API

If you were using the older `ServiceTalkSpanExporter` API (removed in this refactoring), migration is straightforward:

### Before (Old API - Removed)
```java
// Old API - custom SpanExporter, only worked for spans
import io.servicetalk.opentelemetry.client.ServiceTalkGrpcSpanExporter;

ServiceTalkSpanExporter spanExporter = ServiceTalkGrpcSpanExporter.builder()
    .setEndpoint("http://localhost:4317")
    .build();

SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
    .build();
```

### After (New API - Zero Configuration)
```java
// New API - pure OpenTelemetry SDK, ServiceTalk auto-discovered
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

SpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
    .setEndpoint("http://localhost:4317")
    .setTimeout(Duration.ofSeconds(30))
    .build();

// Same SDK registration
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
    .build();
```

### Key Improvements

| Aspect | Old API | New API (SPI) |
|--------|---------|---------------|
| **Configuration** | Custom ServiceTalk API | Standard OpenTelemetry API |
| **Discovery** | Manual instantiation | Automatic via Java SPI |
| **Scope** | Spans only | Traces, Metrics, Logs |
| **Serialization** | Custom protobuf code (~427 lines) | SDK handles it |
| **Implementation** | ~800 lines of code | ~750 lines (with more features) |
| **Maintenance** | High (custom serialization) | Low (transport only) |
| **Standards Compliance** | Custom | Full OpenTelemetry compliance |

## Performance Considerations

- **Connection Pooling**: ServiceTalk manages connection pools efficiently
- **HTTP/2 Multiplexing**: gRPC transport uses HTTP/2 with multiplexing support
- **Compression**: gzip compression reduces bandwidth (enabled by default for HTTP)
- **Async Operations**: All network I/O is non-blocking and asynchronous
- **Back Pressure**: ServiceTalk's reactive API handles back pressure naturally

## Thread Safety

Both `ServiceTalkHttpSender` and `ServiceTalkGrpcSender` are thread-safe and can be used concurrently from multiple threads.

## Shutdown

Always shut down senders when your application terminates:

```java
// Shutdown is async and returns a CompletableResultCode
CompletableResultCode shutdownResult = httpSender.shutdown();
shutdownResult.join(10, TimeUnit.SECONDS);  // Wait up to 10 seconds
```

## Dependencies

This module requires:
- OpenTelemetry SDK 1.59.0 or later
- ServiceTalk HTTP/gRPC client libraries
- Java 8 or later

## Related Modules

- [`servicetalk-opentelemetry-http`](../servicetalk-opentelemetry-http): HTTP server instrumentation
- [`servicetalk-opentelemetry-asynccontext`](../servicetalk-opentelemetry-asynccontext): AsyncContext propagation

## Further Reading

- [OpenTelemetry SDK Documentation](https://opentelemetry.io/docs/languages/java/)
- [OTLP Protocol Specification](https://opentelemetry.io/docs/specs/otlp/)
- [ServiceTalk Documentation](https://docs.servicetalk.io/)
