/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.Collections.emptyList;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * This {@link List}-backed persistent data-structure helps with algorithms that require random access to a read-only
 * snapshot of the data and allows for atomic {@link #add(AsyncCloseable)}, {@link #addIfAbsent(AsyncCloseable)}, {@link
 * #remove(AsyncCloseable)} (Object)} and terminal {@link AsyncCloseable} methods.
 * <p>
 * {@link CopyOnWriteArrayList} is similar, but doesn't expose a snapshot with random access nor a terminal state.
 *
 * @param <T> type of {@link AsyncCloseable} element in the list.
 */
final class CowList<T extends AsyncCloseable> implements AsyncCloseable {

    // TODO(jayv) this data-structure may be improved when under contention (chunked?)

    private static final List CLOSED_LIST = new ArrayList();
    private static final AtomicReferenceFieldUpdater<CowList, List>
            currentEntriesUpdater = newUpdater(CowList.class, List.class, "currentEntries");
    private volatile List<T> currentEntries = emptyList();
    private final AsyncCloseable closable = AsyncCloseables.toAsyncCloseable(g -> {
        List<T> toClose = close0();
        if (toClose == CLOSED_LIST) {
            return Completable.completed();
        }
        CompositeCloseable cc = AsyncCloseables.newCompositeCloseable().prependAll(toClose);
        if (g) {
            return cc.closeAsyncGracefully();
        }
        return cc.closeAsync();
    });

    boolean addIfAbsent(final T entry) {
        List<T> current, entriesAdded;
        do {
            current = this.currentEntries;
            if (current == CLOSED_LIST) {
                return false;
            }
            // TODO(jayv) ideally the underlying data structure makes add()/remove() idempotent
            if (current.contains(entry)) {
                return true;
            }
            entriesAdded = new ArrayList<>(current);
            entriesAdded.add(entry);
        } while (!currentEntriesUpdater.compareAndSet(this, current, entriesAdded));
        return true;
    }

    boolean add(final T entry) {
        List<T> current, entriesAdded;
        do {
            current = this.currentEntries;
            if (current == CLOSED_LIST) {
                return false;
            }
            entriesAdded = new ArrayList<>(current);
            entriesAdded.add(entry);
        } while (!currentEntriesUpdater.compareAndSet(this, current, entriesAdded));
        return true;
    }

    void remove(final T entry) {
        List<T> current, entriesRemoved;
        do {
            current = this.currentEntries;
            if (current == CLOSED_LIST) {
                return;
            }
            entriesRemoved = new ArrayList<>(current);
            entriesRemoved.remove(entry);
        } while (!currentEntriesUpdater.compareAndSet(this, current, entriesRemoved));
    }

    List<T> currentEntries() {
        return currentEntries;
    }

    private List<T> close0() {
        List<T> current;
        do {
            current = currentEntries;
            if (current == CLOSED_LIST) {
                return current;
            }
        } while (!currentEntriesUpdater.compareAndSet(this, current, CLOSED_LIST));
        return current;
    }

    boolean isClosed() {
        return currentEntries == CLOSED_LIST;
    }

    @Override
    public Completable closeAsync() {
        return closable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closable.closeAsyncGracefully();
    }
}
