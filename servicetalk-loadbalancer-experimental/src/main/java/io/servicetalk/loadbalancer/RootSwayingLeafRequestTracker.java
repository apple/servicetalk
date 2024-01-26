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

import io.servicetalk.client.api.ScoreSupplier;

import static java.util.Objects.requireNonNull;

/**
 * A two-level request tracker, namely root and leaf.
 * Each tracking interaction influences both levels, but reporting operations will only
 * consult the leaf.
 */
final class RootSwayingLeafRequestTracker implements RequestTracker, ScoreSupplier {

    private final RequestTracker root;
    private final DefaultRequestTracker leaf;

    RootSwayingLeafRequestTracker(final RequestTracker root, final DefaultRequestTracker leaf) {
        this.root = requireNonNull(root);
        this.leaf = requireNonNull(leaf);
    }

    @Override
    public int score() {
        // report the leaf
        return leaf.score();
    }

    @Override
    public long beforeStart() {
        // Tracks both levels
        final long timestamp = root.beforeStart();
        leaf.beforeStart();
        return timestamp;
    }

    @Override
    public void onSuccess(final long beforeStartTimeNs) {
        // Tracks both levels
        root.onSuccess(beforeStartTimeNs);
        leaf.onSuccess(beforeStartTimeNs);
    }

    @Override
    public void onError(final long beforeStartTimeNs, ErrorClass errorClass) {
        // Tracks both levels
        root.onError(beforeStartTimeNs, errorClass);
        leaf.onError(beforeStartTimeNs, errorClass);
    }
}
