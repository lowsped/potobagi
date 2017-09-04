package com.example.simplecropviewsample;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.isseiaoki.simplecropview.util.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResultActivity extends AppCompatActivity {
  private static final String TAG = ResultActivity.class.getSimpleName();
  private ImageView mImageView;
  private ExecutorService mExecutor;
  private String temp;

  public static Intent createIntent(Activity activity, Uri uri) {
    Intent intent = new Intent(activity, ResultActivity.class);
    intent.setData(uri);
    return intent;

  }

  Button buttono;
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_result);


    buttono = (Button) findViewById(R.id.buttono);
    buttono.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {

        ImageView ivImage = (ImageView) findViewById(R.id.result_image);
        // Get access to the URI for the bitmap
        Uri bmpUri = getLocalBitmapUri(ivImage);
        if (bmpUri != null) {
          // Construct a ShareIntent with link to image
          Intent shareIntent = new Intent();
          shareIntent.setAction(Intent.ACTION_SEND);
          shareIntent.putExtra(Intent.EXTRA_STREAM, bmpUri);
          shareIntent.setType("image/*");
          // Launch sharing dialog for image
          startActivity(Intent.createChooser(shareIntent, "Share Image"));
        } else {
          // ...sharing failed, handle error
        }
      }

      // Returns the URI path to the Bitmap displayed in specified ImageView
      public Uri getLocalBitmapUri(ImageView imageView) {
        // Extract Bitmap from ImageView drawable
        Drawable drawable = imageView.getDrawable();
        Bitmap bmp = null;
        if (drawable instanceof BitmapDrawable){
          bmp = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
        } else {
          return null;
        }
        // Store image to default external storage directory
        Uri bmpUri = null;
        try {
          // Use methods on Context to access package-specific directories on external storage.
          // This way, you don't need to request external read/write permission.
          // See https://youtu.be/5xVh-7ywKpE?t=25m25s
          File file =  new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "share_image_" + System.currentTimeMillis() + ".png");
          FileOutputStream out = new FileOutputStream(file);
          bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
          out.close();
          // **Warning:** This will fail for API >= 24, use a FileProvider as shown below instead.
          bmpUri = Uri.fromFile(file);
        } catch (IOException e) {
          e.printStackTrace();
        }
        return bmpUri;
      }

      // Method when launching drawable within Glide
      public Uri getBitmapFromDrawable(Bitmap bmp){

        // Store image to default external storage directory
        Uri bmpUri = null;
        try {
          // Use methods on Context to access package-specific directories on external storage.
          // This way, you don't need to request external read/write permission.
          // See https://youtu.be/5xVh-7ywKpE?t=25m25s
          File file =  new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "share_image_" + System.currentTimeMillis() + ".png");
          FileOutputStream out = new FileOutputStream(file);
          bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
          out.close();

          // wrap File object into a content provider. NOTE: authority here should match authority in manifest declaration
          bmpUri = FileProvider.getUriForFile(ResultActivity.this, "com.codepath.fileprovider", file);  // use this version for API >= 24

          // **Note:** For API < 24, you may use bmpUri = Uri.fromFile(file);

        } catch (IOException e) {
          e.printStackTrace();
        }
        return bmpUri;
      }

    });

    // apply custom font
    FontUtils.setFont((ViewGroup) findViewById(R.id.layout_root));

    initToolbar();

    mImageView = (ImageView) findViewById(R.id.result_image);
    mExecutor = Executors.newSingleThreadExecutor();

    final Uri uri = getIntent().getData();
    mExecutor.submit(new LoadScaledImageTask(this, uri, mImageView, calcImageSize()));
  }

  @Override protected void onDestroy() {
    mExecutor.shutdown();
    super.onDestroy();
  }

  @Override public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
  }

  @Override public boolean onSupportNavigateUp() {
    onBackPressed();
    return super.onSupportNavigateUp();
  }

  private void initToolbar() {
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar actionBar = getSupportActionBar();
    FontUtils.setTitle(actionBar, "Cropped Image");
    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setHomeButtonEnabled(true);
  }

  private int calcImageSize() {
    DisplayMetrics metrics = new DisplayMetrics();
    Display display = getWindowManager().getDefaultDisplay();
    display.getMetrics(metrics);
    return Math.min(Math.max(metrics.widthPixels, metrics.heightPixels), 2048);
  }

  public static class LoadScaledImageTask implements Runnable {
    private Handler mHandler = new Handler(Looper.getMainLooper());
    Context context;
    Uri uri;
    ImageView imageView;
    int width;

    public LoadScaledImageTask(Context context, Uri uri, ImageView imageView, int width) {
      this.context = context;
      this.uri = uri;
      this.imageView = imageView;
      this.width = width;
    }

    @Override public void run() {
      final int exifRotation = Utils.getExifOrientation(context, uri);
      int maxSize = Utils.getMaxSize();
      int requestSize = Math.min(width, maxSize);
      try {
        final Bitmap sampledBitmap = Utils.decodeSampledBitmapFromUri(context, uri, requestSize);
        mHandler.post(new Runnable() {
          @Override public void run() {
            imageView.setImageMatrix(Utils.getMatrixFromExifOrientation(exifRotation));
            imageView.setImageBitmap(sampledBitmap);
          }
        });
      } catch (OutOfMemoryError e) {
        e.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
