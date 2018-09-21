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
package io.servicetalk.opentracing.core.internal;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class ProgressiveInMemorySpanEventListenerSet implements InMemorySpanEventListener {
    private static final AtomicReferenceFieldUpdater<ProgressiveInMemorySpanEventListenerSet, ProgressiveSet> setUpdater =
            newUpdater(ProgressiveInMemorySpanEventListenerSet.class, ProgressiveSet.class, "set");
    private volatile ProgressiveSet set = EmptyProgressiveSet.INSTANCE;

    boolean add(InMemorySpanEventListener listener) {
        for (;;) {
            ProgressiveSet set = this.set;
            ProgressiveSet newSet = set.add(listener);
            if (newSet == set) {
                return false;
            } else if (setUpdater.compareAndSet(this, set, newSet)) {
                return true;
            }
        }
    }

    boolean remove(InMemorySpanEventListener listener) {
        for (;;) {
            ProgressiveSet set = this.set;
            ProgressiveSet newSet = set.remove(listener);
            if (newSet == set) {
                return false;
            } else if (setUpdater.compareAndSet(this, set, newSet)) {
                return true;
            }
        }
    }

    @Override
    public void onSpanStarted(final InMemorySpan span) {
        set.onSpanStarted(span);
    }

    @Override
    public void onEventLogged(final InMemorySpan span, final long epochMicros, final String eventName) {
        set.onEventLogged(span, epochMicros, eventName);
    }

    @Override
    public void onEventLogged(final InMemorySpan span, final long epochMicros, final Map<String, ?> payload) {
        set.onEventLogged(span, epochMicros, payload);
    }

    @Override
    public void onSpanFinished(final InMemorySpan span, final long durationMicros) {
        set.onSpanFinished(span, durationMicros);
    }

    private interface ProgressiveSet extends InMemorySpanEventListener {
        ProgressiveSet add(InMemorySpanEventListener listener);

        ProgressiveSet remove(InMemorySpanEventListener listener);
    }

    private static final class EmptyProgressiveSet implements ProgressiveSet {
        static final ProgressiveSet INSTANCE = new EmptyProgressiveSet();

        private EmptyProgressiveSet() {
            // singleton
        }

        @Override
        public ProgressiveSet add(final InMemorySpanEventListener listener) {
            return new OneProgressiveSet(listener);
        }

        @Override
        public ProgressiveSet remove(final InMemorySpanEventListener listener) {
            return this;
        }

        @Override
        public void onSpanStarted(final InMemorySpan span) {
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final String eventName) {
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final Map<String, ?> fields) {
        }

        @Override
        public void onSpanFinished(final InMemorySpan span, final long durationMicros) {
        }
    }

    private static final class OneProgressiveSet implements ProgressiveSet {
        private final InMemorySpanEventListener listener;
        OneProgressiveSet(InMemorySpanEventListener listener) {
            this.listener = requireNonNull(listener);
        }

        @Override
        public ProgressiveSet add(final InMemorySpanEventListener listener) {
            return this.listener.equals(listener) ? this : new TwoProgressiveSet(this.listener, listener);
        }

        @Override
        public ProgressiveSet remove(final InMemorySpanEventListener listener) {
            return this.listener.equals(listener) ? EmptyProgressiveSet.INSTANCE : this;
        }

        @Override
        public void onSpanStarted(final InMemorySpan span) {
            listener.onSpanStarted(span);
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final String eventName) {
            listener.onEventLogged(span, epochMicros, eventName);
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final Map<String, ?> fields) {
            listener.onEventLogged(span, epochMicros, fields);
        }

        @Override
        public void onSpanFinished(final InMemorySpan span, final long durationMicros) {
            listener.onSpanFinished(span, durationMicros);
        }
    }

    private static final class TwoProgressiveSet implements ProgressiveSet {
        private final InMemorySpanEventListener first;
        private final InMemorySpanEventListener second;

        TwoProgressiveSet(InMemorySpanEventListener first, InMemorySpanEventListener second) {
            this.first = first;
            this.second = requireNonNull(second);
        }

        @Override
        public ProgressiveSet add(final InMemorySpanEventListener listener) {
            return first.equals(listener) || second.equals(listener) ? this : new ThreeOrMoreProgressiveSet(
                    first, second, requireNonNull(listener));
        }

        @Override
        public ProgressiveSet remove(final InMemorySpanEventListener listener) {
            if (first.equals(listener)) {
                return new OneProgressiveSet(second);
            } else if (second.equals(listener)) {
                return new OneProgressiveSet(first);
            }
            return this;
        }

        @Override
        public void onSpanStarted(final InMemorySpan span) {
            first.onSpanStarted(span);
            second.onSpanStarted(span);
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final String eventName) {
            first.onEventLogged(span, epochMicros, eventName);
            second.onEventLogged(span, epochMicros, eventName);
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final Map<String, ?> fields) {
            first.onEventLogged(span, epochMicros, fields);
            second.onEventLogged(span, epochMicros, fields);
        }

        @Override
        public void onSpanFinished(final InMemorySpan span, final long durationMicros) {
            first.onSpanFinished(span, durationMicros);
            second.onSpanFinished(span, durationMicros);
        }
    }

    private static final class ThreeOrMoreProgressiveSet implements ProgressiveSet {
        private final InMemorySpanEventListener[] listeners;

        ThreeOrMoreProgressiveSet(InMemorySpanEventListener... listeners) {
            this.listeners = listeners;
        }

        @Override
        public ProgressiveSet add(final InMemorySpanEventListener listener) {
            requireNonNull(listener);
            for (final InMemorySpanEventListener listener1 : listeners) {
                if (listener1.equals(listener)) {
                    return this;
                }
            }
            InMemorySpanEventListener[] newListeners = Arrays.copyOf(listeners, listeners.length + 1);
            newListeners[listeners.length] = listener;
            return new ThreeOrMoreProgressiveSet(newListeners);
        }

        @Override
        public ProgressiveSet remove(final InMemorySpanEventListener listener) {
            for (int i = 0; i < listeners.length; ++i) {
                if (listeners[i].equals(listener)) {
                    if (listeners.length == 3) {
                        switch (i) {
                            case 0: return new TwoProgressiveSet(listeners[1], listeners[2]);
                            case 1: return new TwoProgressiveSet(listeners[0], listeners[2]);
                            default: return new TwoProgressiveSet(listeners[0], listeners[1]);
                        }
                    } else {
                        InMemorySpanEventListener[] newListeners = new InMemorySpanEventListener[listeners.length - 1];
                        arraycopy(listeners, 0, newListeners, 0, i);
                        if (i < newListeners.length) {
                            arraycopy(listeners, i + 1, newListeners, i, newListeners.length - i);
                        }
                        return new ThreeOrMoreProgressiveSet(newListeners);
                    }
                }
            }
            return this;
        }

        @Override
        public void onSpanStarted(final InMemorySpan span) {
            for (final InMemorySpanEventListener listener : listeners) {
                listener.onSpanStarted(span);
            }
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final String eventName) {
            for (final InMemorySpanEventListener listener : listeners) {
                listener.onEventLogged(span, epochMicros, eventName);
            }
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final Map<String, ?> fields) {
            for (final InMemorySpanEventListener listener : listeners) {
                listener.onEventLogged(span, epochMicros, fields);
            }
        }

        @Override
        public void onSpanFinished(final InMemorySpan span, final long durationMicros) {
            for (final InMemorySpanEventListener listener : listeners) {
                listener.onSpanFinished(span, durationMicros);
            }
        }
    }
}
