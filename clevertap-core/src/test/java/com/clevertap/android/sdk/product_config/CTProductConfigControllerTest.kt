package com.clevertap.android.sdk.product_config

import android.os.Looper.getMainLooper
import com.clevertap.android.sdk.BaseAnalyticsManager
import com.clevertap.android.sdk.BaseCallbackManager
import com.clevertap.android.sdk.CallbackManager
import com.clevertap.android.sdk.CoreMetaData
import com.clevertap.android.sdk.DeviceInfo
import com.clevertap.android.sdk.MockDeviceInfo
import com.clevertap.android.sdk.task.CTExecutorFactory
import com.clevertap.android.sdk.task.MockCTExecutors
import com.clevertap.android.sdk.utils.FileUtils
import com.clevertap.android.shared.test.BaseTestCase
import org.json.JSONObject
import org.junit.*
import org.junit.Test
import org.junit.jupiter.api.*
import org.junit.jupiter.api.MethodOrderer.*
import org.junit.runner.*
import org.mockito.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@TestMethodOrder(OrderAnnotation::class)
class CTProductConfigControllerTest : BaseTestCase() {

    private lateinit var mProductConfigController: CTProductConfigController
    private lateinit var coreMetaData: CoreMetaData
    private lateinit var analyticsManager: BaseAnalyticsManager
    private lateinit var callbackManager: BaseCallbackManager
    private lateinit var productConfigSettings: ProductConfigSettings
    private lateinit var deviceInfo: DeviceInfo
    private lateinit var fileUtils: FileUtils
    private lateinit var listener: CTProductConfigListener

    @Throws(Exception::class)
    @BeforeEach
    override fun setUp() {
        super.setUp()
        mockStatic(CTExecutorFactory::class.java).use {
            `when`(CTExecutorFactory.getInstance(cleverTapInstanceConfig)).thenReturn(MockCTExecutors())
            val guid = "1212121221"
            coreMetaData = CoreMetaData()
            analyticsManager = mock(BaseAnalyticsManager::class.java)
            deviceInfo = MockDeviceInfo(application, cleverTapInstanceConfig, guid, coreMetaData)
            callbackManager = CallbackManager(cleverTapInstanceConfig, deviceInfo)
            listener = mock(CTProductConfigListener::class.java)
            callbackManager.productConfigListener = listener
            productConfigSettings = mock(ProductConfigSettings::class.java)
            `when`(productConfigSettings.guid).thenReturn(guid)
            fileUtils = spy(FileUtils(application,cleverTapInstanceConfig))
            mProductConfigController = CTProductConfigController(
                application,
                cleverTapInstanceConfig,
                analyticsManager,
                coreMetaData,
                callbackManager,
                productConfigSettings, fileUtils
            )
        }

        mProductConfigController.setDefaults(MockPCResponse().getDefaultConfig())
    }

    @Test
    fun testInit() {
        Assert.assertTrue(mProductConfigController.isInitialized)
    }

    @Test
    fun testFetch_Valid_Guid_Window_Expired() {
        val windowInSeconds = TimeUnit.MINUTES.toSeconds(12)
        `when`(productConfigSettings.nextFetchIntervalInSeconds).thenReturn(windowInSeconds)
        val lastResponseTime = System.currentTimeMillis() - 2 * windowInSeconds * TimeUnit.SECONDS.toMillis(1)
        `when`(productConfigSettings.lastFetchTimeStampInMillis).thenReturn(lastResponseTime)

        mProductConfigController.fetch()
        verify(analyticsManager).sendFetchEvent(any())
        Assert.assertTrue(coreMetaData.isProductConfigRequested)
    }

