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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.Collections.emptyList;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

// TODO(jayv) this should implement BinarySearch to allow more effective updates
final class CowList<T> {

    private static final List CLOSED_LIST = new ArrayList();
    private static final AtomicReferenceFieldUpdater<CowList, List>
            currentEntriesUpdater = newUpdater(CowList.class, List.class, "currentEntries");
    private volatile List<T> currentEntries = emptyList();

    public boolean add(final T entry) {
        List<T> current, entriesAdded;
        do {
            current = this.currentEntries;
            if (current == CLOSED_LIST) {
                return false;
            }
            if (current.contains(entry)) {
                return true;
            }
            entriesAdded = new ArrayList<>(current);
            entriesAdded.add(entry);
        } while (!currentEntriesUpdater.compareAndSet(this, current, entriesAdded));
        return true;
    }

    public void remove(final T entry) {
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

    public List<T> currentEntries() {
        return currentEntries;
    }

    public List<T> close() {
        List<T> current;
        do {
            current = currentEntries;
            if (current == CLOSED_LIST) {
                return current;
            }
        } while (!currentEntriesUpdater.compareAndSet(this, current, CLOSED_LIST));
        return current;
    }

    public boolean isClosed() {
        return currentEntries == CLOSED_LIST;
    }
}
