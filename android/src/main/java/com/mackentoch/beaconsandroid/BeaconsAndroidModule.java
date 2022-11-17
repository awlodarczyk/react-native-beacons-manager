package com.mackentoch.beaconsandroid;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.bluetooth.le.ScanFilter;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class BeaconsAndroidModule extends ReactContextBaseJavaModule
//  implements BeaconConsumer
{
  public static final String LOG_TAG = "BeaconsAndroidModule";
  private static final String NOTIFICATION_CHANNEL_ID = "BeaconsAndroidModule";
  private static final int RUNNING_AVG_RSSI_FILTER = 0;
  private static final int ARMA_RSSI_FILTER = 1;
  private BeaconManager mBeaconManager;
  private Context mApplicationContext;
  private ReactApplicationContext mReactContext;
  private static boolean channelCreated = false;
  private static boolean isActivityActivated = true;
  private static boolean shouldDropEmptyRanges = false;
  public static final String TRANSITION_TASK_NAME = "beacons-monitor-transition";

  public static final String RUUVI_LAYOUT = "m:0-2=0499,i:4-19,i:20-21,i:22-23,p:24-24"; // TBD
  public static final String IBEACON_LAYOUT = "m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24";
  public static final String IBEACON_2_LAYOUT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24";
  public static final String IBEACON_3_LAYOUT = "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";
  public static final String ALTBEACON_LAYOUT = BeaconParser.ALTBEACON_LAYOUT;
  public static final String EDDYSTONE_UID_LAYOUT = BeaconParser.EDDYSTONE_UID_LAYOUT;
  public static final String EDDYSTONE_URL_LAYOUT = BeaconParser.EDDYSTONE_URL_LAYOUT;
  public static final String EDDYSTONE_TLM_LAYOUT = BeaconParser.EDDYSTONE_TLM_LAYOUT;

  public BeaconsAndroidModule(ReactApplicationContext reactContext) {
    super(reactContext);
    Log.d(LOG_TAG, "BeaconsAndroidModule - started");
    this.mReactContext = reactContext;
    this.mApplicationContext = reactContext.getApplicationContext();
    this.mBeaconManager = BeaconManager.getInstanceForApplication(mApplicationContext);
//        BeaconManager.setDebug(true);
    // need to bind at instantiation so that service loads (to test more)

    this.mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(IBEACON_LAYOUT));
    this.mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(RUUVI_LAYOUT));
