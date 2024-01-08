/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import static java.util.Objects.requireNonNull;

/**
 * A two-level latency tracker, namely root and leaf.
 * Each tracking interaction influences both levels, but reporting operations (i.e., {@link #score()}) will only
 * consult the leaf.
 */
final class RootSwayingLeafLatencyTracker implements LatencyTracker {

    private final LatencyTracker root;
    private final LatencyTracker leaf;
    RootSwayingLeafLatencyTracker(final LatencyTracker root, final LatencyTracker leaf) {
        this.root = requireNonNull(root);
        this.leaf = requireNonNull(leaf);
    }

    @Override
    public long beforeStart() {
        // Tracks both levels
        final long timestamp = root.beforeStart();
        leaf.beforeStart();
        return timestamp;
    }

    @Override
    public void observeSuccess(final long beforeStartTimeNs) {
        // Tracks both levels
        root.observeSuccess(beforeStartTimeNs);
        leaf.observeSuccess(beforeStartTimeNs);
    }

    @Override
    public void observeCancel(final long beforeStartTimeNs) {
        // Tracks both levels
        root.observeCancel(beforeStartTimeNs);
        leaf.observeCancel(beforeStartTimeNs);
    }

    @Override
    public void observeError(final long beforeStartTimeNs) {
        // Tracks both levels
        root.observeError(beforeStartTimeNs);
        leaf.observeError(beforeStartTimeNs);
    }

    @Override
    public int score() {
        // Reports the level score
        return leaf.score();
    }
}
