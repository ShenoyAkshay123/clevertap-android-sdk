package com.clevertap.android.sdk.product_config;

import android.content.Context;
import com.clevertap.android.sdk.BaseAnalyticsManager;
import com.clevertap.android.sdk.BaseCallbackManager;
import com.clevertap.android.sdk.CleverTapInstanceConfig;
import com.clevertap.android.sdk.CoreMetaData;
import com.clevertap.android.sdk.DeviceInfo;

public class CTProductConfigFactory {

    public static CTProductConfigController getInstance(Context context, DeviceInfo deviceInfo,
            CleverTapInstanceConfig config, BaseAnalyticsManager baseAnalyticsManager, CoreMetaData coreMetaData,
            BaseCallbackManager callbackManager) {
        final String guid = deviceInfo.getDeviceID();
        ProductConfigSettings configSettings = new ProductConfigSettings(context, guid, config);
        return new CTProductConfigController(context, config, baseAnalyticsManager, coreMetaData, callbackManager, configSettings);
    }
}