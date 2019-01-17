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
package io.servicetalk.concurrent.internal;

/**
 * Error indicating an unbounded queue is unexpectedly full.
 */
public class UnboundQueueFullError extends AssertionError {
    private static final long serialVersionUID = -8660385639316480149L;

    /**
     * New instance.
     *
     * @param queueIdentifier Identifier for the queue that is full.
     */
    public UnboundQueueFullError(final String queueIdentifier) {
        super("Unexpected reject from an unbounded " + queueIdentifier + " queue");
    }
}
