package com.example.levyy.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.AsyncTask;
import android.widget.LinearLayout.LayoutParams;
import android.media.MediaMetadataRetriever;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.app.AlertDialog;
import android.widget.EditText;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

//face detection
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import java.io.InputStream;
import android.app.ActionBar;

//Websocket
import java.net.*;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;


public class DgCamActivity extends Activity implements SensorEventListener {
	private Camera mCamera;
	private CameraPreview mPreview;
	private SensorManager sensorManager = null;
	private int orientation;
	private ExifInterface exif;
	private int deviceHeight;
	private Button ibRetake;
	//private Button ibUse;
	private Button ibCapture;

	//private FrameLayout flBtnContainer;
	private File sdRoot;
	private String dir;
	private String fileName;
	private ImageView rotatingImage;
	private int degrees = -1;
	private WebSocketConnection mWebSocketClient;
	private FaceOverlayView mFaceOverlayView;
	private boolean mManualPictureCapture;
	private ImageButton mQRCode;
	private Menu mMenu;
	private int mPaceOfTakingPictures = 3;
	private int mWaitTimeBeforRetake = 10;
	private int mSmilingProbability = 50;
	private int mNumOfFaces = 2;
	private boolean mShowWebsocketMessage = true;
	private int mConnectTrySkip = 3;
	private boolean mWedsocketRequestTakePicture = false;
	private boolean mWedsocketRequestTakeImmediatePicture = false;
	private PostTask mPostTask;
	private boolean mWebSocketIsOpen = false;
	private String mPhotosUrlTemplate = "path/<time_stamp>.jpg";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);


		// start activity in full screen with no action bar
		//setContentView(R.layout.menu);
		ActionBar actionBar = getActionBar();
		actionBar.hide();

		setContentView(R.layout.activity_camera);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		// Setting all the path for the image
		sdRoot = Environment.getExternalStorageDirectory();
		dir = "/DCIM/Camera/";

		// Getting all the needed elements from the layout
		//rotatingImage = (ImageView) findViewById(R.id.imageView1);
		ibRetake = (Button) findViewById(R.id.ibRetake);
		//ibUse = (Button) findViewById(R.id.ibUse);
		ibCapture = (Button) findViewById(R.id.ibCapture);
		//flBtnContainer = (FrameLayout) findViewById(R.id.flBtnContainer);

		// Getting the sensor service.
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

		// Selecting the resolution of the Android device so we can create a
		// proportional preview
		Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		deviceHeight = display.getHeight();

		// Add a listener to the title message to show the options
		TextView message = (TextView) findViewById(R.id.messages);
		message.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ActionBar actionBar = getActionBar();
				if (actionBar.isShowing())
				{
					actionBar.hide();
					SendWebsocketMessage("nav", "resume");
				}
				else
				{
					actionBar.show();
					SendWebsocketMessage("nav", "pause");
				}
			}
		});

		// Add a listener to the Capture button
		ibCapture.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				sayCheese();

				SendWebsocketMessage("nav","pause" );
				titleMessage("Get ready for a picture in 3 seconds");
				// stop monitoring for good pictures while manual picture is handled
				mCheckForAutoPicture = false;

				//change color to red
				ibCapture = (Button) findViewById(R.id.ibCapture);

				ibCapture.setTextSize(30);
				ibCapture.setTextColor(Color.BLACK);
				ibCapture.setText("3");
				ibCapture.setBackgroundColor(0xFFFFBB33);

				ibCapture.postDelayed(new Runnable() {
					@Override
					public void run() {
						ibCapture.setText("2");
					}
				}, 2000);

				ibCapture.postDelayed(new Runnable() {
					@Override
					public void run() {
						ibCapture.setText("1");
						mManualPictureCapture = true;
						mCamera.takePicture(null, null, mPicture);
					}
				}, 4000);

			}
		});

		// Add a listener to the Retake button
		ibRetake.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Deleting the image from the SD card/
				titleMessage("");
				File discardedPhoto = new File(sdRoot, dir + fileName);
				discardedPhoto.delete();

				// Restart the camera preview.
				FrameLayout preview = (FrameLayout) findViewById(R.id.imageView1);
				preview.removeView(mFaceOverlayView);
				preview.addView(mPreview);

				// start monitoring for good picture
				mCheckForAutoPicture = true;


				// Reorganize the buttons on the screen
				ibCapture.setVisibility(LinearLayout.VISIBLE);
				ibRetake.setVisibility(LinearLayout.GONE);
				//ibUse.setVisibility(LinearLayout.GONE);
				ibCapture.setBackgroundColor(Color.GRAY);

				SendWebsocketMessage("nav", "resume");
			}
		});

		// Add a listener to the Use button
		//ibUse.setOnClickListener(new View.OnClickListener() {
		//	public void onClick(View v) {
		//		// Everything is saved so we can quit the app.
		//		finish();
		//	}
		//});

		//connect to websocket
		connectWebSocket();
		SendWebsocketMessage("nav", "resume");
	}

	private void sayCheese()
	{
		final MediaPlayer mp = MediaPlayer.create(this, R.raw.saycheese);
		mp.start();
	}

	private void createCamera() {
		// Create an instance of Camera
		mCamera = getCameraInstance();

		// Setting the right parameters in the camera
		Camera.Parameters params = mCamera.getParameters();
		List<Camera.Size> sizes = params.getSupportedPictureSizes();
		Camera.Size size = sizes.get(0);
		for (int i = 0; i < sizes.size(); i++) {
			if (sizes.get(i).width > size.width)
				size = sizes.get(i);
		}
		params.setPictureSize(size.width, size.height);
		//params.setPictureSize(1280, 720);
		params.setPictureFormat(PixelFormat.JPEG);
		params.setJpegQuality(85);
		mCamera.setParameters(params);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this, mCamera);
		FrameLayout preview = (FrameLayout) findViewById(R.id.imageView1);

		// Calculating the width of the preview so it is proportional.
		int height = (deviceHeight > 1280) ? 1280 : (deviceHeight); //Math.round((float)deviceHeight * 0.7);
		float widthFloat = (float) height * 16 / 9;
		int width = Math.round(widthFloat);

		// Resizing the LinearLayout so we can make a proportional preview. This
		// approach is not 100% perfect because on devices with a really small
		// screen the the image will still be distorted - there is place for
		// improvment.
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(width, height);
		preview.setLayoutParams(layoutParams);

		// Adding the camera preview after the FrameLayout and before the button
		// as a separated element.
		preview.addView(mPreview, 0);

		// add face detection
		mFaceOverlayView = (FaceOverlayView) findViewById(R.id.imageView1);
		mFaceOverlayView.setWillNotDraw(false);

		// QRCode overlay button
		DrawOnTop mDraw = new DrawOnTop(this);
		LinearLayout widget = new LinearLayout(this);
		mQRCode = new ImageButton(this);
		mQRCode.setMaxWidth(600);
		mQRCode.setMaxHeight(600);
		mQRCode.setVisibility(View.GONE);
		mQRCode.setY(850);
		widget.addView(mQRCode);
		preview.addView(widget);


		addContentView(mDraw, new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT));


		//start looping for good picture
		mCheckForAutoPicture = true;
		//AutoPicture autoPicture = new AutoPicture();
		//autoPicture.execute();

		mAutoPicture.run();
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Test if there is a camera on the device and if the SD card is
		// mounted.
		//if (!checkCameraHardware(this)) {
		//	Intent i = new Intent(this, NoCamera.class);
		//	startActivity(i);
		//	finish();
		//} else if (!checkSDCard()) {
		//	Intent i = new Intent(this, NoSDCard.class);
		//	startActivity(i);
		//	finish();
		//}

		// Creating the camera
		createCamera();
		mCheckForAutoPicture = true;
		SendWebsocketMessage("nav","resume" );

		// Register this class as a listener for the accelerometer sensor
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

	}

	@Override
	protected void onPause() {
		super.onPause();
		// release the camera immediately on pause event
		mCheckForAutoPicture = false;
		releaseCamera();
		SendWebsocketMessage("nav","pause" );


		// removing the inserted view - so when we come back to the app we
		// won't have the views on top of each other.
		FrameLayout preview = (FrameLayout) findViewById(R.id.imageView1);
		preview.removeViewAt(0);
	}

	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}

	/**
	 * Check if this device has a camera
	 */
	private boolean checkCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	private boolean checkSDCard() {
		boolean state = false;

		String sd = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(sd)) {
			state = true;
		}

		return state;
	}

	/**
	 * A safe way to get an instance of the Camera object.
	 */
	public static Camera getCameraInstance() {
		Camera c = null;

		int cameraCount = 0;
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		cameraCount = Camera.getNumberOfCameras();
		for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
			Camera.getCameraInfo(camIdx, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				try {
					c = Camera.open(camIdx);
					// fix orientation for dell venue 8
					int result = (cameraInfo.orientation + 90) % 360;
					c.setDisplayOrientation(result);

				} catch (RuntimeException e) {
					Log.e("CAMERA", "Camera failed to open: " + e.getLocalizedMessage());
				}
			}
		}

		// returns null if camera is unavailable
		return c;
	}

	private PictureCallback mPicture = new PictureCallback() {

		public void onPictureTaken(byte[] data, Camera camera) {

			FrameLayout preview = (FrameLayout) findViewById(R.id.imageView1);

			// add face detection
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inMutable = true;
			Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);
			// create a matrix object
			Matrix matrix = new Matrix();
			matrix.preScale(1, -1);

			// create a new bitmap from the original using the matrix to transform the result
			Bitmap rotatedBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);


			mFaceOverlayView.setWillNotDraw(false);
			if (!mManualPictureCapture) {


				mFaceOverlayView.setBitmap(rotatedBitmap, false, false, false);
				// continue camera stream
				mCamera.startPreview();

				// if this picture was initiated by automatic timer we need to check its goodness
				if ((mFaceOverlayView.getmNumOfFaces() >= mNumOfFaces) || (mFaceOverlayView.getSmilingProbability() > mSmilingProbability)) {
					//inform the platform that we are going to take picture now
					SendWebsocketMessage("nav","pause" );

					// take picture as if it was requested manually
					ibCapture.callOnClick();
				}
			} else {
				preview.removeView(mPreview);
				MenuItem item = (MenuItem) mMenu.findItem(R.id.faceBox);
				boolean drawFaceBox = item.isChecked();
				item = (MenuItem) mMenu.findItem(R.id.faceLandmarks);
				Boolean drawFaceLandmarks = item.isChecked();
				mFaceOverlayView.setBitmap(rotatedBitmap, true, drawFaceBox, drawFaceLandmarks);// Replacing the button after a photo was taken.
				ibCapture.setVisibility(View.GONE);
				ibCapture.setTextSize(20);
				ibCapture.setText("Capture");
				ibCapture.setTextColor(Color.WHITE);
				ibCapture.setBackgroundColor(Color.GRAY);
				ibRetake.setVisibility(View.VISIBLE);

				// Invoke retake after picture taken
				// it takes about 5 seconds for taking the picture
				// so we add that to the user setting
				mHandler.postDelayed(mRetake, mWaitTimeBeforRetake * 1000 + 5000);

				titleMessage("Your picture is ready. Scan the QR Code below");
				SendWebsocketMessage("camera_status","captured");

				// File name of the image that we just took.
				fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()).toString() + ".jpg";

				// Creating the directory where to save the image. Sadly in older
				// version of Android we can not get the Media catalog name
				File mkDir = new File(sdRoot, dir);
				mkDir.mkdirs();

				// Main file where to save the data that we receive from the camera
				File pictureFile = new File(sdRoot, dir + fileName);

				try {
					FileOutputStream purge = new FileOutputStream(pictureFile);
					purge.write(data);
					purge.close();
				} catch (FileNotFoundException e) {
					Log.d("DG_DEBUG", "File not found: " + e.getMessage());
				} catch (IOException e) {
					Log.d("DG_DEBUG", "Error accessing file: " + e.getMessage());
				}

				// Adding Exif data for the orientation. For some strange reason the
				// ExifInterface class takes a string instead of a file.
				try {
					exif = new ExifInterface("/sdcard/" + dir + fileName);
					exif.setAttribute(ExifInterface.TAG_ORIENTATION, "" + orientation);
					exif.saveAttributes();
				} catch (IOException e) {
					e.printStackTrace();
				}

				//use time stamp for the file name
				Long tsLong = System.currentTimeMillis()/1000;
				String ts = tsLong.toString();
				mPostTask = new PostTask();
				mPostTask.setTimestamp(ts);
				String url = mPhotosUrlTemplate;
				url = url.replace("<time_stamp>",ts);
				showQRCode(url);
				String encodedString = Base64.encodeToString(data, Base64.DEFAULT);
				mPostTask.PrepareAndExecute(encodedString);
			}
		}
	};

	/**
	 * Putting in place a listener so we can get the sensor data only when
	 * something changes.
	 */
	public void onSensorChanged(SensorEvent event) {
		synchronized (this) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				RotateAnimation animation = null;
				if (event.values[0] < 4 && event.values[0] > -4) {
					if (event.values[1] > 0 && orientation != ExifInterface.ORIENTATION_ROTATE_90) {
						// UP
						orientation = ExifInterface.ORIENTATION_ROTATE_90;
						animation = getRotateAnimation(270);
						degrees = 270;
					} else if (event.values[1] < 0 && orientation != ExifInterface.ORIENTATION_ROTATE_270) {
						// UP SIDE DOWN
						orientation = ExifInterface.ORIENTATION_ROTATE_270;
						animation = getRotateAnimation(90);
						degrees = 90;
					}
				} else if (event.values[1] < 4 && event.values[1] > -4) {
					if (event.values[0] > 0 && orientation != ExifInterface.ORIENTATION_NORMAL) {
						// LEFT
						orientation = ExifInterface.ORIENTATION_NORMAL;
						animation = getRotateAnimation(0);
						degrees = 0;
					} else if (event.values[0] < 0 && orientation != ExifInterface.ORIENTATION_ROTATE_180) {
						// RIGHT
						orientation = ExifInterface.ORIENTATION_ROTATE_180;
						animation = getRotateAnimation(180);
						degrees = 180;
					}
				}
				if (animation != null) {
					//rotatingImage.startAnimation(animation);
				}
			}

		}
	}

	/**
	 * Calculating the degrees needed to rotate the image imposed on the button
	 * so it is always facing the user in the right direction
	 *
	 * @param toDegrees
	 * @return
	 */
	private RotateAnimation getRotateAnimation(float toDegrees) {
		float compensation = 0;

		if (Math.abs(degrees - toDegrees) > 180) {
			compensation = 360;
		}

		// When the device is being held on the left side (default position for
		// a camera) we need to add, not subtract from the toDegrees.
		if (toDegrees == 0) {
			compensation = -compensation;
		}

		// Creating the animation and the RELATIVE_TO_SELF means that he image
		// will rotate on it center instead of a corner.
		RotateAnimation animation = new RotateAnimation(degrees, toDegrees - compensation, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

		// Adding the time needed to rotate the image
		animation.setDuration(250);

		// Set the animation to stop after reaching the desired position. With
		// out this it would return to the original state.
		animation.setFillAfter(true);

		return animation;
	}

	/**
	 * STUFF THAT WE DON'T NEED BUT MUST BE HEAR FOR THE COMPILER TO BE HAPPY.
	 */
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	private boolean mCheckForAutoPicture = false; // If the Runnable should keep on running
	private final Handler mHandler = new Handler();

	// This runnable will schedule itself to run at 3 second intervals
	// if mCheckForAutoPicture is set true.
	private final Runnable mAutoPicture = new Runnable() {
		public void run() {
			// Call the method to check for smiling faces
			if (mCheckForAutoPicture ||	mWedsocketRequestTakePicture ||	mWedsocketRequestTakeImmediatePicture) {
				mManualPictureCapture = false;
				if (mWedsocketRequestTakePicture || mWedsocketRequestTakeImmediatePicture) {
					mManualPictureCapture = true;
					SendWebsocketMessage("nav","pause" );
				}

				if (mWedsocketRequestTakePicture) {
					ibCapture.callOnClick();
				}
				else {
					mCamera.takePicture(null, null, mPicture);
				}

				// reset websocket pending request after servicing it
				mWedsocketRequestTakePicture = false;
				mWedsocketRequestTakeImmediatePicture = false;
			}

			// Make sure websocket connection is still alive
			if (!mWebSocketClient.isConnected()) {
				mWebSocketIsOpen = false;
				if (mConnectTrySkip > 0) --mConnectTrySkip;
				if (mConnectTrySkip == 0) {
					connectWebSocket();
				}
			}

			// Call this run method every mPaceOfTakingPictures seconds
			mHandler.postDelayed(mAutoPicture, mPaceOfTakingPictures * 1000);
		}
	};

	private final Runnable mRetake = new Runnable() {
		public void run() {
			if (ibRetake.getVisibility() == LinearLayout.VISIBLE) {
				ibRetake.callOnClick();
			}
		}
	};

	private void titleMessage(String message) {
		TextView Title = (TextView) findViewById(R.id.messages);
		Title.setText(message);
	}

	private static final int WHITE = 0xFFFFFFFF;
	private static final int BLACK = 0xFF000000;

	private static String guessAppropriateEncoding(CharSequence contents) {
		// Very crude at the moment
		for (int i = 0; i < contents.length(); i++) {
			if (contents.charAt(i) > 0xFF) {
				return "UTF-8";
			}
		}
		return null;
	}

	private Bitmap encodeAsBitmap(String contents) throws WriterException {
		int img_width = 400;
		int img_height = 400;
		String contentsToEncode = contents;
		if (contentsToEncode == null) {
			return null;
		}
		Map<EncodeHintType, Object> hints = null;
		String encoding = guessAppropriateEncoding(contentsToEncode);
		if (encoding != null) {
			hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
			hints.put(EncodeHintType.CHARACTER_SET, encoding);
		}
		MultiFormatWriter writer = new MultiFormatWriter();
		BitMatrix result;
		try {
			result = writer.encode(contentsToEncode,  BarcodeFormat.QR_CODE, img_width, img_height, hints);
		} catch (IllegalArgumentException iae) {
			// Unsupported format
			return null;
		}
		int width = result.getWidth();
		int height = result.getHeight();
		int[] pixels = new int[width * height];
		for (int y = 0; y < height; y++) {
			int offset = y * width;
			for (int x = 0; x < width; x++) {
				pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
			}
		}

		Bitmap bitmap = Bitmap.createBitmap(width, height,
				Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
		return bitmap;
	}

	public void showQRCode(String code)
	{
		try {
			mQRCode.setVisibility(View.VISIBLE);
			//InputStream stream = getResources().openRawResource(R.raw.intel_qr_code);
			//Bitmap bitmap = BitmapFactory.decodeStream(stream);
			Bitmap QRBitmap = encodeAsBitmap(code);
			mQRCode.setImageBitmap(QRBitmap);
		} catch (WriterException e) {
			e.printStackTrace();
		}


	}
	private void SendWebsocketMessage(String key, String value)
	{
		if ((!mWebSocketClient.isConnected()) || !mWebSocketIsOpen) return;

		try
		{
			JSONObject jasonData = new JSONObject();
			jasonData.put("cmd", key);
			jasonData.put("data", value);
			mWebSocketClient.sendTextMessage(jasonData.toString());
		}
		catch(JSONException e)
		{
			//some exception handler code.
		}
	}

	private void connectWebSocket() {

		mConnectTrySkip = 3;
		mWebSocketClient = new WebSocketConnection();
		try {
			//mWebSocketClient.connect("ws://echo.websocket.org/", new WebSocketHandler() {
			mWebSocketClient.connect("ws://104.198.196.211:8000", new WebSocketHandler() {
				@Override
				public void onOpen() {
					// Debug
					Log.d("WEBSOCKETS", "Connected to server.");
					mWebSocketIsOpen = true;
				}

				@Override
				public void onTextMessage(String payload) {
					// Debug
					Log.d("WEBSOCKETS", payload);
					MenuItem item = (MenuItem) mMenu.findItem(R.id.showWebsocketMessages);
					if (item.isChecked()) {
						titleMessage(payload);
					}

					try {
						JSONObject reader = new JSONObject(payload);
						String cmd  = reader.getString("cmd");
						String data = reader.getString("data");

						if (cmd.equalsIgnoreCase("camera")) {
							if (data.equalsIgnoreCase("capture")) {
								mWedsocketRequestTakePicture = true;
							}
							if (data.equalsIgnoreCase("immediate_capture")) {
								mWedsocketRequestTakeImmediatePicture = true;
							}
						}

						if (cmd.equalsIgnoreCase("photo_template")) {
							mPhotosUrlTemplate = data;
						}
					} catch (JSONException e) {

					}
				}

				@Override
				public void onClose(int code, String reason) {
					// Debug
					Log.d("WEBSOCKETS", "Connection lost.");
				}
			});
		} catch(WebSocketException wse) {
			Log.d("WEBSOCKETS", wse.getMessage());
		}


		// for some reason this work in debug mode only
		//mWebSocketClient.connect();

	}

	private void settingPicturePace()
	{
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DgCamActivity.this);

		final EditText et = new EditText(DgCamActivity.this);
		et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
		et.setHint("number of seconds. Currently: " + String.valueOf(mPaceOfTakingPictures));

		// set prompts.xml to alertdialog builder
		alertDialogBuilder.setView(et);

		// set dialog message
		alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				mPaceOfTakingPictures = Integer.parseInt(et.getText().toString());
			}
		});

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();
		// show it
		alertDialog.show();
		et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {

					mPaceOfTakingPictures = Integer.parseInt(et.getText().toString());
					return true;
				}
				return false;
			}
		});
	}


	private void settingWaitTimeBeforRetake()
	{
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DgCamActivity.this);

		final EditText et = new EditText(DgCamActivity.this);
		et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
		et.setHint("number of seconds. Currently: " + String.valueOf(mWaitTimeBeforRetake));

		// set prompts.xml to alertdialog builder
		alertDialogBuilder.setView(et);

		// set dialog message
		alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				mWaitTimeBeforRetake = Integer.parseInt(et.getText().toString());
			}
		});

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();
		// show it
		alertDialog.show();
		et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {

					mWaitTimeBeforRetake = Integer.parseInt(et.getText().toString());
					return true;
				}
				return false;
			}
		});
	}

	private void settingSmilingPercentage()
	{
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DgCamActivity.this);

		final EditText et = new EditText(DgCamActivity.this);
		et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
		et.setHint("Threshold for smiling probability. Currently: " + String.valueOf(mSmilingProbability));

		// set prompts.xml to alertdialog builder
		alertDialogBuilder.setView(et);

		// set dialog message
		alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				mSmilingProbability = Integer.parseInt(et.getText().toString());
			}
		});

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();
		// show it
		alertDialog.show();
		et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {

					mSmilingProbability = Integer.parseInt(et.getText().toString());
					return true;
				}
				return false;
			}
		});
	}

	private void settingNumOfFaces()
	{
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DgCamActivity.this);

		final EditText et = new EditText(DgCamActivity.this);
		et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
		et.setHint("Threshold for number of faces. Currently: " + String.valueOf(mNumOfFaces));

		// set prompts.xml to alertdialog builder
		alertDialogBuilder.setView(et);

		// set dialog message
		alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				mNumOfFaces = Integer.parseInt(et.getText().toString());
			}
		});

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();
		// show it
		alertDialog.show();
		et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {

					mNumOfFaces = Integer.parseInt(et.getText().toString());
					return true;
				}
				return false;
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		getActionBar().hide();

		// Handle item selection
		switch (item.getItemId()) {
			case R.id.PicturePace:
				settingPicturePace();
				return true;
			case R.id.WaitTimeBeforRetake:
				settingWaitTimeBeforRetake();
				return true;
			case R.id.SmilingPercentage:
				settingSmilingPercentage();
				return true;
			case R.id.NumOfFaces:
				settingNumOfFaces();
				return true;
			case R.id.faceBox:
				if (item.isChecked()) item.setChecked(false);
				else item.setChecked(true);
				return true;
			case R.id.faceLandmarks:
				if (item.isChecked()) item.setChecked(false);
				else item.setChecked(true);
				return true;
			case R.id.showWebsocketMessages:
				if (item.isChecked()) item.setChecked(false);
				else item.setChecked(true);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.layout.main, menu);
		mMenu = menu;
		return true;
	}

}