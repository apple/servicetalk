/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.dns.discovery.netty;

import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;

final class TestRecordStore implements RecordStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRecordStore.class);

    public static final int DEFAULT_TTL = 1;

    private final Map<String, Map<RecordType, List<Supplier<List<ResourceRecord>>>>> recordsToReturnByDomain =
            new HashMap<>();
    private final Map<String, Map<RecordType, Supplier<List<ResourceRecord>>>> defaultRecordsByDomain =
            new HashMap<>();

    public TestRecordStore defaultResponse(final String domain, final RecordType recordType,
                                           final String... ipAddresses) {
        return defaultResponse(domain, recordType, DEFAULT_TTL, ipAddresses);
    }

    public TestRecordStore defaultResponse(final String domain, final RecordType recordType, final int ttl,
                                           final String... ipAddresses) {
        final List<ResourceRecord> records = new ArrayList<>();
        for (final String ipAddress : ipAddresses) {
            records.add(createRecord(domain, recordType, ttl, ipAddress));
        }
        return defaultResponse(domain, recordType, () -> records);
    }

    public TestRecordStore defaultResponse(final String domain, final RecordType recordType,
                                           final Supplier<List<ResourceRecord>> records) {
        final Map<RecordType, Supplier<List<ResourceRecord>>> defaultRecords =
                defaultRecordsByDomain.computeIfAbsent(domain, k -> new HashMap<>());
        defaultRecords.put(recordType, records);
        LOGGER.debug("Set default response for {} type {} to {}", domain, recordType, records);
        return this;
    }

    public TestRecordStore addResponse(final String domain, final RecordType recordType,
                                       final String... ipAddresses) {
        return addResponse(domain, recordType, DEFAULT_TTL, ipAddresses);
    }

    public TestRecordStore addResponse(final String domain, final RecordType recordType, final int ttl,
                                       final String... ipAddresses) {
        final List<ResourceRecord> records = new ArrayList<>();
        for (final String ipAddress : ipAddresses) {
            records.add(createRecord(domain, recordType, ttl, ipAddress));
        }
        return addResponse(domain, recordType, () -> records);
    }

    public TestRecordStore addResponse(final String domain, final RecordType recordType,
                                       final Supplier<List<ResourceRecord>> records) {
        final Map<RecordType, List<Supplier<List<ResourceRecord>>>> recordsToReturn =
                recordsToReturnByDomain.computeIfAbsent(domain, k -> new HashMap<>());
        final List<Supplier<List<ResourceRecord>>> records2 = recordsToReturn.computeIfAbsent(
                recordType, k -> new ArrayList<>());
        records2.add(records);
        LOGGER.debug("Added response for {} type {} of {}", domain, recordType, records);
        return this;
    }

    @Nullable
    @Override
    public Set<ResourceRecord> getRecords(final QuestionRecord questionRecord) {
        final String domain = questionRecord.getDomainName();
        final Map<RecordType, List<Supplier<List<ResourceRecord>>>> recordsToReturn =
                recordsToReturnByDomain.get(domain);
        LOGGER.debug("Getting {} records for {}", questionRecord.getRecordType(), domain);
        if (recordsToReturn != null) {
            final List<Supplier<List<ResourceRecord>>> recordsForType = recordsToReturn.get(
                    questionRecord.getRecordType());
            if (recordsForType != null && !recordsForType.isEmpty()) {
                List<ResourceRecord> records = recordsForType.remove(0).get();
                LOGGER.debug("Found records {}", records);
                return new HashSet<>(records);
            }
        }
        final Map<RecordType, Supplier<List<ResourceRecord>>> defaultRecords = defaultRecordsByDomain.get(domain);
        if (defaultRecords != null) {
            final Supplier<List<ResourceRecord>> recordsForType = defaultRecords.get(questionRecord.getRecordType());
            if (recordsForType != null) {
                List<ResourceRecord> records = recordsForType.get();
                LOGGER.debug("Found default records {}", records);
                return new HashSet<>(records);
            }
        }
        return null;
    }

    static ResourceRecord createRecord(final String domain, final RecordType recordType, final int ttl,
                                       final String ipAddress) {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(DnsAttribute.IP_ADDRESS, ipAddress);
        return new TestResourceRecord(domain, recordType, RecordClass.IN, ttl, attributes);
    }

    // `ResourceRecordImpl`'s hashCode/equals don't include `attributes`, so it's impossible to include multiple
    // `ResourceRecordImpl`s, with different IPs, in a `Set`.
    private static class TestResourceRecord implements ResourceRecord {
        private final String domainName;
        private final RecordType recordType;
        private final RecordClass recordClass;
        private final int timeToLive;
        private final Map<String, Object> attributes;

        TestResourceRecord(final String domainName, final RecordType recordType,
                           final RecordClass recordClass, final int timeToLive,
                           final Map<String, Object> attributes) {
            this.domainName = domainName;
            this.recordType = recordType;
            this.recordClass = recordClass;
            this.timeToLive = timeToLive;
            this.attributes = new HashMap<>();
            for (final Map.Entry<String, Object> entry : attributes.entrySet()) {
                this.attributes.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }

        @Override
        public String getDomainName() {
            return domainName;
        }

        @Override
        public RecordType getRecordType() {
            return recordType;
        }

        @Override
        public RecordClass getRecordClass() {
            return recordClass;
        }

        @Override
        public int getTimeToLive() {
            return timeToLive;
        }

        @Nullable
        @Override
        public String get(final String id) {
            final Object value = attributes.get(id.toLowerCase());
            return value == null ? null : value.toString();
        }

        @Override
        public String toString() {
            return "MyResourceRecord{" +
                    "domainName='" + domainName + '\'' +
                    ", recordType=" + recordType +
                    ", recordClass=" + recordClass +
                    ", timeToLive=" + timeToLive +
                    ", attributes=" + attributes +
                    '}';
        }
    }
}
