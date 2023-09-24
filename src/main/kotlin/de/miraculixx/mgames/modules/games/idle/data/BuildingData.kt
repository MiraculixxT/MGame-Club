package de.miraculixx.mgames.modules.games.idle.data

data class BuildingData(
    val type: Buildings,
    var amount: Int,
    val maxAmount: Int,
    val baseValuePerBuilding: Double
)