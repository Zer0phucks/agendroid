package com.agendroid.core.telephony

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneAccountRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val telecomManager: TelecomManager
        get() = context.getSystemService(TelecomManager::class.java)

    fun register() {
        telecomManager.registerPhoneAccount(
            PhoneAccount.builder(phoneAccountHandle, "Agendroid")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build(),
        )
    }

    fun unregister() {
        telecomManager.unregisterPhoneAccount(phoneAccountHandle)
    }

    fun isRegistered(): Boolean =
        telecomManager.getOwnSelfManagedPhoneAccounts().any { handle ->
            handle.componentName == phoneAccountHandle.componentName && handle.id == phoneAccountHandle.id
        }

    val phoneAccountHandle: PhoneAccountHandle by lazy {
        PhoneAccountHandle(
            ComponentName(context, AgendroidConnectionService::class.java),
            "agendroid_self_managed",
        )
    }
}
