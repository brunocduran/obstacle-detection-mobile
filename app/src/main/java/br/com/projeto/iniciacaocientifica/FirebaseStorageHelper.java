package br.com.projeto.iniciacaocientifica;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.UploadTask;

import java.io.File;

public class FirebaseStorageHelper {

    private FirebaseStorage storage;
    private StorageReference storageRef;

    // Atualize o construtor para receber o contexto
    public FirebaseStorageHelper(Context context) {
        // Inicializa o Firebase Storage após o Firebase ter sido inicializado
        FirebaseApp.initializeApp(context);
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
    }

    // Função para fazer o download do primeiro arquivo .tflite da pasta models
    public void downloadFirstModelFile(Context context, final Runnable onSuccess, final OnFailureListener onFailure) {
        // Referência ao diretório "models" no Firebase Storage
        StorageReference modelsRef = storageRef.child("models/");

        // Lista todos os arquivos no diretório "models"
        modelsRef.listAll().addOnSuccessListener(listResult -> {
            // Verifica se há arquivos no diretório
            if (!listResult.getItems().isEmpty()) {
                // Obtém a referência do primeiro arquivo
                StorageReference modelFileRef = listResult.getItems().get(0);
                String fileName = modelFileRef.getName();

                // Diretório local onde o arquivo será salvo
                File localFile = new File(context.getFilesDir(), "modelo/" + fileName);

                // Baixar o arquivo do Firebase Storage
                modelFileRef.getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                        Log.d("FirebaseStorageHelper", "Modelo baixado e salvo com sucesso: " + fileName);
                        onSuccess.run();
                    }
                }).addOnFailureListener(onFailure);
            } else {
                // Nenhum arquivo encontrado na pasta 'models'
                Log.e("FirebaseStorageHelper", "Nenhum arquivo encontrado na pasta 'models'.");
                onFailure.onFailure(new Exception("Nenhum arquivo encontrado na pasta 'models'."));
            }
        }).addOnFailureListener(onFailure);
    }


    // Função para fazer o upload de imagens da pasta "imagens"
    public void uploadImagesFromDirectory(Context context, final Runnable onSuccess, final OnFailureListener onFailure, final Runnable onNoImagesFound) {
        // Diretório local da pasta "imagens"
        File imagesDir = new File(context.getFilesDir(), "imagens");

        // Verifica se o diretório "imagens" existe e contém arquivos
        if (imagesDir.exists() && imagesDir.isDirectory()) {
            File[] imageFiles = imagesDir.listFiles(); // Lista os arquivos na pasta

            if (imageFiles != null && imageFiles.length > 0) {
                for (File imageFile : imageFiles) {
                    if (imageFile.isFile()) {
                        uploadSingleImage(imageFile, onSuccess, onFailure); // Faz o upload de cada imagem
                    }
                }
            } else {
                // Nenhuma imagem encontrada, chama o callback onNoImagesFound
                Log.e("FirebaseStorageHelper", "Nenhuma imagem encontrada na pasta.");
                onNoImagesFound.run();
            }
        } else {
            // A pasta "imagens" não existe, chama o callback onNoImagesFound
            Log.e("FirebaseStorageHelper", "A pasta 'imagens' não existe ou não é um diretório.");
            onNoImagesFound.run();
        }
    }

    // Função para fazer o upload de uma única imagem
    private void uploadSingleImage(File imageFile, final Runnable onSuccess, final OnFailureListener onFailure) {
        // Caminho no Firebase Storage para a pasta "imagens"
        StorageReference imageRef = storageRef.child("imagens/" + imageFile.getName());

        if (imageFile.exists()) {
            imageRef.putFile(Uri.fromFile(imageFile))
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Log.d("FirebaseStorageHelper", "Imagem " + imageFile.getName() + " enviada com sucesso.");

                            // Após o upload bem-sucedido, apaga a imagem localmente
                            if (imageFile.delete()) {
                                Log.d("FirebaseStorageHelper", "Imagem " + imageFile.getName() + " deletada com sucesso.");
                            } else {
                                Log.e("FirebaseStorageHelper", "Falha ao deletar a imagem " + imageFile.getName());
                            }

                            // Chama o callback de sucesso
                            onSuccess.run();
                        }
                    })
                    .addOnFailureListener(onFailure);
        } else {
            Log.e("FirebaseStorageHelper", "Arquivo " + imageFile.getName() + " não encontrado localmente.");
        }
    }

    public void getModelFileNameStorage(final OnSuccessListener<String> onSuccess, final OnFailureListener onFailure) {
        // Referência ao diretório "models" no Firebase Storage
        StorageReference modelsRef = storageRef.child("models/");

        // Lista todos os arquivos no diretório "models"
        modelsRef.listAll().addOnSuccessListener(listResult -> {
            // Verifica se há arquivos no diretório
            if (!listResult.getItems().isEmpty()) {
                // Obtém o nome do primeiro arquivo encontrado
                StorageReference modelFileRef = listResult.getItems().get(0);
                String fileName = modelFileRef.getName();
                Log.d("FirebaseStorageHelper", "Arquivo encontrado no diretório 'models': " + fileName);

                // Chama o callback de sucesso com o nome do arquivo
                onSuccess.onSuccess(fileName);
            } else {
                Log.e("FirebaseStorageHelper", "Nenhum arquivo encontrado no diretório 'models'.");
                onFailure.onFailure(new Exception("Nenhum arquivo encontrado no diretório 'models'."));
            }
        }).addOnFailureListener(onFailure);
    }


}

