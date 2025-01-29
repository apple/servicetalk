package io.servicetalk.opentelemetry.http;

import io.netty.util.internal.ThreadLocalRandom;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.annotation.Nonnull;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is a variation of Datadog tracing reproducer using OpenTelementry tracing API.
 * This test should normally fail in order to demonstrate Datadog traceID propagation failure when using async ST HTTP
 * client.
 * <p>
 * Datadog agent must be attached: -javaagent:/path/to/dd-java-agent.jar
 * The following env variable must be set: DD_TRACE_OTEL_ENABLED=true
 * <p>
 * Test server consists of 1 filter and 1 service, it generates response asynchronously.
 * <p>
 * Test client attaches 1 connection filter and 2 client filters.
 * Datadog TraceIDs are captured at different stages of client request processing, and then compared.
 * For debug purposes TraceIDs are also logged on the server.
 * <p>
 * TraceIDs captured in connection filter are normally not used for comparison.
 * TraceIDs are generated for every `performRequest` call and are expected to be propagated through the client filters.
 * <p>
 * By running tests you can confirm that async ST client call always results in TraceID being lost inside `Single` async
 * operators like `afterFinally` of `subscribe`. This is the main case which demonstrates the lack of proper ST + DD
 * support.
 * <p>
 * NOTE: Connection filter's TraceID is not used for comparison, but if you uncomment this assert, you'll see
 * that under high load some requests have their connection filter executed on IO thread, and another - on request
 * thread. Those which run on IO thread will have their TraceIDs lost.
 * <p>
 * Intended for manual run only.
 */
public class BigTest {

    private static final int PORT = 33333;

    private static final ExecutorService serverExecutor = createExecutor("server-executor", 10);
    private static final ExecutorService serverResponseExecutor = createExecutor("server-response-executor", 10);
    private static final ExecutorService requestExecutor = createExecutor("request-executor", 100);
    private static final ExecutorService clientExecutor = createExecutor("client-executor", 100);

    private static HttpServerContext server;

    private static final AtomicReference<String> failReason = new AtomicReference<>();

    @BeforeAll
    public static void setup() throws Exception {
        server = getServiceTalkServer(PORT, serverExecutor, serverResponseExecutor);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        server.closeGracefully();
    }

    private static ExecutorService createExecutor(String name, int nThreads) {
        final AtomicInteger threadNumber = new AtomicInteger(1);
        return Executors.newFixedThreadPool(nThreads, r -> new Thread(r, name + "-" + threadNumber.getAndIncrement()));
    }

