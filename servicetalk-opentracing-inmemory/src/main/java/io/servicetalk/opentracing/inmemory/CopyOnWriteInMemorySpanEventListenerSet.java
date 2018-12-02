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
package io.servicetalk.opentracing.inmemory;

import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanEventListener;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class CopyOnWriteInMemorySpanEventListenerSet implements InMemorySpanEventListener {
    private static final AtomicReferenceFieldUpdater<CopyOnWriteInMemorySpanEventListenerSet, CopyOnWriteSet>
            setUpdater = newUpdater(CopyOnWriteInMemorySpanEventListenerSet.class, CopyOnWriteSet.class, "set");
    private volatile CopyOnWriteSet set = EmptyCopyOnWriteSet.INSTANCE;

    boolean add(InMemorySpanEventListener listener) {
        for (;;) {
            CopyOnWriteSet set = this.set;
            CopyOnWriteSet newSet = set.add(listener);
            if (newSet == set) {
                return false;
            } else if (setUpdater.compareAndSet(this, set, newSet)) {
                return true;
            }
        }
    }

    boolean remove(InMemorySpanEventListener listener) {
        for (;;) {
            CopyOnWriteSet set = this.set;
            CopyOnWriteSet newSet = set.remove(listener);
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

    private interface CopyOnWriteSet extends InMemorySpanEventListener {
        CopyOnWriteSet add(InMemorySpanEventListener listener);

        CopyOnWriteSet remove(InMemorySpanEventListener listener);
    }

    private static final class EmptyCopyOnWriteSet implements CopyOnWriteSet {
        static final CopyOnWriteSet INSTANCE = new EmptyCopyOnWriteSet();

        private EmptyCopyOnWriteSet() {
            // singleton
        }

        @Override
        public CopyOnWriteSet add(final InMemorySpanEventListener listener) {
            return new OneCopyOnWriteSet(listener);
        }

        @Override
        public CopyOnWriteSet remove(final InMemorySpanEventListener listener) {
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

    private static final class OneCopyOnWriteSet implements CopyOnWriteSet {
        private final InMemorySpanEventListener listener;
        OneCopyOnWriteSet(InMemorySpanEventListener listener) {
            this.listener = requireNonNull(listener);
        }

        @Override
        public CopyOnWriteSet add(final InMemorySpanEventListener listener) {
            return this.listener.equals(listener) ? this : new TwoCopyOnWriteSet(this.listener, listener);
        }

        @Override
        public CopyOnWriteSet remove(final InMemorySpanEventListener listener) {
            return this.listener.equals(listener) ? EmptyCopyOnWriteSet.INSTANCE : this;
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

    private static final class TwoCopyOnWriteSet implements CopyOnWriteSet {
        private final InMemorySpanEventListener first;
        private final InMemorySpanEventListener second;

        TwoCopyOnWriteSet(InMemorySpanEventListener first, InMemorySpanEventListener second) {
            this.first = first;
            this.second = requireNonNull(second);
        }

        @Override
        public CopyOnWriteSet add(final InMemorySpanEventListener listener) {
            return first.equals(listener) || second.equals(listener) ? this : new ThreeOrMoreCopyOnWriteSet(
                    first, second, requireNonNull(listener));
        }

        @Override
        public CopyOnWriteSet remove(final InMemorySpanEventListener listener) {
            if (first.equals(listener)) {
                return new OneCopyOnWriteSet(second);
            } else if (second.equals(listener)) {
                return new OneCopyOnWriteSet(first);
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

    private static final class ThreeOrMoreCopyOnWriteSet implements CopyOnWriteSet {
        private final InMemorySpanEventListener[] listeners;

        ThreeOrMoreCopyOnWriteSet(InMemorySpanEventListener... listeners) {
            this.listeners = listeners;
        }

        @Override
        public CopyOnWriteSet add(final InMemorySpanEventListener listener) {
            requireNonNull(listener);
            for (final InMemorySpanEventListener listener1 : listeners) {
                if (listener1.equals(listener)) {
                    return this;
                }
            }
            InMemorySpanEventListener[] newListeners = Arrays.copyOf(listeners, listeners.length + 1);
            newListeners[listeners.length] = listener;
            return new ThreeOrMoreCopyOnWriteSet(newListeners);
        }

        @Override
        public CopyOnWriteSet remove(final InMemorySpanEventListener listener) {
            for (int i = 0; i < listeners.length; ++i) {
                if (listeners[i].equals(listener)) {
                    if (listeners.length == 3) {
                        switch (i) {
                            case 0: return new TwoCopyOnWriteSet(listeners[1], listeners[2]);
                            case 1: return new TwoCopyOnWriteSet(listeners[0], listeners[2]);
                            default: return new TwoCopyOnWriteSet(listeners[0], listeners[1]);
                        }
                    } else {
                        InMemorySpanEventListener[] newListeners = new InMemorySpanEventListener[listeners.length - 1];
                        arraycopy(listeners, 0, newListeners, 0, i);
                        if (i < newListeners.length) {
                            arraycopy(listeners, i + 1, newListeners, i, newListeners.length - i);
                        }
                        return new ThreeOrMoreCopyOnWriteSet(newListeners);
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
