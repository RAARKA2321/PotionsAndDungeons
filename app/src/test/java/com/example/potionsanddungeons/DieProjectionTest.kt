package com.example.potionsanddungeons

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DieProjectionTest {
    @Test
    fun quadrilateralCenterIsProjectedWithCameraDepth() {
        val corners = listOf(Offset(20f, 10f), Offset(80f, 10f), Offset(70f, 70f), Offset(30f, 70f))
        val center = projectDieFace(corners, listOf(4f, 4f, 6f, 6f), 0.5f, 0.5f)
        assertEquals(50f, center.x, 0.001f)
        assertEquals(46f, center.y, 0.001f)
    }

    @Test
    fun boldFourStaysInsideATiltedTetrahedronFace() {
        val corners = listOf(Offset(20f, 5f), Offset(10f, 70f), Offset(90f, 60f))
        val depths = listOf(4f, 6f, 3f)
        val edges = corners.zipWithNext() + (corners.last() to corners.first())
        val center = projectDieFace(corners, depths, 0.5f, 0.64f)
        fun side(start: Offset, end: Offset, point: Offset): Float =
            (end.x - start.x) * (point.y - start.y) - (end.y - start.y) * (point.x - start.x)
        robotoDiceDigits[4].forEach { contour ->
            for (index in 0 until contour.size step 2) {
                val point = projectDieFace(corners, depths, 0.5f + contour[index] * 0.00042f, 0.64f + contour[index + 1] * 0.00042f)
                assertTrue(edges.all { (start, end) -> side(start, end, point) * side(start, end, center) > 0 })
            }
        }
    }
}
