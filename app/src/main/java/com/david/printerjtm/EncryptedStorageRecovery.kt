package com.david.printerjtm

internal class EncryptedStorageRecovery<T>(
    private val openStorage: () -> T,
    private val resetStorage: () -> Unit,
    private val isRecoverableFailure: (Exception) -> Boolean,
    private val onRecovered: () -> Unit
) {
    fun open(): T {
        try {
            return openStorage()
        } catch (initialFailure: Exception) {
            if (!isRecoverableFailure(initialFailure)) throw initialFailure

            try {
                resetStorage()
            } catch (resetFailure: Exception) {
                resetFailure.addSuppressed(initialFailure)
                throw resetFailure
            }

            try {
                return openStorage().also { onRecovered() }
            } catch (recoveryFailure: Exception) {
                recoveryFailure.addSuppressed(initialFailure)
                throw recoveryFailure
            }
        }
    }
}
