package com.mobilebot.scenarios.familyshopping

import com.mobilebot.scenarios.runtime.ScenarioConversation
import com.mobilebot.scenarios.runtime.ScenarioAction
import com.mobilebot.scenarios.runtime.ScenarioDecision
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioParticipant
import com.mobilebot.scenarios.runtime.ScenarioProgress
import com.mobilebot.scenarios.runtime.ScenarioSurfaceRole
import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioTaskSeed
import com.mobilebot.scenarios.runtime.ScenarioTaskUpdate

object FamilyShoppingTaskSurface {
    const val TASK_ID = "family-shopping-live"
    const val ACTION_CONFIRM_PURCHASE = "family.confirm_purchase"
    const val ACTION_SKIP_PURCHASE = "family.skip_purchase"
    const val PURPOSE_CONFIRM_PURCHASE = "family_shopping.confirm_purchase"
    const val PURPOSE_CANCEL_PURCHASE = "family_shopping.cancel_purchase"
    const val PURPOSE_ADD_ITEMS = "family_shopping.add_items"
    const val PURPOSE_DELIVERY_WINDOW = "family_shopping.delivery_window"

    fun transcriptForAudioRef(audioRef: String): FamilyShoppingCallTranscript? =
        when (audioRef) {
            ELLA_CALL_TRANSCRIPT.audioRef -> ELLA_CALL_TRANSCRIPT
            else -> null
        }

    fun transcriptForIncomingCall(callId: String): FamilyShoppingCallTranscript? =
        when (callId) {
            "ella-call" -> ELLA_CALL_TRANSCRIPT
            else -> null
        }

    fun callTranscriptContext(
        contact: String,
        transcriptText: String,
    ): FamilyShoppingCallTranscript? =
        FamilyShoppingCallTranscriptExtractor.extract(contact, transcriptText)

    fun isPurchaseCancellationUserText(text: String): Boolean =
        FamilyShoppingCallTranscriptExtractor.isPurchaseCancellationUserText(text)

    fun resolveCallTranscript(
        audioRef: String = ELLA_CALL_TRANSCRIPT.audioRef,
        transcriptText: String? = null,
        contact: String = "Ella",
    ): FamilyShoppingCallTranscript =
        transcriptText
            ?.let { callTranscriptContext(contact, it) }
            ?: transcriptForAudioRef(audioRef)
            ?: ELLA_CALL_TRANSCRIPT

    fun fromEllaCall(
        audioRef: String = ELLA_CALL_TRANSCRIPT.audioRef,
        transcriptText: String? = null,
        contact: String = "Ella",
    ): ScenarioTaskSeed =
        fromCallTranscript(resolveCallTranscript(audioRef, transcriptText, contact))

