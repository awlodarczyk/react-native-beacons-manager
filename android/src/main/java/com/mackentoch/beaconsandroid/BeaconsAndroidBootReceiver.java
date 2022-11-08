package com.mackentoch.beaconsandroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import static com.mackentoch.beaconsandroid.BeaconsAndroidModule.LOG_TAG;

public class BeaconsAndroidBootReceiver extends BroadcastReceiver
{
  @Override
  public void onReceive(Context context, Intent intent)
  {
    Log.d(LOG_TAG, "onReceive...");

    //context.startService(new Intent(context, BeaconsAndroidTransitionService.class));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(new Intent(context, BeaconsAndroidTransitionService.class));
    } else {
      context.startService(new Intent(context, BeaconsAndroidTransitionService.class));
    }
  }
}
