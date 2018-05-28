/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.collector.receiver.zipkin.provider.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.ZipkinReceiverConfig;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.cache.ISpanCache;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.data.ZipkinSpan;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.data.ZipkinTrace;

/**
 * @author wusheng
 */
public class CaffeineSpanCache implements ISpanCache, RemovalListener<String, ZipkinTrace> {
    private Cache<String, ZipkinTrace> inProcessSpanCache;
    private ReentrantLock newTraceLock;

    public CaffeineSpanCache(ZipkinReceiverConfig config) {
        newTraceLock = new ReentrantLock();
        inProcessSpanCache = Caffeine.newBuilder()
            .expireAfterWrite(config.getExpireTime(), TimeUnit.MINUTES)
            .maximumSize(config.getMaxCacheSize())
            .removalListener(this)
            .build();
    }

    /**
     * Zipkin trace finished by the expired rule.
     *
     * @param key
     * @param value
     * @param cause
     */
    @Override
    public void onRemoval(@Nullable String key, @Nullable ZipkinTrace value, @Nonnull RemovalCause cause) {

    }

    @Override public void addSpan(ZipkinSpan span) {
        ZipkinTrace trace = inProcessSpanCache.getIfPresent(span.getTraceId());
        if (trace == null) {
            newTraceLock.lock();
            try {
                trace = new ZipkinTrace();
                inProcessSpanCache.put(span.getTraceId(), trace);
            } finally {
                newTraceLock.unlock();
            }
        }
        trace.addSpan(span);
    }
}
