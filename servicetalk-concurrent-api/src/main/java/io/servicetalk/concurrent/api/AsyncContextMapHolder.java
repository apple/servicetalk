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
package io.servicetalk.concurrent.api;

import javax.annotation.Nullable;

/**
 * Interface for setting and getting a {@link AsyncContextMap}.
 */
public interface AsyncContextMapHolder {
    /**
     * Set the {@link AsyncContextMap}.
     * @param asyncContextMap the new value for {@link AsyncContextMap}.
     */
    void asyncContextMap(@Nullable AsyncContextMap asyncContextMap);

    /**
     * Get the current {@link AsyncContextMap}.
     * @return the current {@link AsyncContextMap}.
     */
    @Nullable
    AsyncContextMap asyncContextMap();
}
