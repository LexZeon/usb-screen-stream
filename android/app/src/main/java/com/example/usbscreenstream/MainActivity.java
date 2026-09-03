package com.example.usbscreenstream;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;
    private static final String TAG = "UsbScreenStream";

    private MediaProjectionManager projectionManager;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(createContentView());
        maybeRequestNotifications();
    }

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("USB Screen Stream");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText("Start projection, then connect from Windows.");
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(16), 0, dp(20));
        root.addView(status, statusParams);

        Button start = new Button(this);
        start.setText("Start projection");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestProjection();
            }
        });
        root.addView(start, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopProjection();
            }
        });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stopParams.setMargins(0, dp(10), 0, 0);
        root.addView(stop, stopParams);

        return root;
    }

    private void requestProjection() {
        Log.i(TAG, "Requesting MediaProjection permission");
        Intent intent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    }

    private void stopProjection() {
        Intent intent = new Intent(this, ScreenStreamService.class);
        intent.setAction(ScreenStreamService.ACTION_STOP);
        startService(intent);
        status.setText("Stopped.");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA_PROJECTION) {
            return;
        }

        if (resultCode != RESULT_OK || data == null) {
            Log.w(TAG, "MediaProjection permission denied or data missing");
            status.setText("Projection permission was not granted.");
            return;
        }

        Log.i(TAG, "MediaProjection permission granted; starting service");
        Intent intent = new Intent(this, ScreenStreamService.class);
        intent.setAction(ScreenStreamService.ACTION_START);
        intent.putExtra(ScreenStreamService.EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(ScreenStreamService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        status.setText("Ready. Connect from Windows over USB.");
        Log.i(TAG, "Start service request sent");
    }

    private void maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
