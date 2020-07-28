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
package io.servicetalk.client.api.internal.partition;

import io.servicetalk.client.api.partition.DuplicateAttributeException;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributes.Key;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class DefaultPartitionAttributesBuilderTest {
    private static final Key<String> SHARD_KEY = Key.newKey("shard");
    private static final Key<String> DC_KEY = Key.newKey("dc");
    private static final Key<Boolean> MAIN_KEY = Key.newKey("main");
    private static final Key<Integer> OTHER_KEY = Key.newKey("other");
    private static final Key<Integer> OTHER_KEY2 = Key.newKey("other2");
    private static final String DEFAULT_SHARD = "myshard";
    private static final String DEFAULT_DC = "mydc";

    @Test
    public void builderGrowsSize() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(0);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        builder.add(DC_KEY, DEFAULT_DC);
        PartitionAttributes partitionAttributes = builder.build();
        assertSize(partitionAttributes, 2);
        assertEquals(DEFAULT_SHARD, partitionAttributes.get(SHARD_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes.get(DC_KEY));
        assertNull(partitionAttributes.get(OTHER_KEY));
    }

    @Test
    public void duplicateKeyDifferentValueThrowsAtAdd() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(8);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(OTHER_KEY, 2);
        builder.add(MAIN_KEY, true);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        try {
            builder.add(OTHER_KEY, 3);
            fail();
        } catch (DuplicateAttributeException e) {
            assertSame(OTHER_KEY, e.getKey());
        }
    }

    @Test
    public void duplicateKeyDifferentValueThrowsAtBuild() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(8);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(OTHER_KEY, 2);
        builder.add(MAIN_KEY, true);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        builder.add(OTHER_KEY2, 10);
        builder.add(OTHER_KEY, 3);
        try {
            builder.build();
            fail();
        } catch (DuplicateAttributeException e) {
            assertSame(OTHER_KEY, e.getKey());
        }
    }

    @Test
    public void duplicateKeySameValueThrowsAtBuild() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(8);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(OTHER_KEY, 2);
        builder.add(MAIN_KEY, true);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        builder.add(OTHER_KEY2, 10);
        builder.add(OTHER_KEY, 2);
        try {
            builder.build();
            fail();
        } catch (DuplicateAttributeException e) {
            assertSame(OTHER_KEY, e.getKey());
        }
    }

    @Test
    public void addFourItemsSortedCorrectlyA() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(8);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(OTHER_KEY, 2);
        builder.add(MAIN_KEY, true);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        PartitionAttributes partitionAttributes = builder.build();
        assertSize(partitionAttributes, 4);
        assertEquals(DEFAULT_SHARD, partitionAttributes.get(SHARD_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes.get(DC_KEY));
        assertEquals((Integer) 2, partitionAttributes.get(OTHER_KEY));
        assertEquals(true, partitionAttributes.get(MAIN_KEY));
    }

    @Test
    public void addFourItemsSortedCorrectlyB() {
        PartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(4);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        builder.add(MAIN_KEY, false);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(OTHER_KEY, 2);
        PartitionAttributes partitionAttributes = builder.build();
        assertSize(partitionAttributes, 4);
        assertEquals(DEFAULT_SHARD, partitionAttributes.get(SHARD_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes.get(DC_KEY));
        assertEquals((Integer) 2, partitionAttributes.get(OTHER_KEY));
        assertEquals(false, partitionAttributes.get(MAIN_KEY));
    }

    @Test
    public void equalsAndHashCode() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(3);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(MAIN_KEY, true);
        PartitionAttributes partitionAttributes = builder.build();

        builder = new DefaultPartitionAttributesBuilder(3);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(MAIN_KEY, true);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        PartitionAttributes partitionAttributes2 = builder.build();

        assertEquals(partitionAttributes, partitionAttributes2);
        assertEquals(partitionAttributes.hashCode(), partitionAttributes2.hashCode());
        assertSize(partitionAttributes, 3);
        assertEquals(DEFAULT_SHARD, partitionAttributes2.get(SHARD_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes2.get(DC_KEY));
        assertEquals(true, partitionAttributes2.get(MAIN_KEY));
        assertSize(partitionAttributes2, 3);
        assertEquals(true, partitionAttributes2.get(MAIN_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes2.get(DC_KEY));
        assertEquals(DEFAULT_SHARD, partitionAttributes2.get(SHARD_KEY));
    }

    @Test
    public void notEqualsSameKeysDifferentValues() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(2);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(MAIN_KEY, true);
        PartitionAttributes partitionAttributes = builder.build();

        builder = new DefaultPartitionAttributesBuilder(3);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(MAIN_KEY, false);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        PartitionAttributes partitionAttributes2 = builder.build();

        assertNotEquals(partitionAttributes, partitionAttributes2);
        assertSize(partitionAttributes, 3);
        assertEquals(DEFAULT_SHARD, partitionAttributes.get(SHARD_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes.get(DC_KEY));
        assertEquals(true, partitionAttributes.get(MAIN_KEY));
        assertSize(partitionAttributes2, 3);
        assertEquals(false, partitionAttributes2.get(MAIN_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes2.get(DC_KEY));
        assertEquals(DEFAULT_SHARD, partitionAttributes2.get(SHARD_KEY));
    }

    @Test
    public void notEqualsDifferentKeys() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(3);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(MAIN_KEY, true);
        PartitionAttributes partitionAttributes = builder.build();

        builder = new DefaultPartitionAttributesBuilder(3);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(MAIN_KEY, true);
        builder.add(OTHER_KEY, 2);
        PartitionAttributes partitionAttributes2 = builder.build();

        assertNotEquals(partitionAttributes, partitionAttributes2);
        assertSize(partitionAttributes, 3);
        assertEquals(DEFAULT_SHARD, partitionAttributes.get(SHARD_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes.get(DC_KEY));
        assertEquals(true, partitionAttributes.get(MAIN_KEY));
        assertNull(partitionAttributes.get(OTHER_KEY));
        assertSize(partitionAttributes2, 3);
        assertEquals(true, partitionAttributes2.get(MAIN_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes2.get(DC_KEY));
        assertEquals((Integer) 2, partitionAttributes2.get(OTHER_KEY));
        assertNull(partitionAttributes2.get(SHARD_KEY));
    }

    @Test
    public void notEqualsMissingKey() {
        DefaultPartitionAttributesBuilder builder = new DefaultPartitionAttributesBuilder(3);
        builder.add(SHARD_KEY, DEFAULT_SHARD);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(MAIN_KEY, true);
        PartitionAttributes partitionAttributes = builder.build();

        builder = new DefaultPartitionAttributesBuilder(2);
        builder.add(DC_KEY, DEFAULT_DC);
        builder.add(MAIN_KEY, true);
        PartitionAttributes partitionAttributes2 = builder.build();

        assertNotEquals(partitionAttributes, partitionAttributes2);
        assertSize(partitionAttributes, 3);
        assertEquals(DEFAULT_SHARD, partitionAttributes.get(SHARD_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes.get(DC_KEY));
        assertEquals(true, partitionAttributes.get(MAIN_KEY));
        assertSize(partitionAttributes2, 2);
        assertEquals(true, partitionAttributes2.get(MAIN_KEY));
        assertEquals(DEFAULT_DC, partitionAttributes2.get(DC_KEY));
    }

    private static void assertSize(PartitionAttributes address, int size) {
        assertEquals(size, address.size());
        assertEquals(size == 0, address.isEmpty());
    }
}
