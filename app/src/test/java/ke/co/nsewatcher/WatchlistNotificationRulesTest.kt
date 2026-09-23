package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistNotificationRulesTest {
    @Test fun watchedCompaniesGetAutomaticNewsAndCorporateRulesWhenEnabled() {
        val rules = automaticWatchlistAlertRules(setOf(" kcb ", "SCOM"), emptyList(), newsEnabled = true, corporateEnabled = true)
        assertEquals(4, rules.size)
        assertEquals(setOf("KCB", "SCOM"), rules.map { it.symbol }.toSet())
        assertEquals(setOf(AlertType.NEWS, AlertType.CORPORATE_ACTION), rules.map { it.type }.toSet())
    }

    @Test fun explicitRulePreventsDuplicateAutomaticRuleForSameCompanyAndType() {
        val configured = listOf(PriceAlert("mine", "KCB", AlertType.NEWS, null, true))
        val rules = automaticWatchlistAlertRules(setOf("KCB"), configured, newsEnabled = true, corporateEnabled = true)
        assertEquals(1, rules.size)
        assertEquals(AlertType.CORPORATE_ACTION, rules.single().type)
    }

    @Test fun disabledCategoriesDoNotCreateAutomaticRules() {
        assertTrue(automaticWatchlistAlertRules(setOf("KCB"), emptyList(), newsEnabled = false, corporateEnabled = false).isEmpty())
    }
}
