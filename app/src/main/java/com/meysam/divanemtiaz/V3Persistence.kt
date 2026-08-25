package com.meysam.divanemtiaz

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "v3_sessions")
data class V3SessionEntity(
    @PrimaryKey val id: Long,
    val game: String,
    val payload: String,
    val startedAt: Long,
    val updatedAt: Long,
    val finished: Boolean
)

@Entity(tableName = "v3_teams")
data class V3TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val member1: String,
    val member2: String,
    val avatar: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "v3_audit")
data class V3AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val roundIndex: Int,
    val action: String,
    val beforeJson: String,
    val afterJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface V3Dao {
    @Query("SELECT * FROM v3_sessions ORDER BY updatedAt DESC")
    suspend fun sessions(): List<V3SessionEntity>

    @Query("SELECT * FROM v3_sessions WHERE id = :id LIMIT 1")
    suspend fun session(id: Long): V3SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(entity: V3SessionEntity)

    @Query("DELETE FROM v3_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM v3_sessions")
    suspend fun clearSessions()

    @Query("SELECT * FROM v3_teams ORDER BY name COLLATE NOCASE")
    suspend fun teams(): List<V3TeamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTeam(entity: V3TeamEntity): Long

    @Query("DELETE FROM v3_teams WHERE id = :id")
    suspend fun deleteTeam(id: Long)

    @Query("DELETE FROM v3_teams")
    suspend fun clearTeams()

    @Query("SELECT * FROM v3_audit WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun auditForSession(sessionId: Long): List<V3AuditEntity>

    @Query("SELECT * FROM v3_audit ORDER BY timestamp DESC")
    suspend fun allAudit(): List<V3AuditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAudit(entity: V3AuditEntity): Long

    @Query("DELETE FROM v3_audit")
    suspend fun clearAudit()
}

@Database(
    entities = [V3SessionEntity::class, V3TeamEntity::class, V3AuditEntity::class],
    version = 1,
    exportSchema = false
)
abstract class V3Database : RoomDatabase() {
    abstract fun dao(): V3Dao
}

class V3Repository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        V3Database::class.java,
        "divan_emtiaz_v3.db"
    ).build()
    private val dao = db.dao()

    suspend fun save(session: V3Session) {
        session.updatedAt = System.currentTimeMillis()
        dao.saveSession(session.toEntity())
    }

    suspend fun history(): List<V3Session> = dao.sessions().mapNotNull { runCatching { it.toModel() }.getOrNull() }
    suspend fun getSession(id: Long): V3Session? = dao.session(id)?.let { runCatching { it.toModel() }.getOrNull() }
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun teams(): List<V3TeamEntity> = dao.teams()
    suspend fun saveTeam(team: V3TeamEntity): Long = dao.saveTeam(team)
    suspend fun deleteTeam(id: Long) = dao.deleteTeam(id)

    suspend fun audit(sessionId: Long): List<V3AuditEntity> = dao.auditForSession(sessionId)
    suspend fun addAudit(sessionId: Long, roundIndex: Int, action: String, before: V3Round?, after: V3Round?) {
        dao.addAudit(V3AuditEntity(
            sessionId = sessionId,
            roundIndex = roundIndex,
            action = action,
            beforeJson = before?.toJson()?.toString() ?: "",
            afterJson = after?.toJson()?.toString() ?: ""
        ))
    }

    suspend fun exportJson(settings: V3Settings): String {
        val root = JSONObject()
        root.put("schema", 3)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", settings.toJson())
        root.put("teams", JSONArray().apply { dao.teams().forEach { put(it.toJson()) } })
        root.put("sessions", JSONArray().apply { dao.sessions().forEach { put(it.toJson()) } })
        root.put("audit", JSONArray().apply { dao.allAudit().forEach { put(it.toJson()) } })
        return root.toString(2)
    }

    suspend fun importJson(raw: String): V3Settings {
        val root = JSONObject(raw)
        require(root.optInt("schema", 0) == 3) { "نسخهٔ بکاپ پشتیبانی نمی‌شود" }
        dao.clearAudit(); dao.clearSessions(); dao.clearTeams()

        root.optJSONArray("teams")?.let { array ->
            for (i in 0 until array.length()) dao.saveTeam(array.getJSONObject(i).toTeamEntity())
        }
        root.optJSONArray("sessions")?.let { array ->
            for (i in 0 until array.length()) dao.saveSession(array.getJSONObject(i).toSessionEntity())
        }
        root.optJSONArray("audit")?.let { array ->
            for (i in 0 until array.length()) dao.addAudit(array.getJSONObject(i).toAuditEntity())
        }
        return settingsFromJson(root.optJSONObject("settings") ?: JSONObject())
    }
}

