package br.com.projeto.iniciacaocientifica;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.widget.Button;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.FirebaseApp;

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
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private Camera mCamera;
    private CameraPreview mCameraPreview;
    private FrameLayout mFrameLayout;
    private MediaPlayer mSoundClear = null;
    private MediaPlayer mSoundNotClear = null;
    private Interpreter tflite;
    private static final int IMAGE_SIZE_X = 224;
    private static final int IMAGE_SIZE_Y = 224;
    private boolean isStopped = false;
    private ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE_X * IMAGE_SIZE_Y * 3);
    private int[] intValues = new int[IMAGE_SIZE_X * IMAGE_SIZE_Y];
    private int numImagesArmazenadas;
    private boolean syncIsRunning = false;
    private boolean numImageIsRestored = false;
    private FirebaseStorageHelper storageHelper;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "app_preferences";
    private static final String AUTO_UPLOAD_KEY = "auto_upload_images";
    boolean isAutoUploadEnabled = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSoundClear = MediaPlayer.create(MainActivity.this, R.raw.clear);
        mSoundNotClear = MediaPlayer.create(MainActivity.this, R.raw.notclear);

        setContentView(R.layout.activity_main);

        // Inicializa o Firebase
        FirebaseApp.initializeApp(this);

        // Inicialize o storageHelper
        storageHelper = new FirebaseStorageHelper(this);

        Button configButton = findViewById(R.id.button_config);
        configButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Configuracoes.class);
            startActivity(intent);
        });

        // Chama a função para criar a pasta "imagens", caso ainda não exista
        DirectoryHelper.createImagesDirectory(this);
        DirectoryHelper.createModelDirectory(this);

        // Verifica se o dispositivo tem câmera
        if (!checkCameraHardware(this)) {
            Toast.makeText(this, "Dispositivo não possui câmera!", Toast.LENGTH_LONG).show();
            finish(); // Encerra o app se não houver câmera
        }

        // Verifica se a permissão para a câmera foi concedida
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Solicita a permissão
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        } else {
            // Se a permissão já foi concedida, inicializa a câmera
            initializeCameraAndModel();
        }
    }

    // Método para inicializar a câmera e o modelo
    private void initializeCameraAndModel() {
        try {
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
            Toast.makeText(this, "Erro ao inicializar a câmera!", Toast.LENGTH_SHORT).show();
        }
    }

    // Método chamado quando o usuário responde à solicitação de permissão
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Se a permissão foi concedida, inicializa a câmera
                initializeCameraAndModel();
            } else {
                // Se a permissão foi negada, exibe uma mensagem e encerra o app
                Toast.makeText(this, "Permissão para usar a câmera foi negada!", Toast.LENGTH_LONG).show();
                finish(); // Encerra o app
            }
        }
    }


    // Carregar o modelo .tflite do assets
    private MappedByteBuffer loadModelFile() throws IOException {

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
            if (result >= 0.5) {
                // Salvar a imagem capturada
                saveImageToFile(data, "clear.");

                mSoundClear.start();  // Se o resultado indicar que não há obstáculo - 1 bip
            } else {
                // Salvar a imagem capturada
                saveImageToFile(data, "nonclear.");

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
        File storageDir = new File(this.getFilesDir(), "imagens");

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
            if (title.equals("clear.")) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Nenhum obstáculo detectado! Imagem salva.", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Obstáculo detectado! Imagem salva.", Toast.LENGTH_SHORT).show());
            }

            Log.e("MainActivity", "Variavel isAutoUploadEnabled: " + isAutoUploadEnabled);

            if (isAutoUploadEnabled) {
                Log.e("MainActivity", "Variavel numImagesArmazenadas: " + numImagesArmazenadas);
                Log.e("MainActivity", "Variavel syncIsRunning: " + syncIsRunning);

                numImagesArmazenadas = numImagesArmazenadas + 1;

                if ((numImagesArmazenadas >= 10) && !syncIsRunning && numImageIsRestored) {
                    Log.e("MainActivity", "Iniciando o envio de imagens...");
                    syncIsRunning = true; // Marcar que o envio está em andamento

                    sendImages(new Runnable() {
                        @Override
                        public void run() {
                            syncIsRunning = false; // Marcar que o envio terminou
                            numImageIsRestored = false;
                            Log.e("MainActivity", "Envio de imagens concluído.");
                        }
                    });
                } else {
                    if (!syncIsRunning && !numImageIsRestored) {
                        numImagesArmazenadas = Configuracoes.getNumberImages(this);
                        numImageIsRestored = true; // voltar variavel para true para que na proxima vez que rodar, entrar no laco de cima para iniciar o envio novamente apos a numImagesArmazenadas ser atualizada
                       /* referente a variavel acima, somente syncIsRunning nao seria suficiente pois apos terminar de rodar a sendImages (assincrona) ele é passado pra false e na proxima vez que o laco principal
                        rodar, ele pode entrar pra enviar novamente sem ter atualizado a variavel numImagesArmazenadas, por isso preciso de uma variavel nova que vai garantir que isso nao aconteça
                        e tambem para so buscar o numImagesArmzenadas uma vez antes de cada inicio de envio*/
                    }
                }
            }
            //runOnUiThread(() -> Toast.makeText(MainActivity.this, "Imagem salva: " + imageFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
            // Exibir mensagem de erro
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Erro ao salvar a imagem.", Toast.LENGTH_SHORT).show());
        }
    }

    // Método para enviar as imagens em uma thread separada
    public void sendImages(Runnable onComplete) {
        new Thread(() -> {
            try {
                Log.e("MainActivity", "Entrou no sendImages");

                int numImages = Configuracoes.getNumberImages(this);
                AtomicBoolean allImagesSent = new AtomicBoolean(false);
                AtomicInteger imagesProcessed = new AtomicInteger(0);

                // Certifique-se de que o storageHelper está inicializado
                if (storageHelper != null) {
                    storageHelper.uploadImagesFromDirectory(this, new Runnable() {
                        @Override
                        public void run() {
                            imagesProcessed.incrementAndGet();
                            if (imagesProcessed.get() == numImages) {
                                allImagesSent.set(true);
                                onComplete.run();
                            }
                        }
                    }, e -> {
                        imagesProcessed.incrementAndGet();
                        if (imagesProcessed.get() == numImages) {
                            allImagesSent.set(true);
                            onComplete.run();
                        }
                    }, onComplete);
                }

            } catch (Exception e) {
                Log.e("MainActivity", "Erro no envio de imagens: " + e.getMessage());
            }
        }).start();
    }


    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (mCamera == null) {
                initializeCameraAndModel();
            }
            // Carrega o modelo TensorFlow Lite
            tflite = new Interpreter(loadModelFile());

            //recupera o valor do checkbox
            sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

            // Recupera o valor do CheckBox (se está marcado ou não)
            isAutoUploadEnabled = sharedPreferences.getBoolean(AUTO_UPLOAD_KEY, false);

            if (isAutoUploadEnabled) {
                numImagesArmazenadas = Configuracoes.getNumberImages(this);
                numImageIsRestored = false;
            }

            Toast.makeText(this, "Modelo carregado com sucesso.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Nenhum modelo encontrado. Sincronize o aplicativo!", Toast.LENGTH_SHORT).show();
        }
    }
}