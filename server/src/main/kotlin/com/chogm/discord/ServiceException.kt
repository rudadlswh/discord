package com.chogm.discord

import io.ktor.http.HttpStatusCode

class ServiceException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)
