package com.ulas.travelbookapp.view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.room.Room;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;
import com.ulas.travelbookapp.R;
import com.ulas.travelbookapp.databinding.ActivityMapsBinding;
import com.ulas.travelbookapp.model.Place;
import com.ulas.travelbookapp.roomdb.PlaceDao;
import com.ulas.travelbookapp.roomdb.PlaceDataBase;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    LocationManager locationManager;
    LocationListener locationListener;
    ActivityResultLauncher<String> permissionLauncher;
    private final CompositeDisposable mDisposable = new CompositeDisposable();
    Double selectedLatitude;
    Double selectedLongitude;
    Place placeFromMain;
    PlaceDataBase db;
    PlaceDao placeDao;
    SharedPreferences sharedPreferences;
    boolean trackBoolean;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        registerLauncher();

        sharedPreferences = MapsActivity.this.getSharedPreferences("com.atilsamancioglu.travelbookjava",MODE_PRIVATE);
        trackBoolean = false;

        selectedLatitude = 0.0;
        selectedLongitude= 0.0;

        binding.saveButton.setEnabled(false);

        db = Room.databaseBuilder(getApplicationContext(),
                        PlaceDataBase.class, "Places")
                //.allowMainThreadQueries()
                .build();

        placeDao = db.placeDao();

    }


    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLongClickListener(this);

        Intent intent = getIntent();
        String info = intent.getStringExtra("info");

        try {
            if ("new".equals(info)) {
                binding.saveButton.setVisibility(View.VISIBLE);
                binding.deleteButton.setVisibility(View.GONE);

                locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
                locationListener = location -> {
                    trackBoolean = sharedPreferences.getBoolean("trackBoolean",false);

                    if(!trackBoolean) {
                        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
                        sharedPreferences.edit().putBoolean("trackBoolean",true).apply();
                    }
                };

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    //request permission
                    if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION)) {
                        Snackbar.make(binding.getRoot(),"Permission needed for maps", Snackbar.LENGTH_INDEFINITE)
                                .setAction("Give Permission", v -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION))
                                .show();
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                    }
                } else {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                    Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastLocation != null) {
                        LatLng lastUserLocation = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastUserLocation, 15));
                    }

                    mMap.setMyLocationEnabled(true);
                }
            } else {
                mMap.clear();
                placeFromMain = (Place) intent.getSerializableExtra("place");
                LatLng latLng = new LatLng(placeFromMain.latitude, placeFromMain.longitude);

                mMap.addMarker(new MarkerOptions().position(latLng).title(placeFromMain.name));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

                binding.placeNameText.setText(placeFromMain.name);
                binding.saveButton.setVisibility(View.GONE);
                binding.deleteButton.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e("MapsActivity", "Error in onMapReady: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {

        mMap.clear();

        mMap.addMarker(new MarkerOptions().position(latLng));

        selectedLatitude = latLng.latitude;
        selectedLongitude = latLng.longitude;

        binding.saveButton.setEnabled(true);

    }

    private void handleResponse() {
        Intent intent = new Intent(this,MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    public void save(View view) {

        //placeDao.insert(place).subscribeOn(Schedulers.io()).subscribe();

        Place place = new Place(binding.placeNameText.getText().toString(),selectedLatitude,selectedLongitude);

        mDisposable.add(placeDao.insert(place)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(MapsActivity.this::handleResponse));

    }

    public void delete(View view) {

        mDisposable.add(placeDao.delete(placeFromMain)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(MapsActivity.this::handleResponse));

    }
    private void registerLauncher() {
        permissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
                    if(result) {
                        //permission granted
                        if (ContextCompat.checkSelfPermission(MapsActivity.this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (lastLocation != null) {
                                LatLng lastUserLocation = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastUserLocation, 15));
                            }
                        }

                    } else {
                        //permission denied
                        Toast.makeText(MapsActivity.this,"Permisson needed!",Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDisposable != null) {
            mDisposable.clear();
        }
    }

}