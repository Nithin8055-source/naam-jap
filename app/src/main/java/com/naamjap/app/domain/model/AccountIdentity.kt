package com.naamjap.app.domain.model

data class AccountIdentity(
    val userId: String,
    val email: String?,
    val displayName: String?
)
