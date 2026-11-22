@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.limuyang2.pdf.viewer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfRenderSchedulerTest {
    @Test
    fun prioritizesPreviewsAndDeduplicatesSharedRequests() = runTest {
        val loaded = mutableListOf<String>()
        val delivered = mutableListOf<String>()
        val cache = mutableMapOf<String, String>()
        val scheduler = PdfRenderScheduler<String, String>(this, cache::get, { k, v -> cache[k] = v }, {
            loaded += it
            it
        })
        scheduler.update("page1", listOf("tile", "other"), 1, { _, v -> delivered += "one:$v" }, { _, e -> throw e })
        scheduler.update("page2", listOf("tile"), 1, { _, v -> delivered += "two:$v" }, { _, e -> throw e })
        scheduler.update("preview", listOf("preview"), 0, { _, v -> delivered += v }, { _, e -> throw e })
        runCurrent()
        assertEquals(listOf("preview", "tile", "other"), loaded)
        assertEquals(listOf("preview", "one:tile", "two:tile", "one:other"), delivered)
        scheduler.close()
    }

    @Test
    fun viewportChangeDropsQueuedWorkButCachesCompletedInFlightResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        val loaded = mutableListOf<String>()
        val delivered = mutableListOf<String>()
        val cache = mutableMapOf<String, String>()
        val scheduler = PdfRenderScheduler<String, String>(this, cache::get, { k, v -> cache[k] = v }, {
            loaded += it
            if (it == "inflight") gate.await()
            it
        })
        scheduler.update("viewport", listOf("inflight", "stale"), 1, { _, v -> delivered += v }, { _, e -> throw e })
        runCurrent()
        scheduler.update("viewport", listOf("current"), 1, { _, v -> delivered += v }, { _, e -> throw e })
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("inflight", "current"), loaded)
        assertEquals(listOf("current"), delivered)
        assertEquals("inflight", cache["inflight"])
        scheduler.update("viewport", listOf("inflight"), 1, { _, v -> delivered += v }, { _, e -> throw e })
        runCurrent()
        assertEquals(listOf("current", "inflight"), delivered)
        assertEquals(2, loaded.size)
        scheduler.close()
    }

    @Test
    fun newPreviewPreemptsQueuedTilesAndDisposedPagesReceiveNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val loaded = mutableListOf<String>()
        val delivered = mutableListOf<String>()
        val scheduler = PdfRenderScheduler<String, String>(this, { null }, { _, _ -> }, {
            loaded += it
            if (it == "first") gate.await()
            it
        })
        scheduler.update("visible", listOf("first", "second"), 1,
            { _, value -> delivered += value }, { _, error -> throw error })
        scheduler.update("disposed", listOf("obsolete"), 1,
            { _, value -> delivered += value }, { _, error -> throw error })
        runCurrent()
        scheduler.remove("disposed")
        scheduler.update("preview", listOf("preview"), 0,
            { _, value -> delivered += value }, { _, error -> throw error })
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "preview", "second"), loaded)
        assertEquals(loaded, delivered)
        scheduler.close()
    }

    @Test
    fun failedTilesDoNotBlockOtherRequestsOrRetryInALoop() = runTest {
        val failures = mutableListOf<String>()
        val delivered = mutableListOf<String>()
        val scheduler = PdfRenderScheduler<String, String>(this, { null }, { _, _ -> }, {
            if (it == "bad") error("render failed")
            it
        })
        scheduler.update("viewport", listOf("bad", "good"), 1, { _, v -> delivered += v }, { k, _ -> failures += k })
        runCurrent()
        assertEquals(listOf("bad"), failures)
        assertEquals(listOf("good"), delivered)
        scheduler.close()
    }

    @Test
    fun closeCancelsWorkerAndPreventsLateDelivery() = runTest {
        val gate = CompletableDeferred<Unit>()
        val delivered = mutableListOf<String>()
        val scheduler = PdfRenderScheduler<String, String>(this, { null }, { _, _ -> }, {
            gate.await()
            it
        })
        scheduler.update("viewport", listOf("tile"), 1, { _, v -> delivered += v }, { _, e -> throw e })
        runCurrent()
        scheduler.close()
        gate.complete(Unit)
        runCurrent()
        assertTrue(delivered.isEmpty())
    }
}
