/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * <p> Tag should follow the
 * <a href="https://github.com/opentracing/specification/blob/master/semantic_conventions.md">OpenTracing</a> tag.
 */
interface TagExtractor<T> {

    /**
     * Returns the number of tags {@code obj} exposes.
     *
     * @param obj the object to evaluate
     * @return the number of tags
     */
    default int len(T obj) {
        return 0;
    }

    /**
     * Returns the name of the tag extracted from {@code obj}at the specified {@code index}.
     *
     * @param obj   the object to extract the tag name from
     * @param index the index of the tag
     * @return the tag name
     */
    default String name(T obj, int index) {
        throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }

    /**
     * Returns the value of the tag extracted from {@code obj} at the specified {@code index}.
     *
     * @param obj   the object to extract the tag name from
     * @param index the index of the tag
     * @return the tag value
     */
    default String value(T obj, int index) {
        throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }

    /**
     * Extract all the tags from {@code obj} into a {@code Map<String, String>}.
     *
     * @param obj the object to extract the tags from
     * @return the map of all tags extracted from the object
     */
    default Map<String, String> extract(T obj) {
        Map<String, String> tags = new HashMap<>();
        extractTo(obj, tags);
        return tags;
    }

    /**
     * Extract all the tags from {@code obj} into the {@code tags} map.
     *
     * @param obj  the object to extract the tags from
     * @param tags the map populated with the tags extracted from the object
     */
    default void extractTo(T obj, Map<String, String> tags) {
        int len = len(obj);
        for (int idx = 0; idx < len; idx++) {
            tags.put(name(obj, idx), value(obj, idx));
        }
    }

    /**
     * Extract all the tags from {@code obj} into a tag {@code consumer}.
     *
     * @param obj      the object to extract the tags from
     * @param consumer the consumer populated with the tags extracted from the object
     */
    default void extractTo(T obj, BiConsumer<String, String> consumer) {
        int len = len(obj);
        for (int idx = 0; idx < len; idx++) {
            consumer.accept(name(obj, idx), value(obj, idx));
        }
    }
}