object V3SettingsStore {
    private const val PREFS = "divan_emtiaz_v3_settings"
    private const val KEY = "settings"

    fun load(context: Context): V3Settings {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return V3Settings()
        return runCatching { settingsFromJson(JSONObject(raw)) }.getOrDefault(V3Settings())
    }

    fun save(context: Context, settings: V3Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, settings.toJson().toString()).apply()
    }
}

private fun V3Session.toEntity(): V3SessionEntity = V3SessionEntity(
    id = id,
    game = game.key,
    payload = toJson().toString(),
    startedAt = startedAt,
    updatedAt = updatedAt,
    finished = finished
)

private fun V3SessionEntity.toModel(): V3Session = sessionFromJson(JSONObject(payload))

fun V3Session.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("game", game.key)
    teamA?.let { put("teamA", it.toJson()) }
    teamB?.let { put("teamB", it.toJson()) }
    put("players", JSONArray().apply { players.forEach { put(it.toJson()) } })
    put("rounds", JSONArray().apply { rounds.forEach { put(it.toJson()) } })
    put("startedAt", startedAt)
    put("updatedAt", updatedAt)
    put("finished", finished)
    put("revealed", revealed)
    put("menfiTrump", menfiTrump)
    put("menfiHandsTarget", menfiHandsTarget)
    put("settingsSnapshot", settingsSnapshot)
}

private object V3SessionJson {
    fun fromJson(o: JSONObject): V3Session {
        val players = mutableListOf<V3Player>()
        o.optJSONArray("players")?.let { a -> for (i in 0 until a.length()) players += a.getJSONObject(i).toPlayer() }
        val rounds = mutableListOf<V3Round>()
        o.optJSONArray("rounds")?.let { a -> for (i in 0 until a.length()) rounds += a.getJSONObject(i).toRound() }
        return V3Session(
            id = o.optLong("id", System.currentTimeMillis()),
            game = V3GameType.fromKey(o.optString("game")),
            teamA = o.optJSONObject("teamA")?.toTeam(),
            teamB = o.optJSONObject("teamB")?.toTeam(),
            players = players,
            rounds = rounds,
            startedAt = o.optLong("startedAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            finished = o.optBoolean("finished", false),
            revealed = o.optBoolean("revealed", false),
            menfiTrump = o.optString("menfiTrump", "پیک"),
            menfiHandsTarget = o.optInt("menfiHandsTarget", 8),
            settingsSnapshot = o.optString("settingsSnapshot", "")
        )
    }
}

private fun V3Player.toJson() = JSONObject().apply { put("name", name); put("avatar", avatar) }
private fun JSONObject.toPlayer() = V3Player(optString("name"), optInt("avatar"))
private fun V3Team.toJson() = JSONObject().apply {
    put("name", name); put("member1", member1); put("member2", member2); put("avatar", avatar)
}
private fun JSONObject.toTeam() = V3Team(optString("name"), optString("member1"), optString("member2"), optInt("avatar"))

fun V3Round.toJson() = JSONObject().apply {
    put("scores", JSONArray().apply { scores.forEach { put(it) } })
    put("note", note)
    put("meta", JSONObject().apply { meta.forEach { (k, v) -> put(k, v) } })
    put("createdAt", createdAt)
    editedAt?.let { put("editedAt", it) }
}

private fun JSONObject.toRound(): V3Round {
    val scoreList = mutableListOf<Int>()
    optJSONArray("scores")?.let { a -> for (i in 0 until a.length()) scoreList += a.optInt(i) }
    val metaMap = mutableMapOf<String, String>()
    optJSONObject("meta")?.let { m -> m.keys().forEach { key -> metaMap[key] = m.optString(key) } }
    return V3Round(
        scores = scoreList,
        note = optString("note"),
        meta = metaMap,
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        editedAt = if (has("editedAt")) optLong("editedAt") else null
    )
}

private fun V3TeamEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("member1", member1); put("member2", member2); put("avatar", avatar); put("createdAt", createdAt)
}
private fun JSONObject.toTeamEntity() = V3TeamEntity(
    id = optLong("id"), name = optString("name"), member1 = optString("member1"), member2 = optString("member2"),
    avatar = optInt("avatar"), createdAt = optLong("createdAt", System.currentTimeMillis())
)
private fun V3SessionEntity.toJson() = JSONObject().apply {
    put("id", id); put("game", game); put("payload", payload); put("startedAt", startedAt); put("updatedAt", updatedAt); put("finished", finished)
}
private fun JSONObject.toSessionEntity() = V3SessionEntity(
    id = optLong("id"), game = optString("game"), payload = optString("payload"), startedAt = optLong("startedAt"),
    updatedAt = optLong("updatedAt"), finished = optBoolean("finished")
)
private fun V3AuditEntity.toJson() = JSONObject().apply {
    put("id", id); put("sessionId", sessionId); put("roundIndex", roundIndex); put("action", action); put("beforeJson", beforeJson); put("afterJson", afterJson); put("timestamp", timestamp)
}
private fun JSONObject.toAuditEntity() = V3AuditEntity(
    id = optLong("id"), sessionId = optLong("sessionId"), roundIndex = optInt("roundIndex"), action = optString("action"),
    beforeJson = optString("beforeJson"), afterJson = optString("afterJson"), timestamp = optLong("timestamp")
)

