package com.clevertap.android.xps;

import android.content.Context;
import android.util.Log;

import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.xiaomi.mipush.sdk.MiPushCommandMessage;
import com.xiaomi.mipush.sdk.MiPushMessage;
import com.xiaomi.mipush.sdk.PushMessageReceiver;

import static com.clevertap.android.xps.XpsConstants.LOG_TAG;

public class XiaomiMessageReceiver extends PushMessageReceiver {
    private IMiMessageHandler handler = new XiaomiMessageHandler();

    @VisibleForTesting
    @RestrictTo(value = RestrictTo.Scope.LIBRARY)
    public void setHandler(IMiMessageHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onReceivePassThroughMessage(Context context, MiPushMessage miPushMessage) {
        Log.i(LOG_TAG, "onReceivePassThroughMessage is called");
        handler.createNotification(context, miPushMessage);
    }

    @Override
    public void onNotificationMessageArrived(Context context, MiPushMessage miPushMessage) {
        super.onNotificationMessageArrived(context, miPushMessage);
        Log.i(LOG_TAG, "onNotificationMessageArrived is called");
        handler.createNotification(context, miPushMessage);
    }

    @Override
    public void onReceiveRegisterResult(Context context, MiPushCommandMessage miPushCommandMessage) {
        super.onReceiveRegisterResult(context, miPushCommandMessage);
        handler.onReceiveRegisterResult(context, miPushCommandMessage);
    }
}