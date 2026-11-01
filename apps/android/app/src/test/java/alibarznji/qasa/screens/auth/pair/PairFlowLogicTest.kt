package alibarznji.qasa.screens.auth.pair

import alibarznji.qasa.R
import alibarznji.qasa.network.DeviceLinkCheckResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expectations follow iOS `PairExistingVaultView.applyPairing` and `PairingSessionStore`. */
class PairFlowLogicTest {
    private val id = "3F2504E0-4F89-11D3-9A0C-0305E82C3301"

    private fun check(eligible: Boolean, tier: String, error: String? = null) =
        DeviceLinkCheckResponse(eligible, tier, devices_used = 1, device_limit = 1, error = error)

    @Test fun uuidMustBeCanonicalAndIsLowercased() {
        assertEquals(id.lowercase(), parseAccountUuid("  $id \n"))
        assertNull(parseAccountUuid("1-2-3-4-5"))
        assertNull(parseAccountUuid("123456"))
    }

    @Test fun accountIdWinsOverCode() {
        val store = PairingSessionStore()
        val code = store.publish("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        assertEquals(id.lowercase(), resolvePairTarget(id, code, store))
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", resolvePairTarget("not a uuid", " $code ", store))
        assertNull(resolvePairTarget("", "000000x", store))
    }

    @Test fun codesExpireAfterTenMinutes() {
        var now = 0L
        val store = PairingSessionStore(now = { now })
        val code = store.publish(id)
        assertEquals(6, code.length)
        assertEquals(code, store.currentPublishedCode(id))
        now = 600_000L
        assertNull(store.resolve(code))
        assertNull(store.currentPublishedCode(id))
    }

    @Test fun linkCheckMapsToIosOutcomes() {
        assertEquals(LinkOutcome.Linked, classifyLinkCheck(check(true, "free")))
        assertEquals(LinkOutcome.NeedsPremium, classifyLinkCheck(check(false, "free", "premium_required")))
        assertEquals(LinkOutcome.NeedsPremium, classifyLinkCheck(check(false, "free", "device_limit_exceeded")))
        assertEquals(
            LinkOutcome.Error(R.string.pair_limitexceeded),
            classifyLinkCheck(check(false, "premium", "device_limit_exceeded")),
        )
    }

    @Test fun backWalksStepsThenCancels() {
        assertNull(PairStep.Intro.previous())
        assertEquals(PairStep.Intro, PairStep.EnterCode.previous())
        assertEquals(PairStep.EnterCode, PairStep.NeedsPremium.previous())
        assertNull(PairStep.Success.previous())
    }
}
