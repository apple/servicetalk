/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.http.api.ClientFilterFunction;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * A builder of {@link StreamingHttpClient} instances which call a single server based on the provided unresolved address.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public interface SingleAddressHttpClientBuilder<U, R>
        extends BaseHttpClientBuilder<U, R, SingleAddressHttpClientBuilder<U, R>> {

    /**
     * Automatically set the provided {@link HttpHeaderNames#HOST} on {@link StreamingHttpRequest}s when it's missing.
     * <p>
     * For known address types such as {@link HostAndPort} the {@link HttpHeaderNames#HOST} is inferred and
     * automatically set by default, if you have a custom address type or want to override the inferred value use this
     * method. Use {@link #disableHostHeaderFallback()} if you don't want any {@link HttpHeaderNames#HOST} manipulation
     * at all.
     * @param hostHeader the value for the {@link HttpHeaderNames#HOST}
     * @return {@code this}
     */
    SingleAddressHttpClientBuilder<U, R> enableHostHeaderFallback(CharSequence hostHeader);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpClient} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #buildStreaming(ExecutionContext)} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param function {@link ClientFilterFunction} to decorate a {@link StreamingHttpClient} for the purpose of filtering.
     * The signature of the {@link BiFunction} is as follows:
     * <pre>
     *     PostFilteredHttpClient func(PreFilteredHttpClient, {@link LoadBalancer#getEventStream()})
     * </pre>
     * @return {@code this}
     */
    SingleAddressHttpClientBuilder<U, R> appendClientFilter(ClientFilterFunction function);

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param sslConfig the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()},
     * {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    SingleAddressHttpClientBuilder<U, R> setSslConfig(@Nullable SslConfig sslConfig);
}
