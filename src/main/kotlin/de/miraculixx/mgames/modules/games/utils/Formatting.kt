package de.miraculixx.mgames.modules.games.utils

import de.miraculixx.mgames.utils.mCoin

fun coinGrantFooter(coins: Int): String {
    return if (coins <= 0) "" else "   ||  + ${coins}$mCoin"
}