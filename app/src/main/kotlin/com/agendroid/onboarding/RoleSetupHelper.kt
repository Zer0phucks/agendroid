package com.agendroid.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
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

    /**
     * Launches the system dialog asking the user to set this app as the default SMS app.
     * Uses [RoleManager.ROLE_SMS] on API 29+ (always available given minSdk 31).
     */
    fun requestDefaultSmsRole(activity: Activity) {
        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        ) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            activity.startActivityForResult(intent, REQUEST_CODE_SMS_ROLE)
        }
    }

    /**
     * Launches the system dialog asking the user to set this app as the default dialer.
     * Uses [RoleManager.ROLE_DIALER] on API 29+ (always available given minSdk 31).
     */
    fun requestDefaultDialer(activity: Activity) {
        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        ) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            activity.startActivityForResult(intent, REQUEST_CODE_DIALER_ROLE)
        }
    }

    companion object {
        const val REQUEST_CODE_SMS_ROLE = 1001
        const val REQUEST_CODE_DIALER_ROLE = 1002
    }
}