    fun fromCallTranscript(transcript: FamilyShoppingCallTranscript): ScenarioTaskSeed {
        if (transcript.userDeclinedPurchase) {
            return purchaseDeclinedFromCall(transcript)
        }
        return ScenarioTaskSeed(
            taskId = TASK_ID,
            title = "家庭采购",
            subtitle = "Ella 电话交代的待办",
            status = ScenarioSurfaceStatus.RUNNING,
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    callSummaryConversation(transcript),
                ),
            ),
            logs = buildList {
                add(ScenarioLog("通话转写完成：${transcript.summary}"))
                add(ScenarioLog("提取待办：${transcript.taskTitle}（${transcript.items.joinToString("、")}）。"))
                if (transcript.priorityItems.isNotEmpty()) {
                    add(ScenarioLog("通话优先级：${transcript.priorityItems.joinToString("、")}。"))
                }
                if (transcript.excludedItems.isNotEmpty()) {
                    add(ScenarioLog("通话排除项：${transcript.excludedItems.joinToString("、")}不用买。"))
                }
                transcript.deliveryWindow?.let {
                    add(ScenarioLog("通话配送偏好：$it。"))
                }
            },
            participants = listOf(ELLA),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "整理待办事项",
                completed = 1,
                total = 5,
            ),
        )
    }

    private fun purchaseDeclinedFromCall(transcript: FamilyShoppingCallTranscript): ScenarioTaskSeed =
        ScenarioTaskSeed(
            taskId = TASK_ID,
            title = "家庭采购",
            subtitle = "通话中已确认暂不采购",
            status = ScenarioSurfaceStatus.DONE,
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "已记录：这次家庭采购不下单，后续超市候选不会继续推进。",
                ),
            ),
            logs = listOf(
                ScenarioLog("通话转写完成：${transcript.summary}"),
                ScenarioLog("用户在通话中拒绝本次采购，停止家庭采购链路。"),
            ),
            participants = listOf(ELLA),
            progress = ScenarioProgress(
                label = "完成",
                detail = "未下单",
                completed = 5,
                total = 5,
            ),
        )

    fun priorityFollowup(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "采购优先级已更新",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "Ella 又补充了采购优先级：低脂牛奶和洗衣液优先，水果顺路再买。我已经同步到任务里。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Ella 的短信：$messageBody"),
                ScenarioLog("更新采购优先级：低脂牛奶、洗衣液优先，水果可选。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "匹配采购方案",
                completed = 2,
                total = 5,
            ),
        )

    fun marketDeliveryCandidate(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.BLOCKED,
            subtitle = "已找到可配送渠道",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "附近超市有低脂牛奶和常用洗衣液，45 分钟内可送达。要我现在锁定这两样吗？",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Ole 通知：$messageBody"),
                ScenarioLog("加入采购候选：低脂牛奶、常用洗衣液，预计 45 分钟内送达。"),
                ScenarioLog("等待用户确认是否购买。"),
            ),
            participants = listOf(ELLA, OLE),
            progress = ScenarioProgress(
                label = "等待",
                detail = "等待购买确认",
                completed = 3,
                total = 5,
            ),
            decision = purchaseDecision(),
        )

    fun clarifiedList(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "采购清单已收敛",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "Ella 又调整了清单：洗衣液买常用款，猫粮不用买。我已经把候选清单收敛好了。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Ella 的短信：$messageBody"),
                ScenarioLog("更新采购清单：保留低脂牛奶、常用洗衣液；移除猫粮。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "清单已收敛",
                completed = 4,
                total = 5,
            ),
        )

    fun orderLocked(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.DONE,
            subtitle = "家庭采购已下单",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "家庭采购我已经按 Ella 调整后的清单处理好了：低脂牛奶和常用洗衣液已锁定，预计 14:12 送到。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Ole 通知：$messageBody"),
                ScenarioLog("调用服务查询：确认低脂牛奶和常用洗衣液库存。"),
                ScenarioLog("支付 Ole：96 元。"),
                ScenarioLog("完成记账：家庭采购 96 元。"),
            ),
            participantsToAdd = listOf(OLE),
            progress = ScenarioProgress(
                label = "完成",
                detail = "预计 14:12 送达",
                completed = 5,
                total = 5,
            ),
        )

    fun orderHeld(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.BLOCKED,
            subtitle = "库存已保留待确认",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "Ole 已暂时保留低脂牛奶和常用洗衣液的库存。确认后我再下单，不会自动付款。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Ole 通知：$messageBody"),
                ScenarioLog("仅保留库存，未支付；等待用户确认购买。"),
            ),
            participantsToAdd = listOf(OLE),
            progress = ScenarioProgress(
                label = "等待",
                detail = "等待购买确认",
                completed = 3,
                total = 5,
            ),
            decision = purchaseDecision(),
        )

    fun purchaseRequested(
        userText: String,
        excludedItems: Set<String>,
        addedItems: List<String>,
        deliveryWindow: String?,
    ): ScenarioTaskUpdate {
        val itemText = currentItemText(excludedItems, addedItems)
        val deliveryText = deliveryWindow?.let { "，配送时间按 $it 处理" }.orEmpty()
        return ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "正在锁定采购订单",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "我会按当前清单锁定$itemText$deliveryText，等 Ole 确认库存和配送。",
                ),
            ),
            logs = listOf(
                ScenarioLog("用户确认购买：$itemText。"),
                deliveryWindow?.let { ScenarioLog("用户要求配送窗口：$it。") },
            ).filterNotNull(),
            participantsToAdd = listOf(OLE),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待 Ole 锁定订单",
                completed = 4,
                total = 5,
            ),
        )
    }

    fun purchaseConfirmedFromHold(
        userText: String,
        excludedItems: Set<String>,
        addedItems: List<String>,
        deliveryWindow: String?,
    ): ScenarioTaskUpdate {
        val itemText = currentItemText(excludedItems, addedItems)
        val detail = deliveryWindow ?: "14:12"
        return ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.DONE,
            subtitle = "家庭采购已下单",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "已按当前清单完成家庭采购：$itemText，预计 $detail 送达。",
                ),
            ),
            logs = listOf(
                ScenarioLog("用户确认购买：$itemText。"),
                ScenarioLog("支付 Ole：96 元。"),
                ScenarioLog("完成记账：家庭采购 96 元。"),
            ),
            participantsToAdd = listOf(OLE),
            progress = ScenarioProgress(
                label = "完成",
                detail = "预计 $detail 送达",
                completed = 5,
                total = 5,
            ),
        )
    }

    fun purchaseSkipped(userText: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.DONE,
            subtitle = "家庭采购暂不下单",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "我先不下单，家庭采购候选已停止；后面需要时可以重新发起。",
                ),
            ),
            logs = listOf(
                ScenarioLog("用户取消家庭采购：$userText"),
                ScenarioLog("停止 Ole 采购候选，不支付。"),
            ),
            participants = listOf(ELLA, OLE),
            progress = ScenarioProgress(
                label = "完成",
                detail = "未下单",
                completed = 5,
                total = 5,
            ),
        )

    fun removeItems(
        userText: String,
        removedItems: List<String>,
        excludedItems: Set<String>,
        addedItems: List<String>,
    ): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "采购清单已更新",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "已从家庭采购里移除 ${removedItems.joinToString("、")}，当前保留 ${currentItemText(excludedItems, addedItems)}。",
                ),
            ),
            logs = listOf(
                ScenarioLog("用户移除采购项：${removedItems.joinToString("、")}。"),
                ScenarioLog("当前采购清单：${currentItemText(excludedItems, addedItems)}。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "清单已更新",
                completed = 4,
                total = 5,
            ),
            decision = purchaseDecision(),
        )

    fun addItems(
        userText: String,
        addedNow: String,
        excludedItems: Set<String>,
        addedItems: List<String>,
    ): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "已追加采购候选",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "我把 $addedNow 加入家庭采购候选，并请 Ole 一起确认库存和配送。",
                ),
            ),
            logs = listOf(
                ScenarioLog("用户追加采购项：$addedNow。"),
                ScenarioLog("当前采购清单：${currentItemText(excludedItems, addedItems)}。"),
            ),
            participantsToAdd = listOf(OLE),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "确认追加商品",
                completed = 4,
                total = 5,
            ),
            decision = purchaseDecision(),
        )

    fun deliveryWindowRequested(
        userText: String,
        deliveryWindow: String,
    ): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "配送时间待确认",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "我会让 Ole 按 $deliveryWindow 配送来确认；未确认前不会自动下单。",
                ),
            ),
            logs = listOf(
                ScenarioLog("用户要求调整配送窗口：$deliveryWindow。"),
                ScenarioLog("等待 Ole 确认新的配送时间。"),
            ),
            participantsToAdd = listOf(OLE),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "确认配送时间",
                completed = 4,
                total = 5,
            ),
            decision = purchaseDecision(),
        )

    fun statusAnswer(
        userText: String,
        stageText: String,
        excludedItems: Set<String>,
        addedItems: List<String>,
        deliveryWindow: String?,
        completed: Int,
        status: ScenarioSurfaceStatus,
    ): ScenarioTaskUpdate {
        val deliveryText = deliveryWindow?.let { "配送窗口：$it。" }.orEmpty()
        return ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = stageText,
            status = status,
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "当前状态：$stageText。采购清单是 ${currentItemText(excludedItems, addedItems)}。$deliveryText",
                ),
            ),
            logs = listOf(
                ScenarioLog("结构化场景输入：family_shopping.ask_status，直接读取场景状态机。"),
            ),
            participantsToAdd = listOf(ELLA, OLE),
            progress = ScenarioProgress(
                label = if (status == ScenarioSurfaceStatus.DONE) "完成" else "进行中",
                detail = stageText,
                completed = completed,
                total = 5,
            ),
        )
    }

    fun clarificationNeeded(userText: String, reason: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.BLOCKED,
            subtitle = "需要确认采购意图",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(ScenarioSurfaceRole.AGENT, clarificationText(reason)),
            ),
            logs = listOf(
                ScenarioLog("自由输入未能落到确定采购动作：$reason。"),
            ),
            progress = ScenarioProgress(
                label = "等待",
                detail = "等待采购确认",
                completed = 3,
                total = 5,
            ),
            decision = purchaseDecision(),
        )

    fun outOfScope(userText: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.BLOCKED,
            subtitle = "采购输入未识别",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "这条不像家庭采购任务里的动作。你可以说“买吧”“先不买”“不要水果”“加一盒鸡蛋”或“晚点送”。",
                ),
            ),
            logs = listOf(
                ScenarioLog("自由输入与家庭采购场景无关，等待澄清。"),
            ),
            progress = ScenarioProgress(
                label = "等待",
                detail = "等待采购确认",
                completed = 3,
                total = 5,
            ),
            decision = purchaseDecision(),
        )

    private val ELLA = ScenarioParticipant(
        id = "ella",
        label = "E",
        displayName = "Ella",
        role = "family",
    )

    private val OLE = ScenarioParticipant(
        id = "ole",
        label = "O",
        displayName = "Ole",
        role = "market",
    )

    private val ELLA_CALL_TRANSCRIPT = FamilyShoppingCallTranscript(
        audioRef = "ella-call-ended",
        transcript = "Ella 说下午如果方便，帮家里补低脂牛奶、常用洗衣液和一点水果；她强调牛奶和洗衣液优先，水果顺路再买。",
        summary = "低脂牛奶和常用洗衣液优先，水果顺路再买。",
        taskTitle = "家庭采购",
        items = listOf("低脂牛奶", "常用洗衣液", "水果可选"),
    )

    private fun callSummaryConversation(transcript: FamilyShoppingCallTranscript): String =
        "已整理家庭采购任务：${transcript.summary.removeSuffix("。")}。"

    private fun purchaseDecision(): ScenarioDecision =
        ScenarioDecision(
            text = "是否按当前家庭采购清单下单？",
            actions = listOf(
                ScenarioAction("买这两样", ACTION_CONFIRM_PURCHASE),
                ScenarioAction("先不买", ACTION_SKIP_PURCHASE),
            ),
        )

    private fun currentItemText(
        excludedItems: Set<String>,
        addedItems: List<String>,
    ): String {
        val baseItems = listOf("低脂牛奶", "常用洗衣液", "水果可选")
            .filterNot { item -> excludedItems.any { excluded -> item.contains(excluded) || excluded.contains(item) } }
        return (baseItems + addedItems).distinct().joinToString("、").ifBlank { "空清单" }
    }

    private fun clarificationText(reason: String): String =
        when (reason) {
            "delivery_window" -> "你想调整到哪个配送时间？可以说“14:30 后送”或“明天上午送”。"
            "purchase_item" -> "你想加购或移除哪一项？请直接说商品名。"
            "empty_cart" -> "当前清单已经没有可购买项了。要重新添加商品，还是先不买？"
            else -> "你是想现在下单、先不买、调整清单，还是改配送时间？"
        }
}

