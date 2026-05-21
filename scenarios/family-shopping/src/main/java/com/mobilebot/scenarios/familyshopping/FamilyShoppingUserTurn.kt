package com.mobilebot.scenarios.familyshopping

import java.util.Locale

sealed interface FamilyShoppingUserTurn {
    data object AskStatus : FamilyShoppingUserTurn
    data object ConfirmPurchase : FamilyShoppingUserTurn
    data object CancelPurchase : FamilyShoppingUserTurn
    data object OutOfScope : FamilyShoppingUserTurn

    data class RemoveItems(
        val items: List<String>,
    ) : FamilyShoppingUserTurn

    data class AddItems(
        val itemText: String,
    ) : FamilyShoppingUserTurn

    data class ChangeDeliveryWindow(
        val windowLabel: String,
    ) : FamilyShoppingUserTurn

    data class NeedsClarification(
        val reason: String,
    ) : FamilyShoppingUserTurn
}

object FamilyShoppingUserTurnInterpreter {
    fun interpret(
        taskId: String?,
        userText: String,
        shoppingKnown: Boolean,
    ): FamilyShoppingUserTurn? {
        val raw = userText.trim()
        if (raw.isBlank()) return null

        val normalized = raw.lowercase(Locale.ROOT)
        val shoppingContext = taskId == FamilyShoppingTaskSurface.TASK_ID ||
            normalized.hasAny(SHOPPING_TERMS) ||
            shoppingKnown && normalized.hasAny(SHORT_CONTEXT_TERMS)
        if (!shoppingContext) return null

        val removedItems = removedItems(normalized)
        if (removedItems.isNotEmpty()) {
            return FamilyShoppingUserTurn.RemoveItems(removedItems)
        }

        deliveryWindowLabel(normalized)?.let { window ->
            return FamilyShoppingUserTurn.ChangeDeliveryWindow(window)
        }

        addedItemText(raw, normalized)?.let { itemText ->
            return FamilyShoppingUserTurn.AddItems(itemText)
        }

        return when {
            normalized.hasAny(CONFIRM_TERMS) -> FamilyShoppingUserTurn.ConfirmPurchase
            normalized.hasAny(CANCEL_TERMS) -> FamilyShoppingUserTurn.CancelPurchase
            normalized.hasAny(STATUS_TERMS) -> FamilyShoppingUserTurn.AskStatus
            normalized.hasAny(AMBIGUOUS_CHANGE_TERMS) -> FamilyShoppingUserTurn.NeedsClarification("purchase_item")
            shoppingKnown || normalized.hasAny(SHOPPING_TERMS) -> FamilyShoppingUserTurn.NeedsClarification("family_shopping_action")
            else -> FamilyShoppingUserTurn.OutOfScope
        }
    }

    private fun removedItems(text: String): List<String> {
        if (!text.hasAny(REMOVE_TERMS)) return emptyList()
        return ITEM_TERMS
            .filter { (term, _) -> text.contains(term) }
            .map { (_, canonical) -> canonical }
            .distinct()
    }

    private fun deliveryWindowLabel(text: String): String? {
        if (!text.hasAny(DELIVERY_TERMS)) return null
        TIME_PATTERN.find(text)?.value?.replace('：', ':')?.let { return it }
        HOUR_PATTERN.find(text)?.value?.let { return it }
        return when {
            text.contains("明天上午") -> "明天上午"
            text.contains("明天下午") -> "明天下午"
            text.contains("明天") -> "明天"
            text.contains("今晚") || text.contains("晚上") -> "今晚"
            text.contains("下午") -> "今天下午"
            text.contains("晚点") || text.contains("迟点") -> "稍晚一点"
            else -> null
        }
    }

    private fun addedItemText(
        raw: String,
        normalized: String,
    ): String? {
        if (!normalized.hasAny(ADD_TERMS)) return null
        val marker = ADD_TERMS.firstOrNull { normalized.contains(it) } ?: return null
        val start = normalized.indexOf(marker) + marker.length
        val suffix = raw.drop(start)
            .trim()
            .trimStart('：', ':', '，', ',', ' ', '个', '点')
            .trimEnd('。', '.', '，', ',', '吧')
        return suffix.takeIf { it.isNotBlank() }
    }

    private fun String.hasAny(terms: List<String>): Boolean =
        terms.any { contains(it) }

    private val SHOPPING_TERMS = listOf(
        "family",
        "grocery",
        "shopping",
        "market",
        "ole",
        "ella",
        "buy",
        "order",
        "delivery",
        "milk",
        "detergent",
        "fruit",
        "采购",
        "买",
        "下单",
        "超市",
        "配送",
        "送达",
        "牛奶",
        "洗衣液",
        "水果",
        "家里",
        "家庭",
        "清单",
        "订单",
    )

    private val SHORT_CONTEXT_TERMS = listOf(
        "status",
        "done",
        "buy",
        "order",
        "skip",
        "cancel",
        "add",
        "remove",
        "状态",
        "进度",
        "买吧",
        "下单",
        "不买",
        "先不",
        "取消",
        "加",
        "不要",
        "不用",
        "晚点",
        "明天",
    )

    private val CONFIRM_TERMS = listOf(
        "buy it",
        "order it",
        "go ahead",
        "confirm",
        "purchase",
        "买吧",
        "买这",
        "下单",
        "确认买",
        "锁定",
        "就买",
        "可以买",
        "去买",
    )

    private val CANCEL_TERMS = listOf(
        "skip",
        "cancel",
        "don't buy",
        "do not buy",
        "不用买",
        "先不买",
        "别买",
        "不买了",
        "取消",
        "算了",
    )

    private val REMOVE_TERMS = listOf(
        "remove",
        "skip",
        "without",
        "don't buy",
        "do not buy",
        "不要",
        "不用买",
        "别买",
        "去掉",
        "删掉",
        "拿掉",
    )

    private val ADD_TERMS = listOf(
        "also get",
        "add",
        "include",
        "再买",
        "顺便买",
        "加上",
        "加一",
        "加点",
        "加",
        "补点",
    )

    private val DELIVERY_TERMS = listOf(
        "delivery",
        "deliver",
        "send",
        "送",
        "配送",
        "送达",
        "送到",
        "晚点",
        "迟点",
        "明天",
        "上午",
        "下午",
        "晚上",
        "今晚",
    )

    private val STATUS_TERMS = listOf(
        "status",
        "where",
        "progress",
        "eta",
        "done",
        "状态",
        "进度",
        "到哪",
        "买了吗",
        "下单了吗",
        "送到",
        "什么时候",
    )

    private val AMBIGUOUS_CHANGE_TERMS = listOf(
        "change",
        "replace",
        "swap",
        "换",
        "改一下",
        "换一下",
        "替换",
    )

    private val ITEM_TERMS = listOf(
        "低脂牛奶" to "低脂牛奶",
        "牛奶" to "低脂牛奶",
        "milk" to "低脂牛奶",
        "常用洗衣液" to "常用洗衣液",
        "洗衣液" to "常用洗衣液",
        "detergent" to "常用洗衣液",
        "水果" to "水果",
        "fruit" to "水果",
        "猫粮" to "猫粮",
        "cat food" to "猫粮",
    )

    private val TIME_PATTERN = Regex("""\b\d{1,2}[:：]\d{2}\b""")
    private val HOUR_PATTERN = Regex("""\b\d{1,2}\s*(点|时)(半|后)?""")
}
