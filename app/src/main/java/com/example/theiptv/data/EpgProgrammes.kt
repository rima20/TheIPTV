package com.example.theiptv.data

/**
 * Drops overlapping copies of the same title. A later listing is a copy when an
 * earlier one with the same title already covers at least half of it. A listing
 * that only overlaps a little, such as the next episode of a show with the same
 * name, is kept and its start is moved to the end of the earlier listing so the
 * two do not draw on top of each other. Different titles are left untouched.
 */
fun coalesceOverlappingProgrammes(programs: List<EpgProgram>): List<EpgProgram> {
    if (programs.size <= 1) return programs
    val sorted = programs.sortedWith(compareBy({ it.startMs }, { it.endMs }))
    val kept = ArrayList<EpgProgram>(sorted.size)
    // Programmes are visited in start order, and a kept listing of the same title
    // always ends later than the previous one, so the latest anchor is enough.
    val anchorByTitle = HashMap<String, EpgProgram>(sorted.size)
    for (program in sorted) {
        if (program.endMs <= program.startMs) continue
        val title = normalizeProgrammeTitle(program.title)
        val anchor = anchorByTitle[title]
        if (anchor == null || anchor.endMs <= program.startMs) {
            anchorByTitle[title] = program
            kept += program
            continue
        }
        if (anchor.startMs >= program.endMs || program.endMs <= anchor.endMs) continue
        val duration = program.endMs - program.startMs
        val overlap = anchor.endMs - program.startMs
        if (overlap * 2 >= duration) continue
        val clipped = program.copy(startMs = anchor.endMs)
        anchorByTitle[title] = clipped
        kept += clipped
    }
    return kept
}

private val collapsedWhitespace = Regex("\\s+")

private fun normalizeProgrammeTitle(title: String): String {
    return title.trim().replace(collapsedWhitespace, " ").lowercase()
}