data class FamilyShoppingCallTranscript(
    val audioRef: String,
    val transcript: String,
    val summary: String,
    val taskTitle: String,
    val items: List<String>,
    val priorityItems: List<String> = emptyList(),
    val excludedItems: List<String> = emptyList(),
    val deliveryWindow: String? = null,
    val userDeclinedPurchase: Boolean = false,
)

private object FamilyShoppingCallTranscriptExtractor {
    private data class ItemSpec(
        val canonical: String,
        val aliases: List<String>,
    )

    private val itemSpecs = listOf(
        ItemSpec("低脂牛奶", listOf("低脂牛奶", "牛奶", "milk")),
        ItemSpec("常用洗衣液", listOf("常用洗衣液", "洗衣液", "detergent")),
        ItemSpec("水果", listOf("水果", "fruit")),
        ItemSpec("猫粮", listOf("猫粮", "cat food")),
        ItemSpec("鸡蛋", listOf("鸡蛋", "eggs", "egg")),
        ItemSpec("面包", listOf("面包", "bread")),
        ItemSpec("酸奶", listOf("酸奶", "yogurt", "yoghurt")),
        ItemSpec("咖啡", listOf("咖啡", "coffee")),
    )
    private val excludedItemNames = setOf("猫粮")
    private val shoppingTerms = listOf(
        "买",
        "采购",
        "补",
        "下单",
        "超市",
        "配送",
        "送",
        "purchase",
        "buy",
        "shopping",
        "order",
        "deliver",
    )
    private val exclusionTerms = listOf(
        "不用买",
        "别买",
        "不要",
        "不需要",
        "不用",
        "skip",
        "don't buy",
        "do not buy",
        "without",
    )
    private val purchaseCancellationTerms = listOf(
        "不买不买",
        "先不买",
        "不买了",
        "别买了",
        "不用买了",
        "不用下单",
        "不下单",
        "取消采购",
        "先别买",
        "不要买了",
        "no need to buy",
        "don't buy",
        "do not buy",
        "skip purchase",
    )
    private val optionalTerms = listOf("顺路", "方便", "可选", "有空", "如果方便", "optional")
    private val priorityTerms = listOf("优先", "先买", "priority", "first")
    private val exactTimeRegex = Regex("""\d{1,2}[:：]\d{2}\s*(?:后|前|左右)?""")
    private val chineseTimeRegex =
        Regex("""(?:今天|明天|后天|下周[一二三四五六日天]?|这周[一二三四五六日天]?)?(?:上午|中午|下午|晚上|今晚)?\s*\d{1,2}\s*(?:点|时)(?:半)?(?:后|前|左右)?""")
    private val deliveryKeywords = listOf(
        "明天上午",
        "明天下午",
        "明天晚上",
        "明天",
        "今天下午",
        "今天晚上",
        "今晚",
        "下午",
        "晚点",
        "尽快",
    )

