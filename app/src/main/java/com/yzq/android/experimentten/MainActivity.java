package com.yzq.android.experimentten;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;

public class MainActivity extends AppCompatActivity {
    private static final String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION};

    private MapView mapView = null;
    private ToggleButton toggle;

    private Bitmap bitmap;
    private BitmapDescriptor bitmapDescriptor;

    private SensorManager sensorManager;
    private Sensor magneticSensor, accelerometerSensor;
    private BaiduMap baiduMap = null;
    private CoordinateConverter converter = new CoordinateConverter();
    private LocationManager locationManager;
    private Location mLocation;

    private float[] accValues = new float[3];
    private float[] magValues = new float[3];
    private float degree = 0;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        showPhonePermissions();

        mapView = (MapView) findViewById(R.id.baidu_map);
        toggle = (ToggleButton) findViewById(R.id.toggle_position);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        baiduMap = mapView.getMap();
        mapView.showZoomControls(false);
        baiduMap.setMyLocationEnabled(true);
        bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_orientations);
        bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
        MyLocationConfiguration config = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, bitmapDescriptor);
        baiduMap.setMyLocationConfigeration(config);

        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (toggle.isChecked()) {
                    followPosition();
                    toggle.setChecked(true);
                    locationBindListener();
                }
            }
        });

        baiduMap.setOnMapTouchListener(new BaiduMap.OnMapTouchListener() {
            @Override
            public void onTouch(MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        toggle.setChecked(false);
                        locationUnbindListener();
                        break;
                    default:
                        break;
                }
            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showPhonePermissions() {
        for (final String permission : permissions) {
            int permissionsCheck = ContextCompat.checkSelfPermission(this, permission);
            if (permissionsCheck != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    requestPermissions(new String[]{permission}, 1);
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{permission}, 1);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onResume() {
        sensorManager.registerListener(sensorEventListener, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(sensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
        locationBindListener();
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(sensorEventListener);
        unbindListener();
        super.onPause();
        mapView.onPause();
    }

    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            switch (sensorEvent.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    accValues = sensorEvent.values;
                    if (Math.abs(accValues[0]) > 18 || Math.abs(accValues[1]) > 18 || Math.abs(accValues[2]) > 18)
                        Toast.makeText(MainActivity.this, "Shake it baby!", Toast.LENGTH_SHORT).show();
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    magValues = sensorEvent.values;
                    break;
                default:
                    break;
            }
            calAndSetOrientation();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    private void calAndSetOrientation() {
        float[] R = new float[9];
        float[] values = new float[3];

        SensorManager.getRotationMatrix(R, null, accValues, magValues);
        SensorManager.getOrientation(R, values);
        degree = (float) Math.toDegrees(values[0]);

        if (mLocation != null) {
            converter.from(CoordinateConverter.CoordType.GPS);
            converter.coord(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()));
            LatLng desLatLng = converter.convert();

            MyLocationData locationData = new MyLocationData.Builder().direction(degree)
                    .latitude(desLatLng.latitude).longitude(desLatLng.longitude).build();
            baiduMap.setMyLocationData(locationData);
        }
    }

    private LocationListener fixLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            mLocation = location;
            fixPosition(location);
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {
            Toast.makeText(MainActivity.this, "Provider now available", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderDisabled(String s) {
            Toast.makeText(MainActivity.this, "Provider is unavailable", Toast.LENGTH_SHORT).show();
        }
    };

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            mLocation = location;
            setPosition(location);
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };

    private void fixPosition(Location location) {
        converter.from(CoordinateConverter.CoordType.GPS);
        converter.coord(new LatLng(location.getLatitude(), location.getLongitude()));
        LatLng desLatLng = converter.convert();

        MyLocationData locationData = new MyLocationData.Builder().direction(degree)
                .latitude(desLatLng.latitude).longitude(desLatLng.longitude).build();
        baiduMap.setMyLocationData(locationData);

        MapStatus mapStatus = new MapStatus.Builder().zoom(18).target(desLatLng).build();
        MapStatusUpdate mapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mapStatus);
        baiduMap.animateMapStatus(mapStatusUpdate);
    }

    private void setPosition(Location location) {
        converter.from(CoordinateConverter.CoordType.GPS);
        converter.coord(new LatLng(location.getLatitude(), location.getLongitude()));
        LatLng desLatLng = converter.convert();

        MyLocationData locationData = new MyLocationData.Builder().direction(degree)
                .latitude(desLatLng.latitude).longitude(desLatLng.longitude).build();
        baiduMap.setMyLocationData(locationData);
    }

    private void followPosition() {
        converter.from(CoordinateConverter.CoordType.GPS);
        converter.coord(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()));
        LatLng desLatLng = converter.convert();

        MapStatus mapStatus = new MapStatus.Builder().zoom(18).target(desLatLng).build();
        MapStatusUpdate mapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mapStatus);
        baiduMap.animateMapStatus(mapStatusUpdate);
    }

    private void locationBindListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, fixLocationListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, fixLocationListener);
        }
    }

    private void locationUnbindListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(fixLocationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }

    private void unbindListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(fixLocationListener);
            locationManager.removeUpdates(locationListener);
        }
    }
}
