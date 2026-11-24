package com.getmobilestack.kmp.pintar.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccessibilitySemanticsTest {
    @Test fun `headings and status messages expose screen reader semantics`() {
        val heading = SemanticsConfiguration().apply { screenHeadingSemantics() }
        val status = SemanticsConfiguration().apply { statusMessageSemantics() }
        assertTrue(heading.contains(SemanticsProperties.Heading))
        assertEquals(LiveRegionMode.Polite, status[SemanticsProperties.LiveRegion])
    }

    @Test fun `primary and body text colors meet normal text contrast`() {
        fun luminance(rgb: Int): Double {
            fun channel(value: Int): Double {
                val v = value / 255.0
                return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel((rgb shr 16) and 255) + 0.7152 * channel((rgb shr 8) and 255) + 0.0722 * channel(rgb and 255)
        }
        fun contrast(foreground: Int, background: Int): Double {
            val a = luminance(foreground); val b = luminance(background)
            return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        }
        assertTrue(contrast(0x155BC4, 0xFFFFFF) >= 4.5)
        assertTrue(contrast(0x414D5E, 0xFFFFFF) >= 4.5)
        assertTrue(contrast(0x092E69, 0xDCE9FF) >= 4.5)
    }
}
