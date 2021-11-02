// FIR_IDENTICAL
// SKIP_TXT
@Suppress("UNCHECKED_CAST")
fun <T> getMetric(metricId: MetricId<T>): Metric<T>? {
    val value: T = when (metricId) {
        is MetricId.Duration -> asLong()
        is MetricId.Counter -> asInt()
    } as? T ?: return null
    return Metric(metricId, value)
}

fun asLong(): Long = 1L
fun asInt(): Int = 1

sealed class MetricId<E> {
    abstract val name: String
    data class Duration(override val name: String) : MetricId<Long>()
    data class Counter(override val name: String) : MetricId<Int>()
}

data class Metric<F>(val id: MetricId<F>, val value: F)