    fun extract(
        contact: String,
        transcriptText: String,
    ): FamilyShoppingCallTranscript? {
        val cleanTranscript = transcriptText.trim()
        if (cleanTranscript.isBlank()) return null

        val lowerTranscript = cleanTranscript.lowercase()
        val userDeclinedPurchase = hasUserPurchaseCancellation(cleanTranscript)
        val hasShoppingTerm = shoppingTerms.any { lowerTranscript.contains(it.lowercase()) }
        val segments = cleanTranscript
            .split(Regex("[，,。；;！!？?\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val presentItems = linkedSetOf<String>()
        val excludedItems = linkedSetOf<String>()
        val priorityItems = linkedSetOf<String>()

        itemSpecs.forEach { spec ->
            val matchedSegments = segments.filter { segment -> spec.matches(segment) }
            if (matchedSegments.isEmpty()) return@forEach

            val hasExclusion = matchedSegments.any { segment -> segment.hasAny(exclusionTerms) } ||
                (spec.canonical in excludedItemNames && lowerTranscript.hasAny(exclusionTerms))
            val optional = spec.canonical == "水果" && matchedSegments.any { segment -> segment.hasAny(optionalTerms) }
            val itemLabel = if (optional) "水果可选" else spec.canonical

            if (hasExclusion) {
                excludedItems += spec.canonical
            } else {
                presentItems += itemLabel
                if (matchedSegments.any { segment -> segment.hasAny(priorityTerms) } && itemLabel != "水果可选") {
                    priorityItems += itemLabel
                }
            }
        }

        if (!userDeclinedPurchase && presentItems.isEmpty() && excludedItems.isEmpty() && !hasShoppingTerm) return null

        val items = presentItems.toList().ifEmpty { listOf("待确认采购项") }
        val deliveryWindow = extractDeliveryWindow(cleanTranscript)
        val summary = if (userDeclinedPurchase) {
            "用户已在通话中拒绝本次采购。"
        } else {
            summaryText(
                items = items,
                priorityItems = priorityItems.toList(),
                excludedItems = excludedItems.toList(),
                deliveryWindow = deliveryWindow,
            )
        }

        return FamilyShoppingCallTranscript(
            audioRef = "runtime-call:${contact.ifBlank { "unknown" }.lowercase()}",
            transcript = cleanTranscript,
            summary = summary,
            taskTitle = "家庭采购",
            items = items,
            priorityItems = priorityItems.toList(),
            excludedItems = excludedItems.toList(),
            deliveryWindow = deliveryWindow,
            userDeclinedPurchase = userDeclinedPurchase,
        )
    }

    private fun hasUserPurchaseCancellation(text: String): Boolean =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("用户：") || it.startsWith("你：") || it.startsWith("User:", ignoreCase = true) }
            .any(::isPurchaseCancellationUserText)

    fun isPurchaseCancellationUserText(text: String): Boolean {
        val value = text.trim()
        val lower = value.lowercase()
        return purchaseCancellationTerms.any { lower.contains(it.lowercase()) } ||
            Regex("""(^|[：:\s，,。])不买(?:不买)?(?:[。！!，,]|\s|$)""").containsMatchIn(value)
    }

    private fun ItemSpec.matches(segment: String): Boolean {
        val lowerSegment = segment.lowercase()
        return aliases.any { lowerSegment.contains(it.lowercase()) }
    }

    private fun String.hasAny(terms: List<String>): Boolean {
        val lower = lowercase()
        return terms.any { lower.contains(it.lowercase()) }
    }

    private fun extractDeliveryWindow(text: String): String? =
        exactTimeRegex.find(text)?.value?.trim()
            ?: chineseTimeRegex.find(text)?.value?.trim()
            ?: deliveryKeywords.firstOrNull { text.contains(it) }

    private fun summaryText(
        items: List<String>,
        priorityItems: List<String>,
        excludedItems: List<String>,
        deliveryWindow: String?,
    ): String {
        val parts = buildList {
            if (priorityItems.isNotEmpty()) {
                add("${priorityItems.joinToString("、")}优先")
            }
            add("${items.joinToString("、")}待采购")
            if (excludedItems.isNotEmpty()) {
                add("${excludedItems.joinToString("、")}不用买")
            }
            deliveryWindow?.let { add("配送偏好：$it") }
        }
        return parts.joinToString("；") + "。"
    }
}
