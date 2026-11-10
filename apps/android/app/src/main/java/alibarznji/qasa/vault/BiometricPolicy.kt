package alibarznji.qasa.vault

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * How long biometrics may stand in for the master password.
 *
 * Once biometric unlock is on, the master password is still asked for every
 * [MASTER_PASSWORD_INTERVAL]: a password that is never typed is a password that gets
 * forgotten, and it is the only way back in when the gate key is invalidated. A biometric
 * unlock never resets the clock; only a vault created, unlocked, or re-keyed with the
 * password does.
 */
object BiometricPolicy {
    val MASTER_PASSWORD_INTERVAL: Duration = 14.days

    /** [MASTER_PASSWORD_INTERVAL] in whole days, for `auth.unlock.masterPasswordDueFmt`. */
    val MASTER_PASSWORD_INTERVAL_DAYS: Int = MASTER_PASSWORD_INTERVAL.inWholeDays.toInt()

    /**
     * Whether the master password must be typed before biometrics may unlock again.
     *
     * Fails closed: no record yet is due, and so is a record in the future (the clock was
     * rolled back, which would otherwise stretch the interval indefinitely).
     */
    fun isMasterPasswordDue(lastMasterPasswordUnlock: Long?, now: Long): Boolean {
        val last = lastMasterPasswordUnlock ?: return true
        if (last <= 0L || last > now) return true
        return now - last >= MASTER_PASSWORD_INTERVAL.inWholeMilliseconds
    }
}
