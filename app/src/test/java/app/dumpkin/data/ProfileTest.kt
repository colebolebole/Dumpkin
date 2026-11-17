package app.dumpkin.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTest {
    @Test fun resourceSettingsRoundTrip() {
        val p = ServerProfile(
            name = "x", memoryLimitMb = 1536, memoryAction = MemoryAction.Restart, cpuThreads = 3, autoRestart = true,
        )
        assertEquals(p, ServerProfile.fromJson(JSONObject(p.toJson().toString())))
    }

    @Test fun oldProfilesGetSafeDefaults() {
        val old = JSONObject().put("id", "abc").put("name", "Legacy").put("port", 25570)
        val p = ServerProfile.fromJson(old)
        assertEquals(0, p.memoryLimitMb)
        assertEquals(MemoryAction.Warn, p.memoryAction)
        assertEquals(0, p.cpuThreads)
        assertEquals(false, p.autoRestart)
        assertEquals(true, p.lightPing)
        assertEquals(Compression.Fast, p.compression)
        assertEquals(true, p.fetchSkins)
    }
}
