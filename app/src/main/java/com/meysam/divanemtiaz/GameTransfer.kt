package com.meysam.divanemtiaz

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest

enum class TransferKind(val key: String, val title: String) {
    GAME("game", "بازی امتیازی"), LEAGUE("league", "لیگ"),
    PANTO("panto", "پانتومیم"), SPY("spy", "جاسوس"), MAFIA("mafia", "راوی مافیا")
}

data class TransferTarget(val kind: TransferKind, val id: Long)

class GameTransfer(private val store: KeyValueStore) {
    private val games = GameRepository(store)
    private val panto = PantoRepository(store)
    private val spy = SpyRepository(store)
    private val mafia = MafiaRepository(store)

    fun export(target: TransferTarget, pro: Boolean): String {
        require(target.kind != TransferKind.MAFIA || pro)
        val out = JSONObject().put("format", FORMAT).put("version", VERSION).put("kind", target.kind.key)
        val data = when (target.kind) {
            TransferKind.GAME -> GameCodec.encodeSession(requireNotNull(games.session(target.id)))
            TransferKind.LEAGUE -> {
                val league = requireNotNull(games.league(target.id))
                val sessions = references(league).mapNotNull(games::session).associateBy { it.id }
                // History can be cleared independently of the league. Export the games that still exist.
                for (i in league.matches.indices) {
                    val match = league.matches[i]
                    league.matches[i] = match.copy(games = match.games.filter(sessions::containsKey))
                }
                league.pastGames.retainAll(sessions.keys)
                out.put("sessions", JSONArray(sessions.values.map(GameCodec::encodeSession)))
                GameCodec.encodeLeague(league)
            }
            TransferKind.PANTO -> PantoCodec.encode(requireNotNull(panto.match(target.id)))
            TransferKind.SPY -> SpyCodec.encode(requireNotNull(spy.game(target.id)))
            TransferKind.MAFIA -> {
                val game = requireNotNull(mafia.game(target.id))
                val context = mafiaContext(store, game.id) ?: JSONObject().apply {
                    mafia.scenario(game.extra["scenario"])?.let { put("scenario", it.toJson()) }
                    put("roles", JSONObject(MafiaRoleRegistry.encode(mafia.roleDefs())))
                }
                out.put("context", context)
                MafiaCodec.encodeGame(game)
            }
        }
        return out.put("data", data).toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }
    }

    fun import(text: String, pro: Boolean): TransferTarget {
        val envelope = parse(text)
        val kind = kind(envelope)
        require(kind != TransferKind.MAFIA || pro)
        val data = envelope.getJSONObject("data")
        val key = "game_transfer_import_" + digest(envelope.toString().toByteArray(Charsets.UTF_8))
        store.get(key)?.toLongOrNull()?.let { id ->
            if (exists(TransferTarget(kind, id))) return TransferTarget(kind, id)
        }

        val ids = games.index() + games.leagueIndex() + panto.index() + spy.index() + mafia.index() + games.roster().map { it.id }
        var lastId = maxOf(System.currentTimeMillis(), ids.maxOrNull() ?: 0)
        require(lastId < Long.MAX_VALUE - 100_000L)
        fun newId() = ++lastId

        val batch = TransferStore(store)
        val repo = GameRepository(batch)
        val people = repo.roster().toMutableList()
        val identities = people.mapTo(HashSet(), TeamIdentity::key)
        fun add(name: String, avatar: Int, team: Boolean = false, members: List<String> = emptyList()) {
            if (name.isBlank()) return
            require(name.length <= 512)
            val entry = RosterEntry(newId(), name, safeAvatar(avatar), team, members)
            if (identities.add(TeamIdentity.key(entry))) people += entry
        }
        fun addSides(sides: List<Side>, teams: Boolean) {
            sides.forEach { side ->
                add(side.name, side.avatar, teams, side.members)
                if (teams) side.members.forEach { add(it, 0) }
            }
        }
        fun saveSession(d: JSONObject, id: Long, leagueId: Long, expected: GameType? = null) {
            val gameType = gameType(d)
            require(expected == null || expected == gameType)
            sanitizeAvatars(d.getJSONArray("sides"), "avatar")
            d.put("id", id).put("league", leagueId)
            if (leagueId == 0L) {
                if (d.optLong("leagueSideA", -1) in 0L..1L) d.put("label", "")
                d.remove("leagueSideA")
            }
            val g = GameCodec.decodeSession(d)
            require(g.sides.size in gameType.minSides..gameType.maxSides)
            addSides(g.sides, g.game.isTeamGame)
            repo.save(g)
        }

        val id = newId()
        require(data.getLong("id") > 0)
        when (kind) {
            TransferKind.GAME -> saveSession(data, id, 0)
            TransferKind.LEAGUE -> {
                val type = gameType(data)
                require(type.isTeamGame)
                sanitizeAvatars(data.getJSONArray("teams"), "avatar")
                data.put("avatar", safeAvatar(data.optInt("avatar", -1)))
                val league = GameCodec.decodeLeague(data)
                validateLeague(league)
                val sessions = objects(envelope.getJSONArray("sessions"))
                val byId = sessions.associateBy { it.getLong("id") }
                val references = references(league)
                require(byId.size == sessions.size && byId.keys == references && references.all { it > 0 })
                val mapped = references.associateWith { newId() }
                for ((old, session) in byId) saveSession(session, mapped.getValue(old), id, type)
                objects(data.getJSONArray("matches")).forEach { match ->
                    match.put("games", JSONArray(longs(match.getJSONArray("games")).map(mapped::getValue)))
                }
                data.put("id", id).put("pastGames", JSONArray(league.pastGames.map(mapped::getValue)))
                val copy = GameCodec.decodeLeague(data)
                addSides(copy.teams, true)
                repo.saveLeague(copy)
                batch.write(mapOf(leagueKey(id) to "1"))
            }
            TransferKind.PANTO -> {
                sanitizeAvatars(data.getJSONArray("teams"), "a")
                val m = PantoCodec.decode(data.put("id", id))
                require(m.teams.size in PantoEngine.MIN_TEAMS..PantoEngine.MAX_TEAMS && m.current in m.teams.indices)
                require(m.turn == null || m.turn!!.team in m.teams.indices)
                m.teams.forEach { team ->
                    add(team.name, team.avatar, !m.mode.individual, team.members)
                    team.members.forEach { add(it, 0) }
                }
                add(m.referee, 0)
                PantoRepository(batch).save(m)
            }
            TransferKind.SPY -> {
                val avatars = data.optJSONArray("avatars") ?: JSONArray()
                data.put("avatars", JSONArray(List(avatars.length()) { safeAvatar(avatars.optInt(it)) }))
                val game = SpyCodec.decode(data.put("id", id))
                require(game.players.size in SpyEngine.MIN_PLAYERS..SpyEngine.MAX_PLAYERS)
                require(game.spies.isNotEmpty() && game.spies.all { it in game.players.indices } && game.spies.size < game.players.size)
                require(game.revealIndex in 0..game.players.size && game.asker in game.players.indices && game.starter in game.players.indices)
                if (game.named) game.players.forEachIndexed { i, name -> add(name, game.avatars.getOrElse(i) { 0 }) }
                SpyRepository(batch).save(game)
            }
            TransferKind.MAFIA -> {
                val context = envelope.getJSONObject("context")
                context.optJSONObject("scenario")?.let { MafiaScenario.fromJson(it) }
                sanitizeAvatars(data.getJSONArray("players"), "avatar")
                val snapshots = data.optJSONArray("snapshots") ?: JSONArray()
                for (entry in objects(snapshots)) {
                    val json = entry.getString("json")
                    checkJson(json)
                    val history = JSONObject(json).put("id", id)
                    history.remove("snapshots")
                    sanitizeAvatars(history.getJSONArray("players"), "avatar")
                    entry.put("json", history.toString())
                }
                val game = MafiaCodec.decodeGame(data.put("id", id))
                require(game.players.size in 1..100 && game.players.map { it.id }.toSet().size == game.players.size)
                game.players.forEach { add(it.name, it.avatar) }
                MafiaRepository(batch).save(game)
                batch.write(mapOf(mafiaKey(id) to context.toString()))
            }
        }
        repo.saveRoster(people)
        batch.write(mapOf(key to id.toString()))
        batch.commit()
        return TransferTarget(kind, id)
    }

    fun exists(target: TransferTarget): Boolean = when (target.kind) {
        TransferKind.GAME -> games.session(target.id) != null
        TransferKind.LEAGUE -> games.league(target.id) != null
        TransferKind.PANTO -> panto.match(target.id) != null
        TransferKind.SPY -> spy.game(target.id) != null
        TransferKind.MAFIA -> mafia.game(target.id) != null
    }

    companion object {
        const val MIME = "application/vnd.divan.game+json"
        const val MAX_BYTES = 4 * 1024 * 1024
        private const val FORMAT = "divan-game-transfer"
        private const val VERSION = 1
        private fun leagueKey(id: Long) = "game_transfer_league_$id"
        private fun mafiaKey(id: Long) = "game_transfer_mafia_$id"

        fun importedLeague(store: KeyValueStore, id: Long): Boolean = id > 0 && store.get(leagueKey(id)) == "1"
        fun mafiaContext(store: KeyValueStore, id: Long): JSONObject? =
            store.get(mafiaKey(id))?.let { runCatching { JSONObject(it) }.getOrNull() }

        fun inspect(text: String): TransferKind = kind(parse(text))

        private fun parse(text: String): JSONObject {
            checkJson(text)
            val envelope = JSONObject(text)
            require(envelope.getString("format") == FORMAT && envelope.getInt("version") == VERSION)
            return envelope
        }

        private fun kind(o: JSONObject): TransferKind = TransferKind.values().first { it.key == o.getString("kind") }

        // Bound parser recursion before a file from another device reaches the JSON decoder.
        private fun checkJson(text: String) {
            require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            var depth = 0
            var quoted = false
            var escaped = false
            for (c in text) {
                if (escaped) escaped = false
                else if (quoted && c == '\\') escaped = true
                else if (c == '"') quoted = !quoted
                else if (!quoted) {
                    if (c == '[' || c == '{') require(++depth <= 40)
                    if (c == ']' || c == '}') require(--depth >= 0)
                }
            }
            require(depth == 0 && !quoted)
        }

        fun read(input: InputStream): ByteArray {
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return output.toByteArray()
                require(output.size() + count <= MAX_BYTES)
                output.write(buffer, 0, count)
            }
        }

        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

        private fun references(league: League): Set<Long> = (league.matches.flatMap { it.games } + league.pastGames).toSet()

        private fun validateLeague(league: League) {
            require(league.teams.size in 2..256 && league.matches.size <= 2048)
            val fixtures = league.matches.associateBy { it.id }
            require(fixtures.size == league.matches.size)
            for (match in league.matches) {
                require(match.feederA == -1 || fixtures[match.feederA]?.id?.let { it < match.id } == true)
                require(match.feederB == -1 || fixtures[match.feederB]?.id?.let { it < match.id } == true)
                require(match.teamA in league.teams.indices || match.feederA != -1)
                require(match.teamB in league.teams.indices || match.teamB == LeagueMatch.BYE || match.feederB != -1)
            }
            val allGames = league.matches.flatMap { it.games } + league.pastGames
            require(allGames.toSet().size == allGames.size)
        }

        private fun gameType(j: JSONObject): GameType = GameType.values().first { it.key == j.getString("game") }
        private fun objects(a: JSONArray): List<JSONObject> = List(a.length()) { a.getJSONObject(it) }
        private fun longs(a: JSONArray): List<Long> = List(a.length()) { a.getLong(it) }
        private fun sanitizeAvatars(a: JSONArray, key: String) = objects(a).forEach { it.put(key, safeAvatar(it.optInt(key))) }
        private fun safeAvatar(value: Int): Int = if (value >= PhotoStore.BASE) 0 else value
    }
}

private class TransferStore(private val base: KeyValueStore) : KeyValueStore {
    private val changes = LinkedHashMap<String, String?>()
    override fun get(key: String): String? = if (changes.containsKey(key)) changes[key] else base.get(key)
    override fun write(changes: Map<String, String?>) { this.changes.putAll(changes) }
    fun commit() = base.write(changes)
}
