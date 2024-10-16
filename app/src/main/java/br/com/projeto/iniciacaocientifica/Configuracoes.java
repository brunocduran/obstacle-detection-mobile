package br.com.projeto.iniciacaocientifica; // Ajuste conforme o nome do seu pacote

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Configuracoes extends AppCompatActivity {

    private FirebaseStorageHelper storageHelper;
    private TextView logTextView;
    private ScrollView scrollViewLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        // Agora passamos 'this' como contexto para o FirebaseStorageHelper
        storageHelper = new FirebaseStorageHelper(this);

        logTextView = findViewById(R.id.log_text_view);
        scrollViewLog = findViewById(R.id.scroll_view_log);

        // Botão para voltar à tela da câmera
        Button voltarButton = findViewById(R.id.btn_voltar);
        voltarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Fecha a activity atual e volta para a tela anterior (câmera)
                finish();
            }
        });

        Button sincronizarButton = findViewById(R.id.btn_sincronizar);
        sincronizarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isInternetAvailable()) {
                    sincronizar(); // Chama o método sincronizar se houver conexão com a internet

                } else {
                    // Mostra um alerta de que não há conexão
                    appendLog("Sem conexão com a internet...");
                    Toast.makeText(Configuracoes.this, "Sem conexão com a internet. Tente novamente.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void sincronizar() {
        // Fazer upload de imagens da pasta "imagens"
        appendLog("Dispositivo conectado a internet...");
        appendLog("Sincronização iniciada...");

        // Enviar as imagens e, após isso, baixar o modelo
        sendImages(new Runnable() {
            @Override
            public void run() {
                // Após o envio de imagens, baixar o modelo
                downloadModel(new Runnable() {
                    @Override
                    public void run() {
                        // Após o download do modelo, finalizar a sincronização
                        appendLog("Sincronização Finalizada...");
                        Toast.makeText(Configuracoes.this, "Sincronização Finalizada", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }



    private void sendImages(Runnable onComplete) {
        appendLog("Iniciando o envio de imagens...");
        final Integer numImages = getNumberImages();

        if (numImages > 0) {
            appendLog("Enviando " + numImages + (numImages > 1 ? " imagens..." : " imagem..."));

            // Variável para contabilizar imagens enviadas
            AtomicBoolean allImagesSent = new AtomicBoolean(false);
            AtomicInteger imagesProcessed = new AtomicInteger(0);

            storageHelper.uploadImagesFromDirectory(this, new Runnable() {
                @Override
                public void run() {
                    // Incrementa o contador de imagens processadas
                    imagesProcessed.incrementAndGet();

                    // Verifica se todas as imagens foram processadas
                    if (imagesProcessed.get() == numImages) {
                        allImagesSent.set(true); // Marca como concluído
                        appendLog("Todas as imagens foram enviadas.");
                        onComplete.run(); // Chama o callback após todas as imagens serem enviadas
                    }
                }
            }, new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    appendLog("Falha ao enviar imagem: " + e.getMessage());
                    imagesProcessed.incrementAndGet();

                    // Verifica se todas as imagens foram processadas
                    if (imagesProcessed.get() == numImages) {
                        allImagesSent.set(true);
                        appendLog("Falha ao enviar algumas imagens, mas o processo foi concluído.");
                        onComplete.run(); // Chama o callback mesmo em caso de falha
                    }
                }
            }, new Runnable() {
                @Override
                public void run() {
                    appendLog("Nenhuma imagem encontrada para enviar...");
                    onComplete.run(); // Não há imagens, então já pode chamar o callback
                }
            });

        } else {
            appendLog("Nenhuma imagem encontrada para enviar...");
            onComplete.run(); // Chama o callback se não houver imagens
        }
    }


    // Função para verificar se há conexão com a internet
    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } else {
            return false;
        }
    }

    // Função para escrever o log
    private void appendLog(String message) {
        logTextView.append("\n" + message);
        // Faz o ScrollView ir para o final conforme novas mensagens são adicionadas
        scrollViewLog.post(() -> scrollViewLog.fullScroll(View.FOCUS_DOWN));
    }

    private int getNumberImages() {
        File imagesDir = new File(this.getFilesDir(), "imagens");

        // Verifica se o diretório "imagens" existe e contém arquivos
        if (imagesDir.exists() && imagesDir.isDirectory()) {
            File[] imageFiles = imagesDir.listFiles(); // Lista os arquivos na pasta
            return imageFiles.length;
        } else {
            return 0;
        }

    }

    private void downloadModel(Runnable onComplete) {
        appendLog("Iniciando o download do modelo...");

        String localFileName = DirectoryHelper.getModelFileName(this);

        if (localFileName.isEmpty()) {
            appendLog("Nenhum modelo local encontrado. Iniciando download...");
            storageHelper.downloadFirstModelFile(this, () -> {
                appendLog("Modelo baixado com sucesso!");
                onComplete.run(); // Continua para o próximo passo
            }, exception -> {
                appendLog("Falha ao baixar o modelo: " + exception.getMessage());
                onComplete.run(); // Continua mesmo em caso de erro
            });
            return;
        }

        storageHelper.getModelFileNameStorage(storageFileName -> {
            if (!localFileName.equals(storageFileName)) {
                appendLog("O modelo local é diferente do modelo no storage. Iniciando o download...");

                storageHelper.downloadFirstModelFile(this, () -> {
                    appendLog("Modelo baixado com sucesso!");

                    File localFile = new File(getFilesDir(), "modelo/" + localFileName);
                    if (localFile.exists()) {
                        boolean deleted = localFile.delete();
                        if (deleted) {
                            appendLog("Modelo local antigo " + localFileName + " foi excluído com sucesso.");
                        } else {
                            appendLog("Falha ao excluir o modelo local antigo " + localFileName + ".");
                        }
                    }
                    onComplete.run(); // Continua para o próximo passo
                }, exception -> {
                    appendLog("Falha ao baixar o modelo: " + exception.getMessage());
                    onComplete.run(); // Continua mesmo em caso de erro
                });
            } else {
                appendLog("O modelo local está atualizado, download não necessário.");
                onComplete.run(); // Continua para o próximo passo
            }
        }, exception -> {
            appendLog("Erro ao buscar o nome do arquivo no storage: " + exception.getMessage());
            onComplete.run(); // Continua mesmo em caso de erro
        });
    }



}


