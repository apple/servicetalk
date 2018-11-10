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
import io.servicetalk.opentracing.inmemory.api.ListenableInMemoryScopeManager.InMemorySpanChangeListener;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class CopyOnWriteInMemorySpanChangeListenerSet implements InMemorySpanChangeListener {
    private static final AtomicReferenceFieldUpdater<CopyOnWriteInMemorySpanChangeListenerSet, CopyOnWriteSet>
            setUpdater = newUpdater(CopyOnWriteInMemorySpanChangeListenerSet.class, CopyOnWriteSet.class, "set");
    private volatile CopyOnWriteSet set = EmptyCopyOnWriteSet.INSTANCE;

    boolean add(InMemorySpanChangeListener listener) {
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

    boolean remove(InMemorySpanChangeListener listener) {
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
    public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
        set.spanChanged(oldSpan, newSpan);
    }

    private interface CopyOnWriteSet extends InMemorySpanChangeListener {
        CopyOnWriteSet add(InMemorySpanChangeListener listener);

        CopyOnWriteSet remove(InMemorySpanChangeListener listener);
    }

    private static final class EmptyCopyOnWriteSet implements CopyOnWriteSet {
        static final CopyOnWriteSet INSTANCE = new EmptyCopyOnWriteSet();

        private EmptyCopyOnWriteSet() {
            // singleton
        }

        @Override
        public CopyOnWriteSet add(final InMemorySpanChangeListener listener) {
            return new OneCopyOnWriteSet(listener);
        }

        @Override
        public CopyOnWriteSet remove(final InMemorySpanChangeListener listener) {
            return this;
        }

        @Override
        public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
        }
    }

    private static final class OneCopyOnWriteSet implements CopyOnWriteSet {
        private final InMemorySpanChangeListener listener;

        OneCopyOnWriteSet(InMemorySpanChangeListener listener) {
            this.listener = requireNonNull(listener);
        }

        @Override
        public CopyOnWriteSet add(final InMemorySpanChangeListener listener) {
            return this.listener.equals(listener) ? this : new TwoCopyOnWriteSet(this.listener, listener);
        }

        @Override
        public CopyOnWriteSet remove(final InMemorySpanChangeListener listener) {
            return this.listener.equals(listener) ? EmptyCopyOnWriteSet.INSTANCE : this;
        }

        @Override
        public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
            listener.spanChanged(oldSpan, newSpan);
        }
    }

    private static final class TwoCopyOnWriteSet implements CopyOnWriteSet {
        private final InMemorySpanChangeListener first;
        private final InMemorySpanChangeListener second;

        TwoCopyOnWriteSet(InMemorySpanChangeListener first, InMemorySpanChangeListener second) {
            this.first = first;
            this.second = requireNonNull(second);
        }

        @Override
        public CopyOnWriteSet add(final InMemorySpanChangeListener listener) {
            return first.equals(listener) || second.equals(listener) ? this : new ThreeOrMoreCopyOnWriteSet(
                    first, second, requireNonNull(listener));
        }

        @Override
        public CopyOnWriteSet remove(final InMemorySpanChangeListener listener) {
            if (first.equals(listener)) {
                return new OneCopyOnWriteSet(second);
            } else if (second.equals(listener)) {
                return new OneCopyOnWriteSet(first);
            }
            return this;
        }

        @Override
        public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
            first.spanChanged(oldSpan, newSpan);
            second.spanChanged(oldSpan, newSpan);
        }
    }

    private static final class ThreeOrMoreCopyOnWriteSet implements CopyOnWriteSet {
        private final InMemorySpanChangeListener[] listeners;

        ThreeOrMoreCopyOnWriteSet(InMemorySpanChangeListener... listeners) {
            this.listeners = listeners;
        }

        @Override
        public CopyOnWriteSet add(final InMemorySpanChangeListener listener) {
            requireNonNull(listener);
            for (final InMemorySpanChangeListener listener1 : listeners) {
                if (listener1.equals(listener)) {
                    return this;
                }
            }
            InMemorySpanChangeListener[] newListeners = Arrays.copyOf(listeners, listeners.length + 1);
            newListeners[listeners.length] = listener;
            return new ThreeOrMoreCopyOnWriteSet(newListeners);
        }

        @Override
        public CopyOnWriteSet remove(final InMemorySpanChangeListener listener) {
            for (int i = 0; i < listeners.length; ++i) {
                if (listeners[i].equals(listener)) {
                    if (listeners.length == 3) {
                        switch (i) {
                            case 0:
                                return new TwoCopyOnWriteSet(listeners[1], listeners[2]);
                            case 1:
                                return new TwoCopyOnWriteSet(listeners[0], listeners[2]);
                            default:
                                return new TwoCopyOnWriteSet(listeners[0], listeners[1]);
                        }
                    } else {
                        InMemorySpanChangeListener[] newListeners = new InMemorySpanChangeListener[
                                listeners.length - 1];
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
        public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
            for (InMemorySpanChangeListener listener : listeners) {
                listener.spanChanged(oldSpan, newSpan);
            }
        }
    }
}
