package org.nypr.cordova.wakeupplugin;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

    private Context context;
    private int duration;

    @SuppressLint({"SimpleDateFormat", "NewApi"})
    @Override
    public void onReceive(Context aContext, Intent intent) {
        context = aContext;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY);
        Log.d(TAG, "onReceive() expired at " + sdf.format(new Date().getTime()));

        try {


            Bundle extrasBundle = intent.getExtras();
            String extras = null;
            if (extrasBundle != null && extrasBundle.get("extra") != null) {
                extras = extrasBundle.get("extra").toString();
            }

            Configuration conf = new Configuration(new JSONObject(extras));

            Log.d(TAG, "onReceive() intent extras[" + extras + "]>" + conf.getSound() + " " + conf.getMessage() + " " + conf.getStreamUrl());

            // try to get the audio

            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            // Request audio focus for playback
            int result = am.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                                                  @Override
                                                  public void onAudioFocusChange(int focusChange) {
                                                      Log.d(TAG, "OnAudioFocusChangeListener onAudioFocusChange" + focusChange);
                                                  }
                                              },
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

                Log.d(TAG, "onReceive() notificationId " + conf.getId() + " stream@" + conf.getStreamUrl());

                NotificationManager manager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);

                Notification notification = buildNotification(
                        conf.getMessage(),
                        getResourceId("ic_fa_bell", "drawable"),
                        conf.getSound(),
                        meshIntentsForNotification(
                                context.getPackageManager().getLaunchIntentForPackage(
                                        context.getPackageName()
                                ),
                                conf.getStreamUrl(),
                                context.getPackageName()
                        )
                );
                manager.notify(conf.getId(), notification);

                // wait duration of jingle and then start stream
                this.duration = conf.getDuration();

                new Thread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "Duration: " + duration);

                        try {
                            Thread.sleep(duration * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        //startStream(context, localUrl);
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
        }
    }

    @NonNull
    private Notification buildNotification(String message, int appIconResId, Uri alarmSound, PendingIntent contentIntent) throws JSONException {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "org.nypr.cordova.wakeupplugin." + TAG)
                .setSmallIcon(appIconResId)
                .setContentTitle(message)
                .setAutoCancel(true);

        builder.setSound(alarmSound,AudioManager.STREAM_MUSIC);
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
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent mainIntent = new Intent(context, Class.forName(packageName + ".MainActivity"));
        mainIntent.setAction(MAIN_ACTION);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return contentIntent;
    }

    private int getResourceId(String resName, String type) {
        Log.d(TAG, "getResourceId() resName = [" + resName + "], type = [" + type + "] packageName = [" + context.getPackageName() + "]");
        return context.getResources().getIdentifier(resName, type, context.getPackageName());
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public class Configuration {
        private final JSONObject config;

        public Configuration(JSONObject config) {
            this.config = config;
        }

        public Uri getSound() throws JSONException {
            return Uri.parse(config.getString("sound"));
        }

        public String getMessage() throws JSONException {
            return config.getString("message");
        }

        public String getStreamUrl() throws JSONException {
            return config.getString("streamurl");
        }

        public int getId() throws JSONException {
            return config.getInt("id");
        }

        public int getDuration() throws JSONException {
            return config.getInt("duration");
        }
    }

}