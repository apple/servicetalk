/**
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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import java.util.Objects;
import java.util.function.Function;

/**
 * Allow to filter connections a.k.a {@link ConnectionContext}s.
 */
@FunctionalInterface
public interface ContextFilter extends AsyncCloseable {

    /**
     * Just ACCEPT all connections.
     */
    ContextFilter ACCEPT_ALL = (context) -> Single.success(Boolean.TRUE);

    /**
     * Filter the {@link ConnectionContext} and notify the returned {@link Single} once done. If notified with
     * {@link Boolean#FALSE} it will be rejected, with {@link Boolean#TRUE} accepted.
     *
     * @param context the {@link ConnectionContext} for which the filter operation is done
     * @return the {@link Single} which is notified once complete
     */
    Single<Boolean> filter(ConnectionContext context);

    @Override
    default Completable closeAsync() {
        return Completable.completed();
    }

    /**
     * Allow to chain multiple {@link ContextFilter} and so compose them for more complex processing logic and small
     * reusable parts. As soon as the {@link ContextFilter} completes the {@link Single} with
     * {@link Boolean#FALSE} the processing will be stopped and the connection rejected.
     *
     * @param after the {@link ContextFilter} which should be executed after this instance
     * @return a new {@link ContextFilter} which will first execute this {@link ContextFilter} and then
     *          {@code after}
     */
    default ContextFilter andThen(ContextFilter after) {
        //TODO 3.x: Implement chaining
        return context -> Single.error(new UnsupportedOperationException("Context filter chaining not implemented"));
    }

    /**
     * Creates a new {@link ContextFilter} from a {@link Function}.
     *
     * @param func the {@link Function} from which a {@link ContextFilter} should be created
     * @return a new {@link ContextFilter} which contains the logic of the given {@link Function}
     */
    static ContextFilter create(Function<ConnectionContext, Single<Boolean>> func) {
        return safe(func::apply);
    }

    /**
     * Just create a {@link ContextFilter} from the given {@link Function}.
     *
     * @param fn the {@link Function} from which a {@link ContextFilter} should be created
     * @return a new {@link ContextFilter} which contains the logic of the given {@link Function}
     */
    static ContextFilter simpleConnectionFilter(Function<ConnectionContext, Boolean> fn) {
        Objects.requireNonNull(fn);
        return ctx -> {
            try {
                return Single.success(fn.apply(ctx));
            } catch (Throwable cause) {
                return Single.error(cause);
            }
        };
    }

    /**
     * Wraps another {@link ContextFilter}.
     * @param filter   the {@link ContextFilter} to wrap
     * @return a new {@link ContextFilter} which wraps the given one
     */
    static ContextFilter safe(ContextFilter filter) {
        Objects.requireNonNull(filter);
        return new ContextFilter() {
            @Override
            public Single<Boolean> filter(ConnectionContext context) {
                try {
                    return filter.filter(context);
                } catch (Throwable cause) {
                    return Single.error(cause);
                }
            }

            @Override
            public Completable closeAsync() {
                return filter.closeAsync();
            }
        };
    }
}