    private static HttpClient getServiceTalkClient() {
        return HttpClients.forSingleAddress("localhost", PORT)
                .executor(io.servicetalk.concurrent.api.Executors.from(clientExecutor))
                .executionStrategy(HttpExecutionStrategies.offloadAll())
                .appendConnectionFilter(new StreamingHttpConnectionFilterFactory() {
                    @Override
                    public HttpExecutionStrategy requiredOffloads() {
                        return HttpExecutionStrategies.offloadAll();
                    }

                    @Override
                    public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
                        return new StreamingHttpConnectionFilter(connection) {
                            @Override
                            public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
                                sleep(new Random().nextInt(10));
                                populateHeaders(request, "connection");
                                // TODO: we're not getting the context here.
                                print("Async context: " + AsyncContext.context()); // === Async context: CopyOnWriteContextMap@171faad1:{}
                                return delegate().request(request);
                            }
                        };
                    }
                }).appendClientFilter(new StreamingHttpClientFilterFactory() {
                    @Override
                    public HttpExecutionStrategy requiredOffloads() {
                        return HttpExecutionStrategies.offloadAll();
                    }

                    @Override
                    public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
                        return new StreamingHttpClientFilter(client) {
                            @Nonnull
                            @Override
                            protected Single<StreamingHttpResponse> request(
                                    @Nonnull StreamingHttpRequester delegate, @Nonnull StreamingHttpRequest request) {
                                sleep(new Random().nextInt(10));
                                populateHeaders(request, "1");
                                return delegate().request(request);
                            }
                        };
                    }
                }).appendClientFilter(new StreamingHttpClientFilterFactory() {
                    @Override
                    public HttpExecutionStrategy requiredOffloads() {
                        return HttpExecutionStrategies.offloadAll();
                    }

                    @Override
                    public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
                        return new StreamingHttpClientFilter(client) {
                            @Nonnull
                            @Override
                            protected Single<StreamingHttpResponse> request(
                                    @Nonnull StreamingHttpRequester delegate, @Nonnull StreamingHttpRequest request) {
                                sleep(new Random().nextInt(10));
                                populateHeaders(request, "2");
                                return delegate().request(request);
                            }
                        };
                    }
                })
                .appendClientFilter(new OpenTelemetryHttpRequestFilter("client")).build();
    }

    private static void populateHeaders(StreamingHttpRequest request, String name) {
        String debug = Span.current().getSpanContext().getTraceId() + ", thread: " + Thread.currentThread();
        String trace = Span.current().getSpanContext().getTraceId();
        request.addHeader("DD_debug_" + name, debug);
        request.addHeader("DD_trace_" + name, trace);
        print("inside client filter " + name + ": " + debug);
    }

    private static HttpServerContext getServiceTalkServer(
            int port, ExecutorService serverExecutor, Executor serverResponseExecutor) throws Exception {
        return HttpServers.forPort(port).executor(io.servicetalk.concurrent.api.Executors.from(serverExecutor))
                .executionStrategy(HttpExecutionStrategies.offloadAll())
                .appendServiceFilter(new OpenTelemetryHttpServerFilter())
                .appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(
                            HttpServiceContext ctx, StreamingHttpRequest request,
                            StreamingHttpResponseFactory responseFactory) {
                        printDebug("service filter");
                        return delegate().handle(ctx, request, responseFactory);
                    }
                }).listenAndAwait((ctx, request, responseFactory) ->
                                ctx.executionContext().executor()
                                        .timer(ThreadLocalRandom.current().nextInt(10), TimeUnit.MILLISECONDS).concat(
                                                Single.defer(() -> {
//                                sleep(new Random().nextInt(10));
                                                    printDebug("server");
                                                    return Single.fromStage(CompletableFuture.supplyAsync(() -> {
                                                        printDebug("server response");
                                                        return responseFactory.ok();
                                                    }, serverResponseExecutor));
                                                }))
                );
    }

    private static CompletableFuture<Void> arrangeExecution(Function<Integer, Runnable> taskProvider, int loops) {
        return CompletableFuture.allOf(IntStream.range(0, loops).mapToObj(
                        i -> CompletableFuture.runAsync(taskProvider.apply(i), requestExecutor))
                .toArray(CompletableFuture[]::new));
    }

    private static void printDebug(String prefix, int loop) {
        printDebug(prefix + ": " + loop);
    }

    private static void printDebug(String prefix) {
        print(String.format("%s: %s, thread: %s", prefix, Span.current().getSpanContext().getTraceId(),
                Thread.currentThread()));
    }

    private static void print(String string) {
        System.out.println("=== " + string);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDataDogTraceId() throws Exception {
        final int LOOPS = 10_000;

        try (HttpClient httpClient = getServiceTalkClient()) {
            arrangeExecution(loop -> () -> {
                try {
                    performRequest(httpClient.get("/"), httpClient, loop);
                } catch (Throwable e) {
                    failReason.compareAndSet(null, e.getMessage());
                }
            }, LOOPS).join();
        }

        if (failReason.get() != null) {
            fail(failReason.get());
        }
    }

    @WithSpan
    @Timeout(300)
    private void performRequest(HttpRequest httpRequest, HttpClient httpClient, int loop) throws InterruptedException {
        CountDownLatch localLatch = new CountDownLatch(2);

        String rootTraceId = Span.current().getSpanContext().getTraceId();
        AtomicReference<String> requestTraceId = new AtomicReference<>();
        AtomicReference<String> requestConnectionTraceId = new AtomicReference<>();
        AtomicReference<String> requestFilterTraceId1 = new AtomicReference<>();
        AtomicReference<String> requestFilterTraceId2 = new AtomicReference<>();
        AtomicReference<String> responseTraceId = new AtomicReference<>();
        AtomicReference<String> afterResponseTraceId = new AtomicReference<>();
        ContextMap.Key<String> key = ContextMap.Key.newKey("name", String.class);

        AsyncContext.context().put(key, "foo"); // this ends up correctly propagated.

        printDebug("client call thread - AsyncContext: " + AsyncContext.context(), loop);
        printDebug("client call thread - " + Context.current().getClass());
        httpClient.request(httpRequest).beforeOnSubscribe(t -> {
            requestTraceId.set(Span.current().getSpanContext().getTraceId());
            printDebug("client request", loop);
        })
                .beforeFinally(() -> {
            afterResponseTraceId.set(Span.current().getSpanContext().getTraceId());
            printDebug("client after response", loop); // TODO: always empty

            requestConnectionTraceId.set(String.valueOf(httpRequest.headers().get("DD_trace_connection")));
            requestFilterTraceId1.set(String.valueOf(httpRequest.headers().get("DD_trace_1")));
            requestFilterTraceId2.set(String.valueOf(httpRequest.headers().get("DD_trace_2")));

            print("client connection stored: " + loop + ": " + httpRequest.headers().get("DD_debug_connection"));
            print("client filter 1 stored: " + loop + ": " + httpRequest.headers().get("DD_debug_1"));
            print("client filter 2 stored: " + loop + ": " + httpRequest.headers().get("DD_debug_2"));

            localLatch.countDown();
        })
        .subscribe(r -> { // TODO: should capture the current trace, start the computation, and then finally emit it again.
            responseTraceId.set(Span.current().getSpanContext().getTraceId());
            printDebug("client response", loop); // TODO: always empty
            localLatch.countDown();
        });

        //=== client call thread: 0: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[request-executor-1,5,main]
        //=== inside client filter 1: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[request-executor-1,5,main]
        //=== inside client filter 2: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[request-executor-1,5,main]
        //=== client request: 0: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[request-executor-1,5,main]
        //=== inside client filter connection: 00000000000000000000000000000000, thread: Thread[servicetalk-global-io-executor-1-3,5,main]
        //=== service filter: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[server-executor-1,5,main]
        //=== server: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[server-executor-2,5,main]
        //=== server response: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[server-response-executor-1,5,main]
        //=== client after response: 0: 00000000000000000000000000000000, thread: Thread[client-executor-7,5,main]
        //=== client connection stored: 0: 00000000000000000000000000000000, thread: Thread[servicetalk-global-io-executor-1-3,5,main]
        //=== client filter 1 stored: 0: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[request-executor-1,5,main]
        //=== client filter 2 stored: 0: 1a983039985929ebbab157f3c7ec32c9, thread: Thread[request-executor-1,5,main]
        //=== client response: 0: 00000000000000000000000000000000, thread: Thread[client-executor-7,5,main]

        localLatch.await(300, TimeUnit.SECONDS);

        String debug = String.format(
                "loop: %d, thread: %s, rootTraceId: %s, requestTraceId: %s, requestFilterTraceId1: %s, "
                        + "requestFilterTraceId2: %s, requestConnectionTraceId: %s, responseTraceId: %s, "
                        + "afterResponseTraceId: %s", loop, Thread.currentThread(), rootTraceId, requestTraceId,
                requestFilterTraceId1, requestFilterTraceId2, requestConnectionTraceId, responseTraceId,
                afterResponseTraceId);

        // These all work as expected.
        assertEquals(rootTraceId, requestTraceId.get(), debug);
        assertEquals(rootTraceId, requestFilterTraceId1.get(), debug);
        assertEquals(rootTraceId, requestFilterTraceId2.get(), debug);

        // java.lang.AssertionError: loop: 168, thread: Thread[#44,request-executor-6,5,main], rootTraceId: b9f5a5c1c5fbdbbfef9440ff9ebbb0d0, requestTraceId: b9f5a5c1c5fbdbbfef9440ff9ebbb0d0, requestFilterTraceId1: b9f5a5c1c5fbdbbfef9440ff9ebbb0d0, requestFilterTraceId2: b9f5a5c1c5fbdbbfef9440ff9ebbb0d0, requestConnectionTraceId: b9f5a5c1c5fbdbbfef9440ff9ebbb0d0, responseTraceId: 00000000000000000000000000000000, afterResponseTraceId: 00000000000000000000000000000000
        //Expected :[b9f5a5c1c5fbdbbfef9440ff9ebbb0d]0
        //Actual   :[0000000000000000000000000000000]0
        assertEquals(rootTraceId, responseTraceId.get(), debug);
        assertEquals(rootTraceId, afterResponseTraceId.get(), debug);

        // java.lang.AssertionError: loop: 96, thread: Thread[#135,request-executor-97,5,main], rootTraceId: f15c27a0574dda869f85d472f4642790, requestTraceId: f15c27a0574dda869f85d472f4642790, requestFilterTraceId1: f15c27a0574dda869f85d472f4642790, requestFilterTraceId2: f15c27a0574dda869f85d472f4642790, requestConnectionTraceId: 00000000000000000000000000000000, responseTraceId: f15c27a0574dda869f85d472f4642790, afterResponseTraceId: f15c27a0574dda869f85d472f4642790
        //Expected :[f15c27a0574dda869f85d472f464279]0
        //Actual   :[0000000000000000000000000000000]0
//        Assert.assertEquals(debug, rootTraceId, requestConnectionTraceId.get());
    }
}
