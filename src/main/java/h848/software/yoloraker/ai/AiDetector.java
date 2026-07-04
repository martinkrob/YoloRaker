package h848.software.yoloraker.ai;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

public class AiDetector {
    private static final Logger logger = LoggerFactory.getLogger(AiDetector.class);

    private final String modelPath;
    private OrtEnvironment env;
    private OrtSession session;
    private boolean modelLoaded = false;
    private final float confidenceThreshold;

    public AiDetector(String modelPath, float confidenceThreshold) {
        this.modelPath = modelPath;
        this.confidenceThreshold = confidenceThreshold;
        initModel();
    }

    private void initModel() {
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            logger.warn("ONNX model file not found at: {}. AI detection will be disabled.", modelFile.getAbsolutePath());
            return;
        }

        try {
            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(modelPath, new OrtSession.SessionOptions());
            this.modelLoaded = true;
            logger.info("ONNX model loaded successfully from {}", modelPath);
        } catch (OrtException e) {
            logger.error("Failed to load ONNX model", e);
        }
    }

    public DetectionResult detect(byte[] imageBytes) {
        if (!modelLoaded) {
            return new DetectionResult(false, 0f);
        }

        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                logger.error("Could not decode image from webcam bytes.");
                return new DetectionResult(false, 0f);
            }

            // YOLO models typically expect 640x640 input
            int inputSize = 640;
            BufferedImage resized = new BufferedImage(inputSize, inputSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, inputSize, inputSize, null);
            g.dispose();

            // Convert to FloatBuffer: [1, 3, 640, 640] layout (NCHW)
            FloatBuffer floatBuffer = FloatBuffer.allocate(3 * inputSize * inputSize);
            for (int y = 0; y < inputSize; y++) {
                for (int x = 0; x < inputSize; x++) {
                    int rgb = resized.getRGB(x, y);
                    // R, G, B channels scaled to 0..1
                    floatBuffer.put(0 * inputSize * inputSize + y * inputSize + x, ((rgb >> 16) & 0xFF) / 255.0f);
                    floatBuffer.put(1 * inputSize * inputSize + y * inputSize + x, ((rgb >> 8) & 0xFF) / 255.0f);
                    floatBuffer.put(2 * inputSize * inputSize + y * inputSize + x, (rgb & 0xFF) / 255.0f);
                }
            }
            floatBuffer.rewind();

            String inputName = session.getInputNames().iterator().next();
            long[] shape = {1, 3, inputSize, inputSize};
            
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, floatBuffer, shape)) {
                Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, tensor);
                
                try (Result result = session.run(inputs)) {
                    // This post-processing depends on the exact YOLO version exported to ONNX.
                    // Assuming a standard YOLOv8 export: shape is usually [1, 84, 8400] (84 = 4 bbox + 80 classes)
                    // Since it's a mock implementation waiting for a real model, we will parse the tensor dynamically.
                    
                    OnnxTensor output = (OnnxTensor) result.get(0);
                    float[][][] outputArray = (float[][][]) output.getValue();
                    // dimensions: [batch][classes+4][anchors]
                    
                    if (outputArray == null || outputArray.length == 0) return new DetectionResult(false, 0f);
                    
                    float maxConf = 0f;
                    int numClasses = outputArray[0].length - 4;
                    int numAnchors = outputArray[0][0].length;
                    
                    for (int a = 0; a < numAnchors; a++) {
                        // For spaghetti detection, let's assume class 0 is spaghetti.
                        float confidence = outputArray[0][4][a]; 
                        if (confidence > maxConf) {
                            maxConf = confidence;
                        }
                    }
                    
                    boolean detected = maxConf >= confidenceThreshold;
                    if (detected) {
                        logger.warn("Spaghetti detected! Confidence: {}", maxConf);
                    }
                    return new DetectionResult(detected, maxConf);
                }
            }
        } catch (Exception e) {
            logger.error("Error during ONNX inference", e);
            return new DetectionResult(false, 0f);
        }
    }
}
