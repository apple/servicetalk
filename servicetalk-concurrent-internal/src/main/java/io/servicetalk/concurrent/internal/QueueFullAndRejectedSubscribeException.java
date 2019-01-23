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
package io.servicetalk.concurrent.internal;

/**
 * Exception indicating a bounded queue is full, which also resulted in a rejected subscribe.
 */
public final class QueueFullAndRejectedSubscribeException extends QueueFullException implements RejectedSubscribeError {
    private static final long serialVersionUID = 2132623149199945728L;

    /**
     * Create a new instance.
     *
     * @param queueIdentifier Identifier for the queue that is full.
     */
    public QueueFullAndRejectedSubscribeException(final String queueIdentifier) {
        super(queueIdentifier);
    }

    /**
     * Create a new instance.
     *
     * @param queueIdentifier Identifier for the queue that is full.
     * @param capacity Capacity for queue.
     */
    public QueueFullAndRejectedSubscribeException(final String queueIdentifier, final int capacity) {
        super(queueIdentifier, capacity);
    }
}
