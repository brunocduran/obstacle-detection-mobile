package br.com.projeto.iniciacaocientifica;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import com.google.firebase.FirebaseApp;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;

import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

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

    // Variáveis de classe para reutilização
    private ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE_X * IMAGE_SIZE_Y * 3);
    private int[] intValues = new int[IMAGE_SIZE_X * IMAGE_SIZE_Y];

    // region Start Application
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSoundClear = MediaPlayer.create(MainActivity.this, R.raw.clear);
        mSoundNotClear = MediaPlayer.create(MainActivity.this, R.raw.notclear);

        setContentView(R.layout.activity_main);

        // Inicializa o Firebase
        FirebaseApp.initializeApp(this);


        /* FUNCAO DO BOTAO CONFIGURACAO*/
        Button configButton = findViewById(R.id.button_config);
        configButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Configuracoes.class);
            startActivity(intent);
        });

        // Chama a função para criar a pasta "imagens", caso ainda não exista
        DirectoryHelper.createImagesDirectory(this);
        //criar a pasta modelo, caso não exista
        DirectoryHelper.createModelDirectory(this);

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

            //permissão para gravar no armazenamento
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }

            // Chama a função para criar a pasta "imagens", caso ainda não exista
            DirectoryHelper.createImagesDirectory(this);
            //criar a pasta modelo, caso não exista
            DirectoryHelper.createModelDirectory(this);

            // Criar uma instância de Camera
            mCamera = getCameraInstance();

            // Cria visualização e define-a como o conteúdo de nossa atividade
            mCameraPreview = new CameraPreview(MainActivity.this, mCamera);
            mFrameLayout = findViewById(R.id.camera_preview);
            mFrameLayout.addView(mCameraPreview);

            // Carrega o modelo TensorFlow Lite
            tflite = new Interpreter(loadModelFile());

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
        //AssetFileDescriptor fileDescriptor = getAssets().openFd("combined_model.tflite");
        //FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        //FileChannel fileChannel = inputStream.getChannel();
        //long startOffset = fileDescriptor.getStartOffset();
        //long declaredLength = fileDescriptor.getDeclaredLength();
        //return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        // Crie um objeto File para o modelo usando o getFilesDir() e o nome do arquivo
        File modelFile = new File(this.getFilesDir(), "modelo/" + DirectoryHelper.getModelFileName(this));

        // Abra o arquivo como FileInputStream
        FileInputStream inputStream = new FileInputStream(modelFile);

        // Obtenha o canal de arquivo e mapeie o conteúdo para a memória
        FileChannel fileChannel = inputStream.getChannel();
        long declaredLength = fileChannel.size(); // Tamanho do arquivo
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, declaredLength);
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

            // Rotaciona a imagem em 90 graus no sentido horário
            bitmap = rotateImage(bitmap, 90);

            // Converte o bitmap rotacionado de volta para byte[]
            data = convertBitmapToByteArray(bitmap);

            // Faz a predição local usando o modelo TensorFlow Lite
            float result = runInference(bitmap);

            // Executa o som baseado no resultado da predição
            if (result >= 0.1) {
                // Salvar a imagem capturada
                saveImageToFile(data, "clear.");

                mSoundClear.start();  // Se o resultado indicar que não há obstáculo - 1 bip
            } else {
                // Salvar a imagem capturada
                saveImageToFile(data, "noclear.");

                mSoundNotClear.start();  // Se houver obstáculo - 2 bips
            }

            //Loop da captura de imagens para detecção de obstáculos
            if (!isStopped) {
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

    // Função para rotacionar a imagem em 90 graus no sentido horário
    private Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    // Função para converter Bitmap de volta para byte[]
    private byte[] convertBitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);  // Aqui, pode ser JPEG ou PNG
        return byteArrayOutputStream.toByteArray();
    }

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

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE_X * IMAGE_SIZE_Y * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[IMAGE_SIZE_X * IMAGE_SIZE_Y];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Processando todos os pixels
        for (int i = 0; i < intValues.length; i++) {
            int pixel = intValues[i];

            // Extrair cores do pixel
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // Normalizar para o intervalo [-1, 1] conforme o preprocess_input do MobileNetV2
            float normalizedR = (r / 127.5f) - 1.0f;
            float normalizedG = (g / 127.5f) - 1.0f;
            float normalizedB = (b / 127.5f) - 1.0f;

            // Colocar os valores no ByteBuffer
            byteBuffer.putFloat(normalizedR);
            byteBuffer.putFloat(normalizedG);
            byteBuffer.putFloat(normalizedB);
        }

        return byteBuffer;
    }

    // Limpando a visualização para o próximo frame
    private void reloadCamera() {
        mCamera.startPreview();
    }

    // Função para salvar a imagem capturada no dispositivo
    private void saveImageToFile(byte[] data, String title) {
        // Criar um nome único para o arquivo da imagem com base na data/hora atual
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = title + timeStamp + ".jpg";

        // Diretório onde a imagem será salva (neste caso, no diretório de imagens do dispositivo)
        //Diretório: Android\data\br.com.projeto.iniciacaocientifica\files\Pictures
        File storageDir = new File(this.getFilesDir(),"imagens");

        // Verifica se o diretório de armazenamento está acessível
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs(); // Cria o diretório, se não existir
        }

        // Cria o arquivo da imagem no diretório
        File imageFile = new File(storageDir, imageFileName);

        // Escrever os bytes da imagem no arquivo
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(data);
            fos.flush();
            // Exibir uma mensagem de sucesso
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Imagem salva: " + imageFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
            // Exibir mensagem de erro
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Erro ao salvar a imagem", Toast.LENGTH_SHORT).show());
        }
    }
}
