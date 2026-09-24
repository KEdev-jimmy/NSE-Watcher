package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeNotificationsTest {
    @Test fun fillNotificationDestinationCarriesExactOrderId() {
        val target = PracticeNotifications.destination(
            action = PracticeNotifications.practiceAction(),
            extraOrderId = "order-abc-123",
            pathOrderId = null
        )

        assertEquals("order-abc-123", target?.orderId)
    }

    @Test fun dataPathCanRecoverOrderIdWhenExtraIsMissing() {
        val target = PracticeNotifications.destination(
            action = PracticeNotifications.practiceAction(),
            extraOrderId = null,
            pathOrderId = "order:path_2"
        )

        assertEquals("order:path_2", target?.orderId)
    }

    @Test fun unrelatedIntentCannotOpenPractice() {
        assertNull(
            PracticeNotifications.destination(
                action = "ke.co.nsewatcher.UNRELATED",
                extraOrderId = "order-1",
                pathOrderId = "order-1"
            )
        )
    }

    @Test fun invalidOrderIdFallsBackToGenericPracticeForLegacySafety() {
        val target = PracticeNotifications.destination(
            action = PracticeNotifications.practiceAction(),
            extraOrderId = "../../bad order",
            pathOrderId = null
        )

        assertEquals("", target?.orderId)
    }
}
