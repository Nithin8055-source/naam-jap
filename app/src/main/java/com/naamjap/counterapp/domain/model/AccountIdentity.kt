package com.naamjap.counterapp.domain.model

data class AccountIdentity(
    val userId: String,
    val email: String?,
    val displayName: String?
)
