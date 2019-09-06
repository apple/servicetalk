/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;

import java.util.function.Function;

/**
 * Provides a default HTTP protocol binding based on a static scoring function to an otherwise non-scored {@link
 * FilterableStreamingHttpConnection}.
 */
final class StaticScoreHttpProtocolBinder extends StreamingHttpConnectionFilter
        implements FilterableStreamingHttpLoadBalancedConnection {

    private final float score;

    private StaticScoreHttpProtocolBinder(final FilterableStreamingHttpConnection delegate, float score) {
        super(delegate);
        this.score = score;
    }

    @Override
    public float score() {
        return score;
    }

    /**
     * Optionally adapts a {@link FilterableStreamingHttpConnection} into a {@link LoadBalancedConnection} and provides
     * a static value for {@link LoadBalancedConnection#score()}.
     *
     * @param score the static score to return for this {@link LoadBalancedConnection} unless the provided {@link
     * FilterableStreamingHttpConnection} is an instance of {@link LoadBalancedConnection} and scoring is delegated.
     * @return the wrapped connection
     */
    static Function<FilterableStreamingHttpConnection, FilterableStreamingHttpLoadBalancedConnection>
    provideStaticScoreIfNeeded(float score) {
        return conn -> conn instanceof FilterableStreamingHttpLoadBalancedConnection ?
                (FilterableStreamingHttpLoadBalancedConnection) conn : new StaticScoreHttpProtocolBinder(conn, score);
    }
}
