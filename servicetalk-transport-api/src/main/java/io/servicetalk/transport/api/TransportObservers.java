/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

/**
 * A factory to create different {@link TransportObserver}s.
 */
public final class TransportObservers {

    private TransportObservers() {
        // No instances.
    }

    /**
     * Coverts passed {@link TransportObserver} to a safe version that catches and logs all exceptions, but does not
     * rethrow them.
     *
     * @param observer {@link TransportObserver} to convert
     * @return a safe version of the passed {@link TransportObserver} that catches and logs all exceptions, but does not
     * rethrow them.
     */
    public static TransportObserver asSafeObserver(final TransportObserver observer) {
        if (observer instanceof CatchAllTransportObserver) {
            return observer;
        }
        return new CatchAllTransportObserver(observer);
    }
}
