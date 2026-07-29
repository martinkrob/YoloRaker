package h848.software.yoloraker.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ModelService {
    private static final Logger logger = LoggerFactory.getLogger(ModelService.class);
    private static final String MODELS_DIR = System.getenv().getOrDefault("YOLORAKER_DATA_PATH", "./data") + "/models";

    public ModelService() {
        ensureModelsDir();
    }

    private void ensureModelsDir() {
        File dir = new File(MODELS_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("Created models directory: {}", MODELS_DIR);
            } else {
                logger.error("Failed to create models directory: {}", MODELS_DIR);
            }
        }
    }

    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        models.add("INBUILT");
        
        File dir = new File(MODELS_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".onnx"));
            if (files != null) {
                for (File f : files) {
                    models.add(f.getName());
                }
            }
        }
        return models;
    }

    public boolean saveModel(String filename, InputStream inputStream) {
        if (filename == null || filename.trim().isEmpty() || !filename.toLowerCase().endsWith(".onnx")) {
            logger.warn("Invalid model filename: {}", filename);
            return false;
        }
        try {
            ensureModelsDir();
            Path targetPath = Paths.get(MODELS_DIR, filename);
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Model saved successfully: {}", targetPath);
            return true;
        } catch (IOException e) {
            logger.error("Error saving model: {}", filename, e);
            return false;
        }
    }

    public boolean deleteModel(String filename) {
        if ("INBUILT".equals(filename) || filename == null || filename.contains("/") || filename.contains("\\")) {
            return false;
        }
        File file = new File(MODELS_DIR, filename);
        if (file.exists() && file.isFile()) {
            boolean deleted = file.delete();
            if (deleted) {
                logger.info("Deleted model: {}", file.getAbsolutePath());
            }
            return deleted;
        }
        return false;
    }

    public String getModelAbsolutePath(String filename) {
        if ("INBUILT".equals(filename) || filename == null) {
            return null;
        }
        File file = new File(MODELS_DIR, filename);
        return file.getAbsolutePath();
    }
}
