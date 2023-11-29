/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static java.util.Objects.requireNonNull;

/**
 * A concurrency primitive for providing thread safety without using locks.
 *
 * A {@link SequentialExecutor} is queue of tasks that are executed one at a time in the order they were
 * received. This provides a way to serialize work between threads without needing to use locks which can
 * result in thread contention and thread deadlock scenarios.
 */
final class SequentialExecutor implements Executor {

    private final Logger logger;
    private final PublisherSource.Processor<Runnable, Runnable> sequentialExecutionQueue =
            newPublisherProcessorDropHeadOnOverflow(Integer.MAX_VALUE);

    SequentialExecutor(Class<?> clazz) {
        this(LoggerFactory.getLogger(clazz));
    }

    SequentialExecutor(final Logger logger) {
        this.logger = requireNonNull(logger, "logger");
        fromSource(sequentialExecutionQueue).forEach(this::safeRun);
    }

    @Override
    public void execute(Runnable command) {
        sequentialExecutionQueue.onNext(command);
    }

    private void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            logger.error("Exception caught in sequential execution", ex);
        }
    }
}
