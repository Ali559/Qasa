package alibarznji.qasa.vault.crypto

import java.security.SecureRandom

/**
 * Cryptographically secure password generator.
 *
 * The generator screen used to draw from `kotlin.random.Random`, which is XorWow — a
 * general-purpose PRNG, not a CSPRNG. Its internal state is recoverable from a handful
 * of outputs, so every password it produced should be treated as predictable. This is
 * a password manager: users copy these strings straight into bank and email accounts.
 */
object PasswordGenerator {
    private val random = SecureRandom()

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()_+-="

    /** Above this the result is unlikely to be memorable anyway and costs a lot of entropy to type. */
    const val MAX_LENGTH = 256

    /** The generator screen's default length; every class is on by default. */
    const val DEFAULT_LENGTH = 16

    /** A password with the default settings (the generator screen's, and autofill's strong password). */
    fun generateDefault(): String = generate(DEFAULT_LENGTH)

    /**
     * Generates a password of [length] characters.
     *
     * Guarantees at least one character from every enabled class. Without that, a user
     * who ticks "symbols" can be handed `aaaaaaaaaaaa` (1-in-22 per character) while the
     * UI claims the password contains symbols.
     *
     * @throws IllegalArgumentException if [length] is out of range or no class is enabled
     *   or the length is too short to include one character from every enabled class.
     */
    fun generate(
        length: Int,
        uppercase: Boolean = true,
        lowercase: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
    ): String {
        require(length in 1..MAX_LENGTH) { "length out of range: $length" }

        val classes = buildList {
            if (uppercase) add(UPPERCASE)
            if (lowercase) add(LOWERCASE)
            if (digits) add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        require(classes.isNotEmpty()) { "no character classes selected" }
        require(length >= classes.size) {
            "length $length is too short to include one character from each of ${classes.size} classes"
        }

        val all = classes.joinToString("")
        val out = StringBuilder(length)
        // One character per enabled class first...
        for (chars in classes) {
            out.append(chars[random.nextInt(chars.length)])
        }
        // ...then fill the rest from the combined pool.
        repeat(length - classes.size) {
            out.append(all[random.nextInt(all.length)])
        }

        // Shuffle so the guaranteed characters are not always in a fixed prefix, which
        // would make "the first N characters" a distinguishable pattern.
        val chars = out.toString().toCharArray()
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    /**
     * Non-throwing variant for UI callers, returning `null` when the requested
     * configuration cannot produce a password.
     */
    fun generateOrNull(
        length: Int,
        uppercase: Boolean = true,
        lowercase: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
    ): String? = try {
        generate(length, uppercase, lowercase, digits, symbols)
    } catch (_: IllegalArgumentException) {
        null
    }
}
