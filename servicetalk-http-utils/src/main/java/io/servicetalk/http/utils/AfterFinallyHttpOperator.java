/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

/**
 * Helper operator for signaling the end of an HTTP Request/Response cycle.
 *
 * <p>{@link StreamingHttpRequest} and {@link StreamingHttpResponse} are nested sources ({@link Single} of meta-data
 * containing a payload {@link Publisher}), which makes it non-trivial to get a single signal at the end of this
 * Request/Response cycle. One needs to consider and coordinate between the multitude of outcomes: cancel/success/error
 * across both sources.</p>
 * <p>This operator ensures that the provided callback is triggered just once whenever the sources reach a terminal
 * state across both sources. An important question is when the ownership of the callback is transferred from the
 * {@link Single} source and the payload {@link Publisher}. In this case ownership is transferred the first time the
 * payload is subscribed to. This means that if a cancellation of the response {@link Single} occurs after the response
 * has been emitted but before the message body has been subscribed to, the callback will observe a cancel. However, if
 * the message payload has been subscribed to, the cancellation of the {@link Single} will have no effect and the result
 * is dictated by the terminal event of the payload body. If the body is subscribed to multiple times, only the first
 * subscribe will receive ownership of the terminal events.</p>
 *
 * Example usage tracking the beginning and ending of a request:
 *
 * <pre>{@code
 *     // coarse grained, any terminal signal calls the provided `Runnable`
 *     return requester.request(strategy, request)
 *                     .beforeOnSubscribe(__ -> tracker.requestStarted())
 *                     .liftSync(new AfterFinallyHttpOperator(tracker::requestFinished));
 *
 *     // fine grained, `tracker` implements `TerminalSignalConsumer`, terminal signal indicated by the callback method
 *     return requester.request(strategy, request)
 *                     .beforeOnSubscribe(__ -> tracker.requestStarted())
 *                     .liftSync(new AfterFinallyHttpOperator(tracker));
 * }</pre>
 *
 * @see BeforeFinallyHttpOperator
 */
public final class AfterFinallyHttpOperator extends AbstractWhenFinallyHttpOperator {

    /**
     * Create a new instance.
     *
     * @param afterFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     */
    public AfterFinallyHttpOperator(final Runnable afterFinally) {
        this(TerminalSignalConsumer.from(afterFinally));
    }

    /**
     * Create a new instance.
     *
     * @param afterFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     */
    public AfterFinallyHttpOperator(final TerminalSignalConsumer afterFinally) {
        this(afterFinally, false);
    }

    /**
     * Create a new instance.
     *
     * @param afterFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     * @param discardEventsAfterCancel if {@code true} further events will be discarded if those arrive after
     * {@link TerminalSignalConsumer#cancel()} is invoked. Otherwise, events may still be delivered if they race with
     * cancellation.
     */
    public AfterFinallyHttpOperator(final TerminalSignalConsumer afterFinally, boolean discardEventsAfterCancel) {
        super(afterFinally, discardEventsAfterCancel, true);
    }
}
