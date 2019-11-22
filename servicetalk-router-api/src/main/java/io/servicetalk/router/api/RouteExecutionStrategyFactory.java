/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.router.api;

import javax.annotation.Nullable;

/**
 * A factory that creates execution strategy for different {@link RouteExecutionStrategy#id() id}s of
 * {@link RouteExecutionStrategy} annotation.
 *
 * @param <ES> protocol-specific execution strategy implementation
 */
@FunctionalInterface
public interface RouteExecutionStrategyFactory<ES> {

    /**
     * Gets an execution strategy for the specified {@code id}{@link RouteExecutionStrategy#id() id} of
     * {@link RouteExecutionStrategy} annotation.
     *
     * @param id of {@link RouteExecutionStrategy}
     * @return {@link ES} implementation or {@code null}
     */
    @Nullable
    ES get(String id);
}