    @Test
    fun testFetch_Valid_Guid_Window_Not_Expired_Request_Not_Sent() {
        shadowOf(getMainLooper()).idle()
        val windowInSeconds = TimeUnit.MINUTES.toSeconds(12)
        `when`(productConfigSettings.nextFetchIntervalInSeconds).thenReturn(windowInSeconds)
        val lastResponseTime = System.currentTimeMillis() - windowInSeconds / 2 * TimeUnit.SECONDS.toMillis(1)
        `when`(productConfigSettings.lastFetchTimeStampInMillis).thenReturn(lastResponseTime)

        mProductConfigController.fetch()
        verify(analyticsManager, never()).sendFetchEvent(any())
        Assert.assertFalse(coreMetaData.isProductConfigRequested)
    }

    @Test
    fun testFetch_InValid_Guid_Request_Not_Sent() {
        val windowInSeconds = TimeUnit.MINUTES.toSeconds(12)
        `when`(productConfigSettings.nextFetchIntervalInSeconds).thenReturn(windowInSeconds)
        val lastResponseTime = System.currentTimeMillis() - (windowInSeconds * 1000 * 2)
        `when`(productConfigSettings.lastFetchTimeStampInMillis).thenReturn(lastResponseTime)
        `when`(productConfigSettings.guid).thenReturn("")
        mProductConfigController.fetch()
        verify(analyticsManager, never()).sendFetchEvent(any())
        Assert.assertFalse(coreMetaData.isProductConfigRequested)
    }

    @Test
    fun testReset_Settings() {
        mProductConfigController.resetSettings()
        verify(productConfigSettings).reset(any(FileUtils::class.java))
    }

    @Test
    fun test_Reset() {
        mockStatic(CTExecutorFactory::class.java).use {
            `when`(CTExecutorFactory.getInstance(cleverTapInstanceConfig)).thenReturn(MockCTExecutors())
            mProductConfigController.reset()
            Assert.assertEquals(mProductConfigController.defaultConfig.size, 0)
            Assert.assertEquals(mProductConfigController.activatedConfig.size, 0)
            verify(productConfigSettings).initDefaults()
            verify(fileUtils).deleteDirectory(mProductConfigController.productConfigDirName)
        }
    }

    @Test
    fun test_setArpValue() {
        val jsonObject = JSONObject()
        mProductConfigController.setArpValue(jsonObject)
        verify(productConfigSettings).setARPValue(jsonObject)
    }

    @Test
    fun test_setMinimumFetchIntervalInSeconds() {
        val timeInSec = TimeUnit.MINUTES.toSeconds(5)
        mProductConfigController.setMinimumFetchIntervalInSeconds(timeInSec)
        verify(productConfigSettings).setMinimumFetchIntervalInSeconds(timeInSec)
    }

    @Test
    fun test_Getters() {
        Assert.assertEquals(mProductConfigController.analyticsManager, analyticsManager)
        Assert.assertEquals(mProductConfigController.callbackManager, callbackManager)
        Assert.assertEquals(mProductConfigController.config, cleverTapInstanceConfig)
        Assert.assertEquals(mProductConfigController.coreMetaData, coreMetaData)
        Assert.assertEquals(mProductConfigController.settings, productConfigSettings)
    }

    @Test
    fun test_activate() {

        mockStatic(CTExecutorFactory::class.java).use {
            shadowOf(getMainLooper()).idle()
            `when`(CTExecutorFactory.getInstance(cleverTapInstanceConfig)).thenReturn(MockCTExecutors())
            `when`(fileUtils.readFromFile(mProductConfigController.activatedFullPath)).thenReturn(
                JSONObject(
                    MockPCResponse().getFetchedConfig() as Map<*, *>
                ).toString()
            )
            mProductConfigController.activate()
            verify(listener).onActivated()
            Assert.assertEquals(333333L, mProductConfigController.getLong("fetched_long"))
            Assert.assertEquals("This is fetched string", mProductConfigController.getString("fetched_str"))
            Assert.assertEquals(44444.4444, mProductConfigController.getDouble("fetched_double"), 0.1212)
            Assert.assertEquals(true, mProductConfigController.getBoolean("fetched_bool"))
            Assert.assertEquals("This is def_string", mProductConfigController.getString("def_str"))
            Assert.assertEquals(11111L, mProductConfigController.getLong("def_long"))
            Assert.assertEquals(2222.2222, mProductConfigController.getDouble("def_double"), 0.1212)
            Assert.assertEquals(false, mProductConfigController.getBoolean("def_bool"))
        }
    }

