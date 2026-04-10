package com.embabel.hub.oauth

data class OAuthUserInfo(
    val provider: String,
    val providerUserId: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
    val avatarUrl: String?,
)
