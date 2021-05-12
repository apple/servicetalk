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
package io.servicetalk.encoding.api;

import javax.annotation.Nullable;

/**
 * Utility class that constructs and provides the default, always supported NOOP 'identity' {@link ContentCodec}.
 */
public final class Identity {

    private static final ContentCodec IDENTITY = new IdentityContentCodec();

    private Identity() {
        // no instances
    }

    /**
     * Returns the default, always supported NOOP 'identity' {@link ContentCodec}.
     * @return the default, always supported NOOP 'identity' {@link ContentCodec}.
     */
    public static ContentCodec identity() {
        return IDENTITY;
    }

    /**
     * Returns if the provided {@link ContentCodec codec} has name equal to {@code identity}.
     *
     * @param codec The {@link ContentCodec codec} to check.
     * @return {@code true} if the provided {@link ContentCodec} has name equal to {@code identity}.
     */
    public static boolean isIdentity(@Nullable final ContentCodec codec) {
        return IDENTITY.equals(codec);
    }
}
