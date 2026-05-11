package com.mobilebot.domain.permissions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCapabilityApprovalGate
    @Inject
    constructor() : CapabilityApprovalGate {
        private val mutex = Mutex()
        private val _pendingRequest = MutableStateFlow<CapabilityApprovalRequest?>(null)
        override val pendingRequest: StateFlow<CapabilityApprovalRequest?> =
            _pendingRequest.asStateFlow()

        private var deferred: CompletableDeferred<CapabilityApprovalResult>? = null

        override suspend fun requestApproval(
            request: CapabilityApprovalRequest,
        ): CapabilityApprovalResult =
            withContext(Dispatchers.Main.immediate) {
                val d = CompletableDeferred<CapabilityApprovalResult>()
                mutex.withLock {
                    deferred?.cancel()
                    deferred = d
                    _pendingRequest.value = request
                }
                try {
                    d.await()
                } finally {
                    mutex.withLock {
                        if (deferred === d) {
                            deferred = null
                            _pendingRequest.value = null
                        }
                    }
                }
            }

        override fun respond(result: CapabilityApprovalResult) {
            deferred?.complete(result)
        }
    }
