package com.example.engine

data class StyledSegment(
    val text: String,
    val isBold: Boolean = false,
    val isHighlight: Boolean = false
)

object InlineSpanParser {
    private val tokenRegex = Regex("(==[^=\\n]+==|\\*\\*[^*\\n]+\\*\\*)")

    fun parse(input: String): List<StyledSegment> {
        if (input.isEmpty()) return emptyList()
        if (!input.contains("==") && !input.contains("**")) {
            return listOf(StyledSegment(text = input))
        }

        val segments = mutableListOf<StyledSegment>()
        var lastIndex = 0

        tokenRegex.findAll(input).forEach { match ->
            if (match.range.first > lastIndex) {
                segments.add(StyledSegment(text = input.substring(lastIndex, match.range.first)))
            }
            val token = match.value
            when {
                token.startsWith("==") && token.endsWith("==") && token.length > 4 -> {
                    segments.add(
                        StyledSegment(
                            text = token.substring(2, token.length - 2),
                            isBold = true,
                            isHighlight = true
                        )
                    )
                }
                token.startsWith("**") && token.endsWith("**") && token.length > 4 -> {
                    segments.add(
                        StyledSegment(
                            text = token.substring(2, token.length - 2),
                            isBold = true,
                            isHighlight = false
                        )
                    )
                }
                else -> segments.add(StyledSegment(text = token))
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < input.length) {
            segments.add(StyledSegment(text = input.substring(lastIndex)))
        }

        return segments
    }

    fun stripMarkers(input: String): String {
        return parse(input).joinToString("") { it.text }
    }
}
