package com.example.facerecognition;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.JavaCameraView;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2, CustomCallback {
    private static final String TAG = "MainActivity";
    private final String apiAddress = BuildConfig.API_ADDRESS;
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private static final int  CAMERA_FRONT = 1;
    private static final int CAMERA_BACK = 0;
    private final int STOP_FRAME = 10;

    private CameraBridgeViewBase mOpenCvCameraView;
    private int mCameraId = CAMERA_BACK;
    private Button capture_btn, register_btn,swap_btn;

    Context context;
    private CustomCallback callback;
    ProgressBar progressBar;


    Mat mRgba;
    //Mat mRgbaT;
    //Mat mRgbaF;
    Mat mGray;
    //Mat mGrayT;
    //Mat mGrayF;
    private boolean recog_flag = false;
    private boolean regis_flag = false;
    private String regis_name = new String();
    private int count = 0;
    private ArrayList<String> encodedImages = new ArrayList<String>();

    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;
    private float mRelativeFaceSize   = 0.25f;
    private int mAbsoluteFaceSize = 0;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    try{
                        //load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1){
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if(mJavaDetector.empty()){
                            Log.e(TAG, "Failed to load cascade classifier.");
                            mJavaDetector = null;
                        }else{
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
                        }

                        cascadeDir.delete();

                    }catch (IOException e){
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);

                    }
                    mOpenCvCameraView.enableView();
                }break;
                default:
                {
                    super.onManagerConnected(status);
                }break;
            }

        }
    };


    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        //getSupportActionBar().setTitle("臉部辨識系統"); // for set actionbar title


        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.show_camera_activity_java_surface_view);
        mOpenCvCameraView.setMaxFrameSize(800, 600);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        context = this;
        callback = this;
        progressBar = (ProgressBar)findViewById(R.id.Login_progressBar);
        capture_btn = (Button) findViewById(R.id.capture_btn);
        register_btn = (Button) findViewById(R.id.register_btn);
        swap_btn = (Button) findViewById(R.id.swap_btn);
        capture_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mRgba != null) {
                    if (!mRgba.empty()) {
                        //start recognize
                        recog_flag = true;
                        progressBar.setVisibility(View.VISIBLE);

/*Save Image to phone
                        //判断是否存在手機内存
                        boolean sdCardExist = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
                        if (sdCardExist) {
                            String Path = Environment.getExternalStorageDirectory().getAbsolutePath()+"/Pictures/";
                            //獲得手機儲存根目錄
                            File sdDir = new File(Path);
                            if (!sdDir.exists()){
                                sdDir.mkdirs();
                                Log.i(TAG,"Pictures/OpenCV directory created.");
                            }
                            //將拍攝時間當作檔名
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                            String filename = sdf.format(new Date());
                            String filePath = Path +  filename + ".png";
                            //將轉化後的BGR矩陣寫入檔案
                            //Imgcodecs.imwrite(filePath, inter);
                            //Toast.makeText(context, "圖片已存到: "+ filePath, Toast.LENGTH_SHORT).show();
                        }
*/

                    }
                }
            }
        });
        register_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mRgba != null){
                    if(!mRgba.empty()){
                        final View item = LayoutInflater.from(context).inflate(R.layout.register_name_layout, null);
                        new AlertDialog.Builder(context)
                                .setTitle("請輸入註冊姓名")
                                .setView(item)
                                .setPositiveButton("開始註冊", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        EditText editText = (EditText) item.findViewById(R.id.name_edtxt);
                                        regis_name = editText.getText().toString();
                                        if(regis_name.isEmpty()){
                                            Toast.makeText(context, "未輸入姓名", Toast.LENGTH_SHORT).show();
                                        } else {
                                            //start register
                                            regis_flag = true;
                                            progressBar.setVisibility(View.VISIBLE);
                                        }
                                        dialog.dismiss();
                                    }
                                })
                                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                                .show();
                    }
                }

            }
        });
        swap_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swapCamera();
            }
        });
    }

