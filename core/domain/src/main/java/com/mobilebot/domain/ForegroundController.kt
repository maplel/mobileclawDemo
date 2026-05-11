package com.mobilebot.domain

/**
 * Controls the foreground service that keeps the process alive while
 * the agent or its subtasks are running.
 *
 * Implementations should be reference-counted: each [onAgentStart] increments
 * the count, each [onAgentStop] decrements it. The underlying OS foreground
 * service should only start on 0→1 and stop on 1→0.
 */
interface ForegroundController {
    fun onAgentStart()

    fun onAgentStop()
}
