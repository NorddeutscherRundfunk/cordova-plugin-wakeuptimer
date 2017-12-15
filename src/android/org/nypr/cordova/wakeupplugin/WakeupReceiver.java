package org.nypr.cordova.wakeupplugin;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WakeupReceiver extends BroadcastReceiver {

    private static final String TAG = "WakeupReceiver";
    public static final String MAIN_ACTION = "ACTION MAIN";

    private Context localContext;
    private int duration;

    @SuppressLint({"SimpleDateFormat", "NewApi"})
    @Override
    public void onReceive(Context context, Intent intent) {
        localContext = context;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY);
        Log.d(TAG, "wakeuptimer expired at " + sdf.format(new Date().getTime()));

        try {
            String packageName = localContext.getPackageName();
            Intent launchIntent = localContext.getPackageManager().getLaunchIntentForPackage(packageName);
            String className = launchIntent.getComponent().getClassName();
            Bundle extrasBundle = intent.getExtras();
            String extras = null;
            if (extrasBundle != null && extrasBundle.get("extra") != null) {
                extras = extrasBundle.get("extra").toString();
            }

            JSONObject notificationSound = new JSONObject(extras);

            Log.d(TAG, "wakeuptimer extras[" + extras + "]>" + notificationSound.getString("sound") + notificationSound.getString("message") + notificationSound.getString("streamurl"));

            // try to get the audio

            AudioManager am = (AudioManager) localContext.getSystemService(Context.AUDIO_SERVICE);
            // Request audio focus for playback
            int result =
                    am.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                                             @Override
                                             public void onAudioFocusChange(int focusChange) {
                                                 Log.d(TAG, "onAudioFocusChange" + focusChange);
                                             }
                                         },
                            // Use the music stream.
                            AudioManager.STREAM_MUSIC,
                            // Request permanent focus.
                            AudioManager.AUDIOFOCUS_GAIN);
  /*
    am.requestAudioFocus(new AudioFocusRequest.Builder(AudioManager.STREAM_MUSIC).setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                        @Override
                        public void onAudioFocusChange(int focusChange) {
                            Log.d(TAG, "onAudioFocusChange" + focusChange);
                        }
                    }).setFocusGain(AudioManager.AUDIOFOCUS_GAIN).build());
*/

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                PackageManager pm = localContext.getPackageManager();
                ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                Resources resources = pm.getResourcesForApplication(applicationInfo);
                int appIconResId = applicationInfo.icon;

                Uri alarmSound = Uri.parse(notificationSound.getString("sound"));

                int notificationId = Integer.parseInt(notificationSound.getString("id"));
                String streamUrl = notificationSound.getString("streamurl");

                Log.d(TAG, "notificationId " + notificationId + " stream@" + streamUrl);
                String localUrl = streamUrl;

                NotificationManager manager = (NotificationManager)
                        localContext.getSystemService(Context.NOTIFICATION_SERVICE);

                Notification notification = buildNotification(
                        notificationSound.getString("message"),
                        appIconResId,
                        alarmSound,
                        meshIntentsForNotification(
                                context.getPackageManager().getLaunchIntentForPackage(
                                        context.getPackageName()
                                ),
                                localUrl,
                                packageName
                        )
                );
                manager.notify(notificationId, notification);

                // wait duration of jingle and then start stream
                this.duration = Integer.parseInt(notificationSound.getString("duration"));

                new Thread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "Duration: " + duration);

                        try {
                            Thread.sleep(duration * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        //startStream(localContext, localUrl);
                        WakeupPlugin.fireEvent("please start the stream :)");

                    }
                }).start();

            }

            if (WakeupPlugin.connectionCallbackContext != null) {
                JSONObject o = new JSONObject();
                o.put("type", "wakeup");
                if (extras != null) {
                    o.put("extra", extras);
                }
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, o);
                pluginResult.setKeepCallback(true);
                WakeupPlugin.connectionCallbackContext.sendPluginResult(pluginResult);
            }

            if (extrasBundle != null && extrasBundle.getString("type") != null && extrasBundle.getString("type").equals("daylist")) {
                // repeat in one week
                Date next = new Date(new Date().getTime() + (7 * 24 * 60 * 60 * 1000));
                Log.d(TAG, "resetting alarm at " + sdf.format(next));

                Intent reschedule = new Intent(context, WakeupReceiver.class);
                if (extras != null) {
                    reschedule.putExtra("extra", intent.getExtras().get("extra").toString());
                }
                reschedule.putExtra("day", WakeupPlugin.daysOfWeek.get(intent.getExtras().get("day")));

                PendingIntent sender = PendingIntent.getBroadcast(context, 19999 + WakeupPlugin.daysOfWeek.get(intent.getExtras().get("day")), intent, PendingIntent.FLAG_UPDATE_CURRENT);
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (Build.VERSION.SDK_INT >= 19) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, next.getTime(), sender);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, next.getTime(), sender);
                }
            }

        } catch (JSONException e) {
            Log.w(TAG, "onReceive: ", e);
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "onReceive: ", e);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "onReceive: ", e);
        }
    }

    @NonNull
    private Notification buildNotification(String message, int appIconResId, Uri alarmSound, PendingIntent contentIntent) throws JSONException {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(localContext, "org.nypr.cordova.wakeupplugin." + TAG)
                .setSmallIcon(appIconResId)
                .setContentTitle(message)
                .setAutoCancel(true);

        builder.setSound(alarmSound);
        builder.setContentIntent(contentIntent);
        return builder.build();
    }


    private PendingIntent meshIntentsForNotification(Intent intent, String localUrl, String packageName) throws ClassNotFoundException {
        Intent notificationIntent = intent;

        notificationIntent.addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        notificationIntent.putExtra("streamurl", localUrl);
        notificationIntent.putExtra("playStream", "true");

        // contentIntent must redirect to App
        PendingIntent contentIntent = PendingIntent.getActivity(localContext, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent mainIntent = new Intent(localContext, Class.forName(packageName + ".MainActivity"));
        mainIntent.setAction(MAIN_ACTION);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return contentIntent;
    }


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) localContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}