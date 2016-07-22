package org.nypr.cordova.wakeupplugin;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class WakeupReceiver extends BroadcastReceiver {

	private static final String LOG_TAG = "WakeupReceiver";
	public static final String MAIN_ACTION               = "ACTION MAIN";

	@SuppressLint({ "SimpleDateFormat", "NewApi" })
	@Override
	public void onReceive(Context context, Intent intent) {
		SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Log.d(LOG_TAG, "wakeuptimer expired at " + sdf.format(new Date().getTime()));
	
	
		
	
		try {
			String packageName = context.getPackageName();
			Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
			String className = launchIntent.getComponent().getClassName();		    	
			Log.d(LOG_TAG, "launching activity for class " + className);

			@SuppressWarnings("rawtypes")
			Class c = Class.forName(className); 

			Intent i = new Intent(context, c);
			i.putExtra("wakeup", true);
			Bundle extrasBundle = intent.getExtras();
			String extras=null;
			if (extrasBundle!=null && extrasBundle.get("extra")!=null) {
				extras = extrasBundle.get("extra").toString();
			}
			
			if (extras!=null) {
				i.putExtra("extra", extras);
			}

			JSONObject notificationSound = new JSONObject(extras);

			Log.d(LOG_TAG, "wakeuptimer extras[" + extras + "]>" + notificationSound.getString("sound") + notificationSound.getString("message"));

			// try to get the audio

			AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			// Request audio focus for playback
			int result = am.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
												  @Override
												  public void onAudioFocusChange(int focusChange) {
													  Log.d(LOG_TAG, "onAudioFocusChange");
												  }
											  },
					// Use the music stream.
					AudioManager.STREAM_MUSIC,
					// Request permanent focus.
					AudioManager.AUDIOFOCUS_GAIN);

			if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
				// am.registerMediaButtonEventReceiver(RemoteControlReceiver); //do we even need that?
				// Start playback
				PackageManager pm=context.getPackageManager();
				ApplicationInfo applicationInfo=pm.getApplicationInfo(packageName,PackageManager.GET_META_DATA);
				Resources resources=pm.getResourcesForApplication(applicationInfo);
				int appIconResId=applicationInfo.icon;
				//Bitmap appIconBitmap=BitmapFactory.decodeResource(resources,appIconResId);

				NotificationCompat.Builder builder = new NotificationCompat.Builder(
						context).setSmallIcon(appIconResId)
						.setContentTitle(notificationSound.getString("message")).setAutoCancel(true);
				Uri alarmSound = Uri.parse(notificationSound.getString("sound"));

				int notificationId = Integer.parseInt(notificationSound.getString("id"));

				Log.d(LOG_TAG, "notificationId " + notificationId);

				builder.setSound(alarmSound);
				//Intent notificationIntent = new Intent(context, WakeupReceiver.class);
				// contentIntent must redirect to App
				//PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent,
				//		PendingIntent.FLAG_UPDATE_CURRENT);

				Intent mainIntent = new Intent(context, Class.forName("de.ndr.app.njoy.MainActivity"));
				mainIntent.setAction(MAIN_ACTION);
				mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
				//PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, mainIntent, 0);
				PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				builder.setContentIntent(pendingIntent);
				NotificationManager manager = (NotificationManager)
						context.getSystemService(Context.NOTIFICATION_SERVICE);
				manager.notify(notificationId, builder.build());

			}
			
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(i);

			if(WakeupPlugin.connectionCallbackContext!=null) {
				JSONObject o=new JSONObject();
				o.put("type", "wakeup");
				if (extras!=null) {
					o.put("extra", extras);
				}
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, o);
				pluginResult.setKeepCallback(true);
				WakeupPlugin.connectionCallbackContext.sendPluginResult(pluginResult);  
			}
			
			if (extrasBundle!=null && extrasBundle.getString("type")!=null && extrasBundle.getString("type").equals("daylist")) {
				// repeat in one week
				Date next = new Date(new Date().getTime() + (7 * 24 * 60 * 60 * 1000));
				Log.d(LOG_TAG,"resetting alarm at " + sdf.format(next));
	
				Intent reschedule = new Intent(context, WakeupReceiver.class);
				if (extras!=null) {
					reschedule.putExtra("extra", intent.getExtras().get("extra").toString());
				}
				reschedule.putExtra("day", WakeupPlugin.daysOfWeek.get(intent.getExtras().get("day")));
	
				PendingIntent sender = PendingIntent.getBroadcast(context, 19999 + WakeupPlugin.daysOfWeek.get(intent.getExtras().get("day")), intent, PendingIntent.FLAG_UPDATE_CURRENT);
				AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
				if (Build.VERSION.SDK_INT>=19) {
					alarmManager.setExact(AlarmManager.RTC_WAKEUP, next.getTime(), sender);
				} else {
					alarmManager.set(AlarmManager.RTC_WAKEUP, next.getTime(), sender);
				}
			}

		} catch (JSONException e){
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
	}
}
