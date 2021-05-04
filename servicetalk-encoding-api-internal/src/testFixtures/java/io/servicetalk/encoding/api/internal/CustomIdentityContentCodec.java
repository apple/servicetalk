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

package io.servicetalk.encoding.api.internal;

import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.Identity;

import java.util.Objects;

public class CustomIdentityContentCodec extends NoopContentCodec {

    public CustomIdentityContentCodec() {
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
