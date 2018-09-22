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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.function.BiConsumer;

/**
 * Extensibility point provided by {@link Publisher} for visibility into asynchronous events.
 */
public interface PublisherPlugin {
    /**
     * Extension point for {@link Publisher#subscribe(Subscriber)}.
     * @param subscriber The {@link Subscriber}.
     * @param offloader The {@link SignalOffloader}.
     * @param handleSubscribe The resulting {@link Publisher#subscribe(Subscriber)} method.
     */
    void handleSubscribe(Subscriber<?> subscriber, SignalOffloader offloader,
                         BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe);
}
