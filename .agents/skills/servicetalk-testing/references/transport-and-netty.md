# Testing transport, codecs, and end-to-end behavior

How to test anything that touches Netty — a codec, a `ChannelHandler`, a
filter, or a full client-to-server round trip. Read [../SKILL.md](../SKILL.md)
first for the rules that apply to every test.

Everything here lives in the same `src/test/java` as the plain unit tests.
There is no separate integration-test source set.

## Where the helpers live

| Class | Module | Gradle line |
|---|---|---|
| `AddressUtils`, `ExecutionContextExtension`, `EmbeddedDuplexChannel`, `CloseUtils`, `MockFlushStrategy`, `RandomDataUtils` | `servicetalk-transport-netty-internal/src/testFixtures` | `testImplementation testFixtures(project(":servicetalk-transport-netty-internal"))` |
| `DefaultTestCerts` | `servicetalk-test-resources/src/main` | `testImplementation project(":servicetalk-test-resources")` |

## Decide first: do you need a socket?

| Testing | Use |
|---|---|
| Encoding/decoding bytes, a single `ChannelHandler` | `EmbeddedChannel` — no sockets, no threads, fastest |
| Half-close (`ALLOW_HALF_CLOSURE`) semantics | `EmbeddedDuplexChannel` — plain `EmbeddedChannel` is not a `DuplexChannel` |
| A filter, client, or server contract | A real bound server and a real client |

Prefer `EmbeddedChannel` whenever it can answer the question. See
`HttpRequestEncoderTest`, `HttpRequestDecoderTest`, and `HttpObjectDecoderTest`.

## Binding a server: `AddressUtils`

| Method | Purpose |
|---|---|
| `localAddress(0)` | Loopback `InetSocketAddress` on an OS-assigned free port. **Always use `0`.** |
| `serverHostAndPort(ServerContext)` | The `HostAndPort` a client should target |
| `serverHostAndPort(SocketAddress)` | Same, from a raw address |
| `hostHeader(HostAndPort)` | `Host` header value, handling IPv6 brackets |
| `newSocketAddress()` | A `DomainSocketAddress` over a temp file, for UDS tests |

Never hardcode a port. Test classes run concurrently, so a fixed port
intermittently collides with another class.

## The canonical end-to-end test

Nested try-with-resources: server first, then client. From
`servicetalk-http-netty/src/test/java/io/servicetalk/http/netty/BlockingStreamingInputStreamTest.java`:

```java
try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
        .listenBlockingStreamingAndAwait((ctx, request, response) -> {
            try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                int b;
                while ((b = request.payloadBodyInputStream().read()) >= 0) {
                    Buffer buffer = ctx.executionContext().bufferAllocator().newBuffer(1);
                    buffer.writeByte(b);
                    writer.write(buffer);
                }
            }
        });
     BlockingStreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
             .buildBlockingStreaming()) {
    // ... exercise the client, assert on the response ...
}
```

Imports:

```java
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
```

When the resources are fields rather than locals, close them in `@AfterEach`
with a composite closeable.

## Teardown: order matters

```java
@AfterEach
void tearDown() throws Exception {
    newCompositeCloseable().appendAll(httpConnection, httpClient, clientExecutor,
            serverContext, serverExecutor).close();
}
```

```java
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
```

Close **client-side handles first, then the `ServerContext`, then executors.**
Closing an executor while a client built on it is still open can hang the close
or throw `RejectedExecutionException` during teardown.

Failing to close is worse than it looks. An orphaned `ServerContext` or
`IoExecutor` does not fail your test — it leaks a socket or a thread pool into
a test class running at the same time, which then looks flaky for no reason.

## Sharing an `ExecutionContext`

Building Netty event loops per test method is slow. `ExecutionContextExtension`
owns an `IoExecutor` plus an `Executor` and closes them for you. It is the
standard way to share them across a test class.

```java
@RegisterExtension
static final ExecutionContextExtension SERVER_CTX =
    ExecutionContextExtension.cached("server-io", "server-executor")
            .setClassLevel(true);
@RegisterExtension
public static final ExecutionContextExtension CLIENT_CTX =
        ExecutionContextExtension.cached("client-io", "client-executor")
                .setClassLevel(true);
```

Factories: `immediate()`, `cached()`, `cached(ioPrefix, executorPrefix)`,
`single()`.

`setClassLevel(true)` creates the context once per class instead of once per
method. Use it whenever the field is `static` — that is the common case, and
forgetting it silently rebuilds event loops for every test.

`ExecutorExtension` (in `servicetalk-concurrent-api` testFixtures) is the
equivalent when you need only an `Executor`, not a full `ExecutionContext`.

## TLS

Certificates come from `DefaultTestCerts` in `servicetalk-test-resources`.

```java
// server
serverBuilder.sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
        DefaultTestCerts::loadServerKey).build());

// client
clientBuilder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
        .peerHost(serverPemHostname()).build());
```

`serverPemHostname()` is a static on `DefaultTestCerts` and returns the name the
test certificate was issued for. Hardcoding `"localhost"` works today but
breaks if the fixture changes.

For mutual TLS add `.keyManager(DefaultTestCerts::loadClientPem,
DefaultTestCerts::loadClientKey)` to the client config — see `MutualSslTest`.

## Protocol and strategy matrices

Two test-only enums in `servicetalk-http-netty` drive the common matrices:
`HttpProtocol` (`HTTP_1`, `HTTP_2`, plus `allCombinations()` for ALPN
negotiation) and `HttpTestExecutionStrategy` (`NO_OFFLOAD`, `DEFAULT`).

Combine them with `@ParameterizedTest` plus `@MethodSource` returning
`Stream<Arguments>`, and prune invalid pairs with an assumption and a message:

```java
assumeFalse(!h2PriorKnowledge && addTrailers,
        "HTTP/1.1 does not support Content-Length with trailers");
```

## Base class, or standalone?

`AbstractNettyHttpServerTest` owns the whole server and client
wiring — TLS toggle, observers, filters, protocol config, executor
combinations. Extend it when you want many parameterized variants against one
standard echo-style server and client pair.

Write a standalone class when the topology is unusual: a proxy, a raw socket
peer, custom TLS, or more than one server. Most newer tests are standalone,
composing `ExecutionContextExtension` with `HttpServers.forAddress(localAddress(0))`
directly.

## Reference examples

- `servicetalk-http-netty/src/test/java/io/servicetalk/http/netty/BlockingStreamingInputStreamTest.java`
  — the compact end-to-end shape, nested try-with-resources.
- `servicetalk-grpc-netty/src/test/java/io/servicetalk/grpc/netty/GrpcUdsTest.java`
  — the same shape for gRPC, over a Unix domain socket.
- `servicetalk-tcp-netty-internal/src/test/java/io/servicetalk/tcp/netty/internal/AbstractTcpServerTest.java`
  — class-level `ExecutionContextExtension` for a shared server and client context.
- `servicetalk-http-netty/src/test/java/io/servicetalk/http/netty/HttpRequestEncoderTest.java`
  — `EmbeddedChannel`, no sockets.
- `servicetalk-http-netty/src/test/java/io/servicetalk/http/netty/SniTest.java`
  — TLS with `DefaultTestCerts`, SNI, and ALPN.
- `servicetalk-http-netty/src/test/java/io/servicetalk/http/netty/AbstractNettyHttpServerTest.java`
  — the shared base class and its teardown ordering.
