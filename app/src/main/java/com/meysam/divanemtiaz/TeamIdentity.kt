package com.meysam.divanemtiaz

/** Membership identifies a team across different member orders and old labels. */
object TeamIdentity {
    fun person(name: String): String = SearchText.norm(name).filterNot { it.isWhitespace() }

    fun members(names: List<String>): List<String> =
        names.map(::person).filter { it.isNotBlank() }.distinct().sorted()

    fun key(name: String, names: List<String>): String {
        val members = members(names)
        return if (members.size < 2) "team:${person(name)}"
        else members.joinToString("", prefix = "members:") { "${it.length}:$it" }
    }

    fun key(entry: RosterEntry): String =
        if (entry.isTeam) key(entry.name, entry.members) else "person:${person(entry.name)}"

    fun key(side: Side): String = key(side.name, side.members)

    /** Blank rosters are supported for quick scoring; a named pair must have two different people. */
    fun validCardTeam(side: Side): Boolean = side.members.isEmpty() ||
        (side.members.size == 2 && members(side.members).size == 2)
}
