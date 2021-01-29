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
package io.servicetalk.oio.api.internal;

import io.servicetalk.oio.api.PayloadWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for {@link PayloadWriter}.
 */
public final class PayloadWriterUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadWriterUtils.class);
    private PayloadWriterUtils() {
    }

    /**
     * Invokes {@link PayloadWriter#close(Throwable)} ignoring an occurred exception if any.
     * @param writer The {@link PayloadWriter} to close.
     * @param cause The cause to pass to {@link PayloadWriter#close(Throwable)}.
     * @param <T> The type of {@link PayloadWriter}.
     */
    public static <T> void safeClose(PayloadWriter<T> writer, Throwable cause) {
        try {
            writer.close(cause);
        } catch (Exception e) {
            LOGGER.info("Unexpected exception from writer {} closing with cause {}", writer, cause, e);
        }
    }
}
