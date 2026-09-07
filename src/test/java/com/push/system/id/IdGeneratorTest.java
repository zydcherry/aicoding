package com.push.system.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ID生成器测试
 */
class IdGeneratorTest {

    @Test
    void testGenerateId() {
        IdGenerator generator = new IdGenerator(1L);
        long id = generator.generateId(IdGenerator.AccessType.API);

        assertTrue(id > 0);
    }

    @Test
    void testGenerateUniqueIds() {
        IdGenerator generator = new IdGenerator(1L);
        Set<Long> ids = new HashSet<>();

        // 生成1000个ID
        for (int i = 0; i < 1000; i++) {
            long id = generator.generateId(IdGenerator.AccessType.API);
            ids.add(id);
        }

        // 验证所有ID都是唯一的
        assertEquals(1000, ids.size());
    }

    @Test
    void testGenerateIdWithDifferentAccessTypes() {
        IdGenerator generator = new IdGenerator(1L);

        long apiId = generator.generateId(IdGenerator.AccessType.API);
        long kafkaId = generator.generateId(IdGenerator.AccessType.KAFKA_DIRECT);

        assertNotEquals(apiId, kafkaId);
    }

    @Test
    void testParseAccessType() {
        IdGenerator generator = new IdGenerator(1L);

        long apiId = generator.generateId(IdGenerator.AccessType.API);
        long kafkaId = generator.generateId(IdGenerator.AccessType.KAFKA_DIRECT);

        assertEquals(IdGenerator.AccessType.API, IdGenerator.parseAccessType(apiId));
        assertEquals(IdGenerator.AccessType.KAFKA_DIRECT, IdGenerator.parseAccessType(kafkaId));
    }

    @Test
    void testParseTimestamp() {
        IdGenerator generator = new IdGenerator(1L);
        long beforeTime = System.currentTimeMillis();

        long id = generator.generateId(IdGenerator.AccessType.API);

        long afterTime = System.currentTimeMillis();
        long parsedTime = IdGenerator.parseTimestamp(id);

        assertTrue(parsedTime >= beforeTime);
        assertTrue(parsedTime <= afterTime);
    }

    @Test
    void testWorkerIdValidation() {
        // 测试合法的workerId
        assertDoesNotThrow(() -> new IdGenerator(0L));
        assertDoesNotThrow(() -> new IdGenerator(512L));
        assertDoesNotThrow(() -> new IdGenerator(1023L));

        // 测试非法的workerId
        assertThrows(IllegalArgumentException.class, () -> new IdGenerator(-1L));
        assertThrows(IllegalArgumentException.class, () -> new IdGenerator(1024L));
    }

    @Test
    void testDefaultWorkerId() {
        // 测试默认workerId（从IP生成）
        IdGenerator generator = new IdGenerator();
        long id = generator.generateId(IdGenerator.AccessType.API);

        assertTrue(id > 0);
    }

    @Test
    void testConcurrentIdGeneration() throws InterruptedException {
        IdGenerator generator = new IdGenerator(1L);
        int threadCount = 10;
        int idsPerThread = 100;
        Set<Long> ids = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = generator.generateId(IdGenerator.AccessType.API);
                        synchronized (ids) {
                            ids.add(id);
                        }
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证所有ID都是唯一的
        assertEquals(threadCount * idsPerThread, successCount.get());
        assertEquals(threadCount * idsPerThread, ids.size());
    }

    @Test
    void testSequenceOverflow() {
        IdGenerator generator = new IdGenerator(1L);

        // 在同一毫秒内快速生成大量ID
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 3000; i++) {
            long id = generator.generateId(IdGenerator.AccessType.API);
            ids.add(id);
        }

        // 所有ID应该都是唯一的
        assertEquals(3000, ids.size());
    }

    @Test
    void testDifferentWorkersGenerateDifferentIds() {
        IdGenerator generator1 = new IdGenerator(1L);
        IdGenerator generator2 = new IdGenerator(2L);

        long id1 = generator1.generateId(IdGenerator.AccessType.API);
        long id2 = generator2.generateId(IdGenerator.AccessType.API);

        assertNotEquals(id1, id2);
    }

    @Test
    void testIdStructure() {
        IdGenerator generator = new IdGenerator(100L);
        long id = generator.generateId(IdGenerator.AccessType.API);

        // 验证ID为正数
        assertTrue(id > 0);

        // 验证ID是64位
        assertTrue(id <= Long.MAX_VALUE);
    }

    @Test
    void testAccessTypeCode() {
        assertEquals(0, IdGenerator.AccessType.API.getCode());
        assertEquals(1, IdGenerator.AccessType.KAFKA_DIRECT.getCode());
    }

    @Test
    void testMultipleGeneratorsInParallel() throws InterruptedException {
        int generatorCount = 5;
        int idsPerGenerator = 100;
        Set<Long> allIds = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(generatorCount);
        ExecutorService executor = Executors.newFixedThreadPool(generatorCount);

        for (int i = 0; i < generatorCount; i++) {
            final long workerId = i;
            executor.submit(() -> {
                try {
                    IdGenerator generator = new IdGenerator(workerId);
                    for (int j = 0; j < idsPerGenerator; j++) {
                        long id = generator.generateId(IdGenerator.AccessType.API);
                        synchronized (allIds) {
                            allIds.add(id);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证不同生成器生成的ID都是唯一的
        assertEquals(generatorCount * idsPerGenerator, allIds.size());
    }

    @Test
    void testGenerateIdPerformance() {
        IdGenerator generator = new IdGenerator(1L);
        int count = 10000;

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            generator.generateId(IdGenerator.AccessType.API);
        }
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        // 10000个ID应该在1秒内生成完成
        assertTrue(duration < 1000, "生成" + count + "个ID耗时: " + duration + "ms");
    }

    @Test
    void testIdGenerationRate() {
        IdGenerator generator = new IdGenerator(1L);
        Set<Long> ids = new HashSet<>();

        // 快速生成ID
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 10) {
            long id = generator.generateId(IdGenerator.AccessType.API);
            ids.add(id);
        }

        // 10毫秒内应该能生成很多唯一ID
        assertTrue(ids.size() > 100, "10ms内生成了" + ids.size() + "个ID");
    }
}
