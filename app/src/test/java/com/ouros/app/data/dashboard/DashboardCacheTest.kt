package com.ouros.app.data.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashboardCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cachedDashboardIsOnlyReadableByItsFarm() = runBlocking {
        val cache = DashboardCache(temporaryFolder.root)
        val dashboard = DashboardResponse(
            title = "Farm telemetry",
            charts = listOf(
                DashboardChartResponse(
                    id = "water",
                    title = "Water",
                    renderAs = "html",
                    html = "<p>usage</p>"
                )
            )
        )

        cache.save(farmId = 101L, dashboard = dashboard)

        assertEquals(dashboard, cache.read(farmId = 101L))
        assertNull(cache.read(farmId = 202L))
    }
}
