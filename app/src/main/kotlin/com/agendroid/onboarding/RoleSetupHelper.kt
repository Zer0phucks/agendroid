package com.agendroid.onboarding

import android.app.role.RoleManager
import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable helper that checks and requests system roles for SMS and Dialer.
 *
 * Uses [RoleManager] on API 29+ (our minSdk is 31, so always available).
 */
@Singleton
class RoleSetupHelper @Inject constructor() {

    /**
     * Returns true if this app is the default SMS application.
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    /**
     * Returns true if this app is the default phone/dialer application.
     */
    fun isDefaultDialer(context: Context): Boolean {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecom.defaultDialerPackage == context.packageName
    }

}
