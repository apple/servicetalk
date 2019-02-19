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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.PublisherSource.Subscription;

/**
 * A {@link Subscription} implementation, which does not do anything.
 */
public final class EmptySubscription implements Subscription {
    public static final EmptySubscription EMPTY_SUBSCRIPTION = new EmptySubscription();

    @Override
    public void request(long n) {
        // No op
    }

    @Override
    public void cancel() {
        // No op
    }
}
