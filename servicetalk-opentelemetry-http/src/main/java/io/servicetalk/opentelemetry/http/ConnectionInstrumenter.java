package io.servicetalk.opentelemetry.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

import static io.servicetalk.opentelemetry.http.Singletons.INSTRUMENTATION_SCOPE_NAME;

final class ConnectionInstrumenter {

    private static final ContextMap.Key<SpanContext> CONNECTION_SPAN_CONTEXT_KEY =
            ContextMap.Key.newKey("connection-span-context-key", SpanContext.class);
    private static final String CONNECTION_SPAN_NAME = "connection_setup";

    // The order of this filter doesn't really matter
    static final class SpanLinkingHttpHttpConnectionFilterFactory implements StreamingHttpConnectionFilterFactory {

        @Override
        public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
            throw new UnsupportedOperationException("not implemented yet");
        }

        @Override
        public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection, @Nullable ContextMap contextMap) {
            Context context = Context.current();
            SpanContext spanContext = contextMap == null ? null : contextMap.get(CONNECTION_SPAN_CONTEXT_KEY);
            Span currentSpan = Span.fromContext(context);
            System.err.println(Thread.currentThread().getName() + ": Expected : " + spanContext + ", current : " + currentSpan);
            return new LinkingStreamingHttpConnectionFilter(connection, spanContext);
        }

        private static final class LinkingStreamingHttpConnectionFilter extends StreamingHttpConnectionFilter {
            @Nullable
            private final SpanContext spanContext;

            public LinkingStreamingHttpConnectionFilter(FilterableStreamingHttpConnection delegate,
                                                        @Nullable SpanContext spanContext) {
                super(delegate);
                this.spanContext = spanContext;
            }

            @Override
            public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
                if (spanContext != null) {
                    System.err.println("Setting span context");
                    Span.current().addLink(spanContext);
                } else {
                    System.err.println("No span context present");
                }

                return delegate().request(request);
            }
        }
    }

    // This needs to be the 'last' filter so it captures the entire connection establishment chain.
    static final class ConnectionFactoryFilterImpl<R, C extends ListenableAsyncCloseable>
            implements ConnectionFactoryFilter<R, C> {

        private final Tracer tracer;

        ConnectionFactoryFilterImpl(OpenTelemetry openTelemetry) {
            this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME);
        }

        @Override
        public ConnectionFactory<R, C> create(ConnectionFactory<R, C> original) {

            return new DelegatingConnectionFactory<R, C>(original) {
                @Override
                public Single<C> newConnection(R r, @Nullable ContextMap context, @Nullable TransportObserver observer) {
                    Span span = createSpan();
                    if (context != null) {
                        context.put(CONNECTION_SPAN_CONTEXT_KEY, span.getSpanContext());
                    }
                    System.err.println(Thread.currentThread().getName() + ": Creating new connection with span " + span + "\nCurrent span: " + Span.current());
                    Context nextContext = span.storeInContext(Context.current());
                    try (Scope ignored = nextContext.makeCurrent()) {
                        Single<C> delegateResult = delegate().newConnection(r, context, observer);
                        return Singletons.withContext(
                                delegateResult.beforeFinally(span::end), nextContext)
                                .beforeFinally(() -> System.err.println("ConnectionCreationScopeEnded"));
                    }
                }
            };
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            return ConnectExecutionStrategy.offloadNone();
        }

        private Span createSpan() {
            return tracer
                    .spanBuilder(CONNECTION_SPAN_NAME)
                    .setSpanKind(SpanKind.PRODUCER)
                    .setNoParent()
                    .startSpan();
        }
    }

    private ConnectionInstrumenter() {
        // noop: no instances.
    }
}
