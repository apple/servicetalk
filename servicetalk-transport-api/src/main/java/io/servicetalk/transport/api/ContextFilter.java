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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.api.Completable.completed;

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
    Single<Boolean> apply(ConnectionContext context);

    @Override
    default Completable closeAsync() {
        return completed();
    }
}
