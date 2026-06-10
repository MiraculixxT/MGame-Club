package de.miraculixx.mgames.modules.games

import de.miraculixx.mgames.utils.api.SQL
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toPNG
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.style.Theme
import org.jetbrains.kotlinx.kandy.util.color.Color as KandyColor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object LeaderboardGraph {
    data class DailyStats(
        val date: LocalDate,
        val played: Int,
        val wins: Int,
        val losses: Int,
        val draws: Int
    )

    data class Summary(
        val played: Int,
        val wins: Int,
        val losses: Int,
        val draws: Int,
        val winRate: Int
    )

    fun lastThirtyDays(): List<LocalDate> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return (29 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
    }

    fun aggregate(rows: List<SQL.GameHistoryEntry>, days: List<LocalDate> = lastThirtyDays()): List<DailyStats> {
        val validDays = days.toSet()
        val matches = rows.groupBy { it.matchID.ifBlank { "${it.playedAt}:${it.result.id}" } }
        val dailyResults = matches.values.mapNotNull { entries ->
            val playedAt = entries.minOfOrNull { it.playedAt } ?: return@mapNotNull null
            val date = Instant.fromEpochMilliseconds(playedAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            if (date !in validDays) return@mapNotNull null
            date to entries.map { it.result }
        }.groupBy({ it.first }, { it.second })

        return days.map { date ->
            val matchesOnDay = dailyResults[date].orEmpty()
            val results = matchesOnDay.flatten()
            DailyStats(
                date = date,
                played = matchesOnDay.size,
                wins = results.count { it == SQL.GameResult.WIN },
                losses = results.count { it == SQL.GameResult.LOSS },
                draws = matchesOnDay.count { match -> match.any { it == SQL.GameResult.DRAW } }
            )
        }
    }

    fun summarize(stats: List<DailyStats>): Summary {
        val wins = stats.sumOf { it.wins }
        val losses = stats.sumOf { it.losses }
        val decisive = wins + losses
        return Summary(
            played = stats.sumOf { it.played },
            wins = wins,
            losses = losses,
            draws = stats.sumOf { it.draws },
            winRate = if (decisive == 0) 0 else ((wins.toDouble() / decisive) * 100).toInt()
        )
    }

    fun renderPng(stats: List<DailyStats>): ByteArray {
        val dayLabels = stats.map { "${it.date.month.ordinal + 1}/${it.date.day}" }
        val played = stats.map { it.played }
        val wins = stats.map { it.wins }
        val losses = stats.map { it.losses }
        val draws = stats.map { it.draws }
        val empty = stats.all { it.played == 0 }

        return plot {
            bars {
                x(dayLabels)
                y(played)
                fillColor = KandyColor.hex("#c29113")
                alpha = 0.45
                width = 0.72
            }
            line {
                x(dayLabels)
                y(wins)
                color = KandyColor.hex("#2ecc71")
                width = 2.7
            }
            points {
                x(dayLabels)
                y(wins)
                color = KandyColor.hex("#2ecc71")
                size = 3.5
            }
            line {
                x(dayLabels)
                y(losses)
                color = KandyColor.hex("#e74c3c")
                width = 2.7
            }
            line {
                x(dayLabels)
                y(draws)
                color = KandyColor.hex("#aeb6bf")
                width = 2.2
            }
            layout {
                title = if (empty) "Game Activity - No games in the last 30 days" else "Game Activity - Last 30 Days"
                subtitle = "Bars: played games   Lines: wins / losses / draws"
                xAxisLabel = "Day"
                yAxisLabel = "Games"
                theme = Theme.DARCULA
                size = 1200 to 520
            }
        }.toPNG(scale = 2)
    }
}
