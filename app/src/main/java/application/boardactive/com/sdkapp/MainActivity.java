/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package application.boardactive.com.sdkapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.onesignal.OSPermissionSubscriptionState;
import com.onesignal.OneSignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * BoardActive 2018.08.05
 */

public class MainActivity extends BaseActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    // Used in checking for runtime permissions.
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    // The BroadcastReceiver used to listen from broadcasts from the service.
    private MyReceiver myReceiver;

    // A reference to the service used to get location updates.
    private LocationUpdatesService mService = null;

    // Tracks the bound state of the service.
    private boolean mBound = false;

    // UI elements.
    private Button mRequestLocationUpdatesButton;
    private Button mRemoveLocationUpdatesButton;

    private Button mbtn_onesignal_notification;
    private Button mbtn_onesignal_deeplink;

    private Location mLocation;

    private AdDropVertAdapter mVertAdapter;
    private RecyclerView mVertRecyclerView;
    ProgressDialog progressDoalog;

    private List<AdDrop> addropList;
    private ProgressDialog progressDialog;
    SwipeRefreshLayout mSwipeRefreshLayout;

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    // Monitors the state of the connection to the service.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationUpdatesService.LocalBinder binder = (LocationUpdatesService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myReceiver = new MyReceiver();
        setContentView(R.layout.activity_main);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.getting_data));

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.activity_main_swipe_refresh_layout);

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                onResume();
            }
        });

        // Check that the user hasn't revoked permissions by going to Settings.
        if (Utils.requestingLocationUpdates(this)) {
            if (!checkPermissions()) {
                requestPermissions();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);

        mRequestLocationUpdatesButton = (Button) findViewById(R.id.request_location_updates_button);
        mRemoveLocationUpdatesButton = (Button) findViewById(R.id.remove_location_updates_button);

        mbtn_onesignal_notification = (Button) findViewById(R.id.btn_onesignal_notification);
        mbtn_onesignal_deeplink = (Button) findViewById(R.id.btn_onesignal_deeplink);

        mRequestLocationUpdatesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!checkPermissions()) {
                    requestPermissions();
                } else {
                    mService.requestLocationUpdates();
                }
            }
        });

        mRemoveLocationUpdatesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.removeLocationUpdates();
            }
        });

        mbtn_onesignal_notification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                OSPermissionSubscriptionState status = OneSignal.getPermissionSubscriptionState();
                String userId = status.getSubscriptionStatus().getUserId();
                boolean isSubscribed = status.getSubscriptionStatus().getSubscribed();

                if (!isSubscribed)
                    return;

                try {
                    JSONObject notificationContent = new JSONObject("{'contents': {'en': 'This is a test of the BoardActive SDK'}," +
                            "'include_player_ids': ['" + userId + "'], " +
                            "'headings': {'en': 'BoardActive Test'}, " +
                            "'big_picture': 'https://res.cloudinary.com/axiomaim/image/upload/v1525722681/boardactive/icon.png'}");
                    OneSignal.postNotification(notificationContent, null);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });

        mbtn_onesignal_deeplink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                OSPermissionSubscriptionState status = OneSignal.getPermissionSubscriptionState();
                String userID = status.getSubscriptionStatus().getUserId();
                boolean isSubscribed = status.getSubscriptionStatus().getSubscribed();

                if (!isSubscribed)
                    return;

                try {
                    OneSignal.postNotification(new JSONObject("{'contents': {'en':'This is a test of the BoardActive SDK'}, " +
                                    "'include_player_ids': ['" + userID + "'], " +
                                    "'headings': {'en': 'BoardActive Test {{user_name}}'}, " +
                                    "'data': {'openURL': 'https://res.cloudinary.com/axiomaim/image/upload/v1525722681/boardactive/icon.png'}," +
                                    "'buttons':[{'id': 'id1', 'text': 'Go to DeepLink'}, {'id':'id2', 'text': 'Go to AdDrop'}]}"),
                            new OneSignal.PostNotificationResponseHandler() {
                                @Override
                                public void onSuccess(JSONObject response) {
                                    Log.i("OneSignalExample", "postNotification Success: " + response);
                                }

                                @Override
                                public void onFailure(JSONObject response) {
                                    Log.e("OneSignalExample", "postNotification Failure: " + response);
                                }
                            });
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });

        // Restore the state of the buttons when the activity (re)launches.
        setButtonsState(Utils.requestingLocationUpdates(this));

        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        bindService(new Intent(this, LocationUpdatesService.class), mServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    private void startDataRequest() {
        new GetDataTask().execute();
    }


    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(myReceiver,
                new IntentFilter(LocationUpdatesService.ACTION_BROADCAST));

        startDataRequest();


    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mBound) {
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            unbindService(mServiceConnection);
            mBound = false;
        }
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    /**
     * Returns the current state of the permissions needed.
     */
    private boolean checkPermissions() {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    findViewById(R.id.activity_main),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Request permission
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    })
                    .show();
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                mService.requestLocationUpdates();
            } else {
                // Permission denied.
                setButtonsState(false);
                Snackbar.make(
                        findViewById(R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }
    }

    /**
     * Receiver for broadcasts sent by {@link LocationUpdatesService}.
     */
    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mLocation = intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION);
            if (mLocation != null) {

                new PostLocation().execute();

                Toast.makeText(MainActivity.this, Utils.getLocationText(mLocation),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        // Update the buttons state depending on whether location updates are being requested.
        if (s.equals(Utils.KEY_REQUESTING_LOCATION_UPDATES)) {
            setButtonsState(sharedPreferences.getBoolean(Utils.KEY_REQUESTING_LOCATION_UPDATES,
                    false));
        }
    }

    private void setButtonsState(boolean requestingLocationUpdates) {
        if (requestingLocationUpdates) {
            mRequestLocationUpdatesButton.setEnabled(false);
            mRemoveLocationUpdatesButton.setEnabled(true);
        } else {
            mRequestLocationUpdatesButton.setEnabled(true);
            mRemoveLocationUpdatesButton.setEnabled(false);
        }
    }

    private class PostLocation extends AsyncTask<Void, Void, Response> {

        OSPermissionSubscriptionState status = OneSignal.getPermissionSubscriptionState();
        String userId = status.getSubscriptionStatus().getUserId();

        String ANDROID_PLAYER_ID = userId; //Tom's Android

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Response doInBackground(Void... params) {
            OkHttpClient client = new OkHttpClient();

            EndPointUrl endPointUrl = EndpointUrlProvider.getDefaultEndPointUrl();
            String url = endPointUrl.getUrl();

            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, "{\n" +
                    "  \"lat\": 33.7490,\n" +
                    "  \"lng\": 84.3880\n" +
                    "}");

            Request request = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .addHeader("X-BoardActive-Application-Key", "key")
                    .addHeader("X-BoardActive-Application-Secret", "secret")
                    .addHeader("X-BoardActive-Advertiser-Id", "*")
                    .addHeader("X-BoardActive-Device-Id", ANDROID_PLAYER_ID)
                    .addHeader("X-BoardActive-Device-OS", "android")
                    .addHeader("X-BoardActive-Latitude", "" + mLocation.getLatitude())
                    .addHeader("X-BoardActive-Longitude", "" + mLocation.getLongitude())
                    .post(body)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                return response;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Response response) {
            if (response != null && response.isSuccessful()) {
                try {


                    String responseData = response.body().string();
//                    addropList = response.body().string();
//                    Toast.makeText(MainActivity.this, responseData,
//                            Toast.LENGTH_SHORT).show();

                } catch (IOException e) {
                    showErrorMessage();
                }
            } else {
//                showErrorMessage();
            }
        }
    }

    private class GetDataTask extends AsyncTask<Void, Void, Response> {

        String IOS_PLAYER_ID = "b11abea5-5a74-43f3-b58a-57f141614695"; //Tom's iPhone
        String ANDROID_PLAYER_ID = "a66f7bbc-f98c-4684-bbb7-e08721c8ddba"; //Tom's Android

        @Override
        protected void onPreExecute() {
            progressDialog.show();
        }

        @Override
        protected Response doInBackground(Void... params) {
            OkHttpClient client = new OkHttpClient();

            EndPointUrl endPointUrl = EndpointUrlProvider.getDefaultEndPointUrl();
            String url = endPointUrl.getUrl();

            Request request = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .addHeader("X-BoardActive-Application-Key", "key")
                    .addHeader("X-BoardActive-Application-Secret", "secret")
                    .addHeader("X-BoardActive-Advertiser-Id", "*")
                    .addHeader("X-BoardActive-Device-Id", ANDROID_PLAYER_ID)
                    .addHeader("X-BoardActive-Device-OS", "android")
                    .addHeader("X-BoardActive-Latitude", "33.889760")
                    .addHeader("X-BoardActive-Longitude", "-84.469898")
                    .build()
                    ;

            try {
                Response response = client.newCall(request).execute();
                return response;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Response response) {
            if (response != null && response.isSuccessful()) {
                try {

                    String responseData = response.body().string();

                    GsonBuilder gsonBuilder = new GsonBuilder();
                    Gson gson = gsonBuilder.create();
                    List<AdDrop> dataList = Arrays.asList(gson.fromJson(responseData, AdDrop[].class));
//                    generateHorzDataList(dataList);
                    generateVertDataList(dataList);

                } catch (IOException e) {
                    showErrorMessage();
                }
            } else {
                showErrorMessage();
            }

            progressDialog.dismiss();
        }
    }


//    private void generateHorzDataList(List<AdDrop> adDropList) {
//        mHorzRecyclerView = findViewById(R.id.custom_horz_recyclerview);
//        mHorxAdapter = new AdDropHorzAdapter(this,adDropList);
//        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
//        mHorzRecyclerView.setLayoutManager(layoutManager);
//        mHorzRecyclerView.setAdapter(mHorxAdapter);
//    }

    private void generateVertDataList(List<AdDrop> adDropList) {
        mVertRecyclerView = findViewById(R.id.custom_vert_recyclerview);
        mVertAdapter = new AdDropVertAdapter(this,adDropList);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
        mVertRecyclerView.setLayoutManager(layoutManager);
        mVertRecyclerView.setAdapter(mVertAdapter);
        mSwipeRefreshLayout.setRefreshing(false);

    }

}

