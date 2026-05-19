package com.mobilebot.scenarios.petgrooming

import java.util.Locale

sealed interface PetGroomingUserTurn {
    data object ExpediteService : PetGroomingUserTurn
    data object AskStatus : PetGroomingUserTurn
    data object CancelService : PetGroomingUserTurn
    data object OutOfScope : PetGroomingUserTurn

    data class RescheduleService(
        val targetWeekOffset: Int,
        val unavailableWeekOffsets: List<Int>,
    ) : PetGroomingUserTurn

    data class NeedsClarification(
        val reason: String,
    ) : PetGroomingUserTurn
}

object PetGroomingUserTurnInterpreter {
    fun interpret(
        taskId: String?,
        userText: String,
        petCareKnown: Boolean,
    ): PetGroomingUserTurn? {
        val raw = userText.trim()
        if (raw.isBlank()) return null

        val normalized = raw.lowercase(Locale.ROOT)
        val petContext = taskId == PetGroomingTaskSurface.TASK_ID || normalized.hasAny(PET_TERMS)
        if (!petContext) return null

        val weeks = weekMentions(normalized)
        val asksReschedule = normalized.hasAny(RESCHEDULE_TERMS) || weeks.isNotEmpty()

        return when {
            normalized.hasAny(CANCEL_TERMS) -> PetGroomingUserTurn.CancelService
            asksReschedule && weeks.isNotEmpty() -> {
                val target = weeks.last()
                val unavailable = weeks
                    .dropLast(1)
                    .filter { normalized.hasUnavailableCueNear(it.range) }
                    .map { it.offset }
                    .distinct()
                PetGroomingUserTurn.RescheduleService(
                    targetWeekOffset = target.offset,
                    unavailableWeekOffsets = unavailable,
                )
            }
            asksReschedule -> PetGroomingUserTurn.NeedsClarification("reschedule_target")
            normalized.hasAny(EXPEDITE_TERMS) -> PetGroomingUserTurn.ExpediteService
            normalized.hasAny(STATUS_TERMS) -> PetGroomingUserTurn.AskStatus
            normalized.hasAny(AMBIGUOUS_TIME_TERMS) -> PetGroomingUserTurn.NeedsClarification("reschedule_target")
            normalized.hasAny(PET_TERMS) || petCareKnown -> PetGroomingUserTurn.NeedsClarification("pet_grooming_action")
            else -> PetGroomingUserTurn.OutOfScope
        }
    }

    fun weekLabel(offset: Int): String =
        when (offset) {
            0 -> "这周"
            1 -> "下周"
            2 -> "下下周"
            3 -> "下下下周"
            else -> "${offset} 周后"
        }

    private fun weekMentions(text: String): List<WeekMention> {
        val mentions = mutableListOf<WeekMention>()
        CHINESE_WEEK_TERMS.forEach { (term, offset) ->
            var start = text.indexOf(term)
            while (start >= 0) {
                mentions.addIfNoOverlap(WeekMention(offset, start until start + term.length))
                start = text.indexOf(term, startIndex = start + 1)
            }
        }
        ENGLISH_WEEK_TERMS.forEach { (regex, offset) ->
            regex.findAll(text).forEach { match ->
                mentions.addIfNoOverlap(WeekMention(offset, match.range))
            }
        }
        return mentions.sortedBy { it.range.first }
    }

    private fun MutableList<WeekMention>.addIfNoOverlap(mention: WeekMention) {
        if (none { it.range.overlaps(mention.range) }) add(mention)
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last

    private fun String.hasUnavailableCueNear(range: IntRange): Boolean {
        val windowStart = maxOf(0, range.first - 8)
        val windowEnd = minOf(length, range.last + 13)
        val window = substring(windowStart, windowEnd)
        return window.hasAny(UNAVAILABLE_TERMS)
    }

    private fun String.hasAny(terms: List<String>): Boolean =
        terms.any { contains(it) }

    private data class WeekMention(
        val offset: Int,
        val range: IntRange,
    )

    private val PET_TERMS = listOf(
        "kylin",
        "petsmart",
        "pet",
        "dog",
        "groom",
        "bath",
        "狗",
        "宠物",
        "宠物店",
        "洗澡",
        "洗护",
        "去浮毛",
        "浮毛",
        "门店",
        "店里",
    )

    private val RESCHEDULE_TERMS = listOf(
        "reschedule",
        "move",
        "change",
        "postpone",
        "改",
        "改成",
        "改到",
        "换",
        "推迟",
        "延期",
        "没空",
        "没时间",
    )

    private val CANCEL_TERMS = listOf(
        "cancel",
        "取消",
        "别去了",
        "不去了",
        "不用洗",
        "别洗",
    )

    private val EXPEDITE_TERMS = listOf(
        "faster",
        "speed up",
        "hurry",
        "quick",
        "asap",
        "催",
        "加快",
        "快点",
        "洗快",
        "尽快",
    )

    private val STATUS_TERMS = listOf(
        "status",
        "where",
        "eta",
        "finished",
        "done",
        "ready",
        "到哪",
        "进度",
        "状态",
        "好了吗",
        "洗完",
        "几点",
        "什么时候",
    )

    private val AMBIGUOUS_TIME_TERMS = listOf(
        "晚点",
        "换个时间",
        "改一下时间",
        "later",
        "another time",
    )

    private val UNAVAILABLE_TERMS = listOf(
        "unavailable",
        "busy",
        "no time",
        "can't",
        "cant",
        "没空",
        "没时间",
        "不行",
        "去不了",
    )

    private val CHINESE_WEEK_TERMS = listOf(
        "下下下周" to 3,
        "下下周" to 2,
        "下周" to 1,
        "这周" to 0,
        "本周" to 0,
    )

    private val ENGLISH_WEEK_TERMS = listOf(
        Regex("\\bthis\\s+week\\b") to 0,
        Regex("\\bnext\\s+week\\b") to 1,
        Regex("\\bweek\\s+after\\s+next\\b") to 2,
        Regex("\\bin\\s+two\\s+weeks\\b") to 2,
        Regex("\\btwo\\s+weeks?\\s+later\\b") to 2,
        Regex("\\bin\\s+three\\s+weeks\\b") to 3,
        Regex("\\bthree\\s+weeks?\\s+later\\b") to 3,
        Regex("\\bthird\\s+week\\b") to 3,
    )
}
