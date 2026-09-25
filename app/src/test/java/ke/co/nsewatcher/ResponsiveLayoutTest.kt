package ke.co.nsewatcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {
    @Test
    fun fiveTabsStayInlineWhenTheyFitAtNormalPhoneScale() {
        assertFalse(
            shouldScrollTabs(
                widthDp = 393f,
                fontScale = 1f,
                optionCount = 5,
                minimumOptionWidthDp = 70f
            )
        )
    }

    @Test
    fun fiveTabsScrollAtLargeTextOnSamePhone() {
        assertTrue(
            shouldScrollTabs(
                widthDp = 393f,
                fontScale = 1.2f,
                optionCount = 5,
                minimumOptionWidthDp = 70f
            )
        )
    }

    @Test
    fun narrowPhoneScrollsFiveTabs() {
        assertTrue(
            shouldScrollTabs(
                widthDp = 320f,
                fontScale = 1f,
                optionCount = 5,
                minimumOptionWidthDp = 70f
            )
        )
    }

    @Test
    fun tabletKeepsFiveTabsInlineAtNormalScale() {
        assertFalse(
            shouldScrollTabs(
                widthDp = 800f,
                fontScale = 1f,
                optionCount = 5,
                minimumOptionWidthDp = 70f
            )
        )
    }

    @Test
    fun fourSecondaryTabsRemainInlineOnRedmiClassWidth() {
        assertFalse(
            shouldScrollTabs(
                widthDp = 393f,
                fontScale = 1f,
                optionCount = 4,
                minimumOptionWidthDp = 72f
            )
        )
    }
}
