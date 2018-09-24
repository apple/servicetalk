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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class ProgressiveInMemorySpanChangeListenerSet implements ListenableInMemoryScopeManager.InMemorySpanChangeListener {
    private static final AtomicReferenceFieldUpdater<ProgressiveInMemorySpanChangeListenerSet, ProgressiveSet> setUpdater =
            newUpdater(ProgressiveInMemorySpanChangeListenerSet.class, ProgressiveSet.class, "set");
    private volatile ProgressiveSet set = EmptyProgressiveSet.INSTANCE;

    boolean add(ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
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

    boolean remove(ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
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
    public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
        set.spanChanged(oldSpan, newSpan);
    }

    private interface ProgressiveSet extends ListenableInMemoryScopeManager.InMemorySpanChangeListener {
        ProgressiveSet add(ListenableInMemoryScopeManager.InMemorySpanChangeListener listener);

        ProgressiveSet remove(ListenableInMemoryScopeManager.InMemorySpanChangeListener listener);
    }

    private static final class EmptyProgressiveSet implements ProgressiveSet {
        static final ProgressiveSet INSTANCE = new EmptyProgressiveSet();

        private EmptyProgressiveSet() {
            // singleton
        }

        @Override
        public ProgressiveSet add(final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            return new OneProgressiveSet(listener);
        }

        @Override
        public ProgressiveSet remove(final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            return this;
        }

        @Override
        public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
        }
    }

    private static final class OneProgressiveSet implements ProgressiveSet {
        private final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener;

        OneProgressiveSet(ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            this.listener = requireNonNull(listener);
        }

        @Override
        public ProgressiveSet add(final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            return this.listener.equals(listener) ? this : new TwoProgressiveSet(this.listener, listener);
        }

        @Override
        public ProgressiveSet remove(final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            return this.listener.equals(listener) ? EmptyProgressiveSet.INSTANCE : this;
        }

        @Override
        public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
            listener.spanChanged(oldSpan, newSpan);
        }
    }

    private static final class TwoProgressiveSet implements ProgressiveSet {
        private final ListenableInMemoryScopeManager.InMemorySpanChangeListener first;
        private final ListenableInMemoryScopeManager.InMemorySpanChangeListener second;

        TwoProgressiveSet(ListenableInMemoryScopeManager.InMemorySpanChangeListener first, ListenableInMemoryScopeManager.InMemorySpanChangeListener second) {
            this.first = first;
            this.second = requireNonNull(second);
        }

        @Override
        public ProgressiveSet add(final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            return first.equals(listener) || second.equals(listener) ? this : new ThreeOrMoreProgressiveSet(
                    first, second, requireNonNull(listener));
        }

        @Override
        public ProgressiveSet remove(final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            if (first.equals(listener)) {
                return new OneProgressiveSet(second);
            } else if (second.equals(listener)) {
                return new OneProgressiveSet(first);
            }
            return this;
        }

        @Override
        public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
            first.spanChanged(oldSpan, newSpan);
            second.spanChanged(oldSpan, newSpan);
        }
    }

    private static final class ThreeOrMoreProgressiveSet implements ProgressiveSet {
        private final ListenableInMemoryScopeManager.InMemorySpanChangeListener[] listeners;

        ThreeOrMoreProgressiveSet(ListenableInMemoryScopeManager.InMemorySpanChangeListener... listeners) {
            this.listeners = listeners;
        }

        @Override
        public ProgressiveSet add(final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            requireNonNull(listener);
            for (final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener1 : listeners) {
                if (listener1.equals(listener)) {
                    return this;
                }
            }
            ListenableInMemoryScopeManager.InMemorySpanChangeListener[] newListeners = Arrays.copyOf(listeners, listeners.length + 1);
            newListeners[listeners.length] = listener;
            return new ThreeOrMoreProgressiveSet(newListeners);
        }

        @Override
        public ProgressiveSet remove(final ListenableInMemoryScopeManager.InMemorySpanChangeListener listener) {
            for (int i = 0; i < listeners.length; ++i) {
                if (listeners[i].equals(listener)) {
                    if (listeners.length == 3) {
                        switch (i) {
                            case 0:
                                return new TwoProgressiveSet(listeners[1], listeners[2]);
                            case 1:
                                return new TwoProgressiveSet(listeners[0], listeners[2]);
                            default:
                                return new TwoProgressiveSet(listeners[0], listeners[1]);
                        }
                    } else {
                        ListenableInMemoryScopeManager.InMemorySpanChangeListener[] newListeners = new ListenableInMemoryScopeManager.InMemorySpanChangeListener[
                                listeners.length - 1];
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
        public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
            for (ListenableInMemoryScopeManager.InMemorySpanChangeListener listener : listeners) {
                listener.spanChanged(oldSpan, newSpan);
            }
        }
    }
}
