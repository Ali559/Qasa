package alibarznji.qasa.license

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Ed25519 signature verification — RFC 8032 §5.1.7.
 *
 * The platform has no Ed25519 before API 33 and Conscrypt's availability is not
 * guaranteed on every `minSdk = 26` device, so `java.security.Signature` cannot be
 * relied on for the one operation a forged license token depends on. This is a
 * self-contained pure-Kotlin verifier: it runs identically on every device and in
 * JVM unit tests, and it is verify-only, so no secret material is ever handled here.
 */
internal object Ed25519 {
    /** Not `BigInteger.TWO`: that field is API 33, and this runs on every minSdk 26 device. */
    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val P: BigInteger = TWO.pow(255) - BigInteger.valueOf(19)
    private val L: BigInteger =
        TWO.pow(252) + BigInteger("27742317777372353535851937790883648493")
    private val D: BigInteger = BigInteger.valueOf(-121665)
        .multiply(BigInteger.valueOf(121666).modInverse(P))
        .mod(P)
    private val TWO_D: BigInteger = D.add(D).mod(P)
    private val SQRT_M1: BigInteger = TWO.modPow(
        P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)),
        P,
    )

    private val BX: BigInteger =
        BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202")
    private val BY: BigInteger =
        BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960")

    /** Extended homogeneous coordinates (X : Y : Z : T) with `x = X/Z`, `y = Y/Z`. */
    private class Point(
        val x: BigInteger,
        val y: BigInteger,
        val z: BigInteger,
        val t: BigInteger,
    )

    private val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    private val BASE = Point(
        BX,
        BY,
        BigInteger.ONE,
        BX.multiply(BY).mod(P),
    )

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != KEY_SIZE || signature.size != SIGNATURE_SIZE) return false
        return try {
            val rBytes = signature.copyOfRange(0, KEY_SIZE)
            val s = littleEndianToBigInteger(signature.copyOfRange(KEY_SIZE, SIGNATURE_SIZE))
            // A non-canonical S is malleable and must never be accepted.
            if (s >= L) return false

            val a = decodePoint(publicKey) ?: return false
            val r = decodePoint(rBytes) ?: return false

            val digest = MessageDigest.getInstance("SHA-512")
            digest.update(rBytes)
            digest.update(publicKey)
            digest.update(message)
            val k = littleEndianToBigInteger(digest.digest()).mod(L)

            val left = scalarMultiply(s, BASE)
            val right = add(r, scalarMultiply(k, a))
            MessageDigest.isEqual(compress(left), compress(right))
        } catch (_: Exception) {
            false
        }
    }

    /** add-2008-hwcd-3. */
    private fun add(p1: Point, p2: Point): Point {
        val a = p1.y.subtract(p1.x).multiply(p2.y.subtract(p2.x)).mod(P)
        val b = p1.y.add(p1.x).multiply(p2.y.add(p2.x)).mod(P)
        val c = p1.t.multiply(TWO_D).multiply(p2.t).mod(P)
        val d = p1.z.add(p1.z).multiply(p2.z).mod(P)
        val e = b.subtract(a).mod(P)
        val f = d.subtract(c).mod(P)
        val g = d.add(c).mod(P)
        val h = b.add(a).mod(P)
        return Point(
            e.multiply(f).mod(P),
            g.multiply(h).mod(P),
            f.multiply(g).mod(P),
            e.multiply(h).mod(P),
        )
    }

    /** dbl-2008-hwcd. */
    private fun double(p1: Point): Point {
        val a = p1.x.multiply(p1.x).mod(P)
        val b = p1.y.multiply(p1.y).mod(P)
        val c = p1.z.add(p1.z).multiply(p1.z).mod(P)
        val h = a.add(b).mod(P)
        val sum = p1.x.add(p1.y).mod(P)
        val e = h.subtract(sum.multiply(sum)).mod(P)
        val g = a.subtract(b).mod(P)
        val f = c.add(g).mod(P)
        return Point(
            e.multiply(f).mod(P),
            g.multiply(h).mod(P),
            f.multiply(g).mod(P),
            e.multiply(h).mod(P),
        )
    }

    private fun scalarMultiply(scalar: BigInteger, point: Point): Point {
        var acc = IDENTITY
        val reduced = scalar.mod(L)
        for (bit in 254 downTo 0) {
            acc = double(acc)
            if (reduced.testBit(bit)) acc = add(acc, point)
        }
        return acc
    }

    /** Recovers a point from its 32-byte compressed encoding, rejecting non-canonical y. */
    private fun decodePoint(encoded: ByteArray): Point? {
        val copy = encoded.copyOf()
        val signBit = (copy[KEY_SIZE - 1].toInt() and 0x80) != 0
        copy[KEY_SIZE - 1] = (copy[KEY_SIZE - 1].toInt() and 0x7F).toByte()
        val y = littleEndianToBigInteger(copy)
        if (y >= P) return null

        val y2 = y.multiply(y).mod(P)
        // x² = (y² - 1) / (d·y² + 1)
        val numerator = y2.subtract(BigInteger.ONE).mod(P)
        val denominator = D.multiply(y2).add(BigInteger.ONE).mod(P)
        if (denominator.signum() == 0) return null
        val x2 = numerator.multiply(denominator.modInverse(P)).mod(P)
        if (x2.signum() == 0) return if (signBit) null else Point(BigInteger.ZERO, y, BigInteger.ONE, BigInteger.ZERO)

        var x = x2.modPow(P.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), P)
        if (x.multiply(x).mod(P) != x2) x = x.multiply(SQRT_M1).mod(P)
        if (x.multiply(x).mod(P) != x2) return null
        if (x.testBit(0) != signBit) x = P.subtract(x)
        if (x.signum() == 0 && signBit) return null

        return Point(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    private fun compress(point: Point): ByteArray {
        val zInv = point.z.modInverse(P)
        val x = point.x.multiply(zInv).mod(P)
        val y = point.y.multiply(zInv).mod(P)
        val out = bigIntegerToLittleEndian32(y)
        if (x.testBit(0)) {
            out[KEY_SIZE - 1] = (out[KEY_SIZE - 1].toInt() or 0x80).toByte()
        }
        return out
    }

    private fun littleEndianToBigInteger(bytes: ByteArray): BigInteger =
        BigInteger(1, bytes.reversedArray())

    private fun bigIntegerToLittleEndian32(value: BigInteger): ByteArray {
        val out = ByteArray(KEY_SIZE)
        val bigEndian = value.toByteArray()
        var src = bigEndian.size - 1
        var dst = 0
        while (src >= 0 && dst < KEY_SIZE) {
            out[dst++] = bigEndian[src--]
        }
        return out
    }

    const val KEY_SIZE = 32
    const val SIGNATURE_SIZE = 64
}
