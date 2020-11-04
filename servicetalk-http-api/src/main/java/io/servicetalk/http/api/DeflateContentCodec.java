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
package io.servicetalk.http.api;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static io.servicetalk.http.api.CharSequences.newAsciiString;

final class DeflateContentCodec extends AbstractZipContentCodec {

    private static final CharSequence NAME = newAsciiString("deflate");

    DeflateContentCodec(final int chunkSize, final int maxSize) {
        super(chunkSize, maxSize);
    }

    @Override
    public CharSequence name() {
        return NAME;
    }

    @Override
    boolean supportsChecksum() {
        return false;
    }

    @Override
    Inflater newRawInflater() {
        return new Inflater(false);
    }

    @Override
    InflaterInputStream newInflaterInputStream(final InputStream in) {
        return new InflaterInputStream(in);
    }

    @Override
    DeflaterOutputStream newDeflaterOutputStream(final OutputStream out) {
        // TODO tk - Optimization, we could rely on the Deflater directly to avoid the intermediate
        // copy on the stream buffer
        return new DeflaterOutputStream(out, new Deflater(), chunkSize, true);
    }
}
