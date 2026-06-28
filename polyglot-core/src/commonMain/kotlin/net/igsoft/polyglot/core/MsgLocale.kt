package net.igsoft.polyglot.core

@JvmInline
value class MsgLocale private constructor(
    val languageTag: String,
) {
    companion object {
        fun of(languageTag: String): MsgLocale {
            val trimmedLanguageTag = languageTag.trim()
            require(trimmedLanguageTag.isNotBlank()) {
                "languageTag can not be blank"
            }

            val normalizedTag = normalizeLanguageTag(trimmedLanguageTag, languageTag)

            return MsgLocale(normalizedTag)
        }
    }
}

private fun normalizeLanguageTag(
    trimmedLanguageTag: String,
    originalLanguageTag: String,
): String {
    val parts = trimmedLanguageTag.split('-')
    val normalizedParts = mutableListOf<String>()

    parts.forEachIndexed { index, part ->
        if (part.isEmpty()) {
            throw invalidLanguageTag(originalLanguageTag)
        }

        if (index > 0 && part.length == 1) {
            return normalizedParts
                .takeIf { it.isNotEmpty() }
                ?.joinToString("-")
                ?: throw invalidLanguageTag(originalLanguageTag)
        }

        normalizedParts += normalizeSubtag(part, index, originalLanguageTag)
    }

    return normalizedParts.joinToString("-")
}

private fun normalizeSubtag(
    subtag: String,
    index: Int,
    originalLanguageTag: String,
): String {
    return when {
        index == 0 && subtag.matches(LANGUAGE_SUBTAG_REGEX) -> subtag.lowercase()
        index > 0 && subtag.matches(SCRIPT_SUBTAG_REGEX) -> subtag.lowercase().replaceFirstChar(Char::titlecase)
        index > 0 && subtag.matches(REGION_ALPHA_SUBTAG_REGEX) -> subtag.uppercase()
        index > 0 && subtag.matches(REGION_NUMERIC_SUBTAG_REGEX) -> subtag
        index > 0 && subtag.matches(VARIANT_SUBTAG_REGEX) -> subtag.lowercase()
        else -> throw invalidLanguageTag(originalLanguageTag)
    }
}

private fun invalidLanguageTag(languageTag: String): IllegalArgumentException {
    return IllegalArgumentException("languageTag has invalid format: '$languageTag'")
}

private val LANGUAGE_SUBTAG_REGEX = Regex("[A-Za-z]{2,8}")
private val SCRIPT_SUBTAG_REGEX = Regex("[A-Za-z]{4}")
private val REGION_ALPHA_SUBTAG_REGEX = Regex("[A-Za-z]{2}")
private val REGION_NUMERIC_SUBTAG_REGEX = Regex("[0-9]{3}")
private val VARIANT_SUBTAG_REGEX = Regex("([A-Za-z0-9]{5,8}|[0-9][A-Za-z0-9]{3})")
