package com.clevertap.android.sdk.product_config;

import android.content.Context;
import android.text.TextUtils;

import com.clevertap.android.sdk.CleverTapInstanceConfig;
import com.clevertap.android.sdk.Constants;
import com.clevertap.android.sdk.FileUtils;
import com.clevertap.android.sdk.TaskManager;
import com.clevertap.android.sdk.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.clevertap.android.sdk.product_config.CTProductConfigConstants.DEFAULT_MIN_FETCH_INTERVAL_SECONDS;
import static com.clevertap.android.sdk.product_config.CTProductConfigConstants.DEFAULT_VALUE_FOR_BOOLEAN;
import static com.clevertap.android.sdk.product_config.CTProductConfigConstants.DEFAULT_VALUE_FOR_STRING;
import static com.clevertap.android.sdk.product_config.CTProductConfigConstants.PRODUCT_CONFIG_JSON_KEY_FOR_KEY;
import static com.clevertap.android.sdk.product_config.CTProductConfigConstants.PRODUCT_CONFIG_JSON_KEY_FOR_VALUE;

public class CTProductConfigController {

    private String guid;

    public boolean isInitialized() {
        return isInitialized;
    }

    private boolean isInitialized = false;
    private final CleverTapInstanceConfig config;
    private final Context context;
    private HashMap<String, String> defaultConfig = new HashMap<>();
    private final HashMap<String, String> activatedConfig = new HashMap<>();
    private final HashMap<String, String> fetchedConfig = new HashMap<>();
    private final CTProductConfigControllerListener listener;
    private boolean isFetching = false;
    private boolean isActivating = false;
    private boolean isFetchAndActivating = false;
    private final ProductConfigSettings settings;


    public CTProductConfigController(Context context, String guid, CleverTapInstanceConfig config, CTProductConfigControllerListener listener) {
        this.context = context;
        this.guid = guid;
        this.config = config;
        this.listener = listener;
        this.settings = new ProductConfigSettings(context, guid, config);
        initAsync();
    }

    private void initAsync() {
        if (TextUtils.isEmpty(guid))
            return;
        TaskManager.getInstance().execute(new TaskManager.TaskListener<Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void params) {
                synchronized (this) {
                    try {
                        activatedConfig.clear();
                        //apply default config first
                        if (!defaultConfig.isEmpty()) {
                            activatedConfig.putAll(defaultConfig);
                        }
                        activatedConfig.putAll(getStoredValues(getActivatedFullPath()));
                        FileUtils.writeJsonToFile(context, config, getProductConfigDirName(), CTProductConfigConstants.FILE_NAME_ACTIVATED, new JSONObject(activatedConfig));
                        config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : initialized with configs: " + activatedConfig);
                        settings.loadSettings();
                        isInitialized = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "initAsync failed - " + e.getLocalizedMessage());
                        return false;
                    }
                    return true;
                }
            }

