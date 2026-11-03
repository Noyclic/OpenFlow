package com.noyclic.openflow.core

/**
 * Distinguishes a verified end-of-list from a scroll action that did not move
 * the visible content. A no-op on the first attempt is ambiguous and must pause.
 */
class ScrollProgressTracker(
    private val maxSteps: Int = 24,
    private val maxUnchangedAfterMovement: Int = 1
) {
    var steps: Int = 0
        private set
    private var unchangedAfterMovement = 0
    private var observedMovement = false

    enum class Result { CONTINUE, REACHED_END, UNCERTAIN }

    fun recordAttempt(): Boolean {
        if (steps >= maxSteps) return false
        steps++
        return true
    }

    fun recordFingerprintChanged(): Result {
        observedMovement = true
        unchangedAfterMovement = 0
        return Result.CONTINUE
    }

    fun recordFingerprintUnchanged(): Result {
        if (!observedMovement) return Result.UNCERTAIN
        unchangedAfterMovement++
        return if (unchangedAfterMovement >= maxUnchangedAfterMovement) Result.REACHED_END
        else Result.CONTINUE
    }

    fun exhausted(): Boolean = steps >= maxSteps
}
