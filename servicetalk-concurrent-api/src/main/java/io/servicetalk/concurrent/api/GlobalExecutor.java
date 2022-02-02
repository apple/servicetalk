/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;

/**
 * Implements {@link Executor} for {@link Executors#global()}.
 */
final class GlobalExecutor extends DelegatingExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExecutor.class);

    static final String NAME_PREFIX = "servicetalk-global-executor";

    static final Executor GLOBAL_EXECUTOR = new GlobalExecutor(newCachedThreadExecutor(
            new DefaultThreadFactory(GlobalExecutor.NAME_PREFIX)));

    private GlobalExecutor(final Executor delegate) {
        super(delegate);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + "}";
    }

    @Override
    public Completable closeAsync() {
        return delegate().closeAsync()
                .beforeOnSubscribe(__ -> log(LOGGER, NAME_PREFIX, "closeAsync()"));
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate().closeAsyncGracefully()
                .beforeOnSubscribe(__ -> log(LOGGER, NAME_PREFIX, "closeAsyncGracefully()"));
    }

    private static void log(final Logger logger, final String name, final String methodName) {
        logger.debug("Closure of \"{}\" was initiated using {} method. Closing the global instance before " +
                "closing all resources that use it may result in unexpected behavior.", name, methodName);
    }
}
