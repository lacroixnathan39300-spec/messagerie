package fr.nexus.one

import org.junit.Assert.assertEquals
import org.junit.Test

class FonctionsTest {
    @Test
    fun heuresValides() {
        assertEquals(7 * 60 + 30, lireHeure("07:30"))
        assertEquals(7 * 60 + 30, lireHeure("7h30"))
        assertEquals(22 * 60, lireHeure("22h"))
        assertEquals(0, lireHeure("0:00"))
    }

    @Test
    fun heuresInvalides() {
        assertEquals(-1, lireHeure(""))
        assertEquals(-1, lireHeure("25:00"))
        assertEquals(-1, lireHeure("12:75"))
        assertEquals(-1, lireHeure("midi"))
    }

    @Test
    fun codesMeteo() {
        assertEquals("Ciel dégagé", decrireCode(0))
        assertEquals("Pluie", decrireCode(63))
        assertEquals("Neige", decrireCode(73))
        assertEquals("Orage", decrireCode(95))
        assertEquals("Inconnu", decrireCode(42))
    }
}
