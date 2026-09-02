package com.jhendefr.pixelupia.domain.model

import android.content.IntentSender

sealed interface MediaOperationResult {
    data object Success : MediaOperationResult
    data class RequiresIntentSender(val intentSender: IntentSender) : MediaOperationResult
    data class Failure(val message: String) : MediaOperationResult
}
