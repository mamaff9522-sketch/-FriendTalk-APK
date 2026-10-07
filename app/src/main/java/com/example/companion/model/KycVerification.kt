package com.example.companion.model

data class KycVerificationInfo(
    val userId: String,
    val fullName: String,
    val idCardNumber: String,
    val birthDate: String,
    val calculatedAge: Int,
    val idCardPhotoUrl: String = "",
    val selfieWithIdUrl: String = "",
    val status: KycStatus = KycStatus.NOT_SUBMITTED,
    val verifiedAt: String? = null,
    val rejectionReason: String? = null
)
