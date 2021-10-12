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
package io.servicetalk.http.api;

import io.servicetalk.transport.api.ExecutionContext;

/**
 * An extension of {@link ExecutionContext} for HTTP.
 */
public interface HttpExecutionContext extends ExecutionContext {

    /**
     * Returns the {@link HttpExecutionStrategy} associated with this context.
     *
     * @return The {@link HttpExecutionStrategy} associated with this context.
     */
    @Override
    HttpExecutionStrategy executionStrategy();
}
