package com.meysam.divanemtiaz

/** A fresh server response opens a preview for the remaining server time, at most 600 seconds.
 * Kept only in memory: process death/reboot requires an online check, never a fresh 10 minutes.
 * Monotonic elapsed time includes background/sleep; refresh cannot extend the same lease.
 */
class MafiaTrialLease {
    private var uid = 0L
    private var expiry = 0L
    private var deadline = 0L

    fun accept(user: Long, endsAt: Long, seconds: Long, elapsedMs: Long) {
        if (user <= 0 || endsAt <= 0 || seconds <= 0) { clear(); return }
        val candidate = elapsedMs + seconds.coerceAtMost(600) * 1000
        deadline = if (uid == user && expiry == endsAt && deadline > 0) minOf(deadline, candidate) else candidate
        uid = user
        expiry = endsAt
    }

    fun remaining(user: Long, endsAt: Long, elapsedMs: Long): Long =
        if (uid != user || expiry != endsAt || deadline <= elapsedMs) 0 else (deadline - elapsedMs + 999) / 1000

    fun clear() { uid = 0; expiry = 0; deadline = 0 }
}