fun V3Settings.toJson() = JSONObject().apply {
    put("haptic", haptic); put("keepScreenAwake", keepScreenAwake); put("persianDigits", persianDigits); put("largeText", largeText)
    put("shalamWithJoker", shalamWithJoker); put("shalamTarget", shalamTarget); put("shalamMinBid", shalamMinBid)
    put("shalamAwardContractOnly", shalamAwardContractOnly); put("shalamMultiplier", shalamMultiplier)
    put("shalamYasaEnabled", shalamYasaEnabled); put("shalamYasaThreshold", shalamYasaThreshold); put("shalamSarShalamEnabled", shalamSarShalamEnabled)
    put("menfiHands", menfiHands); put("menfiMinBid", menfiMinBid); put("menfiBaseWin", menfiBaseWin); put("menfiBaseLoss", menfiBaseLoss)
    put("menfiWinStep", menfiWinStep); put("menfiLossStep", menfiLossStep)
    put("menfiWin11", menfiWin11); put("menfiLoss11", menfiLoss11); put("menfiWin12", menfiWin12); put("menfiLoss12", menfiLoss12); put("menfiWin13", menfiWin13); put("menfiLoss13", menfiLoss13)
    put("menfiHideMode", menfiHideMode); put("menfiTieExtraMode", menfiTieExtraMode)
    put("hezartaiiMinPlayers", hezartaiiMinPlayers); put("hezartaiiRounds", hezartaiiRounds); put("hezartaiiZeroPenalty", hezartaiiZeroPenalty); put("hezartaiiHideUntilEnd", hezartaiiHideUntilEnd)
}

private object V3SettingsJson {
    fun fromJson(o: JSONObject) = V3Settings(
        haptic = o.optBoolean("haptic", true), keepScreenAwake = o.optBoolean("keepScreenAwake", true), persianDigits = o.optBoolean("persianDigits", true), largeText = o.optBoolean("largeText", false),
        shalamWithJoker = o.optBoolean("shalamWithJoker", false), shalamTarget = o.optInt("shalamTarget", 1200), shalamMinBid = o.optInt("shalamMinBid", 100),
        shalamAwardContractOnly = o.optBoolean("shalamAwardContractOnly", false), shalamMultiplier = o.optInt("shalamMultiplier", 2), shalamYasaEnabled = o.optBoolean("shalamYasaEnabled", true),
        shalamYasaThreshold = o.optInt("shalamYasaThreshold", 85), shalamSarShalamEnabled = o.optBoolean("shalamSarShalamEnabled", true),
        menfiHands = o.optInt("menfiHands", 8), menfiMinBid = o.optInt("menfiMinBid", 3), menfiBaseWin = o.optInt("menfiBaseWin", 20), menfiBaseLoss = o.optInt("menfiBaseLoss", -10),
        menfiWinStep = o.optInt("menfiWinStep", 0), menfiLossStep = o.optInt("menfiLossStep", 0),
        menfiWin11 = o.optInt("menfiWin11", 20), menfiLoss11 = o.optInt("menfiLoss11", -10), menfiWin12 = o.optInt("menfiWin12", 20), menfiLoss12 = o.optInt("menfiLoss12", -10), menfiWin13 = o.optInt("menfiWin13", 20), menfiLoss13 = o.optInt("menfiLoss13", -10),
        menfiHideMode = o.optInt("menfiHideMode", 0), menfiTieExtraMode = o.optInt("menfiTieExtraMode", 0),
        hezartaiiMinPlayers = o.optInt("hezartaiiMinPlayers", 5), hezartaiiRounds = o.optInt("hezartaiiRounds", 4), hezartaiiZeroPenalty = o.optInt("hezartaiiZeroPenalty", -50), hezartaiiHideUntilEnd = o.optBoolean("hezartaiiHideUntilEnd", false)
    )
}

fun sessionFromJson(o: JSONObject): V3Session = V3SessionJson.fromJson(o)
fun settingsFromJson(o: JSONObject): V3Settings = V3SettingsJson.fromJson(o)
