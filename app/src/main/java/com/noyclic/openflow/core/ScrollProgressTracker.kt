package com.noyclic.openflow.core

/**
 * Tracks a bounded scroll probe. A failed backward probe only means that the
 * view may already be at its top, so it must be followed by a forward scan.
 * The end is confirmed only when forward scrolling is rejected after visible
 * content movement has been observed.
 */
class ScrollProgressTracker(
    private val maxSteps: Int = 24
) {
    enum class Direction { BACKWARD_PROBE, FORWARD }
    enum class Result { CONTINUE, REACHED_END, UNCERTAIN }

    var steps: Int = 0
        private set
    private var observedMovement = false

    fun recordAttempt(): Boolean {
        if (steps >= maxSteps) return false
        steps++
        return true
    }

    fun recordFingerprintChanged(): Result {
        observedMovement = true
        return Result.CONTINUE
    }

    fun recordFingerprintUnchanged(): Result = Result.UNCERTAIN

    fun recordScrollRejected(direction: Direction): Result = when (direction) {
        Direction.BACKWARD_PROBE -> Result.CONTINUE
        Direction.FORWARD -> if (observedMovement) Result.REACHED_END else Result.UNCERTAIN
    }

    fun exhausted(): Boolean = steps >= maxSteps
}
