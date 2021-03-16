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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

final class RangeIntPublisher extends AbstractSynchronousPublisher<Integer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RangeIntPublisher.class);
    private final int begin;
    private final int end;
    private final int stride;

    RangeIntPublisher(int begin, int end) {
        this(begin, end, 1);
    }

    RangeIntPublisher(int begin, int end, int stride) {
        if (begin > end) {
            throw new IllegalArgumentException("begin(" + begin + ") > end(" + end + ")");
        }
        if (stride <= 0) {
            throw new IllegalArgumentException("stride: " + stride + " (expected >0)");
        }
        this.begin = begin;
        this.end = end;
        this.stride = stride;
    }

    @Override
    void doSubscribe(final Subscriber<? super Integer> subscriber) {
        subscriber.onSubscribe(new RangeIntSubscription(subscriber));
    }

    private final class RangeIntSubscription implements PublisherSource.Subscription {
        private final PublisherSource.Subscriber<? super Integer> subscriber;
        private long pendingN;
        private int index = RangeIntPublisher.this.begin;

        private RangeIntSubscription(PublisherSource.Subscriber<? super Integer> subscriber) {
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void request(long n) {
            if (pendingN < 0) {
                return;
            }
            if (!isRequestNValid(n)) {
                sendOnError(newExceptionForInvalidRequestN(n));
                return;
            }
            if (pendingN != 0) {
                // this call is re-entrant. just add to pending and deliver onNext when the stack unwinds.
                pendingN = addWithOverflowProtection(pendingN, n);
                return;
            }
            pendingN = addWithOverflowProtection(pendingN, n);
            for (; pendingN > 0 && index < end; --pendingN, index += (int) min(stride, (long) end - index)) {
                try {
                    subscriber.onNext(index);
                } catch (Throwable cause) {
                    sendOnError(cause);
                    return;
                }
            }
            if (index == end) {
                sendComplete();
            }
        }

        @Override
        public void cancel() {
            pendingN = -1; // must be negative value to prevent signal after terminal event.
        }

        private void sendOnError(Throwable cause) {
            cancel();
            try {
                subscriber.onError(cause);
            } catch (Throwable t) {
                LOGGER.info("Ignoring exception from onError of Subscriber {}.", subscriber, t);
            }
        }

        private void sendComplete() {
            cancel();
            try {
                subscriber.onComplete();
            } catch (Throwable t) {
                LOGGER.info("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
            }
        }
    }
}
