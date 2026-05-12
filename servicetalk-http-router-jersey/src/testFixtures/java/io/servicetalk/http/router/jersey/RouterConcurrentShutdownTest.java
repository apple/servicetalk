/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.IoExecutor;

import org.glassfish.jersey.process.internal.RequestScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproducer for the production exception:
 * <pre>
 *   java.lang.IllegalStateException: Could not find an active context for
 *       org.glassfish.jersey.process.internal.RequestScoped
 *     at org.jvnet.hk2.internal.ServiceLocatorImpl._resolveContext(...)
 *     at org.jvnet.hk2.internal.Utilities.createService(...)
 *     at org.jvnet.hk2.internal.IterableProviderImpl.get(...)
 *     at EndpointEnhancingRequestFilter.filter(EndpointEnhancingRequestFilter.java:100)
 * </pre>
 *
 * <h3>Root cause — the HK2 2.6.1 shutdown race</h3>
 * <p>In {@code ServiceLocatorImpl.shutdown()} the context-shutdown loop runs <em>outside</em> the
 * write lock. It calls {@code context.shutdown()} on every registered {@code Context<?>}. For Jersey's
 * {@code RequestContext} this delegates to {@code RequestScope.shutdown()}, which sets the
 * <em>volatile</em> field {@code RequestScope.isActive = false}. After this write, but before the
 * write lock is acquired and {@code state = ServiceLocatorState.SHUTDOWN} is set, there is a window
 * where:
 * <ol>
 *   <li>{@code checkState()} inside {@code internalGetService()} still passes (state = RUNNING).</li>
 *   <li>{@code ServiceLocatorImpl.resolveContext(RequestScoped)} checks
 *       {@code cachedContext.isActive()} → false, clears the cache, and calls
 *       {@code _resolveContext()} again.</li>
 *   <li>{@code _resolveContext()} finds <em>no</em> active {@code Context<RequestScoped>}
 *       (all implementations return {@code isActive() == false}) and throws the production
 *       exception.</li>
 * </ol>
 *
 * <h3>How the test replicates the exact condition</h3>
 * <p>{@link ScopeDeactivatingFilter} runs at default JAX-RS priority — before
 * {@link EndpointEnhancingRequestFilter} ({@code @Priority(MAX_VALUE)}) — and directly calls
 * {@code requestScope.shutdown()}, setting {@code isActive = false} while the HK2
 * {@code ServiceLocator} is still fully alive ({@code state = RUNNING}). This is the exact state
 * that the production race creates between phases 1 and 3 of the shutdown sequence.
 *
 * <p>When {@link EndpointEnhancingRequestFilter#filter} then calls {@code ctxRefProvider.get()},
 * HK2's {@code resolveContext()} sees {@code isActive == false}, retries, finds nothing, and
 * throws the exact production exception.
 *
 * <p>Note: {@code requestScope.shutdown()} is public and safe to call from the request thread.
 * Jersey's {@code runInScope()} finally-block only calls {@code resume()} (sets a ThreadLocal)
 * and {@code context.release()} (decrements a ref count) — neither checks {@code isActive}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RouterConcurrentShutdownTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    @Mock
    private HttpServiceContext ctx;

    private StreamingHttpService jerseyRouter;

    /**
     * Class-registered filter (Jersey injects {@code @Context RequestScope}).
     * Runs at default JAX-RS priority, before {@link EndpointEnhancingRequestFilter}
     * ({@code @Priority(MAX_VALUE)}).
     *
     * <p>Calls {@code requestScope.shutdown()} to set {@code isActive = false} while the HK2
     * locator is still live — replicating the exact state that the production shutdown race creates
     * between {@code context.shutdown()} (phase 1) and {@code state = SHUTDOWN} (phase 3).
     */
    public static final class ScopeDeactivatingFilter implements ContainerRequestFilter {
        @Context
        private RequestScope requestScope;

        @Override
        public void filter(ContainerRequestContext requestCtx) {
            // Replicate the production race condition:
            // context.shutdown() was called (setting isActive=false) but state != SHUTDOWN yet.
            requestScope.shutdown();
        }
    }

    @Path("/ping")
    @Produces(MediaType.TEXT_PLAIN)
    public static final class PingResource {
        @GET
        public String ping() {
            return "pong";
        }
    }

    @BeforeEach
    void setUp() {
        final HttpExecutionContext execCtx = mock(HttpExecutionContext.class);
        when(ctx.executionContext()).thenReturn(execCtx);
        when(ctx.localAddress()).thenReturn(createUnresolved("localhost", 8080));
        when(execCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        when(execCtx.ioExecutor()).thenReturn(mock(IoExecutor.class));
        when(execCtx.executionStrategy()).thenReturn(defaultStrategy());

        jerseyRouter = new HttpJerseyRouterBuilder().buildStreaming(new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return new HashSet<>(asList(ScopeDeactivatingFilter.class, PingResource.class));
            }
        });
    }

    /**
     * Verifies that when {@code RequestScope.isActive()} becomes {@code false} while
     * {@link EndpointEnhancingRequestFilter#filter} is about to call {@code ctxRefProvider.get()},
     * no internal exception leaks to the caller.
     *
     * <p><b>Current (unfixed) behaviour:</b> the exact production exception propagates as
     * {@code onError} on the response {@link io.servicetalk.concurrent.api.Single}:
     * <pre>IllegalStateException: Could not find an active context for
     *   org.glassfish.jersey.process.internal.RequestScoped</pre>
     *
     * <p><b>Expected behaviour after fix:</b> the request either completes normally or is rejected
     * with a graceful status (e.g. 503), but no internal exception leaks to the caller.
     */
    @Test
    public void requestScopedContextSurvivesConcurrentShutdown() throws Exception {
        final StreamingHttpRequest req = REQ_RES_FACTORY.get("/ping");
        final Throwable[] error = new Throwable[1];
        final Object lock = new Object();
        final boolean[] done = {false};

        toSource(jerseyRouter.handle(ctx, req, REQ_RES_FACTORY)).subscribe(
                new SingleSource.Subscriber<StreamingHttpResponse>() {
                    @Override
                    public void onSubscribe(Cancellable cancellable) {
                    }

                    @Override
                    public void onSuccess(@Nullable StreamingHttpResponse result) {
                        synchronized (lock) {
                            done[0] = true;
                            lock.notifyAll();
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        synchronized (lock) {
                            error[0] = t;
                            done[0] = true;
                            lock.notifyAll();
                        }
                    }
                });

        synchronized (lock) {
            long deadline = System.currentTimeMillis() + SECONDS.toMillis(5);
            while (!done[0] && System.currentTimeMillis() < deadline) {
                lock.wait(100);
            }
        }

        // Failure here means the exact production exception was propagated to the caller:
        //   "Could not find an active context for org.glassfish.jersey.process.internal.RequestScoped"
        assertThat("Concurrent shutdown must not leak an internal exception to the caller",
                error[0], nullValue());
    }
}
