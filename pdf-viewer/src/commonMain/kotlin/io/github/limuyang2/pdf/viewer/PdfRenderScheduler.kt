package io.github.limuyang2.pdf.viewer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** One worker per view. All calls and callbacks use the owning UI coroutine context. */
internal class PdfRenderScheduler<K, V>(
    scope: CoroutineScope,
    private val cached: (K) -> V?,
    private val store: (K, V) -> Unit,
    private val load: suspend (K) -> V,
) {
    private class Demand<K, V>(
        val keys: List<K>,
        val priority: Int,
        val result: (K, V) -> Unit,
        val error: (K, Throwable) -> Unit,
        val completed: MutableSet<K> = mutableSetOf(),
    )

    private val demands = mutableMapOf<Any, Demand<K, V>>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var closed = false
    private val worker = scope.launch {
        for (signal in wake) {
            while (true) {
                val next = demands.values.sortedBy { it.priority }.firstNotNullOfOrNull { demand ->
                    demand.keys.firstOrNull { it !in demand.completed }
                } ?: break
                try {
                    val value = cached(next) ?: load(next).also { store(next, it) }
                    demands.values.toList().forEach { demand ->
                        if (next in demand.keys && demand.completed.add(next)) demand.result(next, value)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    demands.values.toList().forEach { demand ->
                        if (next in demand.keys && demand.completed.add(next)) demand.error(next, failure)
                    }
                }
                // Give input/layout a chance to replace queued work between native calls.
                yield()
            }
        }
    }

    fun update(owner: Any, keys: List<K>, priority: Int, result: (K, V) -> Unit, error: (K, Throwable) -> Unit) {
        if (closed) return
        val demand = Demand(keys.distinct(), priority, result, error)
        demands[owner] = demand
        demand.keys.forEach { key ->
            cached(key)?.let { value ->
                demand.completed += key
                result(key, value)
            }
        }
        wake.trySend(Unit)
    }

    fun remove(owner: Any) {
        demands.remove(owner)
    }

    fun close() {
        closed = true
        demands.clear()
        worker.cancel()
        wake.close()
    }
}