//        this.mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(IBEACON_2_LAYOUT));
//        this.mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(IBEACON_3_LAYOUT));
//        this.mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(ALTBEACON_LAYOUT));
    this.mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(EDDYSTONE_UID_LAYOUT));
    this.mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(EDDYSTONE_URL_LAYOUT));
    this.mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(EDDYSTONE_TLM_LAYOUT));
    // Uncomment the code below to use a foreground service to scan for beacons. This unlocks
    // the ability to continually scan for long periods of time in the background on Andorid 8+
    // in exchange for showing an icon at the top of the screen and a always-on notification to
    // communicate to users that your app is using resources in the background.

    Resources res = mApplicationContext.getResources();
    String packageName = mApplicationContext.getPackageName();
    String CHANNEL_ID = "rn-push-notification-channel-id";
    String CHANNEL_NAME = res.getString(res.getIdentifier("notification_channel_name", "string", packageName));
    String CHANNEL_DESCRIPTION = res.getString(res.getIdentifier("notification_channel_description", "string", packageName));

    Notification.Builder builder = new Notification.Builder(mApplicationContext);
    builder.setSmallIcon(mApplicationContext.getResources().getIdentifier("ic_launcher", "mipmap", mApplicationContext.getPackageName()));
    builder.setPriority(Notification.PRIORITY_MIN);
    //builder.setContentTitle("Scanning for Beacons");
    builder.setContentTitle(CHANNEL_DESCRIPTION);

    Class intentClass = getMainActivityClass();
    Intent intent = new Intent(mApplicationContext, intentClass);
    PendingIntent pendingIntent = PendingIntent.getActivity(
      mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
    );
    builder.setContentIntent(pendingIntent);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
        CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
      channel.setDescription(CHANNEL_DESCRIPTION);
      NotificationManager notificationManager = (NotificationManager) mApplicationContext.getSystemService(
        Context.NOTIFICATION_SERVICE);
      notificationManager.createNotificationChannel(channel);
      builder.setChannelId(channel.getId());
    }
    if (this.mBeaconManager.getForegroundServiceNotificationId() != 456) {
      this.mBeaconManager.enableForegroundServiceScanning(builder.build(), 456);
    }
    // For the above foreground scanning service to be useful, you need to disable
    // JobScheduler-based scans (used on Android 8+) and set a fast background scan
    // cycle that would otherwise be disallowed by the operating system.
    //
    this.mBeaconManager.setEnableScheduledScanJobs(false);
    this.mBeaconManager.setBackgroundBetweenScanPeriod(0);
    this.mBeaconManager.setBackgroundScanPeriod(1100);

    this.mBeaconManager.addRangeNotifier(mRangeNotifier);
    this.mBeaconManager.addMonitorNotifier(mMonitorNotifier);
  }

  @ReactMethod
  public void init(Callback resolve, Callback reject) {
    try {
      Log.d(LOG_TAG, "BeaconsAndroidModule - init");
      // Fix: may not be called after consumers are already bound beacon
      if (mBeaconManager != null && !mBeaconManager.isAnyConsumerBound()) {

//        Resources res = mApplicationContext.getResources();
//        String packageName = mApplicationContext.getPackageName();
//        String CHANNEL_ID = "rn-push-notification-channel-id";
//        String CHANNEL_NAME = res.getString(res.getIdentifier("notification_channel_name", "string", packageName));
//        String CHANNEL_DESCRIPTION = res.getString(res.getIdentifier("notification_channel_description", "string", packageName));
//
//        Notification.Builder builder = new Notification.Builder(mApplicationContext);
//        builder.setSmallIcon(mApplicationContext.getResources().getIdentifier("ic_notification", "mipmap", mApplicationContext.getPackageName()));
//        //builder.setContentTitle("Scanning for Beacons");
//        builder.setContentTitle(CHANNEL_DESCRIPTION);
//        Class intentClass = getMainActivityClass();
//        Intent intent = new Intent(mApplicationContext, intentClass);
//        PendingIntent pendingIntent = PendingIntent.getActivity(mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//        builder.setContentIntent(pendingIntent);
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//          NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
//          channel.setDescription(CHANNEL_DESCRIPTION);
//          NotificationManager notificationManager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
//          notificationManager.createNotificationChannel(channel);
//          builder.setChannelId(channel.getId());
//        }

//        mBeaconManager.enableForegroundServiceScanning(builder.build(), 456);

        sendEvent(mReactContext, "beaconServiceConnected", null);


        bindManager();
      }

      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void stop(Callback resolve, Callback reject) {
    try {
      Log.d(LOG_TAG, "BeaconsAndroidModule - stop");

      NotificationManager notificationManager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.cancel(456);

      if (mBeaconManager != null) {
        unbindManager();
      }

      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @Override
  public String getName() {
    return LOG_TAG;
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("SUPPORTED", BeaconTransmitter.SUPPORTED);
    constants.put("NOT_SUPPORTED_MIN_SDK", BeaconTransmitter.NOT_SUPPORTED_MIN_SDK);
    constants.put("NOT_SUPPORTED_BLE", BeaconTransmitter.NOT_SUPPORTED_BLE);
    constants.put("NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS", BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS);
    constants.put("NOT_SUPPORTED_CANNOT_GET_ADVERTISER", BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER);
    constants.put("RUNNING_AVG_RSSI_FILTER", RUNNING_AVG_RSSI_FILTER);
    constants.put("ARMA_RSSI_FILTER", ARMA_RSSI_FILTER);
    return constants;
  }

  @ReactMethod
  public void setHardwareEqualityEnforced(Boolean e) {
    Beacon.setHardwareEqualityEnforced(e.booleanValue());
  }

  public void bindManager() {
    mBeaconManager.applySettings();
//    if (!mBeaconManager.isBound(this)) {
//      Log.d(LOG_TAG, "BeaconsAndroidModule - bindManager: ");
//      mBeaconManager.bind(this);
//    }
  }

  public void unbindManager() {
//    if (mBeaconManager.isBound(this)) {
//      Log.d(LOG_TAG, "BeaconsAndroidModule - unbindManager: ");
//      mBeaconManager.unbind(this);
//    }
  }

  @ReactMethod
  public void addParser(String parser, Callback resolve, Callback reject) {
    try {
      Log.d(LOG_TAG, "BeaconsAndroidModule - addParser: " + parser);
      unbindManager();
      mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
      bindManager();
      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void removeParser(String parser, Callback resolve, Callback reject) {
    try {
      Log.d(LOG_TAG, "BeaconsAndroidModule - removeParser: " + parser);
      unbindManager();
      mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
      bindManager();
      resolve.invoke();
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void addParsersListToDetection(ReadableArray parsers, Callback resolve, Callback reject) {
    try {
      unbindManager();
      for (int i = 0; i < parsers.size(); i++) {
        String parser = parsers.getString(i);
        Log.d(LOG_TAG, "addParsersListToDetection - add parser: " + parser);
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
      }
      bindManager();
      resolve.invoke(parsers);
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void removeParsersListToDetection(ReadableArray parsers, Callback resolve, Callback reject) {
    try {
      unbindManager();
      for (int i = 0; i < parsers.size(); i++) {
        String parser = parsers.getString(i);
        Log.d(LOG_TAG, "removeParsersListToDetection - remove parser: " + parser);
        mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
      }
      bindManager();
      resolve.invoke(parsers);
    } catch (Exception e) {
      reject.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void setBackgroundScanPeriod(int period) {
    mBeaconManager.setBackgroundScanPeriod((long) period);
  }

  @ReactMethod
  public void setBackgroundBetweenScanPeriod(int period) {
    mBeaconManager.setBackgroundBetweenScanPeriod((long) period);
  }

  @ReactMethod
  public void setForegroundScanPeriod(int period) {
    mBeaconManager.setForegroundScanPeriod((long) period);
  }

  @ReactMethod
  public void setForegroundBetweenScanPeriod(int period) {
    mBeaconManager.setForegroundBetweenScanPeriod((long) period);
  }

  @ReactMethod
  public void setRssiFilter(int filterType, double avgModifier) {
    String logMsg = "Could not set the rssi filter.";
    if (filterType == RUNNING_AVG_RSSI_FILTER) {
      logMsg = "Setting filter RUNNING_AVG";
      BeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
      if (avgModifier > 0) {
        RunningAverageRssiFilter.setSampleExpirationMilliseconds((long) avgModifier);
        logMsg += " with custom avg modifier";
      }
    } else if (filterType == ARMA_RSSI_FILTER) {
      logMsg = "Setting filter ARMA";
      BeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);
      if (avgModifier > 0) {
        ArmaRssiFilter.setDEFAULT_ARMA_SPEED(avgModifier);
        logMsg += " with custom avg modifier";
      }
    }
    Log.d(LOG_TAG, logMsg);
  }

  @ReactMethod
  public void checkTransmissionSupported(Callback callback) {
    int result = BeaconTransmitter.checkTransmissionSupported(mReactContext);
    callback.invoke(result);
  }

  @ReactMethod
  public void getMonitoredRegions(Callback callback) {
    WritableArray array = new WritableNativeArray();
    for (Region region : mBeaconManager.getMonitoredRegions()) {
      WritableMap map = new WritableNativeMap();
      map.putString("identifier", region.getUniqueId());
      map.putString("uuid", region.getId1().toString());
      map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
      map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
      array.pushMap(map);
    }
    callback.invoke(array);
  }

  @ReactMethod
  public void getRangedRegions(Callback callback) {
    WritableArray array = new WritableNativeArray();
    for (Region region : mBeaconManager.getRangedRegions()) {
      WritableMap map = new WritableNativeMap();
      map.putString("region", region.getUniqueId());
      map.putString("uuid", region.getId1().toString());
      array.pushMap(map);
    }
    callback.invoke(array);
  }

  @ReactMethod
  public void shouldDropEmptyRanges(boolean should) {
    this.shouldDropEmptyRanges = should;
  }
  /***********************************************************************************************
   * BeaconConsumer
   **********************************************************************************************/
//  @Override
//  public void onBeaconServiceConnect() {
//    Log.v(LOG_TAG, "onBeaconServiceConnect");
//
//    // deprecated since v2.9 (see github: https://github.com/AltBeacon/android-beacon-library/releases/tag/2.9)
////         mBeaconManager.setMonitorNotifier(mMonitorNotifier);
////         mBeaconManager.setRangeNotifier(mRangeNotifier);
//    mBeaconManager.addMonitorNotifier(mMonitorNotifier);
//    mBeaconManager.addRangeNotifier(mRangeNotifier);
//    sendEvent(mReactContext, "beaconServiceConnected", null);
//  }
//
//  @Override
//  public Context getApplicationContext() {
//    return mApplicationContext;
//  }
//
//  @Override
//  public void unbindService(ServiceConnection serviceConnection) {
//    mApplicationContext.unbindService(serviceConnection);
//  }
//
//  @Override
//  public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
//    return mApplicationContext.bindService(intent, serviceConnection, i);
//  }

  /***********************************************************************************************
   * Monitoring
   **********************************************************************************************/
  @ReactMethod
  public void startMonitoring(String regionId, String beaconUuid, int minor, int major, Callback resolve, Callback reject) {
    Log.d(LOG_TAG, "startMonitoring, monitoringRegionId: " + regionId + ", monitoringBeaconUuid: " + beaconUuid + ", minor: " + minor + ", major: " + major);
    try {
      Region region = createRegion(
        regionId,
        beaconUuid.isEmpty() ? null : beaconUuid,
        String.valueOf(minor).equals("-1") ? null : String.valueOf(minor),
        String.valueOf(major).equals("-1") ? null : String.valueOf(major)
      );
      mBeaconManager.startMonitoring(region);

      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "startMonitoring, error: ", e);
      reject.invoke(e.getMessage());
    }
  }

  private MonitorNotifier mMonitorNotifier = new MonitorNotifier() {
    @Override
    public void didEnterRegion(Region region) {
      Log.i(LOG_TAG, "regionDidEnter");

      sendEvent(mReactContext, "regionDidEnter", createMonitoringResponse(region));

      //wakeUpAppIfNotRunning();
    }

    @Override
    public void didExitRegion(Region region) {
      Log.i(LOG_TAG, "regionDidExit");
      sendEvent(mReactContext, "regionDidExit", createMonitoringResponse(region));
    }

    @Override
    public void didDetermineStateForRegion(int i, Region region) {
      Log.i(LOG_TAG, "didDetermineStateForRegion");
      sendEvent(mReactContext, "didDetermineStateForRegion", createMonitoringResponse(region));
    }
  };

  private WritableMap createMonitoringResponse(Region region) {
    WritableMap map = new WritableNativeMap();
    map.putString("identifier", region.getUniqueId());
    map.putString("uuid", region.getId1() != null ? region.getId1().toString() : "");
    map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
    map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
    return map;
  }

  @ReactMethod
  public void stopMonitoring(String regionId, String beaconUuid, int minor, int major, Callback resolve, Callback reject) {
    Region region = createRegion(
      regionId,
      beaconUuid,
      String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
      String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
      // minor,
      // major
    );

    try {
      mBeaconManager.stopMonitoringBeaconsInRegion(region);

      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "stopMonitoring, error: ", e);
      reject.invoke(e.getMessage());
    }
  }

  /***********************************************************************************************
   * Ranging
   **********************************************************************************************/
  @ReactMethod
  public void startRanging(String regionId, String beaconUuid, Callback resolve, Callback reject) {
    Log.d(LOG_TAG, "startRanging, rangingRegionId: " + regionId + ", rangingBeaconUuid: " + beaconUuid);
    try {
      Region region = createRegion(regionId, beaconUuid);
      Log.d(LOG_TAG, region.toString());
      mBeaconManager.startRangingBeacons(region);
      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "startRanging, error: ", e);
      reject.invoke(e.getMessage());
    }
  }

  private RangeNotifier mRangeNotifier = new RangeNotifier() {
    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
      if (shouldDropEmptyRanges && beacons.isEmpty()) {
        return;
      }
      Log.d(LOG_TAG, "rangingConsumer didRangeBeaconsInRegion, beacons: " + beacons.toString());
      Log.d(LOG_TAG, "rangingConsumer didRangeBeaconsInRegion, region: " + region.toString());
      sendEvent(mReactContext, "beaconsDidRange", createRangingResponse(beacons, region));

      if (!beacons.isEmpty()) {
        wakeUpAppIfNotRunning();
      }
    }
  };

  private WritableMap createRangingResponse(Collection<Beacon> beacons, Region region) {
    WritableMap map = new WritableNativeMap();
    map.putString("identifier", region.getUniqueId());
    map.putString("uuid", region.getId1() != null ? region.getId1().toString() : "");
    WritableArray a = new WritableNativeArray();
    for (Beacon beacon : beacons) {
      WritableMap b = new WritableNativeMap();
      b.putString("uuid", beacon.getId1().toString());
      if (beacon.getIdentifiers().size() > 2) {
        b.putInt("major", beacon.getId2().toInt());
        b.putInt("minor", beacon.getId3().toInt());
      }
      b.putInt("rssi", beacon.getRssi());
      if (beacon.getDistance() == Double.POSITIVE_INFINITY
        || Double.isNaN(beacon.getDistance())
        || beacon.getDistance() == Double.NaN
        || beacon.getDistance() == Double.NEGATIVE_INFINITY) {
        b.putDouble("distance", 999.0);
        b.putString("proximity", "far");
      } else {
        b.putDouble("distance", beacon.getDistance());
        b.putString("proximity", getProximity(beacon.getDistance()));
      }
      a.pushMap(b);
    }
    map.putArray("beacons", a);
    return map;
  }

  private String getProximity(double distance) {
    if (distance == -1.0) {
      return "unknown";
    } else if (distance < 1) {
      return "immediate";
    } else if (distance < 3) {
      return "near";
    } else {
      return "far";
    }
  }

  @ReactMethod
  public void stopRanging(String regionId, String beaconUuid, Callback resolve, Callback reject) {
//    if (!mBeaconManager.isBound(this)) {
//      return;
//    }
    Region region = createRegion(regionId, beaconUuid);
    try {
      mBeaconManager.stopRangingBeacons(region);
      resolve.invoke();
    } catch (Exception e) {
      Log.e(LOG_TAG, "stopRanging, error: ", e);
      reject.invoke(e.getMessage());
    }
  }


  /***********************************************************************************************
   * Utils
   **********************************************************************************************/
  private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    if (reactContext.hasActiveCatalystInstance()) {
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
    }
  }

  private Region createRegion(String regionId, String beaconUuid) {
    Identifier id1 = (beaconUuid == null) ? null : Identifier.parse(beaconUuid);
    return new Region(regionId, id1, null, null);
  }

  private Region createRegion(String regionId, String beaconUuid, String minor, String major) {
    Identifier id1 = (beaconUuid == null) ? null : Identifier.parse(beaconUuid);
    return new Region(
      regionId,
      id1,
      major != null && major.length() > 0 ? Identifier.parse(major) : null,
      minor != null && minor.length() > 0 ? Identifier.parse(minor) : null
    );
  }

  private Class getMainActivityClass() {
    String packageName = mApplicationContext.getPackageName();
    Intent launchIntent = mApplicationContext.getPackageManager().getLaunchIntentForPackage(packageName);
    String className = launchIntent.getComponent().getClassName();
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return null;
    }
  }

  private void checkOrCreateChannel(NotificationManager manager) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
      return;
    if (channelCreated)
      return;
    if (manager == null)
      return;

    @SuppressLint("WrongConstant") NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Smart_Space_Pro_Channel", android.app.NotificationManager.IMPORTANCE_HIGH);
    channel.setDescription("Smart_Space_Pro_Channel_Description");
    channel.enableLights(true);
    channel.enableVibration(true);

    manager.createNotificationChannel(channel);
    channelCreated = true;
  }

  private void createNotification(String title, String message) {
    Class intentClass = getMainActivityClass();
    Intent notificationIntent = new Intent(mApplicationContext, intentClass);
    Integer requestCode = new Random().nextInt(10000);
    PendingIntent contentIntent = PendingIntent.getActivity(mApplicationContext, requestCode, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    NotificationCompat.Builder notification = new NotificationCompat.Builder(mApplicationContext, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title)
      .setContentText(message)
      .setAutoCancel(false)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
      .setContentIntent(contentIntent);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      notification.setCategory(NotificationCompat.CATEGORY_CALL);
    }

    NotificationManager notificationManager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
    checkOrCreateChannel(notificationManager);

    Notification info = notification.build();
    info.defaults |= Notification.DEFAULT_LIGHTS;

    notificationManager.notify(requestCode, info);
  }

  private Boolean isActivityRunning(Class activityClass) {
    ActivityManager activityManager = (ActivityManager) mApplicationContext.getSystemService(Context.ACTIVITY_SERVICE);
    List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

    for (ActivityManager.RunningTaskInfo task : tasks) {
      if (activityClass.getCanonicalName().equalsIgnoreCase(task.baseActivity.getClassName()))
        return true;
    }

    return false;
  }

  private void wakeUpAppIfNotRunning() {
    Class intentClass = getMainActivityClass();
    Boolean isRunning = isActivityRunning(intentClass);

    if (!isRunning) {
      Intent intent = new Intent(mApplicationContext, intentClass);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      // Important:  make sure to add android:launchMode="singleInstance" in the manifest
      // to keep multiple copies of this activity from getting created if the user has
      // already manually launched the app.
      mApplicationContext.startActivity(intent);
    }
  }
}
