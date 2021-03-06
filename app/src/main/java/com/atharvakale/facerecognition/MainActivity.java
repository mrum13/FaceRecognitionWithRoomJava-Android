package com.atharvakale.facerecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.atharvakale.facerecognition.database.AppDatabase;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;


import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.text.InputType;
import android.util.Base64;
import android.util.Pair;
import android.util.Size;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    protected FaceDetector detector;

    protected ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    protected PreviewView previewView;
    protected ImageView face_preview;
    protected Interpreter tfLite;
    protected TextView reco_name,preview_info;
    protected Button recognize,camera_switch;
    protected ImageButton add_face;
    protected CameraSelector cameraSelector;
    protected boolean startAddFace=false,flipX=false, registeredEmbed=false, startRecogFace=false; //kondisi start = true adalah untuk scan langsung (realtime)
    protected Context context=MainActivity.this;

    protected int cam_face=CameraSelector.LENS_FACING_FRONT; //Default Front Camera
    Button btnScan;
    protected int[] intValues;
    protected int inputSize=112;  //Input size for model
    protected boolean isModelQuantized=false;
    protected float[][] embeedings;
    protected float IMAGE_MEAN = 128.0f;
    protected float IMAGE_STD = 128.0f;
    protected int OUTPUT_SIZE=192; //Output size of model

    protected ProcessCameraProvider cameraProvider;
    private static final int MY_CAMERA_REQUEST_CODE = 100;

    protected String modelFile="mobile_face_net.tflite"; //model name

    public SharedPreferences sharedPreferencesBedaBitmap, sharedPreferencesBeda1, sharedPreferencesBeda2;

    private AppDatabase database;

    private String dataEmbedFromDb, dataNameFromDb, dataAllFromDb;

    private HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>(); //saved Faces
    @RequiresApi(api = Build.VERSION_CODES.M)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = AppDatabase.getInstance(getApplicationContext());
        face_preview =findViewById(R.id.imageView);
        reco_name =findViewById(R.id.textView);
        preview_info =findViewById(R.id.textView2);
        add_face=findViewById(R.id.imageButton);
        add_face.setVisibility(View.INVISIBLE);
        btnScan = findViewById(R.id.scan_face);

        face_preview.setVisibility(View.INVISIBLE);
        recognize=findViewById(R.id.button3);
        camera_switch=findViewById(R.id.button5);

