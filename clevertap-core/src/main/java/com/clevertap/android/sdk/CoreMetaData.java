package com.clevertap.android.sdk;

import android.app.Activity;
import android.location.Location;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import java.lang.ref.WeakReference;
import org.json.JSONObject;

//TODO make singleton

/**
 * This class stores run time state of CleverTap's instance
 */
@RestrictTo(Scope.LIBRARY)
public class CoreMetaData extends CleverTapMetaData {

    private static boolean appForeground = false;

    private static WeakReference<Activity> currentActivity;

    private static int activityCount = 0;

    private long appInstallTime = 0;

    private boolean appLaunchPushed = false;

    private final Object appLaunchPushedLock = new Object();

    private String currentScreenName = "";

    private int currentSessionId = 0;

    private boolean currentUserOptedOut = false;

    private boolean firstRequestInSession = false;

    private boolean firstSession = false;

    private int geofenceSDKVersion = 0;

    private boolean installReferrerDataSent = false;

    private boolean isBgPing = false;

    private boolean isLocationForGeofence = false;

    private boolean isProductConfigRequested;

    private int lastSessionLength = 0;

    private Location locationFromUser = null;

    private boolean offline;

    private final Object optOutFlagLock = new Object();

    private long referrerClickTime = 0;

    private String source = null, medium = null, campaign = null;

    private JSONObject wzrkParams = null;

    private static int initialAppEnteredForegroundTime = 0;

    public static Activity getCurrentActivity() {
        return (currentActivity == null) ? null : currentActivity.get();
    }

    static int getInitialAppEnteredForegroundTime() {
        return initialAppEnteredForegroundTime;
    }

    public static void setCurrentActivity(@Nullable Activity activity) {
        if (activity == null) {
            currentActivity = null;
            return;
        }
        if (!activity.getLocalClassName().contains("InAppNotificationActivity")) {
            currentActivity = new WeakReference<>(activity);
        }
    }

    public static String getCurrentActivityName() {
        Activity current = getCurrentActivity();
        return (current != null) ? current.getLocalClassName() : null;
    }

    public static boolean isAppForeground() {
        return appForeground;
    }

    public static void setAppForeground(boolean isForeground) {
        appForeground = isForeground;
    }

    static void setInitialAppEnteredForegroundTime(final int initialAppEnteredForegroundTime) {
        CoreMetaData.initialAppEnteredForegroundTime = initialAppEnteredForegroundTime;
    }

    public long getAppInstallTime() {
        return appInstallTime;
    }

    public void setAppInstallTime(final long appInstallTime) {
        this.appInstallTime = appInstallTime;
    }

    public Location getLocationFromUser() {
        return locationFromUser;
    }

    public void setLocationFromUser(final Location locationFromUser) {
        this.locationFromUser = locationFromUser;
    }

    public boolean isProductConfigRequested() {
        return isProductConfigRequested;
    }

    public void setProductConfigRequested(final boolean productConfigRequested) {
        isProductConfigRequested = productConfigRequested;
    }

    public void setCurrentScreenName(final String currentScreenName) {
        this.currentScreenName = currentScreenName;
    }

    synchronized void clearCampaign() {
        campaign = null;
    }

    synchronized void clearMedium() {
        medium = null;
    }

    synchronized void clearSource() {
        source = null;
    }

    synchronized void clearWzrkParams() {
        wzrkParams = null;
    }

    synchronized String getCampaign() {
        return campaign;
    }

    synchronized void setCampaign(String campaign) {
        if (this.campaign == null) {
            this.campaign = campaign;
        }
    }

    int getCurrentSessionId() {
        return currentSessionId;
    }

    int getGeofenceSDKVersion() {
        return geofenceSDKVersion;
    }

    void setGeofenceSDKVersion(int geofenceSDKVersion) {
        this.geofenceSDKVersion = geofenceSDKVersion;
    }

    //Session
    int getLastSessionLength() {
        return lastSessionLength;
    }

    void setLastSessionLength(final int lastSessionLength) {
        this.lastSessionLength = lastSessionLength;
    }

    synchronized String getMedium() {
        return medium;
    }

    // only set if not already set during the session
    synchronized void setMedium(String medium) {
        if (this.medium == null) {
            this.medium = medium;
        }
    }

    long getReferrerClickTime() {
        return referrerClickTime;
    }

    void setReferrerClickTime(final long referrerClickTime) {
        this.referrerClickTime = referrerClickTime;
    }

    String getScreenName() {
        return currentScreenName.equals("") ? null : currentScreenName;
    }

    public synchronized String getSource() {
        return source;
    }

    //UTM
    // only set if not already set during the session
    synchronized void setSource(String source) {
        if (this.source == null) {
            this.source = source;
        }
    }

    synchronized JSONObject getWzrkParams() {
        return wzrkParams;
    }

    public synchronized void setWzrkParams(JSONObject wzrkParams) {
        if (this.wzrkParams == null) {
            this.wzrkParams = wzrkParams;
        }
    }

    boolean inCurrentSession() {
        return currentSessionId > 0;
    }

    boolean isAppLaunchPushed() {
        synchronized (appLaunchPushedLock) {
            return appLaunchPushed;
        }
    }

    void setAppLaunchPushed(boolean pushed) {
        synchronized (appLaunchPushedLock) {
            appLaunchPushed = pushed;
        }
    }

    boolean isBgPing() {
        return isBgPing;
    }

    void setBgPing(final boolean bgPing) {
        isBgPing = bgPing;
    }

    boolean isCurrentUserOptedOut() {
        synchronized (optOutFlagLock) {
            return currentUserOptedOut;
        }
    }

    public void setCurrentUserOptedOut(boolean enable) {
        synchronized (optOutFlagLock) {
            currentUserOptedOut = enable;
        }
    }

    boolean isFirstRequestInSession() {
        return firstRequestInSession;
    }

    void setFirstRequestInSession(boolean firstRequestInSession) {
        this.firstRequestInSession = firstRequestInSession;
    }

    //Session
    boolean isFirstSession() {
        return firstSession;
    }

    void setFirstSession(final boolean firstSession) {
        this.firstSession = firstSession;
    }

    boolean isInstallReferrerDataSent() {
        return installReferrerDataSent;
    }

    void setInstallReferrerDataSent(final boolean installReferrerDataSent) {
        this.installReferrerDataSent = installReferrerDataSent;
    }

    boolean isLocationForGeofence() {
        return isLocationForGeofence;
    }

    void setLocationForGeofence(boolean locationForGeofence) {
        isLocationForGeofence = locationForGeofence;
    }

    boolean isOffline() {
        return offline;
    }

    void setOffline(boolean value) {
        offline = value;
    }

    void setCurrentSessionId(int sessionId) {
        this.currentSessionId = sessionId;
    }

    static int getActivityCount() {
        return activityCount;
    }

    public static void setActivityCount(final int count) {
        activityCount = activityCount;
    }

    static void incrementActivityCount() {
        activityCount++;
    }
}