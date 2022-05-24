package com.example.trackerapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.example.trackerapp.databinding.ActivityMapsBinding;
import com.example.trackerapp.directionhelpers.TaskLoadedCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.ui.IconGenerator;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener, TaskLoadedCallback, RoutingListener {
    private ActivityMapsBinding binding;
    private DatabaseReference reference;
    private LocationManager manager;

    private String fileName = "Test";
    private GoogleMap mMap;
    private String mLastUpdateTime;
    LatLng mCurrPosition = new LatLng(31.0461, 34.8516);
    MyLocation mCurrentLocation;


    private static final int REQUEST_CODE = 101;


    private final int MIN_TIME = 1000; // 1 sec
    private final float MIN_DISTANCE = (float) 5;

    private Polyline currentPolyline;
    ArrayList<LatLng> markerPoints = new ArrayList<>();
    private List<Polyline> polylines = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        reference = FirebaseDatabase.getInstance().getReference().child("LocationTracker").child(fileName);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        getLocationUpdates();

        readChanges();


    }

    private void readChanges() {
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.child("location").exists()) {
                    try {
                        MyLocation location = snapshot.child("location").getValue(MyLocation.class);
                        if (location != null) {
                            mCurrentLocation = location;
                            LatLng newMarker = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());

                            mCurrPosition = newMarker;
                            markerPoints.add(newMarker);
                            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                            addMarker();
                            findRoutes();
                        }
                    } catch (Exception e) {
                        Toast.makeText(MapsActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void addMarker() {
        MarkerOptions options = new MarkerOptions();
        IconGenerator iconFactory = new IconGenerator(this);
        iconFactory.setStyle(IconGenerator.STYLE_PURPLE);
        options.icon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(mLastUpdateTime)));
        options.anchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());

        options.position(mCurrPosition);
        Marker mapMarker = mMap.addMarker(options);
        long atTime = mCurrentLocation.getTime();
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date(atTime));
        mapMarker.setTitle(mLastUpdateTime);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(mCurrPosition));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrPosition, (float) 20));


    }


    private void getLocationUpdates() {
        Location location = null;
        if (manager != null) {
            if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    && (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {

                // getting GPS status
                boolean isGPSEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);

                // getting network status
                boolean isNetworkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (!isGPSEnabled && !isNetworkEnabled) {
                    Toast.makeText(this, "No Provider Enabled", Toast.LENGTH_SHORT).show();
                } else {
                    // First get location from gps
                    if (isGPSEnabled) {
                        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME, MIN_DISTANCE, this);
                        if (manager != null) {
                            location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (location != null) {
                                double longitude = location.getLongitude();
                                double latitude = location.getLatitude();
                                mCurrPosition = new LatLng(latitude, longitude);
                                mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                                saveLocation(location);
                            }
                        }
                    }
                    //get the location by Network Provider
                    if (isNetworkEnabled) {
                        if (location == null) {
                            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME, MIN_DISTANCE, this);
                            if (manager != null) {
                                location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                                if (location != null) {
                                    double longitude = location.getLongitude();
                                    double latitude = location.getLatitude();
                                    mCurrPosition = new LatLng(latitude, longitude);
                                    mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                                    saveLocation(location);
                                }
                            }
                        }
                    }
                }
            }

        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
        }

    }


    // function to find Routes.
    public void findRoutes() {
        if (markerPoints.size()>=2) {
            Routing routing = new Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.WALKING)
                    .withListener(this)
                    .alternativeRoutes(true)
                    .waypoints(markerPoints.get(0), markerPoints.get(markerPoints.size() - 1))
                    .key(getResources().getString(R.string.api_key))  //also define your api key here.
                    .build();
            routing.execute();
        }
//        else if (markerPoints.size()==1) {
//            Routing routing = new Routing.Builder()
//                    .travelMode(AbstractRouting.TravelMode.WALKING)
//                    .withListener(this)
//                    .alternativeRoutes(true)
//                    .waypoints(markerPoints.get(0))
//                    .key(getResources().getString(R.string.api_key))  //also define your api key here.
//                    .build();
//            routing.execute();
//        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (REQUEST_CODE) {
            case REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocationUpdates();
                } else {
                    Toast.makeText(this, "Permission Required", Toast.LENGTH_SHORT).show();
                }
        }
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);

        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setAllGesturesEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(mCurrPosition));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrPosition, (float) 20));//Animates camera and zooms to preferred state on the user's current location.

    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (location != null) {
            saveLocation(location);
        } else {
            Toast.makeText(this, "No Location", Toast.LENGTH_SHORT).show();
        }

    }

    private void saveLocation(Location location) {
        reference.child("location").setValue(location);
    }

    private void savePoint(LatLng point, String key) {
        reference.child(key).setValue(point);
    }

    @Override
    public void onTaskDone(Object... values) {
        if (currentPolyline != null)
            currentPolyline.remove();
        currentPolyline = mMap.addPolyline((PolylineOptions) values[0]);
    }

    @Override
    public void onRoutingFailure(RouteException e) {
        View parentLayout = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(parentLayout, e.toString(), Snackbar.LENGTH_LONG);
        snackbar.show();
    }

    @Override
    public void onRoutingStart() {
//        Toast.makeText(MapsActivity.this, "Finding Route...", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {

        if (polylines != null) {
            polylines.clear();
        }
        PolylineOptions polyOptions = new PolylineOptions();
        LatLng polylineStartLatLng = markerPoints.get(0);
        LatLng polylineEndLatLng = markerPoints.get(markerPoints.size() - 1);
        savePoint(polylineStartLatLng, "start");
        savePoint(polylineEndLatLng, "finish");


        polylines = new ArrayList<>();

        polyOptions.color(getResources().getColor(R.color.colorPrimary));
        polyOptions.width(7);
        polyOptions.addAll(markerPoints);
        Polyline polyline = mMap.addPolyline(polyOptions);
        polylines.add(polyline);

        //Add Marker on route starting position
//        MarkerOptions startMarker = new MarkerOptions();
//        startMarker.position(polylineStartLatLng);
//        startMarker.title("My Location");
//        mMap.addMarker(startMarker);

        //Add Marker on route ending position
//        MarkerOptions endMarker = new MarkerOptions();
//        endMarker.position(polylineEndLatLng);
//        endMarker.title("Destination");
//        mMap.addMarker(endMarker);
    }

    @Override
    public void onRoutingCancelled() {
        findRoutes();
    }
}