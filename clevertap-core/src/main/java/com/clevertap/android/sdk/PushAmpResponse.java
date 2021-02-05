package com.clevertap.android.sdk;

import static com.clevertap.android.sdk.CTJsonConverter.getRenderedTargetList;

import android.content.Context;
import android.os.Bundle;
import com.clevertap.android.sdk.pushnotification.PushProviders;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class PushAmpResponse extends CleverTapResponseDecorator {

    private final Object inboxControllerLock;

    private final BaseCallbackManager mCallbackManager;

    private final CleverTapResponse mCleverTapResponse;

    private final CleverTapInstanceConfig mConfig;

    private final Context mContext;

    private final DBAdapter mDBAdapter;

    private final Logger mLogger;

    private final PushProviders mPushProviders;

    PushAmpResponse(CleverTapResponse cleverTapResponse,
            Context context,
            CleverTapInstanceConfig config,
            CTLockManager ctLockManager,
            BaseDatabaseManager dbManager,
            BaseCallbackManager callbackManager,
            ControllerManager controllerManager) {
        mCleverTapResponse = cleverTapResponse;
        mContext = context;
        mConfig = config;
        mPushProviders = controllerManager.getPushProviders();
        mLogger = mConfig.getLogger();
        inboxControllerLock = ctLockManager.getInboxControllerLock();
        mDBAdapter = dbManager.loadDBAdapter(context);
        mCallbackManager = callbackManager;
    }

    @Override
    void processResponse(final JSONObject response, final String stringBody, final Context context) {
        //Handle Push Amplification response
        if (mConfig.isAnalyticsOnly()) {
            mLogger.verbose(mConfig.getAccountId(),
                    "CleverTap instance is configured to analytics only, not processing push amp response");

            // process Display Unit response
            mCleverTapResponse.processResponse(response, stringBody, context);

            return;
        }
        try {
            if (response.has("pushamp_notifs")) {
                mLogger.verbose(mConfig.getAccountId(), "Processing pushamp messages...");
                JSONObject pushAmpObject = response.getJSONObject("pushamp_notifs");
                final JSONArray pushNotifications = pushAmpObject.getJSONArray("list");
                if (pushNotifications.length() > 0) {
                    mLogger.verbose(mConfig.getAccountId(), "Handling Push payload locally");
                    handlePushNotificationsInResponse(pushNotifications);
                }
                if (pushAmpObject.has("pf")) {
                    try {
                        int frequency = pushAmpObject.getInt("pf");
                        mPushProviders.updatePingFrequencyIfNeeded(context, frequency);
                    } catch (Throwable t) {
                        mLogger
                                .verbose("Error handling ping frequency in response : " + t.getMessage());
                    }

                }
                if (pushAmpObject.has("ack")) {
                    boolean ack = pushAmpObject.getBoolean("ack");
                    mLogger.verbose("Received ACK -" + ack);
                    if (ack) {
                        JSONArray rtlArray = getRenderedTargetList(mDBAdapter);
                        String[] rtlStringArray = new String[0];
                        if (rtlArray != null) {
                            rtlStringArray = new String[rtlArray.length()];
                        }
                        for (int i = 0; i < rtlStringArray.length; i++) {
                            rtlStringArray[i] = rtlArray.getString(i);
                        }
                        mLogger.verbose("Updating RTL values...");
                        mDBAdapter.updatePushNotificationIds(rtlStringArray);
                    }
                }
            }
        } catch (Throwable t) {
            //Ignore
        }

        // process Display Unit response
        mCleverTapResponse.processResponse(response, stringBody, context);
    }

    //PN
    private void handlePushNotificationsInResponse(JSONArray pushNotifications) {
        try {
            for (int i = 0; i < pushNotifications.length(); i++) {
                Bundle pushBundle = new Bundle();
                JSONObject pushObject = pushNotifications.getJSONObject(i);
                if (pushObject.has("wzrk_ttl")) {
                    pushBundle.putLong("wzrk_ttl", pushObject.getLong("wzrk_ttl"));
                }

                Iterator iterator = pushObject.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next().toString();
                    pushBundle.putString(key, pushObject.getString(key));
                }
                if (!pushBundle.isEmpty() && !mDBAdapter
                        .doesPushNotificationIdExist(pushObject.getString("wzrk_pid"))) {
                    mLogger.verbose("Creating Push Notification locally");
                    if (mCallbackManager.getPushAmpListener() != null) {
                        mCallbackManager.getPushAmpListener().onPushAmpPayloadReceived(pushBundle);
                    } else {
                        mPushProviders
                                ._createNotification(mContext, pushBundle, Constants.EMPTY_NOTIFICATION_ID);
                    }
                } else {
                    mLogger.verbose(mConfig.getAccountId(),
                            "Push Notification already shown, ignoring local notification :" + pushObject
                                    .getString("wzrk_pid"));
                }
            }
        } catch (JSONException e) {
            mLogger.verbose(mConfig.getAccountId(), "Error parsing push notification JSON");
        }
    }


}