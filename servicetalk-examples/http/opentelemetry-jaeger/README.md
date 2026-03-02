# OpenTelemetry OTLP Export with Jaeger

This example demonstrates how to use ServiceTalk's OpenTelemetry HTTP transport to export traces to Jaeger using the OTLP (OpenTelemetry Protocol) over HTTP.

## Key Features

- **Automatic Transport Discovery**: ServiceTalk's HTTP transport is automatically discovered via Java SPI - no manual configuration required
- **OTLP over HTTP**: Traces are exported using the standard OTLP/HTTP protocol
- **Real Collector Integration**: Sends traces to an actual Jaeger instance
- **Distributed Tracing**: Demonstrates client-server span relationships
- **Manual Instrumentation**: Shows how to create custom spans alongside automatic HTTP instrumentation

## Prerequisites

### 1. Start Jaeger with OTLP Support

Jaeger all-in-one includes the OTLP HTTP endpoint. Use the provided script to start it:

```bash
./servicetalk-examples/http/opentelemetry-jaeger/start-jaeger.sh
```

Or start it manually with Docker:

```bash
docker run -d --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 \
  -p 4318:4318 \
  jaegertracing/all-in-one:latest
```

**Ports:**
- `16686` - Jaeger UI (http://localhost:16686)
- `4318` - OTLP HTTP endpoint (http://localhost:4318)

### 2. Verify Jaeger is Running

```bash
# Check if Jaeger is running
docker ps | grep jaeger

# Access Jaeger UI
open http://localhost:16686
```

## Running the Example

From the ServiceTalk root directory:

```bash
./gradlew :servicetalk-examples-http-opentelemetry-jaeger:run
```

Or using the `java` command:

```bash
./gradlew :servicetalk-examples-http-opentelemetry-jaeger:installDist
./servicetalk-examples/http/opentelemetry-jaeger/build/install/servicetalk-examples-http-opentelemetry-jaeger/bin/servicetalk-examples-http-opentelemetry-jaeger
```

## What the Example Does

1. **Configures OpenTelemetry SDK** with OTLP HTTP exporter pointing to Jaeger
2. **Starts an HTTP server** on port 8080 with OpenTelemetry server filter
3. **Makes HTTP requests** to the server with OpenTelemetry client filter
4. **Creates custom spans** to demonstrate manual instrumentation
5. **Exports all spans** to Jaeger using ServiceTalk's HTTP transport

## Viewing Traces in Jaeger

1. Open Jaeger UI: http://localhost:16686
2. Select service: `servicetalk-jaeger-example`
3. Click "Find Traces"
4. You should see traces with spans including:
   - Custom workflow spans
   - HTTP client spans
   - HTTP server spans
   - Nested preparation/processing spans

## How SPI Discovery Works

The example includes these dependencies:

```gradle
// OTLP exporter (provides the API)
implementation "io.opentelemetry:opentelemetry-exporter-otlp"

// ServiceTalk HTTP transport (SPI provider)
runtimeOnly project(":servicetalk-opentelemetry-client")
```

When `OtlpHttpSpanExporter` is created, it uses Java's `ServiceLoader` to discover available HTTP transport implementations. ServiceTalk's `ServiceTalkHttpSenderProvider` is automatically found and used - no explicit configuration needed!

## Code Highlights

### Automatic Transport Discovery

```java
// ServiceTalk's HTTP transport is automatically used via SPI
OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
        .setEndpoint("http://localhost:4318/v1/traces")
        .setTimeout(Duration.ofSeconds(10))
        .build();
```

### Manual Instrumentation

```java
Tracer tracer = openTelemetry.getTracer("my-service");

Span span = tracer.spanBuilder("my-operation")
        .setAttribute("key", "value")
        .startSpan();
try {
    // do work
} finally {
    span.end();
}
```

### Automatic HTTP Instrumentation

```java
// Client filter automatically creates spans for HTTP requests
HttpClients.forSingleAddress("localhost", 8080)
        .appendClientFilter(new OpenTelemetryHttpRequesterFilter.Builder()
                .componentName("my-client")
                .build())
        .buildBlocking();
```

## Troubleshooting

### Jaeger Not Receiving Traces

1. Verify Jaeger is running: `docker ps | grep jaeger`
2. Check OTLP endpoint is accessible: `curl http://localhost:4318/v1/traces`
3. Look for errors in the example output
4. Check Jaeger logs: `docker logs jaeger`

### Port Already in Use

If port 8080 or 4318 is already in use, either:
- Stop the conflicting service
- Modify the example to use different ports

### No Spans Visible in Jaeger UI

- Wait a few seconds for spans to be batched and exported
- Check the time range in Jaeger UI (try "Last 15 minutes")
- Verify the service name matches: `servicetalk-jaeger-example`

## Cleanup

Stop and remove the Jaeger container:

```bash
docker stop jaeger
docker rm jaeger
```

## Learn More

- [ServiceTalk OpenTelemetry Integration](../../../servicetalk-opentelemetry-client/README.md)
- [OpenTelemetry Java SDK](https://github.com/open-telemetry/opentelemetry-java)
- [OTLP Specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