    @Test
    fun test_activate_Fail() {
        mockStatic(CTExecutorFactory::class.java).use {
            `when`(productConfigSettings.guid).thenReturn("")
            `when`(CTExecutorFactory.getInstance(cleverTapInstanceConfig)).thenReturn(MockCTExecutors())
            mProductConfigController.activate()
            verify(listener, never()).onActivated()
        }
    }

    @Test
    fun test_fetch_activate() {
        mockStatic(CTExecutorFactory::class.java).use {
            `when`(CTExecutorFactory.getInstance(cleverTapInstanceConfig)).thenReturn(MockCTExecutors())
            val windowInSeconds = TimeUnit.MINUTES.toSeconds(12)
            `when`(productConfigSettings.nextFetchIntervalInSeconds).thenReturn(windowInSeconds)
            val lastResponseTime = System.currentTimeMillis() - 2 * windowInSeconds * TimeUnit.SECONDS.toMillis(1)
            `when`(productConfigSettings.lastFetchTimeStampInMillis).thenReturn(lastResponseTime)

            mProductConfigController.fetchAndActivate()
            verify(analyticsManager).sendFetchEvent(any())
            Assert.assertTrue(coreMetaData.isProductConfigRequested)

            `when`(CTExecutorFactory.getInstance(cleverTapInstanceConfig)).thenReturn(MockCTExecutors())

            `when`(fileUtils.readFromFile(mProductConfigController.activatedFullPath)).thenReturn(
                JSONObject(
                    MockPCResponse().getFetchedConfig() as Map<*, *>
                ).toString()
            )
            mProductConfigController.activate()
            verify(listener).onActivated()
            Assert.assertEquals(mProductConfigController.getString("fetched_str"), "This is fetched string")
            Assert.assertEquals(mProductConfigController.getLong("fetched_long"), 333333L)
            Assert.assertEquals(mProductConfigController.getDouble("fetched_double"), 44444.4444, 0.1212)
            Assert.assertEquals(mProductConfigController.getBoolean("fetched_bool"), true)
            Assert.assertEquals(mProductConfigController.getString("def_str"), "This is def_string")
            Assert.assertEquals(mProductConfigController.getLong("def_long"), 11111L)
            Assert.assertEquals(mProductConfigController.getDouble("def_double"), 2222.2222, 0.1212)
            Assert.assertEquals(mProductConfigController.getBoolean("def_bool"), false)
        }
    }

    @Test
    fun test_getLastFetchTimeStampInMillis() {
        mProductConfigController.lastFetchTimeStampInMillis
        verify(productConfigSettings).lastFetchTimeStampInMillis
    }

    @Test
    fun test_onFetchSuccess() {
        mockStatic(CTExecutorFactory::class.java).use {
            `when`(CTExecutorFactory.getInstance(cleverTapInstanceConfig)).thenReturn(MockCTExecutors())
            mProductConfigController.onFetchSuccess(MockPCResponse().getMockPCResponse())
            verify(fileUtils).writeJsonToFile(
                ArgumentMatchers.eq(mProductConfigController.productConfigDirName),
                ArgumentMatchers.eq(CTProductConfigConstants.FILE_NAME_ACTIVATED),
                any(JSONObject::class.java)
            )
            verify(listener).onFetched()
        }
    }

    @Test
    fun test_onFetchFailed() {
        mProductConfigController.onFetchFailed()
        Assert.assertEquals(mProductConfigController.isFetchAndActivating, false)
    }
}