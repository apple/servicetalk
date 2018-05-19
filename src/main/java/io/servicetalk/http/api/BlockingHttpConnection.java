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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.http.api.HttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;

/**
 * The equivalent of {@link HttpConnection} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingHttpConnection extends BlockingHttpRequester {
    /**
     * Get the {@link ConnectionContext}.
     *
     * @return the {@link ConnectionContext}.
     */
    public abstract ConnectionContext getConnectionContext();

    /**
     * Returns a {@link BlockingIterable} that gives the current value of the setting as well as subsequent changes to
     * the setting value.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link BlockingIterable} for the setting values.
     */
    public abstract <T> BlockingIterable<T> getSettingIterable(SettingKey<T> settingKey);

    /**
     * Convert this {@link BlockingHttpConnection} to the {@link HttpConnection} API.
     * <p>
     * Note that the resulting {@link HttpConnection} will still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingHttpConnection}.
     *
     * @return a {@link HttpConnection} representation of this {@link BlockingHttpConnection}.
     */
    public final HttpConnection asAsynchronousConnection() {
        return asAsynchronousConnectionInternal();
    }

    HttpConnection asAsynchronousConnectionInternal() {
        return new BlockingHttpConnectionToHttpConnection(this);
    }
}
