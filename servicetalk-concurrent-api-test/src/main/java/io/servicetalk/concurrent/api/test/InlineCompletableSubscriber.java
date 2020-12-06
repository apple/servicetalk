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
package io.servicetalk.concurrent.api.test;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadEvent;
import io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent;

import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.CompletableSource.Subscriber;

final class InlineCompletableSubscriber implements Subscriber, InlineVerifiableSubscriber {
    private final InlinePublisherSubscriber<Void> publisherSubscriber;

    InlineCompletableSubscriber(NormalizedTimeSource timeSource, List<PublisherEvent> events,
                                String exceptionClassNamePrefix) {
        publisherSubscriber = new InlinePublisherSubscriber<>(0, timeSource, events, exceptionClassNamePrefix);
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        publisherSubscriber.onSubscribe(new PublisherSource.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancellable.cancel();
            }
        });
    }

    @Override
    public void onComplete() {
        publisherSubscriber.onComplete();
    }

    @Override
    public void onError(Throwable t) {
        publisherSubscriber.onError(t);
    }

    @Override
    public Publisher<VerifyThreadEvent> verifyThreadEvents() {
        return publisherSubscriber.verifyThreadEvents();
    }

    @Nullable
    @Override
    public PublisherEvent externalTimeout() {
        return publisherSubscriber.externalTimeout();
    }
}
