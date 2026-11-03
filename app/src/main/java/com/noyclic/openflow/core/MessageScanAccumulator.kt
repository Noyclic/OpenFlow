package com.noyclic.openflow.core

/** Counts distinct long text blocks across overlapping scroll pages; stores fingerprints only. */
class MessageScanAccumulator(private val maxEntries: Int = 512) {
    private val fingerprints = LinkedHashSet<String>()
    fun reset() = fingerprints.clear()
    fun record(keys: Set<String>) {
        for (key in keys) {
            if (fingerprints.size >= maxEntries) break
            if (key.isNotBlank()) fingerprints.add(key)
        }
    }
    val uniqueCount: Int get() = fingerprints.size
}