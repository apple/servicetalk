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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.api.AsyncContext.Listener;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;
import static java.util.Objects.requireNonNull;

/**
 * This class provides a Copy-on-Write set behavior and is special cased for cardinality of less than 4 elements.
 * Less than 4 elements was chosen because it is not common to have more than this number of {@link Listener}s in
 * practice. Common {@link Listener} types are for MDC, tracing, and maybe debugging.
 */
final class CopyOnWriteAsyncContextListenerSet implements AsyncContextListenerSet {
    private final AtomicReference<ProgressiveListenerSet> setRef =
            new AtomicReference<>(EmptyProgressiveListenerSet.INSTANCE);

    @Override
    public boolean add(final Listener listener) {
        requireNonNull(listener);
        for (;;) {
            ProgressiveListenerSet set = setRef.get();
            ProgressiveListenerSet afterAddSet = set.add(listener);
            if (set == afterAddSet) {
                return false;
            } else if (setRef.compareAndSet(set, afterAddSet)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(final Listener listener) {
        for (;;) {
            ProgressiveListenerSet set = setRef.get();
            ProgressiveListenerSet afterRemoveSet = set.remove(listener);
            if (set == afterRemoveSet) {
                return false;
            } else if (setRef.compareAndSet(set, afterRemoveSet)) {
                return true;
            }
        }
    }

    @Override
    public void clear() {
        setRef.set(EmptyProgressiveListenerSet.INSTANCE);
    }

    @Override
    public void setContextMapAndNotifyListeners(final AsyncContextMap newContextMap,
                                                final ThreadLocal<AsyncContextMap> contextLocal) {
        setRef.get().setContextMapAndNotifyListeners(newContextMap, contextLocal);
    }

    private interface ProgressiveListenerSet {
        ProgressiveListenerSet add(Listener listener);

        ProgressiveListenerSet remove(Listener listener);

        void setContextMapAndNotifyListeners(AsyncContextMap newContextMap,
                                             ThreadLocal<AsyncContextMap> contextLocal);
    }

    private static final class EmptyProgressiveListenerSet implements ProgressiveListenerSet {
        static final EmptyProgressiveListenerSet INSTANCE = new EmptyProgressiveListenerSet();

        private EmptyProgressiveListenerSet() {
            // singleton
        }

        @Override
        public ProgressiveListenerSet add(final Listener listener) {
            return new OneProgressiveListenerSet(listener);
        }

        @Override
        public ProgressiveListenerSet remove(final Listener listener) {
            return this;
        }

        @Override
        public void setContextMapAndNotifyListeners(final AsyncContextMap newContextMap,
                                                    final ThreadLocal<AsyncContextMap> contextLocal) {
            contextLocal.set(newContextMap);
        }
    }

    private static final class OneProgressiveListenerSet implements ProgressiveListenerSet {
        private final Listener one;

        OneProgressiveListenerSet(Listener one) {
            this.one = one;
        }

        @Override
        public ProgressiveListenerSet add(final Listener listener) {
            return one.equals(listener) ? this : new TwoProgressiveListenerSet(one, listener);
        }

        @Override
        public ProgressiveListenerSet remove(final Listener listener) {
            return one.equals(listener) ? EmptyProgressiveListenerSet.INSTANCE : this;
        }

        @Override
        public void setContextMapAndNotifyListeners(final AsyncContextMap newContextMap,
                                                    final ThreadLocal<AsyncContextMap> contextLocal) {
            final AsyncContextMap oldContextMap = contextLocal.get();
            if (oldContextMap != newContextMap) {
                contextLocal.set(newContextMap);
                one.contextMapChanged(oldContextMap, newContextMap);
            }
        }
    }

    private static final class TwoProgressiveListenerSet implements ProgressiveListenerSet {
        private final Listener one;
        private final Listener two;

        TwoProgressiveListenerSet(Listener one, Listener two) {
            this.one = one;
            this.two = two;
        }

        @Override
        public ProgressiveListenerSet add(final Listener listener) {
            return one.equals(listener) || two.equals(listener) ?
                    this : new ThreeProgressiveListenerSet(one, two, listener);
        }

        @Override
        public ProgressiveListenerSet remove(final Listener listener) {
            if (one.equals(listener)) {
                return new OneProgressiveListenerSet(two);
            } else if (two.equals(listener)) {
                return new OneProgressiveListenerSet(one);
            }
            return this;
        }

        @Override
        public void setContextMapAndNotifyListeners(final AsyncContextMap newContextMap,
                                                    final ThreadLocal<AsyncContextMap> contextLocal) {
            final AsyncContextMap oldContextMap = contextLocal.get();
            if (oldContextMap != newContextMap) {
                contextLocal.set(newContextMap);
                one.contextMapChanged(oldContextMap, newContextMap);
                two.contextMapChanged(oldContextMap, newContextMap);
            }
        }
    }

    private static final class ThreeProgressiveListenerSet implements ProgressiveListenerSet {
        private final Listener one;
        private final Listener two;
        private final Listener three;

        ThreeProgressiveListenerSet(Listener one, Listener two, Listener three) {
            this.one = one;
            this.two = two;
            this.three = three;
        }

        @Override
        public ProgressiveListenerSet add(final Listener listener) {
            return one.equals(listener) || two.equals(listener) || three.equals(listener) ?
                    this : new FourOrMoreProgressiveListenerSet(one, two, three, listener);
        }

        @Override
        public ProgressiveListenerSet remove(final Listener listener) {
            if (one.equals(listener)) {
                return new TwoProgressiveListenerSet(two, three);
            } else if (two.equals(listener)) {
                return new TwoProgressiveListenerSet(one, three);
            } else if (three.equals(listener)) {
                return new TwoProgressiveListenerSet(one, two);
            }
            return this;
        }

        @Override
        public void setContextMapAndNotifyListeners(final AsyncContextMap newContextMap,
                                                    final ThreadLocal<AsyncContextMap> contextLocal) {
            final AsyncContextMap oldContextMap = contextLocal.get();
            if (oldContextMap != newContextMap) {
                contextLocal.set(newContextMap);
                one.contextMapChanged(oldContextMap, newContextMap);
                two.contextMapChanged(oldContextMap, newContextMap);
                three.contextMapChanged(oldContextMap, newContextMap);
            }
        }
    }

    /**
     * This class is useful when two iterations must be done over the same set of elements.
     * Using a {@link CopyOnWriteArraySet} would make it difficult (or impossible) to ensure two successive iterations
     * see the same set of elements because only a forward {@link Iterator} is exposed.
     * <p>
     * Using a {@link CopyOnWriteArrayList} exposes a {@link ListIterator} but that would require 3 traversals
     * of the list for each event.
     * <p>
     * This implementation is currently optimized for low volume modifications and high volume of
     * {@link #setContextMapAndNotifyListeners(AsyncContextMap, ThreadLocal)}.
     */
    private static final class FourOrMoreProgressiveListenerSet implements ProgressiveListenerSet {
        private final Listener[] listeners;

        FourOrMoreProgressiveListenerSet(Listener... listeners) {
            this.listeners = listeners;
        }

        @Override
        public ProgressiveListenerSet add(final Listener listener) {
            int i = indexOf(listener, listeners);
            if (i >= 0) {
                return this;
            }
            Listener[] newArray = copyOf(listeners, listeners.length + 1);
            newArray[listeners.length] = listener;
            return new FourOrMoreProgressiveListenerSet(newArray);
        }

        @Override
        public ProgressiveListenerSet remove(final Listener listener) {
            int i = indexOf(listener, listeners);
            if (i < 0) {
                return this;
            }
            if (listeners.length == 4) {
                switch (i) {
                    case 0:
                        return new ThreeProgressiveListenerSet(listeners[1], listeners[2], listeners[3]);
                    case 1:
                        return new ThreeProgressiveListenerSet(listeners[0], listeners[2], listeners[3]);
                    case 2:
                        return new ThreeProgressiveListenerSet(listeners[0], listeners[1], listeners[3]);
                    case 3:
                        return new ThreeProgressiveListenerSet(listeners[0], listeners[1], listeners[2]);
                    default:
                        throw new RuntimeException("programming error. i: " + i);
                }
            }
            Listener[] newArray = new Listener[listeners.length - 1];
            arraycopy(listeners, 0, newArray, 0, i);
            arraycopy(listeners, i + 1, newArray, i, listeners.length - i - 1);
            return new FourOrMoreProgressiveListenerSet(newArray);
        }

        @Override
        public void setContextMapAndNotifyListeners(final AsyncContextMap newContextMap,
                                                    final ThreadLocal<AsyncContextMap> contextLocal) {
            final AsyncContextMap oldContextMap = contextLocal.get();
            if (oldContextMap != newContextMap) {
                contextLocal.set(newContextMap);
                int i = 0;
                do {
                    listeners[i].contextMapChanged(oldContextMap, newContextMap);
                } while (++i < listeners.length);
            }
        }

        private static <X> int indexOf(X l, X[] array) {
            for (int i = 0; i < array.length; ++i) {
                // Null elements are not permitted in the array, so no null check is necessary.
                if (array[i].equals(l)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
