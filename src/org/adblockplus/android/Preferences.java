package org.adblockplus.android;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	private final static String TAG = "Preferences";

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		setContentView(R.layout.preferences);
		addPreferencesFromResource(R.xml.preferences);
		copyAssets();
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		AdblockPlus.getApplication().startEngine();
		AdblockPlus.getApplication().startInteractive();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

		final AdblockPlus application = AdblockPlus.getApplication();
		
		RefreshableListPreference subscriptionList = (RefreshableListPreference) findPreference(getString(R.string.pref_subscription));
		List<Subscription> subscriptions = application.getSubscriptions();
		String[] entries = new String[subscriptions.size()];
		String[] entryValues = new String[subscriptions.size()];
		String current = prefs.getString(getString(R.string.pref_subscription), (String) null);
		int i = 0;
		for (Subscription subscription : subscriptions)
		{
			entries[i] = subscription.title;
			entryValues[i] = subscription.url;
			i++;
		}
		subscriptionList.setEntries(entries);
		subscriptionList.setEntryValues(entryValues);

		if (current == null)
		{
			Subscription offer = application.offerSubscription();
			current = offer.url;
			if (offer != null)
			{
				subscriptionList.setValue(offer.url);
	 			application.setSubscription(offer);
	 			new AlertDialog.Builder(this)
	 				.setTitle(R.string.app_name)
	 				.setMessage(String.format(getString(R.string.msg_subscription_offer, offer.title)))
	 				.setIcon(android.R.drawable.ic_dialog_info)
	 				.setPositiveButton(R.string.ok, null)
	 				.create()
	 				.show();
			}
		}
		
		subscriptionList.setOnRefreshClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				application.refreshSubscription();
			}
		});
		
		setPrefSummary(subscriptionList);
		registerReceiver(receiver, new IntentFilter(AdblockPlus.BROADCAST_SUBSCRIPTION_STATUS));
		registerReceiver(receiver, new IntentFilter(ProxyService.BROADCAST_PROXY_FAILED));

		final String url = current;
		
		(new Thread() {
			@Override
			public void run()
			{
				if (!application.checkSubscriptions())
				{
					Subscription subscription = application.getSubscription(url);
		 			application.setSubscription(subscription);
				}
			}
		}).start();

		boolean enabled = prefs.getBoolean(getString(R.string.pref_enabled), false);
		if (enabled && ! isServiceRunning())
		{
			setNotEnabled();
            enabled = false;
		}
		findPreference(getString(R.string.pref_port)).setEnabled(! enabled);
				
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.unregisterOnSharedPreferenceChangeListener(this);
		unregisterReceiver(receiver);
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean enabled = prefs.getBoolean(getString(R.string.pref_enabled), false);
		AdblockPlus.getApplication().stopInteractive();
		if (! enabled)
			AdblockPlus.getApplication().stopEngine(true);
	}

    private void setPrefSummary(Preference pref)
	{
        if (pref instanceof ListPreference)
        {
	        CharSequence summary = ((ListPreference) pref).getEntry();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
	}

    private void setNotEnabled()
    {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putBoolean(getString(R.string.pref_enabled), false);
        editor.commit();
        ((CheckBoxPreference) findPreference(getString(R.string.pref_enabled))).setChecked(false);
    }
    
	private boolean isServiceRunning()
	{
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
		{
			if ("org.adblockplus.android.ProxyService".equals(service.service.getClassName()))
				return true;
		}
		return false;
	}

	private void copyAssets()
	{
		// TODO Copy only if version changed
		AssetManager assetManager = getAssets();
		String[] files = null;
		try
		{
			files = assetManager.list("install");
		}
		catch (IOException e)
		{
			Log.e(TAG, e.getMessage());
		}
		for (int i = 0; i < files.length; i++)
		{
			InputStream in = null;
			OutputStream out = null;
			try
			{
				in = assetManager.open("install/" + files[i]);
				out = new FileOutputStream(ProxyService.BASE + files[i]);
				byte[] buffer = new byte[1024];
				int read;
				while ((read = in.read(buffer)) != -1)
				{
					out.write(buffer, 0, read);
				}
				in.close();
				in = null;
				out.flush();
				out.close();
				out = null;

			}
			catch (Exception e)
			{
				Log.e(TAG, "Asset copy error", e);
			}
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (getString(R.string.pref_enabled).equals(key))
		{
			boolean enabled = sharedPreferences.getBoolean(key, false);
			findPreference(getString(R.string.pref_port)).setEnabled(! enabled);
			if (enabled && ! isServiceRunning())
				startService(new Intent(this, ProxyService.class));
			else if (!enabled && isServiceRunning())
				stopService(new Intent(this, ProxyService.class));
		}
		if (getString(R.string.pref_subscription).equals(key))
		{
			String current = sharedPreferences.getString(key, null);
			AdblockPlus application = AdblockPlus.getApplication();
			Subscription subscription = application.getSubscription(current);
 			application.setSubscription(subscription);
		}

		Preference pref = findPreference(key);
		setPrefSummary(pref);
	}
	
	private BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, Intent intent)
		{
			String action = intent.getAction();
			Bundle extra = intent.getExtras();
			if (action.equals(ProxyService.BROADCAST_PROXY_FAILED))
			{
				String msg = extra.getString("msg");
	 			new AlertDialog.Builder(Preferences.this)
 				.setTitle(R.string.error)
 				.setMessage(msg)
 				.setIcon(android.R.drawable.ic_dialog_alert)
 				.setPositiveButton(R.string.ok, null)
 				.create()
 				.show();
				setNotEnabled();
			}
			if (action.equals(AdblockPlus.BROADCAST_SUBSCRIPTION_STATUS))
			{
				final String text = extra.getString("text");
				final long time = extra.getLong("time");
				runOnUiThread(new Runnable() {
					public void run()
	                {
						ListPreference subscriptionList = (ListPreference) findPreference(getString(R.string.pref_subscription));
				        CharSequence summary = subscriptionList.getEntry();
				        StringBuilder builder = new StringBuilder();
				        if (summary != null)
				        {
				        	builder.append(summary);
				        	if (text != "")
				        	{
				        		builder.append(" (");
				        		int id = getResources().getIdentifier(text, "string", getPackageName());
				        		if (id > 0)
				        			builder.append(getString(id, text));
				        		else
				        			builder.append(text);
				        		if (time > 0)
				        		{
				        			builder.append(": ");
				        			Calendar calendar = Calendar.getInstance();
				        			calendar.setTimeInMillis(time);
				        			Date date = calendar.getTime();
				        			builder.append(DateFormat.getDateFormat(context).format(date));
				        			builder.append(" ");
				        			builder.append(DateFormat.getTimeFormat(context).format(date));
				        		}
				        		builder.append(")");
				        	}
				        	subscriptionList.setSummary(builder.toString());
				        }
	                }
				});
			}
		}
	};
}