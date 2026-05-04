package com.mobilebot.domain.tools

import com.mobilebot.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolExecutor
    @Inject
    constructor(
        private val registry: ToolRegistry,
    ) {
        suspend fun execute(
            name: String,
            args: String,
        ): ToolResult = registry.execute(name, args)
    }
