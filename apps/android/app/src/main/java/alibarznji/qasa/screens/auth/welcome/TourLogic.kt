package alibarznji.qasa.screens.auth.welcome

import kotlin.math.abs

/** Where a move on the first-launch tour lands. */
sealed interface TourMove {
    /** Show this page. */
    data class Page(val index: Int) : TourMove

    /** Past the last page: the tour is over (the name screen follows). */
    data object Done : TourMove

    /** Before the first page: nothing happens. */
    data object Stay : TourMove
}

/**
 * The tour's decisions, kept apart from Compose so they can be tested (iOS
 * `OnboardingTourView.move` / `handleGesture`).
 */
object TourLogic {
    /** Vault, sign-up, code, health. */
    const val PAGE_COUNT = 4

    /** iOS: a drag wider than 40pt is a swipe. */
    const val SWIPE_DISTANCE_DP = 40f

    /** iOS: a touch that moved 20pt or more up or down is neither a tap nor a swipe. */
    const val TAP_VERTICAL_SLOP_DP = 20f

    fun move(page: Int, delta: Int, count: Int = PAGE_COUNT): TourMove {
        val next = page + delta
        return when {
            next >= count -> TourMove.Done
            next < 0 -> TourMove.Stay
            else -> TourMove.Page(next)
        }
    }

    fun isLast(page: Int, count: Int = PAGE_COUNT): Boolean = page == count - 1

    /**
     * What a finished touch on the page does: +1 forward, -1 back, 0 nothing. A swipe
     * towards the leading edge goes forward, towards the trailing edge back; a tap on the
     * trailing half goes forward, on the leading half back. Both mirror in RTL. Positions
     * are in pixels, x measured from the left edge of a page [width] wide.
     */
    fun gestureDelta(
        startX: Float,
        endX: Float,
        dy: Float,
        width: Float,
        rtl: Boolean,
        swipePx: Float,
        tapSlopPx: Float,
    ): Int {
        // Along the reading direction: positive moves towards the trailing edge.
        val dx = (endX - startX) * if (rtl) -1f else 1f
        if (dx < -swipePx) return 1
        if (dx > swipePx) return -1
        if (abs(dy) >= tapSlopPx) return 0
        val onRight = endX > width / 2f
        val trailing = if (rtl) !onRight else onRight
        return if (trailing) 1 else -1
    }
}
