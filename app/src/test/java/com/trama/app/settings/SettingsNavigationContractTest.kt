package com.trama.app.settings

import com.trama.app.ui.screens.SettingsSection
import com.trama.app.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationContractTest {

    @Test
    fun `settings routes are unique and round trip`() {
        val routes = SettingsSection.entries.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
        SettingsSection.entries.forEach { section ->
            assertEquals(section, SettingsSection.fromRoute(section.route))
        }
    }

    @Test
    fun `all basic settings destinations remain distinct`() {
        val basic = setOf(
            SettingsSection.CAPTURE_MEMORY,
            SettingsSection.AGENDA_CALENDARS,
            SettingsSection.PRIVACY_DATA,
            SettingsSection.APPEARANCE
        )

        assertEquals(4, basic.size)
        assertTrue(SettingsSection.IA !in basic)
        assertTrue(SettingsSection.ADVANCED !in basic)
        assertNotEquals(SettingsSection.ROOT, SettingsSection.fromRoute("capture-memory"))
    }

    @Test
    fun `home reachability contract includes every public utility screen`() {
        assertEquals(
            setOf(
                Routes.SETTINGS,
                Routes.SEARCH,
                Routes.CHAT,
                Routes.AGENDA,
                Routes.RECORDINGS_LIST
            ),
            Routes.HOME_REACHABLE_DESTINATIONS
        )
    }
}
