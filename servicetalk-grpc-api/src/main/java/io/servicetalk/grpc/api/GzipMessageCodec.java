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
package io.servicetalk.grpc.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

final class GzipMessageCodec extends AbstractZipMessageCodec {

    @Override
    InflaterInputStream newInflaterInputStream(final InputStream in) throws IOException {
        return new GZIPInputStream(in);
    }

    @Override
    DeflaterOutputStream newDeflaterOutputStream(final OutputStream out) throws IOException {
        // TODO tk - Optimization, we could rely on the Deflater directly to avoid the intermediate
        // copy on the stream buffer
        return new GZIPOutputStream(out, ONE_KB, true);
    }
}
