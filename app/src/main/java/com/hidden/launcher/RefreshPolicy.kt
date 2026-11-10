package com.hidden.launcher

/**
 * Why a home-screen refresh was requested. Used to decide whether a refresh is
 * actually needed (e.g. returning from a launched app with nothing changed is not).
 */
enum class RefreshReason {
    /** First load after process start / activity creation. */
    STARTUP,

    /** Regular onResume (returning from a launched app, screen on, etc.). */
    RESUME,

    /** Returning from the app picker / settings screen. */
    SETTINGS_RETURN,

    /** A package-added / removed / changed / replaced broadcast was received. */
    PACKAGE_CHANGE,

    /** Explicit user action inside the launcher (group create/merge/rename, ...). */
    USER_ACTION
}

/** What the home screen should show. Loading and "no apps selected" are distinct. */
enum class HomeUiState {
    LOADING,
    EMPTY,
    GRID
}

object HomeState {

    /**
     * State for the very first frame.
     *
     * An empty selection is a genuine empty state (show the "choose apps" screen),
     * while any non-empty selection must render loading/skeleton tiles - never the
     * "No apps selected" message.
     */
    fun initialState(selected: Set<String>): HomeUiState =
        if (selected.isEmpty()) HomeUiState.EMPTY else HomeUiState.LOADING

    /**
     * Cheap change detector for "is a refresh actually needed?".
     * Covers everything the home screen renders: selected packages and groups.
     */
    fun selectionSignature(selected: Set<String>, groups: List<AppGroup>): String {
        val selection = selected.sorted().joinToString(",")
        val grouped = groups.sortedBy { it.id }.joinToString(";") { group ->
            "${group.id}:${group.name}:${group.packageNames.joinToString(",")}"
        }
        return "$selection|$grouped"
    }
}

/**
 * Serializes home-screen refreshes.
 *
 * - Only one refresh may run at a time; requests arriving meanwhile are coalesced into
 *   a single follow-up refresh instead of spawning overlapping PackageManager work.
 * - Every refresh gets a monotonically increasing token so late results can be
 *   recognized as outdated and discarded.
 * - A plain resume is skipped when nothing relevant changed (same selection signature
 *   and no package-change broadcast), which avoids a full refresh every time the user
 *   returns from a launched app.
 *
 * Pure logic (no Android dependencies) so it is unit testable.
 */
class RefreshScheduler {

    private var nextToken = 0
    private var inFlightToken: Int? = null
    private var pendingReason: RefreshReason? = null
    private var pendingPackagesChanged = false
    private var lastSignature: String? = null

    /** @return a token when a refresh should run, or null when suppressed/coalesced. */
    @Synchronized
    fun request(
        reason: RefreshReason,
        signature: String,
        packagesChanged: Boolean = false
    ): Int? {
        if (inFlightToken != null) {
            pendingReason = higherPriority(pendingReason, reason)
            pendingPackagesChanged = pendingPackagesChanged || packagesChanged
            return null
        }
        if (shouldSkip(reason, signature, packagesChanged)) return null
        return begin()
    }

    /**
     * Report that the refresh identified by [token] finished.
     *
     * @param signature the selection signature that is now on screen.
     * @param painted false when the result was outdated and therefore *not* shown; in
     *   that case the "last shown" signature is kept so the next resume still runs.
     *
     * @return a token for a coalesced follow-up refresh that must now be started, or
     *   null when nothing further is needed. Completions for outdated tokens are
     *   ignored so a stale refresh can never drive the state machine.
     */
    @Synchronized
    fun complete(token: Int, signature: String, painted: Boolean = true): Int? {
        if (token != inFlightToken) return null
        inFlightToken = null

        val pending = pendingReason
        val pendingChanged = pendingPackagesChanged
        pendingReason = null
        pendingPackagesChanged = false

        if (pending != null) {
            // A resume/settings-return queued while we were refreshing is redundant
            // when the refresh just painted exactly the current state.
            val redundant = painted &&
                !pendingChanged &&
                (pending == RefreshReason.RESUME || pending == RefreshReason.SETTINGS_RETURN) &&
                (lastSignature == null || lastSignature == signature)
            if (!redundant) return begin()
            lastSignature = signature
            return null
        }
        if (painted) lastSignature = signature
        return null
    }

    /** True while [token] is the refresh currently running. */
    @Synchronized
    fun isCurrent(token: Int): Boolean = token == inFlightToken

    @Synchronized
    fun isIdle(): Boolean = inFlightToken == null && pendingReason == null

    /** Force the next resume to run (e.g. after a package change). */
    @Synchronized
    fun invalidateSignature() {
        lastSignature = null
    }

    private fun shouldSkip(reason: RefreshReason, signature: String, packagesChanged: Boolean): Boolean {
        if (reason != RefreshReason.RESUME && reason != RefreshReason.SETTINGS_RETURN) return false
        if (packagesChanged) return false
        val known = lastSignature ?: return false
        return known == signature
    }

    private fun begin(): Int {
        val token = ++nextToken
        inFlightToken = token
        return token
    }

    private fun higherPriority(current: RefreshReason?, next: RefreshReason): RefreshReason {
        if (current == null) return next
        return if (rank(next) >= rank(current)) next else current
    }

    private fun rank(reason: RefreshReason): Int = when (reason) {
        RefreshReason.RESUME -> 0
        RefreshReason.SETTINGS_RETURN -> 1
        RefreshReason.STARTUP -> 2
        RefreshReason.PACKAGE_CHANGE -> 3
        RefreshReason.USER_ACTION -> 4
    }
}
