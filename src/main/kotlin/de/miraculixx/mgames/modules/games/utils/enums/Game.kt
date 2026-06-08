package de.miraculixx.mgames.modules.games.utils.enums

enum class Game(val id: Int, val short: String, val title: String, val coinValue: Int) {
    TIC_TAC_TOE(1, "TTT", "Tic Tac Toe", 3),
    CONNECT_4(2, "C4", "Connect 4", 5),
    QUICK_MATH(3, "QuickMath", "Quick Math", 1),
    TRIVIA(4, "Trivia", "Trivia", 1),
}
