package alibarznji.qasa.screens.auth.pair

import alibarznji.qasa.R
import alibarznji.qasa.network.DeviceLinkCheckResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expectations follow iOS `PairExistingVaultView.applyPairing`. */
class PairFlowLogicTest {
    private val id = "3F2504E0-4F89-11D3-9A0C-0305E82C3301"

    private fun check(eligible: Boolean, tier: String, error: String? = null) =
        DeviceLinkCheckResponse(eligible, tier, devices_used = 1, device_limit = 1, error = error)

    @Test fun uuidMustBeCanonicalAndIsLowercased() {
        assertEquals(id.lowercase(), parseAccountUuid("  $id \n"))
        assertNull(parseAccountUuid("1-2-3-4-5"))
        assertNull(parseAccountUuid("123456"))
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
        assertEquals(PairStep.Intro, PairStep.AccountId.previous())
        assertEquals(PairStep.AccountId, PairStep.NeedsPremium.previous())
        assertNull(PairStep.Success.previous())
        assertNull(PairStep.Adopted.previous())
    }
}