/*
    // create an action bar button when action bar is created
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mymenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        int id = item.getItemId();
        if (id == R.id.rotate_btn) {
            swapCamera();
        }
        return super.onOptionsItemSelected(item);
    }
*/


    private void swapCamera() {
        mCameraId = mCameraId^1; //bitwise not operation to flip 1 to 0 and vice versa
        mOpenCvCameraView.disableView();
        mOpenCvCameraView.setCameraIndex(mCameraId);
        mOpenCvCameraView.enableView();
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mOpenCvCameraView != null){
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!OpenCVLoader.initDebug()){
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);

        }else{
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if(mOpenCvCameraView != null){
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        //mRgbaF = new Mat();
        //mRgbaT = new Mat(width,width,CvType.CV_8UC4);
        mGray = new Mat();
        //mGrayF = new Mat();
        //mGrayT = new Mat(width,width,CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        //前鏡頭時 水平翻轉frame
        if (mCameraId == CAMERA_FRONT){
            Core.flip(mRgba,mRgba,1);
            Core.flip(mGray,mGray,1);
        }

        /* 豎立Camera時 需轉正frame的code, activity orientation為portrait時

        //轉正彩色frame
        //Core.transpose(mRgba, mRgbaT);
        mRgbaT = mRgba.t();
        Imgproc.resize(mRgbaT, mRgbaF, mRgba.size(), 0, 0, 0);
        //轉正灰階frame
        //Core.transpose(mGray, mGrayT);
        mGrayT = mGray.t();
        Imgproc.resize(mGrayT, mGrayF, mGray.size(), 0, 0, 0);
        switch (mCameraId){
            case CAMERA_BACK:
                Core.flip(mRgbaF, mRgba, 1);
                Core.flip(mGrayF, mGray, 1);
                break;
            case CAMERA_FRONT:
                Core.flip(mRgbaF, mRgba, -1);
                Core.flip(mGrayF, mGray, -1);
                break;
        }
*/
        //開始偵測人臉並畫框
        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            Log.d(TAG, Integer.toString(height));
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }
        MatOfRect faces = new MatOfRect();

        if (mJavaDetector != null){
            mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }
        Rect[] facesArray = faces.toArray();
        if(facesArray.length > 0){
            for (int i = 0; i < facesArray.length; i++){
                Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
            }
        }
        //結束偵測人臉

        //開始辨識STOP_FRAME張frame
        if (recog_flag){
            if (count<STOP_FRAME){
                if(facesArray.length > 0){
                    Mat crop_face=new Mat();
                    crop_face = mRgba.submat(facesArray[0]);
                    //CvType.CV_8UC4 8U=8bit unsigned=0-255, C4=4 channels
                    Mat inter = new Mat(crop_face.width(), crop_face.height(), CvType.CV_8UC4);
                    //convert RGBA to RGB
                    Imgproc.cvtColor(crop_face, inter, Imgproc.COLOR_RGBA2RGB);
                    Size size = new Size(224,224);
                    Imgproc.resize(inter, inter, size);
                    Bitmap bitmap = Bitmap.createBitmap(inter.width(), inter.height(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(inter, bitmap);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                    byte[] byteArray = byteArrayOutputStream .toByteArray();

                    String encodedImage = new String (Base64.encode(byteArray, Base64.DEFAULT));
                    encodedImages.add(encodedImage);
                }

                Log.i(TAG,"EncodedImages: "+encodedImages);
                count ++;
            }else{
                if(encodedImages.isEmpty()){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(context, "未偵測到人臉",Toast.LENGTH_LONG).show();
                        }
                    });
                }else{
                    try {
                        JSONObject postData = new JSONObject();
                        JSONArray list = new JSONArray(encodedImages);
                        postData.put("faces",list);
                        HttpRequestAsyncTask task = new HttpRequestAsyncTask(postData,RequestType.POST,callback);
                        task.execute(apiAddress+"recognize");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                recog_flag = false;
                count = 0;
                encodedImages.clear();
            }
        }

        if (regis_flag) {
            if (count < 10) {
                if (facesArray.length > 0) {
                    Mat crop_face = new Mat();
                    crop_face = mRgba.submat(facesArray[0]);
                    //CvType.CV_8UC4 8U=8bit unsigned=0-255, C4=4 channels
                    Mat inter = new Mat(crop_face.width(), crop_face.height(), CvType.CV_8UC4);
                    //convert RGBA to RGB
                    Imgproc.cvtColor(crop_face, inter, Imgproc.COLOR_RGBA2RGB);
                    Size size = new Size(250,250);
                    Imgproc.resize(inter, inter, size);
                    Bitmap bitmap = Bitmap.createBitmap(inter.width(), inter.height(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(inter, bitmap);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    String encodedImage = new String(Base64.encode(byteArray, Base64.DEFAULT));
                    try {
                        JSONObject postData = new JSONObject();
                        postData.put("img",encodedImage);
                        postData.put("name",regis_name );
                        HttpRequestAsyncTask task = new HttpRequestAsyncTask(postData,RequestType.POST,callback);
                        task.execute(apiAddress+"registered");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    regis_flag = false;
                    count = 0;
                } else {
                    count++;
                }
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(context, "未偵測到人臉",Toast.LENGTH_LONG).show();
                    }
                });
                regis_flag = false;
                count = 0;
            }
        }

        return mRgba;
    }

    @Override
    public void completionHandler(final Boolean success, RequestType type, final Object object) {
        switch (type){
            case POST:
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (success){
                            try {
                                JSONObject response = new JSONObject((String) object);
                                Log.i("response","response: " + response);
                                if (response.has("status")){
                                    String status = response.getString("status");
                                    Toast.makeText(context, status, Toast.LENGTH_LONG).show();
                                }else{
                                    String identity = response.getString("identity");
                                    Toast.makeText(context,"身份: "+ identity ,Toast.LENGTH_LONG).show();
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }else{
                            Toast.makeText(context,"請求伺服器失敗!" + object.toString() ,Toast.LENGTH_LONG).show();
                        }
                        progressBar.setVisibility(View.GONE);
                    }
                });
                break;
        }

    }
}