package com.mobilebot.domain

import com.mobilebot.domain.tools.CallServiceTool
import com.mobilebot.domain.tools.CheckSubtaskTool
import com.mobilebot.domain.tools.CompletePaymentTool
import com.mobilebot.domain.tools.CopyToClipboardTool
import com.mobilebot.domain.tools.CreatePlanTool
import com.mobilebot.domain.tools.DialNumberTool
import com.mobilebot.domain.tools.GetCurrentLocationTool
import com.mobilebot.domain.tools.ListNotificationsTool
import com.mobilebot.domain.tools.OpenCameraTool
import com.mobilebot.domain.tools.OpenAppTool
import com.mobilebot.domain.tools.OpenMapTool
import com.mobilebot.domain.tools.OpenSettingsTool
import com.mobilebot.domain.tools.OpenUrlTool
import com.mobilebot.domain.tools.ReadSandboxFileTool
import com.mobilebot.domain.tools.ReadUserProfileTool
import com.mobilebot.domain.tools.SearchContactsTool
import com.mobilebot.domain.tools.SendSmsTool
import com.mobilebot.domain.tools.SetAlarmTool
import com.mobilebot.domain.tools.SetTimerTool
import com.mobilebot.domain.tools.ShareTextTool
import com.mobilebot.domain.tools.PublishFactTool
import com.mobilebot.domain.tools.SpawnSubtaskTool
import com.mobilebot.domain.tools.CreateCalendarEventTool
import com.mobilebot.domain.tools.CreateNotificationTool
import com.mobilebot.domain.tools.DeepLinkAppTool
import com.mobilebot.domain.tools.DeleteMemoryTool
import com.mobilebot.domain.tools.GetDeviceStateTool
import com.mobilebot.domain.tools.PlayMediaTool
import com.mobilebot.domain.tools.QueryCalendarTool
import com.mobilebot.domain.tools.QueryServiceTool
import com.mobilebot.domain.tools.RecordExpenseTool
import com.mobilebot.domain.tools.RecallMemoriesTool
import com.mobilebot.domain.tools.SkillTool
import com.mobilebot.domain.tools.ResolvePlaceTool
import com.mobilebot.domain.tools.SaveMemoryTool
import com.mobilebot.domain.tools.ToggleFlashlightTool
import com.mobilebot.domain.tools.TranscribeCallTool
import com.mobilebot.domain.tools.WriteSandboxFileTool
import com.mobilebot.domain.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainToolModule {
    @Binds
    @IntoSet
    abstract fun bindReadSandbox(tool: ReadSandboxFileTool): Tool

    @Binds
    @IntoSet
    abstract fun bindOpenCamera(tool: OpenCameraTool): Tool

    @Binds
    @IntoSet
    abstract fun bindListNotifications(tool: ListNotificationsTool): Tool

    @Binds
    @IntoSet
    abstract fun bindOpenUrl(tool: OpenUrlTool): Tool

    @Binds
    @IntoSet
    abstract fun bindOpenMap(tool: OpenMapTool): Tool

    @Binds
    @IntoSet
    abstract fun bindCopyClipboard(tool: CopyToClipboardTool): Tool

    @Binds
    @IntoSet
    abstract fun bindGetLocation(tool: GetCurrentLocationTool): Tool

    @Binds
    @IntoSet
    abstract fun bindDial(tool: DialNumberTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSendSms(tool: SendSmsTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSearchContacts(tool: SearchContactsTool): Tool

    @Binds
    @IntoSet
    abstract fun bindShareText(tool: ShareTextTool): Tool

    @Binds
    @IntoSet
    abstract fun bindCallService(tool: CallServiceTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSpawnSubtask(tool: SpawnSubtaskTool): Tool

    @Binds
    @IntoSet
    abstract fun bindCheckSubtask(tool: CheckSubtaskTool): Tool

    @Binds
    @IntoSet
    abstract fun bindPublishFact(tool: PublishFactTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSaveMemory(tool: SaveMemoryTool): Tool

    @Binds
    @IntoSet
    abstract fun bindRecallMemories(tool: RecallMemoriesTool): Tool

    @Binds
    @IntoSet
    abstract fun bindDeleteMemory(tool: DeleteMemoryTool): Tool

    @Binds
    @IntoSet
    abstract fun bindReadUserProfile(tool: ReadUserProfileTool): Tool

    @Binds
    @IntoSet
    abstract fun bindOpenSettings(tool: OpenSettingsTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSetAlarm(tool: SetAlarmTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSetTimer(tool: SetTimerTool): Tool

    @Binds
    @IntoSet
    abstract fun bindToggleFlashlight(tool: ToggleFlashlightTool): Tool

    @Binds
    @IntoSet
    abstract fun bindOpenApp(tool: OpenAppTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSkillTool(tool: SkillTool): Tool

    @Binds
    @IntoSet
    abstract fun bindCreatePlan(tool: CreatePlanTool): Tool

    @Binds
    @IntoSet
    abstract fun bindCreateCalendarEvent(tool: CreateCalendarEventTool): Tool

    @Binds
    @IntoSet
    abstract fun bindQueryCalendar(tool: QueryCalendarTool): Tool

    @Binds
    @IntoSet
    abstract fun bindCreateNotification(tool: CreateNotificationTool): Tool

    @Binds
    @IntoSet
    abstract fun bindDeepLinkApp(tool: DeepLinkAppTool): Tool

    @Binds
    @IntoSet
    abstract fun bindWriteSandboxFile(tool: WriteSandboxFileTool): Tool

    @Binds
    @IntoSet
    abstract fun bindGetDeviceState(tool: GetDeviceStateTool): Tool

    @Binds
    @IntoSet
    abstract fun bindPlayMedia(tool: PlayMediaTool): Tool

    @Binds
    @IntoSet
    abstract fun bindQueryService(tool: QueryServiceTool): Tool

    @Binds
    @IntoSet
    abstract fun bindResolvePlace(tool: ResolvePlaceTool): Tool

    @Binds
    @IntoSet
    abstract fun bindTranscribeCall(tool: TranscribeCallTool): Tool

    @Binds
    @IntoSet
    abstract fun bindCompletePayment(tool: CompletePaymentTool): Tool

    @Binds
    @IntoSet
    abstract fun bindRecordExpense(tool: RecordExpenseTool): Tool

}
