/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.cpp.pointtopoint;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.projecttango.examples.cpp.util.TangoInitializationHelper;

import org.opencv.android.CameraBridgeViewBase;

/**
 * Primary activity of the example.
 */
public class MainActivity extends Activity {
  private static final String TAG = MainActivity.class.getSimpleName();

  // The minimum Tango Core version required from this application.
  private static final int  MIN_TANGO_CORE_VERSION = 9377;

  // The package name of Tang Core, used for checking minimum Tango Core version.
  private static final String TANGO_PACKAGE_NAME = "com.projecttango.tango";

  // The interval at which we'll update our UI debug text in milliseconds.
  // This is the rate at which we query our native wrapper around the tango
  // service for pose and event information.
  private static final int UPDATE_UI_INTERVAL_MS = 10;

  // GLSurfaceView and renderer, all of the graphic content is rendered
  // through OpenGL ES 2.0 in native code.
  private GLSurfaceView mGLView;
  private GLSurfaceRenderer mRenderer;
  // Current frame's pose information.
  private TextView mDistanceMeasure;

  private CheckBox mBilateralBox;
  private boolean mBilateralFiltering;

  // Handles the debug text UI update loop.
  private Handler mHandler = new Handler();

  private CameraBridgeViewBase mOpenCvCameraView;

  // Tango Service connection.
  ServiceConnection mTangoServiceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName name, IBinder service) {
        JNIInterface.onTangoServiceConnected(service);

        // Setup the configuration of the Tango Service.
        int ret = JNIInterface.tangoSetupAndConnect();

        if (ret != 0) {
          Log.e(TAG, "Failed to set config and connect with code: " + ret);
          finish();
        }
      }

      public void onServiceDisconnected(ComponentName name) {
        // Handle this if you need to gracefully shutdown/retry
        // in the event that Tango itself crashes/gets upgraded while running.
      }
    };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    // Check if the Tango Core is out dated.
    if (!JNIInterface.checkTangoVersion(this, MIN_TANGO_CORE_VERSION)) {
      Toast.makeText(this, "Tango Core out dated, please update in Play Store", 
                     Toast.LENGTH_LONG).show();
      finish();
      return;
    }

    setContentView(R.layout.activity_main);

    // Text views for Tango library versions
    mDistanceMeasure = (TextView) findViewById(R.id.distance_textview);

    configureGlSurfaceView();
    configureFilteringOption();
  }

  @Override
  public boolean onTouchEvent(final MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      // Ensure that handling of the touch event is run on the GL thread
      // rather than Android UI thread. This ensures we can modify
      // rendering state without locking.  This event triggers a plane
      // fit.
      mGLView.queueEvent(new Runnable() {
          @Override
          public void run() {
            JNIInterface.onTouchEvent(event.getX(), event.getY());
          }
        });
    }

    return super.onTouchEvent(event);
  }

  private void configureGlSurfaceView() {
    // OpenGL view where all of the graphics are drawn.
    mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

    mGLView.setEGLContextClientVersion(2);
    // Configure the OpenGL renderer.
    mRenderer = new GLSurfaceRenderer(this);
    mGLView.setRenderer(mRenderer);
  }

  private void configureFilteringOption() {
    mBilateralBox = (CheckBox) findViewById(R.id.check_box);
    mBilateralBox.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          // Is the view now checked?
          mBilateralFiltering = ((CheckBox) view).isChecked();
          if (mBilateralFiltering) {
            JNIInterface.setUpsampleViaBilateralFiltering(true);
          } else {
            JNIInterface.setUpsampleViaBilateralFiltering(false);
          }
        }
      });
  }

  @Override
  protected void onResume() {
    super.onResume();
    mGLView.onResume();
    TangoInitializationHelper.bindTangoService(this, mTangoServiceConnection);
    mHandler.post(mUpdateUiLoopRunnable);
  }

  @Override
  protected void onPause() {
    super.onPause();
    mGLView.onPause();
    JNIInterface.deleteResources();

    // Disconnect from the Tango Service, release all the resources that
    // the app is holding from the Tango Service.
    JNIInterface.tangoDisconnect();
    unbindService(mTangoServiceConnection);
  }

  public void surfaceCreated() {
    int ret = JNIInterface.initializeGLContent();

    if (ret != 0) {
      Log.e(TAG, "Failed to connect texture with code: " + ret);
    }
  }

  // Debug text UI update loop, updating at 10Hz.
  private Runnable mUpdateUiLoopRunnable = new Runnable() {
      public void run() {
        updateUi();
        mHandler.postDelayed(this, UPDATE_UI_INTERVAL_MS);
      }
    };

  // Update the debug text UI.
  private void updateUi() {
    try {
      mDistanceMeasure.setText(JNIInterface.getPointSeparation());
    } catch (Exception e) {
      e.printStackTrace();
      Log.e(TAG, "Exception updating UI elements");
    }
  }
}
