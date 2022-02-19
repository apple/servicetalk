/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.Completable;

/**
 * A {@link RequestConcurrencyController} that also allows to {@link #tryReserve()} a connection for exclusive use.
 */
public interface ReservableRequestConcurrencyController extends RequestConcurrencyController {

    /**
     * Attempts to reserve a connection for exclusive use until {@link #releaseAsync()} is called.
     * @return {@code true} if this connection is available and reserved for performing a single request.
     */
    boolean tryReserve();

    /**
     * Must be called (and subscribed to) to signify the reservation has completed after {@link #tryReserve()}.
     * @return a {@link Completable} for the release.
     */
    Completable releaseAsync();
}
