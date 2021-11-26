package com.clevertap.demo

import com.clevertap.android.sdk.pushnotification.fcm.FcmMessageHandlerImpl
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFcmMessageListenerService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        var pushType = "fcm"
        if (pushType.equals("fcm")) {
            FcmMessageHandlerImpl().onMessageReceived(applicationContext, message)
            //FcmMessageHandlerImpl().processPushAmp(applicationContext, message)
        } else if (pushType.equals("hps")) {
            //HmsMessageHandlerImpl().createNotification(applicationContext,message)
            //HmsMessageHandlerImpl().processPushAmp(applicationContext,message)
        } else if (pushType.equals("xps")) {
            //XiaomiMessageHandlerImpl().createNotification(applicationContext,message)
            //XiaomiMessageHandlerImpl().processPushAmp(applicationContext,message)
        }
    }
}