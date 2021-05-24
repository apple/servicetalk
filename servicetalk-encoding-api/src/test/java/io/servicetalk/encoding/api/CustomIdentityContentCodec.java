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

import java.util.Objects;

/**
 * Extension of {@link NoopContentCodec} with custom implementations for {@link #equals(Object)} and {@link #hashCode()}
 * based on {@link #name()}.
 * This class can be used as is or can be extended to create a {@link ContentCodec} with custom behaviour.
 */
class CustomIdentityContentCodec extends NoopContentCodec {

    CustomIdentityContentCodec() {
        super(Identity.identity().name());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ContentCodec) {
            return Objects.equals(name(), ((ContentCodec) other).name());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }
}