            @Override
            public void onPostExecute(Boolean isInitSuccess) {
                if (isInitSuccess) {
                    sendCallback(PROCESSING_STATE.INIT_SUCCESS);
                } else {
                    sendCallback(PROCESSING_STATE.INIT_FAILED);
                }
            }
        });
    }

    private HashMap<String, String> getStoredValues(String fullFilePath) {
        HashMap<String, String> map = new HashMap<>();
        String content = null;
        try {
            content = FileUtils.readFromFile(context, config, fullFilePath);
        } catch (Exception e) {
            e.printStackTrace();
            config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : getStoredValues reading file failed: " + e.getLocalizedMessage());
        }
        if (!TextUtils.isEmpty(content)) {
            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject(content);
            } catch (JSONException e) {
                e.printStackTrace();
                config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : getStoredValues failed due to malformed json: " + e.getLocalizedMessage());
            }
            if (jsonObject != null) {
                Iterator<String> iterator = jsonObject.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    if (!TextUtils.isEmpty(key)) {
                        String value = null;
                        try {
                            value = String.valueOf(jsonObject.get(key));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : getStoredValues for key " + key + " while parsing json: " + e.getLocalizedMessage());
                        }
                        if (!TextUtils.isEmpty(value))
                            map.put(key, value);
                    }
                }
            }

        }
        return map;
    }

    /**
     * Sets default configs using an XML resource.
     *
     * @param resourceID
     */
    public void setDefaults(final int resourceID) {
        TaskManager.getInstance().execute(new TaskManager.TaskListener<Void, Void>() {
            @Override
            public Void doInBackground(Void aVoid) {
                defaultConfig.clear();
                defaultConfig.putAll(DefaultXmlParser.getDefaultsFromXml(context, resourceID));
                return null;
            }

            @Override
            public void onPostExecute(Void aVoid) {
                config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config: setDefaults Completed with: " + defaultConfig);
                initAsync();
            }
        });
    }

    /**
     * Sets default configs using the given Map.
     *
     * @param map
     */
    public void setDefaults(final HashMap<String, Object> map) {
        TaskManager.getInstance().execute(new TaskManager.TaskListener<Void, Void>() {
            @Override
            public Void doInBackground(Void aVoid) {
                if (map != null && !map.isEmpty()) {
                    defaultConfig.clear();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        if (entry != null) {
                            String key = entry.getKey();
                            Object value = entry.getValue();
                            if (!TextUtils.isEmpty(key) && ProductConfigUtil.isSupportedDataType(value)) {
                                defaultConfig.put(key, String.valueOf(value));
                            }
                        }
                    }
                }
                return null;
            }

            @Override
            public void onPostExecute(Void aVoid) {
                config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config: setDefaults Completed with: " + defaultConfig);
                initAsync();
            }
        });
    }

    /**
     * Starts fetching configs, adhering to the default minimum fetch interval.
     */
    public void fetch() {
        fetch(settings.getNextFetchIntervalInSeconds());
    }

    /**
     * Starts fetching configs, adhering to the specified minimum fetch interval in seconds.
     *
     * @param minimumFetchIntervalInSeconds - long value of seconds
     */
    @SuppressWarnings("WeakerAccess")
    public void fetch(long minimumFetchIntervalInSeconds) {
        if (canRequest(minimumFetchIntervalInSeconds)) {
            isFetching = true;
            listener.fetchProductConfig();
        } else {
            config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config: Throttled");
        }
    }

    /**
     * Asynchronously activates the most recently fetched configs, so that the fetched key value pairs take effect.
     */
    @SuppressWarnings("WeakerAccess")
    public void activate() {
        if (isActivating)
            return;
        TaskManager.getInstance().execute(new TaskManager.TaskListener<Void, Void>() {
            @Override
            public Void doInBackground(Void params) {
                synchronized (this) {
                    isActivating = true;
                    try {
                        activatedConfig.clear();
                        //apply default config first
                        if (defaultConfig != null && !defaultConfig.isEmpty()) {
                            activatedConfig.putAll(defaultConfig);
                        }
                        //read fetched info
                        if (!fetchedConfig.isEmpty()) {
                            activatedConfig.putAll(fetchedConfig);
                        } else {
                            activatedConfig.putAll(getStoredValues(getFetchedFullPath()));
                        }
                        FileUtils.writeJsonToFile(context, config, getProductConfigDirName(), CTProductConfigConstants.FILE_NAME_ACTIVATED, new JSONObject(activatedConfig));
                        FileUtils.deleteFile(context, config, getFetchedFullPath());
                    } catch (Exception e) {
                        e.printStackTrace();
                        config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : activate failed: " + e.getLocalizedMessage());
                    }
                    return null;
                }
            }

            @Override
            public void onPostExecute(Void isSuccess) {
                config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : activated successfully with configs: " + activatedConfig);
                sendCallback(PROCESSING_STATE.ACTIVATED);
                isActivating = false;
                isFetchAndActivating = false;
            }
        });
    }

    @SuppressWarnings("WeakerAccess")
    /**
     * Sets the minimum interval between successive fetch calls.
     */
    public void setMinimumFetchIntervalInSeconds(long fetchIntervalInSeconds) {
        settings.setMinimumFetchIntervalInSeconds(fetchIntervalInSeconds);
    }


    /**
     * Returns the parameter value for the given key as a String.
     *
     * @param Key - String
     * @return String
     */
    public String getString(String Key) {
        if (isInitialized && !TextUtils.isEmpty(Key)) {
            String value = activatedConfig.get(Key);
            if (value != null) {
                return value;
            }
        }
        return DEFAULT_VALUE_FOR_STRING;
    }

    /**
     * Returns the parameter value for the given key as a boolean.
     *
     * @param Key - String
     * @return String
     */
    public Boolean getBoolean(String Key) {
        if (isInitialized && !TextUtils.isEmpty(Key)) {
            return Boolean.parseBoolean(activatedConfig.get(Key));
        }
        return DEFAULT_VALUE_FOR_BOOLEAN;
    }

    /**
     * Returns the parameter value for the given key as a long.
     *
     * @param Key - String
     * @return String
     */
    public Long getLong(String Key) {
        if (isInitialized && !TextUtils.isEmpty(Key)) {
            try {
                return Long.parseLong(activatedConfig.get(Key));
            } catch (NumberFormatException e) {
                e.printStackTrace();
                config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Error getting Long for Key-" + Key + " " + e.getMessage());
            }
        }
        return CTProductConfigConstants.DEFAULT_VALUE_FOR_LONG;
    }

    /**
     * Returns the parameter value for the given key as a double.
     *
     * @param Key String
     * @return String
     */
    public Double getDouble(String Key) {
        if (isInitialized && !TextUtils.isEmpty(Key)) {
            try {
                return Double.parseDouble(activatedConfig.get(Key));
            } catch (NumberFormatException e) {
                e.printStackTrace();
                config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Error getting Double for Key-" + Key + " " + e.getMessage());
            }

        }
        return CTProductConfigConstants.DEFAULT_VALUE_FOR_DOUBLE;
    }

    private boolean canRequest(long minimumFetchIntervalInSeconds) {
        return !isFetching
                && !TextUtils.isEmpty(guid)
                && (System.currentTimeMillis() - settings.getLastFetchTimeStampInMillis()) > TimeUnit.SECONDS.toMillis(minimumFetchIntervalInSeconds);
    }

    public void afterFetchProductConfig(JSONObject kvResponse) {
        synchronized (this) {
            if (kvResponse != null) {
                try {
                    parseFetchedResponse(kvResponse);
                    FileUtils.writeJsonToFile(context, config, getProductConfigDirName(), CTProductConfigConstants.FILE_NAME_FETCHED, new JSONObject(fetchedConfig));
                    config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : fetch file write success: from init " + fetchedConfig);
                    Utils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config: fetch Success");
                            sendCallback(PROCESSING_STATE.FETCHED);
                        }
                    });
                    if (isFetchAndActivating) {
                        activate();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config: fetch Failed");
                    sendCallback(PROCESSING_STATE.FETCHED);
                    e.printStackTrace();
                    isFetchAndActivating = false;// set fetchAndActivating flag to false if fetch fails.
                }
            }
            isFetching = false;
        }
    }

    private void parseFetchedResponse(JSONObject jsonObject) {
        HashMap<String, String> map = convertServerJsonToMap(jsonObject);
        fetchedConfig.clear();
        fetchedConfig.putAll(map);
        config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config: Fetched response:" + jsonObject);
        Integer timestamp = null;
        try {
            timestamp = (Integer) jsonObject.get(CTProductConfigConstants.KEY_LAST_FETCHED_TIMESTAMP);
        } catch (JSONException e) {
            e.printStackTrace();
            config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : parseFetchedResponse failed: " + e.getLocalizedMessage());
        }
        if (timestamp != null) {
            settings.setLastFetchTimeStampInMillis(timestamp);
        }
    }

    private HashMap<String, String> convertServerJsonToMap(JSONObject jsonObject) {
        HashMap<String, String> map = new HashMap<>();
        JSONArray kvArray = null;
        try {
            kvArray = jsonObject.getJSONArray(Constants.KEY_KV);
        } catch (JSONException e) {
            e.printStackTrace();
            config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "convertServerJsonToMap failed - " + e.getLocalizedMessage());
        }

        if (kvArray != null && kvArray.length() > 0) {
            for (int i = 0; i < kvArray.length(); i++) {
                JSONObject object;
                try {
                    object = (JSONObject) kvArray.get(i);
                    if (object != null) {
                        String Key = object.getString(PRODUCT_CONFIG_JSON_KEY_FOR_KEY);
                        String Value = object.getString(PRODUCT_CONFIG_JSON_KEY_FOR_VALUE);
                        if (!TextUtils.isEmpty(Key)) {
                            map.put(Key, String.valueOf(Value));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : convertServerJsonToMap failed: " + e.getLocalizedMessage());
                }
            }
        }
        return map;
    }

    public void setGuidAndInit(String cleverTapID) {
        if (TextUtils.isEmpty(guid))
            return;
        this.guid = cleverTapID;
        initAsync();
    }

    private String getProductConfigDirName() {
        return CTProductConfigConstants.DIR_PRODUCT_CONFIG + "_" + config.getAccountId() + "_" + guid;
    }

    private String getFetchedFullPath() {
        return getProductConfigDirName() + "/" + CTProductConfigConstants.FILE_NAME_FETCHED;
    }

    private String getActivatedFullPath() {
        return getProductConfigDirName() + "/" + CTProductConfigConstants.FILE_NAME_ACTIVATED;
    }

    /**
     * Deletes all activated, fetched and defaults configs and resets all Product Config settings.
     */
    public void reset() {
        synchronized (this) {
            if (null != defaultConfig) {
                defaultConfig.clear();
            }

            activatedConfig.clear();
            try {
                FileUtils.deleteDirectory(context, config, getProductConfigDirName());
            } catch (Exception e) {
                e.printStackTrace();
                config.getLogger().verbose(ProductConfigUtil.getLogTag(config), "Product Config : reset failed: " + e.getLocalizedMessage());
            }
            setMinimumFetchIntervalInSeconds(DEFAULT_MIN_FETCH_INTERVAL_SECONDS);
        }
    }

    public void setArpValue(JSONObject arp) {
        settings.setARPValue(arp);
    }

    private void sendCallback(PROCESSING_STATE state) {
        if (state != null) {
            switch (state) {
                case INIT_SUCCESS:
                    listener.onInitSuccess();
                    break;
                case INIT_FAILED:
                    listener.onInitFailed();
                    break;
                case FETCHED:
                    listener.onFetched();
                    break;
                case ACTIVATED:
                    listener.onActivated();
                    break;
            }
        }

    }

    /**
     * Asynchronously fetches and then activates the fetched configs.
     */
    public void fetchAndActivate() {
        if (isFetchAndActivating)
            return;
        fetch();
    }

    private enum PROCESSING_STATE {
        INIT_SUCCESS,
        INIT_FAILED,
        FETCHED,
        ACTIVATED
    }
}