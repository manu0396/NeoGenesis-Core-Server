package com.neogenesis.server.application.error

open class ApplicationException(
    val code: String,
    message: String = code
) : RuntimeException(message)

class BadRequestException(
    code: String,
    message: String = code
) : ApplicationException(code, message)

class ConflictException(
    code: String,
    message: String = code
) : ApplicationException(code, message)

class DependencyUnavailableException(
    code: String,
    message: String = code
) : ApplicationException(code, message)
