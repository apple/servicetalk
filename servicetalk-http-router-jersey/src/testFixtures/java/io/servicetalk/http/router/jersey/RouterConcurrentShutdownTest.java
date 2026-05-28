/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
import org.glassfish.jersey.server.ApplicationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
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
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproducer for the HK2 shutdown race that causes {@link EndpointEnhancingRequestFilter} to throw an
 * internal exception when an in-flight request lands during {@code ServiceLocatorImpl.shutdown()}.
 * Two distinct windows are exercised:
 * <ol>
 *   <li>{@link ScopeDeactivatingFilter} — request scope deactivated, locator state still {@code RUNNING}.
 *       {@code ctxRefProvider.get()} resolves into {@code Utilities.createService}, which throws
 *       {@code MultiException} wrapping {@code IllegalStateException("Could not find an active context for
 *       RequestScoped")}.</li>
 *   <li>{@link LocatorShutdownFilter} — entire HK2 locator shut down, state {@code SHUTDOWN}.
 *       {@code ctxRefProvider.get()} fails the {@code checkState()} guard and throws a raw
 *       {@code IllegalStateException("<locator> has been shut down")}, not wrapped in any
 *       {@code MultiException}.</li>
 * </ol>
 * Both must be coalesced into a graceful 503 by {@link EndpointEnhancingRequestFilter}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouterConcurrentShutdownTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    @Mock
    private HttpServiceContext ctx;

    /**
     * Runs before {@link EndpointEnhancingRequestFilter} ({@code @Priority(MAX_VALUE)}) and calls
     * {@code requestScope.shutdown()} to set {@code isActive = false} while the locator is still alive.
     * Reproduces the pre-state-flip window where HK2 wraps the failure in a {@code MultiException}.
     */
    public static final class ScopeDeactivatingFilter implements ContainerRequestFilter {
        @Context
        private RequestScope requestScope;

        @Override
        public void filter(ContainerRequestContext requestCtx) {
            requestScope.shutdown();
        }
    }

    /**
     * Runs before {@link EndpointEnhancingRequestFilter} and shuts down the entire HK2 service locator
     * via {@code InjectionManager.shutdown()}. After this returns, the locator's state is
     * {@code SHUTDOWN} so subsequent service lookups fail at {@code ServiceLocatorImpl.checkState()}
     * with a raw, unwrapped {@code IllegalStateException}.
     */
    public static final class LocatorShutdownFilter implements ContainerRequestFilter {
        @Context
        private ApplicationHandler applicationHandler;

        @Override
        public void filter(ContainerRequestContext requestCtx) {
            applicationHandler.getInjectionManager().shutdown();
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
    }

    @Test
    void requestScopedContextSurvivesConcurrentShutdown() throws Exception {
        assertGracefullyAborted(buildRouter(ScopeDeactivatingFilter.class));
    }

    @Test
    void requestSurvivesPostLocatorShutdown() throws Exception {
        assertGracefullyAborted(buildRouter(LocatorShutdownFilter.class));
    }

    private StreamingHttpService buildRouter(final Class<? extends ContainerRequestFilter> raceFilter) {
        return new HttpJerseyRouterBuilder().buildStreaming(new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return new HashSet<>(asList(raceFilter, PingResource.class));
            }
        });
    }

    private void assertGracefullyAborted(final StreamingHttpService router) throws Exception {
        final StreamingHttpRequest req = REQ_RES_FACTORY.get("/ping");
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];
        final StreamingHttpResponse[] response = new StreamingHttpResponse[1];

        toSource(router.handle(ctx, req, REQ_RES_FACTORY)).subscribe(
                new SingleSource.Subscriber<StreamingHttpResponse>() {
                    @Override
                    public void onSubscribe(Cancellable cancellable) {
                    }

                    @Override
                    public void onSuccess(@Nullable StreamingHttpResponse result) {
                        response[0] = result;
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable t) {
                        error[0] = t;
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, SECONDS), "Timed out waiting for a response");
        assertThat("Concurrent shutdown must not leak an internal exception to the caller",
                error[0], nullValue());
        assertThat("Request during concurrent shutdown must be rejected with 503 Service Unavailable",
                response[0].status(), is(SERVICE_UNAVAILABLE));
    }
}
