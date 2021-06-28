/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;

final class ReentryPublisher implements PublisherSource<Integer> {
    private final int begin;
    private final int end;

    ReentryPublisher(int begin, int end) {
        if (begin > end) {
            throw new IllegalArgumentException("begin: " + begin + " end: " + end + " (expected begin <= end)");
        }
        this.begin = begin;
        this.end = end;
    }

    @Override
    public void subscribe(final Subscriber<? super Integer> subscriber) {
        subscriber.onSubscribe(new Subscription() {
            private int index = begin;
            private boolean terminated;

            @Override
            public void request(long n) {
                if (!isRequestNValid(n) && !terminated) {
                    cancel();
                    subscriber.onError(newExceptionForInvalidRequestN(n));
                    return;
                }
                for (; index < end && n > 0; --n) {
                    subscriber.onNext(index++);
                }
                if (index == end && !terminated) {
                    terminated = true;
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                terminated = true;
                index = end;
            }
        });
    }
}
