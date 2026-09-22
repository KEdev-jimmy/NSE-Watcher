package ke.co.nsewatcher

internal data class ArticleBlock(val text: String, val heading: Boolean = false)

internal object ArticlePresentation {
    fun text(raw: String): String {
        var value = raw.replace(Regex("<(script|style)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex("<h[1-6][^>]*>([\\s\\S]*?)</h[1-6]>", RegexOption.IGNORE_CASE)) { "\n\n## ${it.groupValues[1]}\n\n" }
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(?:p|div|li|blockquote|section|article)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</?[a-zA-Z][^>]*>"), "")
        val entities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "ndash" to "–", "mdash" to "—", "hellip" to "…")
        value = value.replace(Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")) { match ->
            val entity = match.groupValues[1]
            if (entity.startsWith("#")) {
                val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
                code?.takeIf { Character.isValidCodePoint(it) && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it)) } ?: match.value
            } else entities[entity] ?: match.value
        }
        return value.replace("\r\n", "\n").replace(Regex("[\\t ]+"), " ").replace(Regex("\n[ ]+"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun blocks(raw: String): List<ArticleBlock> = text(raw).split(Regex("\n\\s*\n")).mapNotNull { paragraph ->
        val value = paragraph.trim()
        if (value.isBlank()) null else ArticleBlock(value.replace(Regex("^#{1,6}\\s+"), ""), Regex("^#{1,6}\\s+").containsMatchIn(value))
    }
    private fun normalized(raw: String) = text(raw).replace(Regex("\\s+"), " ").trim()
    fun hasDistinctBody(item: NewsItem): Boolean = normalized(item.body).let { it.isNotEmpty() && it != normalized(item.summary) && it != normalized(item.title) }
    fun keyPoints(item: NewsItem): List<String> {
        if (!hasDistinctBody(item)) return emptyList()
        return text(item.summary).split(Regex("(?<=[.!?])\\s+(?=[A-Z0-9])|\n+"))
            .map { it.trim().removePrefix("• ") }.filter { it.length in 15..450 && it != text(item.title) }.distinct().take(3)
    }
    fun company(item: NewsItem, companies: List<Stock>): Stock? {
        if (item.symbol.isNotBlank()) return companies.firstOrNull { it.symbol.equals(item.symbol.trim(), true) }
        if (item.companyName.isBlank()) return null
        return companies.filter { it.name.trim().equals(item.companyName.trim(), true) }.singleOrNull()
    }
    fun merge(original: NewsItem, fresh: NewsItem): NewsItem {
        if (original.id != fresh.id) return original
        return fresh.copy(title = fresh.title.ifBlank { original.title }, body = fresh.body.ifBlank { original.body },
            summary = fresh.summary.ifBlank { original.summary }, source = fresh.source.ifBlank { original.source },
            publishedAt = fresh.publishedAt.ifBlank { original.publishedAt }, category = fresh.category.ifBlank { original.category },
            symbol = fresh.symbol.ifBlank { original.symbol }, companyName = fresh.companyName.ifBlank { original.companyName },
            imageUrl = fresh.imageUrl.ifBlank { original.imageUrl }, url = fresh.url.ifBlank { original.url },
            dividendAmount = fresh.dividendAmount.ifBlank { original.dividendAmount }, exDate = fresh.exDate.ifBlank { original.exDate }, paymentDate = fresh.paymentDate.ifBlank { original.paymentDate })
    }
}
