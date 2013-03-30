package com.example.loctracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

	private ToggleButton mLocToggle;
	private TextView mLocText;
	private LocationListener mLocListener;
	private LocationManager mLocManager;
	private File mLocFile;
	private OutputStreamWriter mLocFileWriter;
	private Boolean mFirstObj = true;

	private final String FILENAME = "loctracker.json";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// toggle
		mLocToggle = (ToggleButton) findViewById(R.id.locToggle);
		mLocToggle.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (isChecked) {
					startLocTracking();
				} else {
					stopLocTracking();
				}
			}

		});

		// text
		mLocText = (TextView) findViewById(R.id.locText);
		mLocText.setMovementMethod(new ScrollingMovementMethod());

		// set location manager
		mLocManager = (LocationManager) getSystemService(LOCATION_SERVICE);

		// set location listener
		mLocListener = new LocationListener() {

			@Override
			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			@Override
			public void onProviderEnabled(String provider) {
				// notify that a provider was enabled
				mLocText.setText("Provider enabled: " + provider + "\n"
						+ mLocText.getText());
			}

			@Override
			public void onProviderDisabled(String provider) {
				// notify that a provider was disabled
				mLocText.setText("Provider disabled: " + provider + "\n"
						+ mLocText.getText());
			}

			@Override
			public void onLocationChanged(Location loc) {
				processLocation(loc);
			}

		};

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private void startLocTracking() {
		// reset loc text
		mLocText.setText("");
		mFirstObj = true;

		// start location updates
		boolean hasProvider = false;

		// attach gps
		if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			mLocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocListener);
			hasProvider = true;
		}

		// attach network
		if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			mLocManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocListener);
			hasProvider = true;
		}

		// check if there is an active provider,
		// if not start location settings activity
		if (!hasProvider) {
			mLocToggle.setChecked(false);
			startActivityForResult(new Intent(
					android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS),
					0);
			return;
		}

		// get handle on file for writing location data
		try {
			// set file directory
			File dir = new File(Environment.getExternalStorageDirectory()
					.toString() + "/loctracker");
			if (!dir.isDirectory()) {
				// create the directory if not exists
				dir.mkdir();
			}
			// set the tracking file
			mLocFile = new File(dir, FILENAME);
			if (!mLocFile.isFile()) {
				// create the file if not exists
				mLocFile.createNewFile();
			}
			// create output stream and set file writer member
			FileOutputStream locFileOS = new FileOutputStream(mLocFile);
			mLocFileWriter = new OutputStreamWriter(locFileOS);
			mLocFileWriter.write("[");
		} catch (FileNotFoundException e) {
			mLocToggle.setChecked(false);
			Log.e("Trackr:FileNotFound", "An error occurred creating file: " + e.getMessage());
			return;
		} catch (IOException e) {
			mLocToggle.setChecked(false);
			Log.e("Trackr:IOException", "An error occurred writing to file:" + e.getMessage());
			return;
		}

		// start tracking notice
		mLocText.setText("Tracking started...\n" + mLocText.getText());
	}

	private void stopLocTracking() {
		// stop location updates
		mLocManager.removeUpdates(mLocListener);

		try {
			mLocFileWriter.write("]");
			mLocFileWriter.flush();
			mLocFileWriter.close();
		} catch (IOException e) {
			mLocText.setText("An error occurred closing file: "
					+ e.getMessage() + "\n" + mLocText.getText());
		}

		PostLocationData pld = new PostLocationData();
		pld.execute(mLocFile);

		// tracking stopped notice
		mLocText.setText("Tracking stopped...\n" + mLocText.getText());
	}

	private void processLocation(Location loc) {
		// notify lat, long and accuracy
		mLocText.setText(loc.getLatitude() + "," + loc.getLongitude()
				+ " (accuracy: " + loc.getAccuracy() + ")\n"
				+ mLocText.getText());
		JSONObject locJSON = locationToJSON(loc);
		try {
			if (!mFirstObj) {
				mLocFileWriter.write(",");
			}
			mLocFileWriter.write(locJSON.toString());
			mFirstObj = false;
		} catch (IOException e) {
			Log.e("Trackr:IOException", "Error writing location", e);
		}
	}

	private JSONObject locationToJSON(Location loc) {
		JSONObject obj = new JSONObject();

		try {
			obj.put("latitude", loc.getLatitude())
				.put("longitude", loc.getLongitude())
				.put("altitude", loc.getAltitude())
				.put("accuracy", loc.getAccuracy())
				.put("time", loc.getTime())
				.put("bearing", loc.getBearing())
				.put("speed", loc.getSpeed())
				.put("provider", loc.getProvider());

			return obj;
		} catch (JSONException e) {
			Log.e("Trackr:JSONException",
					"Error creating JSON location object", e);
		}

		return obj;
	}

	private class PostLocationData extends AsyncTask<File, Void, Boolean> {

		private final String mUploadEndpoint = "YOUR_UPLOAD_ENDPOINT_HERE";
		private final String mCType = "application/json";
		private final int mTimeout = 5000;

		@Override
		protected Boolean doInBackground(File... params) {
			// set file from params
			File file = params[0];

			// setup http post request
			HttpParams clientParams = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(clientParams, mTimeout);
			HttpConnectionParams.setSoTimeout(clientParams, mTimeout);
			HttpClient client = new DefaultHttpClient(clientParams);
			HttpPost post = new HttpPost(mUploadEndpoint);
			try {
				FileInputStream fileIS = new FileInputStream(file);
				InputStreamEntity entity = new InputStreamEntity(fileIS,
						file.length());
				entity.setContentType(mCType);
				entity.setContentEncoding(mCType);
				post.setEntity(entity);
			} catch (FileNotFoundException e) {
				return false;
			}

			// execute http request
			try {
				HttpResponse resp = client.execute(post);
				if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					Log.i("Trackr:AsyncResponse",
							"Received 200 response from server.");
					return true;
				}
				return false;
			} catch (ClientProtocolException e) {
				Log.e("Trackr:ClientProtocolException",
						"ClientProtocolException", e);
				return false;
			} catch (IOException e) {
				Log.e("Trackr:IOException", "IOException", e);
				return false;
			}
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (!result) {
				mLocText.setText("Unable to post location data. Check error log for details.");
			}
		}

	}

}
