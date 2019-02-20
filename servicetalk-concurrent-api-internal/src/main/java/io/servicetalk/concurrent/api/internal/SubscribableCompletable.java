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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.SourceAdapters;

/**
 * A {@link Completable} that is also a {@link CompletableSource} and hence can be subscribed.
 * <p>
 * Typically, this will be used to implement a {@link Completable} that does not require an additional allocation when
 * converting to a {@link CompletableSource} via {@link SourceAdapters#toSource(Completable)}.
 */
public abstract class SubscribableCompletable extends Completable implements CompletableSource {

    @Override
    public final void subscribe(final Subscriber subscriber) {
        subscribeInternal(subscriber);
    }
}
