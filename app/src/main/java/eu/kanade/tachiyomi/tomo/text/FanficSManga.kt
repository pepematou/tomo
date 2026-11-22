package eu.kanade.tachiyomi.tomo.text

import eu.kanade.tachiyomi.source.model.SManga

fun FanficWork.toSManga(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.author = author
    it.description = summary
    it.genre = tags.joinToString()
    it.status = if (meta.complete) SManga.COMPLETED else SManga.ONGOING
    it.memo = meta.toMemo()
    it.initialized = true
}
