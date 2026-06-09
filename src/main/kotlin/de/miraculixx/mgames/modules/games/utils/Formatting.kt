package de.miraculixx.mgames.modules.games.utils

import de.miraculixx.mgames.utils.Icons

fun coinGrantFooter(coins: Int): String {
    return if (coins <= 0) "" else "   ||  +${coins} ${Icons.mCoins}"
}
