package app.dumpkin.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileRulesTest {
    private val base = ServerProfile(id = "a", name = "A", port = 25565)

    @Test fun the_default_profile_is_valid() = assertNull(ProfileRules.validate(base, emptyList()))

    @Test fun each_rule_rejects_what_it_should() {
        fun msg(p: ServerProfile, others: List<ServerProfile> = emptyList()) = ProfileRules.validate(p, others)
        assertTrue(msg(base.copy(name = " "))!!.contains("name", true))
        assertTrue(msg(base.copy(name = "x".repeat(41)))!!.contains("40"))
        assertTrue(msg(base.copy(motd = "x".repeat(121)))!!.contains("120"))
        assertTrue(msg(base.copy(port = 1023))!!.contains("port", true))
        assertTrue(msg(base.copy(port = 65536))!!.contains("port", true))
        assertTrue(msg(base, listOf(ServerProfile(id = "b", name = "B", port = 25565)))!!.contains("already uses"))
        assertNull(msg(base, listOf(base))) // a server never clashes with itself
        assertTrue(msg(base.copy(maxPlayers = 1001))!!.contains("players", true))
        assertTrue(msg(base.copy(viewDistance = 1))!!.contains("view", true))
        assertTrue(msg(base.copy(simulationDistance = 17))!!.contains("simulation", true))
        assertTrue(msg(base.copy(memoryLimitMb = 100))!!.contains("memory", true))
        assertNull(msg(base.copy(memoryLimitMb = 0)))
        assertTrue(msg(base.copy(cpuThreads = 65))!!.contains("cpu", true))
    }

    @Test fun values_pumpkin_would_reset_its_config_over_are_never_accepted() {
        // Pumpkin panics on view_distance outside 2..=64 and resets the whole config on bad types.
        assertNotNull(ProfileRules.validate(base.copy(viewDistance = 0), emptyList()))
        assertNotNull(ProfileRules.validate(base.copy(viewDistance = 25), emptyList()))
    }

    @Test fun next_free_port_skips_taken_ones() {
        assertEquals(25565, ProfileRules.nextFreePort(emptySet()))
        assertEquals(25567, ProfileRules.nextFreePort(setOf(25565, 25566)))
    }

    @Test fun patching_changes_only_the_fields_given() {
        val p = ProfilePatch.apply(base, JSONObject("""{"motd":"hi","port":25600,"gameMode":"CREATIVE","onlineMode":false,"compression":"off"}"""))
        assertEquals(base.copy(motd = "hi", port = 25600, gameMode = GameMode.Creative, onlineMode = false, compression = Compression.Off), p)
        assertEquals(base, ProfilePatch.apply(base, JSONObject("{}")))
    }

    @Test fun unknown_keys_are_ignored_and_the_id_cannot_be_changed() {
        val p = ProfilePatch.apply(base, JSONObject("""{"id":"hacked","evil":1}"""))
        assertEquals("a", p.id)
    }

    @Test fun engine_id_can_be_set_and_cleared() {
        val set = ProfilePatch.apply(base, JSONObject("""{"engineId":"0.2.2"}"""))
        assertEquals("0.2.2", set.engineId)
        assertNull(ProfilePatch.apply(set, JSONObject("""{"engineId":null}""")).engineId)
        assertNull(ProfilePatch.apply(set, JSONObject("""{"engineId":""}""")).engineId)
    }

    @Test fun wrong_types_are_rejected_not_coerced() {
        val bad = listOf(
            """{"port":"25565"}""", """{"port":2.5}""", """{"name":5}""", """{"onlineMode":"true"}""",
            """{"gameMode":3}""", """{"gameMode":"Nope"}""", """{"compression":true}""",
        )
        for (json in bad) {
            try {
                ProfilePatch.apply(base, JSONObject(json)); fail("accepted $json")
            } catch (_: ProfilePatch.Invalid) {
            }
        }
    }
}
