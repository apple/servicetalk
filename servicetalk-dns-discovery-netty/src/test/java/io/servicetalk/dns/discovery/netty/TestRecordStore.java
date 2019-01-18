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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final class TestRecordStore implements RecordStore {

    private final Map<String, Map<RecordType, List<List<ResourceRecord>>>> recordsToReturnByDomain = new HashMap<>();
    private final Map<String, Map<RecordType, List<ResourceRecord>>> defaultRecordsByDomain = new HashMap<>();

    private int defaultTtl = 1;

    public TestRecordStore defaultTtl(final int defaultTtl) {
        this.defaultTtl = defaultTtl;
        return this;
    }

    public TestRecordStore setDefaultResponse(String domain, RecordType recordType, String... ipAddresses) {
        return setDefaultResponse(domain, recordType, defaultTtl, ipAddresses);
    }

    public TestRecordStore setDefaultResponse(String domain, RecordType recordType, int ttl, String... ipAddresses) {
        List<ResourceRecord> records = new ArrayList<>();

        for (String ipAddress : ipAddresses) {
            final Map<String, Object> attributes = new HashMap<>();
            attributes.put(DnsAttribute.IP_ADDRESS, ipAddress);
            records.add(new TestResourceRecord(
                    domain, recordType, RecordClass.IN, ttl, attributes));
        }
        return setDefaultResponse(domain, records);
    }

    private TestRecordStore setDefaultResponse(final String domain, final List<ResourceRecord> records) {
        Map<RecordType, List<ResourceRecord>> recordsByType = new HashMap<>();

        for (ResourceRecord record : records) {
            List<ResourceRecord> records2 = recordsByType.computeIfAbsent(record.getRecordType(), k -> new ArrayList<>());
            records2.add(record);
        }

        final Map<RecordType, List<ResourceRecord>> recordsToReturnForDomain = defaultRecordsByDomain.computeIfAbsent(domain, k -> new HashMap<>());
        for (Map.Entry<RecordType, List<ResourceRecord>> entry : recordsByType.entrySet()) {
            final RecordType recordType = entry.getKey();
            recordsToReturnForDomain.put(recordType, entry.getValue());
        }

        return this;
    }

    public TestRecordStore addResponse(String domain, RecordType recordType, String... ipAddresses) {
        return addResponse(domain, recordType, defaultTtl, ipAddresses);
    }

    public TestRecordStore addResponse(String domain, RecordType recordType, int ttl, String... ipAddresses) {
        List<ResourceRecord> records = new ArrayList<>();

        for (String ipAddress : ipAddresses) {
            final Map<String, Object> attributes = new HashMap<>();
            attributes.put(DnsAttribute.IP_ADDRESS, ipAddress);
            records.add(new TestResourceRecord(
                    domain, recordType, RecordClass.IN, ttl, attributes));
        }

        return addResponse(domain, records);
    }

    private TestRecordStore addResponse(String domain, List<ResourceRecord> records) {
        Map<RecordType, List<ResourceRecord>> recordsByType = new HashMap<>();

        for (ResourceRecord record : records) {
            List<ResourceRecord> records2 = recordsByType.computeIfAbsent(record.getRecordType(), k -> new ArrayList<>());
            records2.add(record);
        }

        final Map<RecordType, List<List<ResourceRecord>>> recordsToReturnForDomain = recordsToReturnByDomain.computeIfAbsent(domain, k -> new HashMap<>());
        for (Map.Entry<RecordType, List<ResourceRecord>> entry : recordsByType.entrySet()) {
            final RecordType recordType = entry.getKey();
            final List<List<ResourceRecord>> records2 = recordsToReturnForDomain.computeIfAbsent(recordType, k -> new ArrayList<>());
            records2.add(entry.getValue());
        }

        return this;
    }

    @Nullable
    @Override
    public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
        String domain = questionRecord.getDomainName();
        final Map<RecordType, List<List<ResourceRecord>>> recordsToReturnForDomain = recordsToReturnByDomain.get(domain);
        if (recordsToReturnForDomain != null) {
            final List<List<ResourceRecord>> recordsToReturn = recordsToReturnForDomain.get(questionRecord.getRecordType());
            if (recordsToReturn != null && recordsToReturn.size() > 0) {
                return new HashSet<>(recordsToReturn.remove(0));
            }
        }
        final Map<RecordType, List<ResourceRecord>> defaultRecords = defaultRecordsByDomain.get(domain);
        if (defaultRecords != null) {
            final List<ResourceRecord> recordsToReturn = defaultRecords.get(questionRecord.getRecordType());
            if (recordsToReturn != null) {
                return new HashSet<>(recordsToReturn);
            }
        }

        return null;
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
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
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
        public String get(String id) {
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
