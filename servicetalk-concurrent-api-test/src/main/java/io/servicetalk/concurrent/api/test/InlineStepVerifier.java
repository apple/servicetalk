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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadAwaitEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadRunEvent;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Arrays.copyOfRange;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.function.Function.identity;

abstract class InlineStepVerifier<Source, Sub extends InlineVerifiableSubscriber> implements StepVerifier {
    private final Source source;
    private final List<PublisherEvent> events;
    private final NormalizedTimeSource timeSource;

    InlineStepVerifier(final Source source, final NormalizedTimeSource timeSource, final List<PublisherEvent> events) {
        this.source = source;
        this.events = events;
        this.timeSource = timeSource;
    }

    abstract Sub newSubscriber(NormalizedTimeSource timeSource, List<PublisherEvent> events);

    abstract void subscribe(Source source, Sub subscriber);

    abstract String exceptionPrefixFilter();

    @Override
    public final Duration verify() throws AssertionError {
        return verify(identity());
    }

    private Duration verify(Function<Source, Source> operators) throws AssertionError {
        Sub subscriber = newSubscriber(timeSource, events);
        final long startTime = timeSource.currentTime();
        subscribe(operators.apply(source), subscriber);
        try {
            CountDownLatch doneLatch = new CountDownLatch(1);
            BlockingIterable<VerifyThreadEvent> iterable = subscriber.verifyThreadEvents()
                    .beforeFinally(doneLatch::countDown)
                    .toIterable();
            for (VerifyThreadEvent event : iterable) {
                processVerifyEvent(event, doneLatch);
            }
        } catch (RuntimeException t) {
            processRuntimeException(t);
        }
        return timeSource.timeElapsed(startTime);
    }

    @Override
    public final Duration verify(Duration duration) throws AssertionError {
        Sub subscriber = newSubscriber(timeSource, events);
        final long startTime = timeSource.currentTime();
        subscribe(source, subscriber);
        try {
            CountDownLatch doneLatch = new CountDownLatch(1);
            BlockingIterable<VerifyThreadEvent> iterable = subscriber.verifyThreadEvents()
                    .beforeFinally(doneLatch::countDown)
                    // TODO(scott): use timeSource for test timeout
                    .toIterable();
            iterable.forEach(event -> processVerifyEvent(event, doneLatch), duration.toNanos(), NANOSECONDS);
        } catch (RuntimeException t) {
            processRuntimeException(t);
        } catch (TimeoutException e) {
            PublisherEvent event = subscriber.externalTimeout();
            if (event == null) {
                throw new AssertionError(e);
            }
            throw event.newException(e.getMessage(), e, exceptionPrefixFilter());
        }
        return timeSource.timeElapsed(startTime);
    }

    private void processRuntimeException(RuntimeException t) {
        Throwable cause = t.getCause();
        if (cause instanceof AssertionError) {
            throw (AssertionError) cause;
        } else if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("unexpected interrupt!", t);
        }
        throw t;
    }

    private void processVerifyEvent(VerifyThreadEvent event, CountDownLatch doneLatch) {
        if (event instanceof VerifyThreadRunEvent) {
            ((VerifyThreadRunEvent) event).run();
        } else if (event instanceof VerifyThreadAwaitEvent) {
            Duration duration = ((VerifyThreadAwaitEvent) event).duration();
            if (timeSource instanceof ModifiableTimeSource) {
                ((ModifiableTimeSource) timeSource).incrementCurrentTime(duration);
            } else {
                try {
                    doneLatch.await(duration.toNanos(), NANOSECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e); // will be unwrapped in the verify method above
                }
            }
        } else {
            throw new IllegalStateException("unsupported event type: " + event.getClass());
        }
    }

    abstract static class PublisherEvent {
        private final StackTraceElement[] originalStackTrace;

        PublisherEvent() {
            originalStackTrace = Thread.currentThread().getStackTrace();
        }

        final AssertionError newException(String message, Throwable cause, String stepClassName) {
            return StepAssertionError.newInstance(this, message, cause, stepClassName);
        }

        static <T> boolean notEqualsOnNext(@Nullable final T expected, @Nullable final T actual) {
            return (expected == null || !expected.equals(actual)) && (expected != null || actual != null);
        }

        abstract String description();

        static final class StepAssertionError extends AssertionError {
            private final PublisherEvent event;

            private StepAssertionError(PublisherEvent event, String message, Throwable cause) {
                super(message, cause);
                this.event = event;
            }

            private static StepAssertionError newInstance(PublisherEvent event, String message, Throwable cause,
                                                          String stepClassName) {
                StepAssertionError e = new StepAssertionError(event, message, cause);
                e.setStackTrace(filterStackTrace(stepClassName, event.originalStackTrace));
                return e;
            }

            StackTraceElement testMethodStackTrace() {
                StackTraceElement[] thisTrace = this.getStackTrace();
                int length = event.originalStackTrace.length - thisTrace.length;
                return event.originalStackTrace[length - 1];
            }

            @Override
            public Throwable fillInStackTrace() {
                return this;
            }

            private static StackTraceElement[] filterStackTrace(String stepClassName, StackTraceElement[] elements) {
                assert elements.length > 3 &&
                        elements[1].getClassName().startsWith(PublisherEvent.class.getName()) &&
                        elements[2].getClassName().startsWith(InlinePublisherSubscriber.class.getName()) &&
                        (elements[3].getClassName().startsWith(stepClassName) ||
                         elements[3].getClassName().startsWith(InlinePublisherSubscriber.class.getName()));
                for (int i = 4; i < elements.length; ++i) {
                    StackTraceElement element = elements[i];
                    if (!element.getClassName().startsWith(stepClassName)) {
                        return copyOfRange(elements, i, elements.length);
                    }
                }

                return elements;
            }

            @Override
            public String toString() {
                String s = StepAssertionError.class.getSimpleName();
                String message = getLocalizedMessage();
                return (message != null) ? (s + ": " + message) : s;
            }
        }
    }
}
