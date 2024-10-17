package br.com.projeto.iniciacaocientifica;

import android.content.Context;
import android.util.Log;

import java.io.File;


public class DirectoryHelper {

    // Função para criar a pasta "imagens", caso não exista
    public static void createImagesDirectory(Context context) {
        File imagesDir = new File(context.getFilesDir(), "imagens");

        if (!imagesDir.exists()) {
            boolean isCreated = imagesDir.mkdir();
            if (isCreated) {
                Log.d("DirectoryHelper", "Pasta 'imagens' criada com sucesso.");
            } else {
                Log.e("DirectoryHelper", "Falha ao criar a pasta 'imagens'.");
            }
        } else {
            Log.d("DirectoryHelper", "A pasta 'imagens' ja existe.");
        }
    }

    public static void createModelDirectory(Context context) {
        File imagesDir = new File(context.getFilesDir(), "modelo");

        if (!imagesDir.exists()) {
            boolean isCreated = imagesDir.mkdir();
            if (isCreated) {
                Log.d("DirectoryHelper", "Pasta 'modelo' criado com sucesso.");
            } else {
                Log.e("DirectoryHelper", "Falha ao criar a pasta 'modelo'.");
            }
        } else {
            Log.d("DirectoryHelper", "A pasta 'modelo' ja existe.");
        }
    }

    public static String getModelFileName(Context context) {
        File modelDir = new File(context.getFilesDir(), "modelo");

        if (modelDir.exists() && modelDir.isDirectory()) {
            File[] files = modelDir.listFiles();

            if (files != null && files.length == 1) {
                return files[0].getName();
            } else if (files != null && files.length == 0) {
                Log.d("DirectoryHelper", "Nenhum arquivo encontrado na pasta 'modelo'.");
            } else {
                Log.e("DirectoryHelper", "A pasta 'modelo' contém mais de um arquivo.");
            }
        } else {
            Log.e("DirectoryHelper", "A pasta 'modelo' não existe.");
        }

        return "";
    }

}

