package br.com.projeto.iniciacaocientifica;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;

import java.io.FileInputStream;
import java.io.IOException;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {

    // region Variables Global
    private Camera mCamera;
    private CameraPreview mCameraPreview;
    private FrameLayout mFrameLayout;

    private MediaPlayer mSoundClear = null;
    private MediaPlayer mSoundNotClear = null;

    private Interpreter tflite;
    private static final int IMAGE_SIZE_X = 224;
    private static final int IMAGE_SIZE_Y = 224;
    private boolean isStopped = false;
    // endregion

    // region Start Application
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSoundClear = MediaPlayer.create(MainActivity.this, R.raw.clear);
        mSoundNotClear = MediaPlayer.create(MainActivity.this, R.raw.notclear);

        setContentView(R.layout.activity_main);

        try {
            // Verifica se o dispositivo tem câmera
            if (!checkCameraHardware(this)) {
                throw new Exception("Dispositvo não possúi câmera! Não é possível utilizar o APP.");
            } else {
                // Caso tenha câmera, verifica se o aplicativo possui permissão para utilizá-la
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
                }
            }

            // Carrega o modelo TensorFlow Lite
            tflite = new Interpreter(loadModelFile());

            // Criar uma instância de Camera
            mCamera = getCameraInstance();

            // Cria visualização e define-a como o conteúdo de nossa atividade
            mCameraPreview = new CameraPreview(MainActivity.this, mCamera);
            mFrameLayout = findViewById(R.id.camera_preview);
            mFrameLayout.addView(mCameraPreview);

            findViewById(R.id.button_capture).setOnClickListener(v -> {
                isStopped = false;
                mCamera.takePicture(null, null, mPicture);
            });

            findViewById(R.id.button_stop).setOnClickListener(v -> isStopped = true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // endregion

    // region Methods

    // Carregar o modelo .tflite do assets
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("combined_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    // Verifica se o dispositivo tem uma câmera
    private boolean checkCameraHardware(@NonNull Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    // Obtendo uma instância do objeto Camera
    public static Camera getCameraInstance() {
        Camera cam = null;
        try {
            cam = Camera.open();
            cam.setDisplayOrientation(90);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cam;
    }

    // Capturando o frame
    private PictureCallback mPicture = new PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            // Converte os bytes da imagem para um bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

            // Faz a predição local usando o modelo TensorFlow Lite
            float result = runInference(bitmap);

            // Executa o som baseado no resultado da predição
            if (result >= 0.5) {
                mSoundClear.start();  // Se o resultado indicar que não há obstáculo
            } else {
                mSoundNotClear.start();  // Se houver obstáculo
            }

            if (!isStopped) {
                //Comentar caso quiser detectar apenas uma imagem
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mCamera.takePicture(null, null, mPicture);
                    }
                }, 500);
            }

            reloadCamera();
        }
    };

    // Método para rodar a inferência no modelo TFLite
    private float runInference(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE_X, IMAGE_SIZE_Y, true);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

        // Atualize para lidar com a saída bidimensional
        float[][] output = new float[1][1];  // Alterado para [1][1] para refletir o shape correto

        // Executa a inferência
        tflite.run(inputBuffer, output);

        // Acessa o valor de saída (primeiro e único valor)
        float result = output[0][0];

        return result;
    }

    // Converter Bitmap para ByteBuffer para ser usado no modelo TFLite
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE_X * IMAGE_SIZE_Y * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[IMAGE_SIZE_X * IMAGE_SIZE_Y];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < IMAGE_SIZE_X; ++i) {
            for (int j = 0; j < IMAGE_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                // corresponder ao preprocess_input da VGG16/VGG19:
                byteBuffer.putFloat((((val >> 16) & 0xFF) - 123.68f));  // Canal Vermelho
                byteBuffer.putFloat((((val >> 8) & 0xFF) - 116.779f));  // Canal Verde
                byteBuffer.putFloat(((val & 0xFF) - 103.939f));         // Canal Azu
            }
        }
        return byteBuffer;
    }

    // Limpando a visualização para o próximo frame
    private void reloadCamera() {
        mCamera.startPreview();
    }
    // endregion
}
