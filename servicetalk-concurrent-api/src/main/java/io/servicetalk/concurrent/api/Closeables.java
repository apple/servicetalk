/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.GracefulCloseable;
import io.servicetalk.utils.internal.PlatformDependent;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A utility class for methods related to {@link java.io.Closeable}.
 */
public final class Closeables {

    private Closeables() {
        // No instances
    }

    /**
     * Invokes {@link GracefulCloseable#closeGracefully()} on the {@code closable}, applies a timeout, and if the
     * timeout fires forces a call to {@link GracefulCloseable#close()}.
     *
     * @param executor {@link Executor} to use for applying timeout.
     * @param closable The {@link GracefulCloseable} to initiate {@link GracefulCloseable#closeGracefully()} on.
     * @param gracefulCloseTimeout The timeout duration to wait for {@link GracefulCloseable#closeGracefully()} to
     * complete.
     * @param gracefulCloseTimeoutUnit The time unit applied to {@code gracefulCloseTimeout}.
     *
     * @throws Exception if graceful closure failed.
     */
    public static void closeGracefully(final Executor executor, final GracefulCloseable closable,
                                       final long gracefulCloseTimeout, final TimeUnit gracefulCloseTimeoutUnit)
            throws Exception {
        Future<Void> graceful = executor.submit(() -> {
            try {
                closable.closeGracefully();
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        }).toFuture();
        try {
            graceful.get(gracefulCloseTimeout, gracefulCloseTimeoutUnit);
        } catch (TimeoutException e) {
            closable.close();
        }
    }
}
