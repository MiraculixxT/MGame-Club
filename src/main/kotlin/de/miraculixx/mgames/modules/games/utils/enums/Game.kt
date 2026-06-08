package de.miraculixx.mgames.modules.games.utils.enums

enum class Game(val short: String, val title: String, val coinValue: Int) {
    TIC_TAC_TOE("TTT", "Tic Tac Toe", 3),
    CONNECT_4("C4", "Connect 4", 5),
    QUICK_MATH("QuickMath", "Quick Math", 1),
}
