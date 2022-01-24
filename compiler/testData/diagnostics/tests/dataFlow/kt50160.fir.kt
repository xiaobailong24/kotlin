// WITH_STDLIB

sealed interface Square {
    object MARKED : Square
    object UNMARKED : Square
}

fun main() {
    val lines: List<String> = listOf()
    val cards = lines.windowed(6)
        .map { card ->
            card.map { line: String ->
                line.map { Square.UNMARKED as Square }
                    .toMutableList()
            }
        }
    cards[0][0][0] = Square.MARKED
}