package com.mobilebot.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        ToolEventEntity::class,
        SessionSummaryEntity::class,
        WorkingMemoryEntity::class,
        MemoryFactEntity::class,
        ToolApprovalEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun messageDao(): MessageDao

    abstract fun toolEventDao(): ToolEventDao

    abstract fun sessionSummaryDao(): SessionSummaryDao

    abstract fun workingMemoryDao(): WorkingMemoryDao

    abstract fun memoryFactDao(): MemoryFactDao

    abstract fun toolApprovalDao(): ToolApprovalDao
}
