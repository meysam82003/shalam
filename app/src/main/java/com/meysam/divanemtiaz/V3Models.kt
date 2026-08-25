package com.meysam.divanemtiaz

enum class V3GameType(val key: String, val title: String) {
    SHALAM("shalam", "شلم"),
    MENFI("menfi", "منفی"),
    HEZARTAII("hezartaii", "هزارتایی");

    companion object {
        fun fromKey(key: String): V3GameType = values().firstOrNull { it.key == key } ?: SHALAM
    }
}

data class V3Player(
    var name: String,
    var avatar: Int = 0
)

data class V3Team(
    var name: String,
    var member1: String = "",
    var member2: String = "",
    var avatar: Int = 0
)

data class V3Round(
    var scores: MutableList<Int>,
    var note: String = "",
    var meta: MutableMap<String, String> = mutableMapOf(),
    var createdAt: Long = System.currentTimeMillis(),
    var editedAt: Long? = null
)

data class V3Session(
    val id: Long = System.currentTimeMillis(),
    val game: V3GameType,
    var teamA: V3Team? = null,
    var teamB: V3Team? = null,
    var players: MutableList<V3Player> = mutableListOf(),
    var rounds: MutableList<V3Round> = mutableListOf(),
    var startedAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var finished: Boolean = false,
    var revealed: Boolean = false,
    var menfiTrump: String = "پیک",
    var menfiHandsTarget: Int = 8,
    var settingsSnapshot: String = ""
)

data class V3Settings(
    var haptic: Boolean = true,
    var keepScreenAwake: Boolean = true,
    var persianDigits: Boolean = true,
    var largeText: Boolean = false,

    var shalamWithJoker: Boolean = false,
    var shalamTarget: Int = 1200,
    var shalamMinBid: Int = 100,
    var shalamAwardContractOnly: Boolean = false,
    var shalamMultiplier: Int = 2,
    var shalamYasaEnabled: Boolean = true,
    var shalamYasaThreshold: Int = 85,
    var shalamSarShalamEnabled: Boolean = true,

    var menfiHands: Int = 8,
    var menfiMinBid: Int = 3,
    var menfiBaseWin: Int = 20,
    var menfiBaseLoss: Int = -10,
    var menfiWinStep: Int = 0,
    var menfiLossStep: Int = 0,
    var menfiWin11: Int = 20,
    var menfiLoss11: Int = -10,
    var menfiWin12: Int = 20,
    var menfiLoss12: Int = -10,
    var menfiWin13: Int = 20,
    var menfiLoss13: Int = -10,
    var menfiHideMode: Int = 0,
    var menfiTieExtraMode: Int = 0,

    var hezartaiiMinPlayers: Int = 5,
    var hezartaiiRounds: Int = 4,
    var hezartaiiZeroPenalty: Int = -50,
    var hezartaiiHideUntilEnd: Boolean = false
)

data class ShalamHandResult(
    val contractScore: Int,
    val opponentScore: Int,
    val contractActual: Int,
    val succeeded: Boolean,
    val yasaApplied: Boolean,
    val shalamApplied: Boolean
)

object ShalamEngineV3 {
    const val TOTAL = 165

    fun readyBids(minBid: Int): List<Int> = buildList {
        var value = minBid.coerceIn(5, 160)
        while (value <= 160) {
            add(value)
            value += 5
        }
        add(165)
    }.distinct()

    fun score(
        contract: Int,
        opponentActual: Int,
        settings: V3Settings,
        declaredShalam: Boolean = false
    ): ShalamHandResult {
        require(contract in 1..TOTAL)
        require(opponentActual in 0..TOTAL)
        val contractActual = TOTAL - opponentActual
        val succeeded = contractActual >= contract
        val yasa = !succeeded && settings.shalamYasaEnabled && contractActual < settings.shalamYasaThreshold
        val fullSweep = contractActual == TOTAL
        val shalam = fullSweep && (declaredShalam || contract == TOTAL)
        val contractScore = when {
            yasa -> -TOTAL
            !succeeded -> -contract
            shalam -> TOTAL * settings.shalamMultiplier.coerceAtLeast(1)
            settings.shalamAwardContractOnly -> contract
            else -> contractActual
        }
        return ShalamHandResult(contractScore, opponentActual, contractActual, succeeded, yasa, shalam)
    }
}

data class MenfiHandResult(
    val teamAScore: Int,
    val teamBScore: Int,
    val teamAMade: Boolean,
    val teamBMade: Boolean
)

object MenfiEngineV3 {
    fun scoreForBid(bid: Int, made: Boolean, settings: V3Settings): Int {
        require(bid in settings.menfiMinBid..13)
        if (bid >= 11) {
            return when (bid) {
                11 -> if (made) settings.menfiWin11 else settings.menfiLoss11
                12 -> if (made) settings.menfiWin12 else settings.menfiLoss12
                else -> if (made) settings.menfiWin13 else settings.menfiLoss13
            }
        }
        val delta = (bid - settings.menfiMinBid).coerceAtLeast(0)
        return if (made) settings.menfiBaseWin + delta * settings.menfiWinStep
        else settings.menfiBaseLoss - delta * kotlin.math.abs(settings.menfiLossStep)
    }

    fun score(
        bidA: Int,
        tricksA: Int,
        bidB: Int,
        tricksB: Int,
        settings: V3Settings
    ): MenfiHandResult {
        require(tricksA in 0..13 && tricksB in 0..13)
        val madeA = tricksA >= bidA
        val madeB = tricksB >= bidB
        return MenfiHandResult(
            scoreForBid(bidA, madeA, settings),
            scoreForBid(bidB, madeB, settings),
            madeA,
            madeB
        )
    }
}

object HezartaiiEngineV3 {
    fun normalize(raw: Int, settings: V3Settings): Int =
        if (raw == 0) settings.hezartaiiZeroPenalty else raw

    fun totals(players: List<V3Player>, rounds: List<V3Round>): List<Int> =
        players.indices.map { index -> rounds.sumOf { it.scores.getOrElse(index) { 0 } } }

    fun ranking(players: List<V3Player>, rounds: List<V3Round>): List<Pair<V3Player, Int>> {
        val totals = totals(players, rounds)
        return players.mapIndexed { index, player -> player to totals[index] }
            .sortedByDescending { it.second }
    }
}

object ScoreGapV3 {
    fun teamGap(nameA: String, scoreA: Int, nameB: String, scoreB: Int): String = when {
        scoreA == scoreB -> "دو گروه برابرند."
        scoreA > scoreB -> "$nameB، ${scoreA - scoreB} امتیاز عقب‌تر از $nameA است."
        else -> "$nameA، ${scoreB - scoreA} امتیاز عقب‌تر از $nameB است."
    }

    fun individualGap(ranking: List<Pair<V3Player, Int>>): List<String> {
        if (ranking.isEmpty()) return emptyList()
        val first = ranking.first().second
        return ranking.mapIndexed { index, pair ->
            if (index == 0) "${pair.first.name} صدرنشین است."
            else "${pair.first.name}، ${first - pair.second} امتیاز عقب‌تر از نفر اول است."
        }
    }
}

object LeaguePlannerV3 {
    fun roundRobin(teamIds: List<Long>): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()
        for (i in teamIds.indices) for (j in i + 1 until teamIds.size) result += teamIds[i] to teamIds[j]
        return result
    }
}