//        if(registeredEmbed){
//            dataEmbedFromDb = database.userDao().getAll().get(0).muka;
//        } else {
//            dataEmbedFromDb = "Embed belum tersimpan";
//        }

        preview_info.setText("\n        Recognized Face:");

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AttendanceActivity.class));
            }
        });

        //Camera Permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }

        camera_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cam_face==CameraSelector.LENS_FACING_BACK) {
                    cam_face = CameraSelector.LENS_FACING_FRONT;
                    flipX=true;
                }
                else {
                    cam_face = CameraSelector.LENS_FACING_BACK;
                    flipX=false;
                }
                cameraProvider.unbindAll();
                cameraBind();
            }
        });

        add_face.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecogFace = true;
                addFace();
            }
        }));

        recognize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(recognize.getText().toString().equals("Recognize"))
                {
                    startAddFace=false;
                    startRecogFace=true;
                    recognize.setText("Add Face");
                    add_face.setVisibility(View.INVISIBLE);
                    reco_name.setVisibility(View.VISIBLE);
                    face_preview.setVisibility(View.INVISIBLE);
                    preview_info.setText("\n    Recognized Face:");
                }
                else
                {
                    startAddFace = true;
                    startRecogFace = true;
                    recognize.setText("Recognize");
                    reco_name.setVisibility(View.INVISIBLE);
                    add_face.setVisibility(View.VISIBLE); //button add face second
                    face_preview.setVisibility(View.VISIBLE); //preview face
//                    preview_info.setText("1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face.");


                }

            }
        });

        //Load model
        System.out.println("jalankan load model MainAct");
        try {
            tfLite=new Interpreter(loadModelFile(MainActivity.this,modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);

        cameraBind();
    }

    //permission camera(hanya 1x)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    //Step Initial Default Camera :
    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private void cameraBind()
    {
        System.out.println("Jalankan method cameraBind");
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        previewView=findViewById(R.id.previewView);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this in Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        System.out.println("Jalankan method bindPreview");
        Preview preview = new Preview.Builder().build();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cam_face)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
                        .build();

        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {

                InputImage image = null;

                @SuppressLint("UnsafeExperimentalUsageError")
                // Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(needed for ML kit face detection)

                Image mediaImage = imageProxy.getImage();

                if (mediaImage != null) {
                    image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    System.out.println("Rotation "+imageProxy.getImageInfo().getRotationDegrees());
                }

                System.out.println("ANALYSIS");

                //Process acquired image to detect faces
                Task<List<Face>> result =
                        detector.process(image)
                                .addOnSuccessListener(
                                        new OnSuccessListener<List<Face>>() {
                                            @Override
                                            public void onSuccess(List<Face> faces) {
                                                //faces adalah variable untuk face detection
                                                if(faces.size()!=0) {

                                                    if(registeredEmbed){
//                                                        dataEmbedFromDb = database.userDao().getAll().get(0).muka;
//                                                        dataNameFromDb = database.userDao().getAll().get(0).fullName;
                                                        dataAllFromDb = database.userDao().getAll().muka;
                                                    } else {
                                                        dataEmbedFromDb = "Embed waiting registered";
                                                        dataNameFromDb = "Name waiting registered";
                                                    }

                                                    System.out.print("embed tersave all="+dataAllFromDb);

                                                    System.out.println("jalankan faces Main = "+faces);
                                                    Face face = faces.get(0); //Get first face from detected faces

                                                    //mediaImage to Bitmap
                                                    Bitmap frame_bmp = toBitmap(mediaImage);

                                                    int rot = imageProxy.getImageInfo().getRotationDegrees();

                                                    //Adjust orientation of Face
                                                    Bitmap frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false);

                                                    //Get bounding box of face
                                                    RectF boundingBox = new RectF(face.getBoundingBox());

                                                    //Crop out bounding box from whole Bitmap(image)
                                                    Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                                    if(flipX)
                                                        cropped_face = rotateBitmap(cropped_face, 0, flipX, false);

                                                    //Scale the acquired Face to 112*112 which is required input for model
                                                    Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);

//

//                                                    String scaledToConvert = BitMapToString(scaled);
//                                                    database.userDao().insertBitmap(scaledToConvert);
//
//                                                    String scaledSaved = database.userDao().getAll().get(0).bitmap;
//                                                    Bitmap scaledConvertAfterSaved = StringToBitMap(scaledSaved);

                                                    if(startRecogFace)
                                                        recognizeImage(scaled); //Send scaled bitmap to create face embeddings.
                                                    System.out.println(boundingBox);
                                                    try {
                                                        Thread.sleep(10);  //Camera preview refreshed every 10 millisec(adjust as required)
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                else
                                                {
                                                    if(registered.isEmpty())
                                                        reco_name.setText("Add Face");
                                                    else {
                                                        reco_name.setText("No Face Detected!");
                                                    }

                                                }

                                            }
                                        })
                                .addOnFailureListener(
                                        new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                // Task failed with an exception
                                                // ...
                                                System.out.print("tidak ada wajah");
                                            }
                                        })
                                .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                                    @Override
                                    public void onComplete(@NonNull Task<List<Face>> task) {

                                        imageProxy.close(); //v.important to acquire next frame for analysis
                                    }
                                });


            }
        });


        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);


    }

    public String BitMapToString(Bitmap bitmap) {
        ByteArrayOutputStream baos=new  ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG,100, baos);
        byte [] b=baos.toByteArray();
        String temp= Base64.encodeToString(b, Base64.DEFAULT);
        return temp;
    }

    public Bitmap StringToBitMap(String encodedString){
        try {
            byte [] encodeByte= Base64.decode(encodedString,Base64.DEFAULT);
            Bitmap bitmap=BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
            return bitmap;
        } catch(Exception e) {
            e.getMessage();
            return null;
        }
    }

    public void recognizeImage(final Bitmap bitmap) {
        // set Face to Preview
        face_preview.setImageBitmap(bitmap);

        //Create ByteBuffer to store normalized image

        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);

        imgData.order(ByteOrder.nativeOrder());

        intValues = new int[inputSize * inputSize];

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);

                }
            }
        }
        //imgData is input to our model
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();

        embeedings = new float[1][OUTPUT_SIZE]; //output of model will be stored in this variable
        System.out.println("conv embed di recognizeImage="+Arrays.deepToString(embeedings));
        outputMap.put(0, embeedings);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap); //Run model

        float distance = Float.MAX_VALUE;
        String id = "0";
        String label = "?";

        //Compare new face with saved Faces.
        if (registered.size() > 0) {

            final Pair<String, Float> nearest = findNearest(embeedings[0]);//Find closest matching face
            System.out.print("anu embed banding=");
            System.out.println(Arrays.deepToString(new float[][]{embeedings[0]}));
            if (nearest != null) {

                final String name = nearest.first;
//                label = name;
                distance = nearest.second;
                if(distance<1.000f) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                    reco_name.setText(name);
                else
                    reco_name.setText("Unknown");
                System.out.println("nearest: " + name + " - distance: " + distance);
            }
        }
    }


    //Compare Faces by distance between face embeddings
    private Pair<String, Float> findNearest(float[] emb) {

        Pair<String, Float> ret = null;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {

            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];
            System.out.print("anu embed pair=");
            System.out.println(Arrays.toString(knownEmb));

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff*diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }

        return ret;

    }
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }
    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }
    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        System.out.println("jalankan method getCrop MainAct");
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(//from  w w  w. ja v  a  2s. c  om
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);
        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }

        return resultBitmap;
    }
    private Bitmap toBitmap(Image image) {

        byte[] nv21=YUV_420_888toNV21(image);


        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        //System.out.println("bytes"+ Arrays.toString(imageBytes));

        //System.out.println("FORMAT"+image.getFormat());

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            }
            catch (ReadOnlyBufferException ex) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private float[][] convertStringToFloat(String facenya) {
        String[] strings = facenya.split("");
        List<String> stringList = new ArrayList<>();
        List<String[]> tempResult = new ArrayList<>();
        for(String str : strings) {
            if(str.endsWith(">")) {
                str = str.substring(0, str.length() - 1);
                if(str.endsWith(">")) {
                    str = str.substring(0, str.length() - 1);
                }
                stringList.add(str);
                tempResult.add(stringList.toArray(new String[stringList.size()]));
                stringList = new ArrayList<>();
            } else {
                stringList.add(str);
            }
        }
        float[][] originalArray = tempResult.toArray(new float[tempResult.size()][]);

        return originalArray;
    }

    private float[][] convertStringToFloat2(String facenya2){
        String[] rowWithoutSplit = facenya2.replace("[","").split("]");
        int width = rowWithoutSplit.length;
        String cells[] = rowWithoutSplit[0].split(",");
        int height = cells.length;
        float[][] output = new float[width][height];

        for (int i=0; i<width; i++) {
            String cells2[] = rowWithoutSplit[i].split(",");
            for(int j=0; j<height; j++) {
                output[i][j] = Float.parseFloat(cells2[j]);
            }
        }

        return output;
    }

    //Step Registration Face :
    private void addFace()
    {
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Enter Name");

                // Set up the input
            final EditText input = new EditText(context);

            input.setInputType(InputType.TYPE_CLASS_TEXT );
            builder.setView(input);


                // Set up the buttons
            builder.setPositiveButton("ADD", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //Create and Initialize new object with Face embeddings and Name.
                    //membuat face id baru dengan nama objek result

                    database.userDao().insertAll(input.getText().toString(),Arrays.deepToString(embeedings));
                    System.out.println("anu embed kk sebelum="+Arrays.deepToString(embeedings));

                    SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition(
                            "0", "", -1f);
                    dataAllFromDb = database.userDao().getAll().muka;
                    System.out.println("anu embed kk setelah="+Arrays.deepToString(convertStringToFloat2(dataAllFromDb)));

                    result.setExtra(convertStringToFloat2(dataAllFromDb));

                    //menyimpan nama yang diinput dan object result dengan nama SharedPreferenced adalah registered
                    registered.put( input.getText().toString(),result);


                    System.out.println("embed convert="+Arrays.deepToString(embeedings));
                    System.out.println("embed convert false="+embeedings.toString());

                    database.userDao().insertEmbed(Arrays.deepToString(embeedings));
                    database.userDao().insertName(input.getText().toString());


                    registeredEmbed=true;
                    startAddFace=false;
                    startRecogFace=false;
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startAddFace=false;
                    startRecogFace=false;
                    dialog.cancel();
                }
            });

            builder.show();
        }
    }

}

