package com.neogenesis.server.domain.model

data class ControlCommandEvent(
    val command: ControlCommand,
    val createdAtMs: Long = System.currentTimeMillis()
)
