package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import com.lumen.coacervation.engine.interaction.ElasticGroupNode
import com.lumen.coacervation.engine.interaction.ElasticMotionGroupPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Long-press elasticity of grouped settings: a row inside a section card has no surface of its own, so the engine must
 * promote the gesture to the group card, never stop at the row (which would move clipped inside the card) and never
 * climb into the page.
 */
class GroupElasticityTest {
    private val row = ElasticGroupNode(hasSurface = false, fillsWindow = false, containerOnly = false, childCount = 2)
    private val group = ElasticGroupNode(hasSurface = true, fillsWindow = false, containerOnly = false, childCount = 4)
    private val page = ElasticGroupNode(hasSurface = false, fillsWindow = true, containerOnly = false, childCount = 8)

    @Test fun rowInAGroupMovesTheGroup() {
        assertEquals(1, ElasticMotionGroupPolicy.promotionDepth(listOf(row, group, page)))
    }

    @Test fun rowUnderAnAccordionStillMovesTheGroup() {
        val content = ElasticGroupNode(hasSurface = false, fillsWindow = false, containerOnly = false, childCount = 2)
        val accordion = ElasticGroupNode(hasSurface = false, fillsWindow = false, containerOnly = false, childCount = 2)
        assertEquals(3, ElasticMotionGroupPolicy.promotionDepth(listOf(row, content, accordion, group, page)))
    }

    @Test fun standAloneCardStaysItsOwnUnit() {
        val card = ElasticGroupNode(hasSurface = true, fillsWindow = false, containerOnly = false, childCount = 2)
        val column = ElasticGroupNode(hasSurface = false, fillsWindow = false, containerOnly = false, childCount = 6)
        assertEquals(0, ElasticMotionGroupPolicy.promotionDepth(listOf(card, column, page)))
    }
}
