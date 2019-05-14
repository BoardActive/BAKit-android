package com.boardactive.bakit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class JobDispatcherService extends JobService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        ResultCallback<Status>
{

    public static final String TAG = JobDispatcherService.class.getSimpleName();

    /**
     * Update interval of location request
     */
    private final int UPDATE_INTERVAL = 5000;

    /**
     * fastest possible interval of location request
     */
    private final int FASTEST_INTERVAL = 2500;

    /**
     * LocationRequest instance
     */
    private LocationRequest locationRequest;

    /**
     * GoogleApiClient instance
     */
    private GoogleApiClient googleApiClient;

    /**
     * Location instance
     */
    private android.location.Location lastLocation;

    private BoardActive mBoardActive = new BoardActive();

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
        Log.d(TAG, "onStartJob() " + currentDateTimeString);
        int permissionState = ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            createGoogleApi();
        } else {
            Log.d(TAG, "No Location Permissions" + currentDateTimeString);
        }

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "onStopJob() Job cancelled!");
        return false;
    }

    /**
     * this method tells whether google api client connected.
     *
     * @param bundle - to get api instance
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected()");
        getLastKnownLocation();
    }

    /**
     * this method returns whether connection is suspended
     *
     * @param i - 0/1
     */
    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "connection suspended");
    }

    /**
     * this method checks connection status
     *
     * @param connectionResult - connected or failed
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "connection failed");
    }

    /**
     * this method tells the result of status of google api client
     *
     * @param status - success or failure
     */
    @Override
    public void onResult(@NonNull Status status) {
        Log.d(TAG, "result of google api client : " + status);
    }

    /**
     * Method is called when location is changed
     *
     * @param location - location from fused location provider
     */
    @Override
    public void onLocationChanged(android.location.Location location) {

        Log.d(TAG, "onLocationChanged [" + location + "]");
        lastLocation = location;
        writeActualLocation(location);
    }

    /**
     * extract last location if location is not available
     */
    @SuppressLint("MissingPermission")
    private void getLastKnownLocation() {
        lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if (lastLocation != null) {

            Log.d(TAG, "LastKnown location. " +
                    "  Lat: " + lastLocation.getLatitude() + " | Long: " + lastLocation.getLongitude());
            writeLastLocation();
            startLocationUpdates();

            DateFormat df = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss Z (zzzz)");
            String date = df.format(Calendar.getInstance().getTime());
            Double latitude = lastLocation.getLatitude();
            Double longitude = lastLocation.getLongitude();

            mBoardActive.postLocation(new BoardActive.LocationCallback() {
                @Override
                public void onResponse(Object value) {
                    Log.d(TAG, "onResponse" + value.toString());
                }
            }, latitude.toString(), longitude.toString(), date);

            Log.d(TAG, "LATLNG" + latitude.toString() + latitude.toString());

        } else {
            Log.d(TAG, "No location retrieved yet");
            startLocationUpdates();
        }
    }

    /**
     * this method writes location to text view or shared preferences
     *
     * @param location - location from fused location provider
     */
    @SuppressLint("SetTextI18n")
    private void writeActualLocation(android.location.Location location) {
        //here in this method we can do something with the location
    }

    /**
     * this method only provokes writeActualLocation().
     */
    private void writeLastLocation() {
        writeActualLocation(lastLocation);
    }

    /**
     * this method fetches location from fused location provider and passes to writeLastLocation
     */
    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    /**
     * Create google api instance
     */
    private void createGoogleApi() {
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        //connect google api
        googleApiClient.connect();

    }

}

