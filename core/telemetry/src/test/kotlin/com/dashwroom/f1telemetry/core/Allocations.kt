package com.dashwroom.f1telemetry.core

import java.lang.management.ManagementFactory

/** Bytes allocated on the calling thread by [block] (HotSpot's per-thread TLAB accounting). */
object Allocations {
    private val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    init {
        bean.isThreadAllocatedMemoryEnabled = true
    }

    fun current(): Long = bean.getThreadAllocatedBytes(Thread.currentThread().id)

    inline fun measure(block: () -> Unit): Long {
        val baselineStart = current()
        val baselineEnd = current() // cost of the probe itself
        val start = current()
        block()
        val end = current()
        return (end - start) - (baselineEnd - baselineStart)
    }
}
