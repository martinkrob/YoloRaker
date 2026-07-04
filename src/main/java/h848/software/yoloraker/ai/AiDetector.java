package h848.software.yoloraker.ai;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiDetector {

    private static final Logger logger = LoggerFactory.getLogger(AiDetector.class);

    private final String modelPath;
    private OrtEnvironment env;
    private OrtSession session;
    private boolean modelLoaded = false;

    public AiDetector(String modelPath) {
        this.modelPath = modelPath;
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
            return new DetectionResult(0f, 0f, 0f, DetectionResult.FailureType.NONE, 0f);
        }

        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                logger.error("Could not decode image from webcam bytes.");
                return new DetectionResult(0f, 0f, 0f, DetectionResult.FailureType.NONE, 0f);
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

                    if (outputArray == null || outputArray.length == 0) {
                        return new DetectionResult(0f, 0f, 0f, DetectionResult.FailureType.NONE, 0f);
                    }

                    float maxSpaghetti = 0f;
                    float maxStringing = 0f;
                    float maxZits = 0f;
                    
                    int numClasses = outputArray[0].length - 4;
                    int numAnchors = outputArray[0][0].length;

                    for (int a = 0; a < numAnchors; a++) {
                        // Index 4 is class 0 (spaghetti), 5 is class 1 (stringing), 6 is class 2 (zits)
                        if (numClasses >= 1) {
                            float conf = outputArray[0][4][a];
                            if (conf > maxSpaghetti) maxSpaghetti = conf;
                        }
                        if (numClasses >= 2) {
                            float conf = outputArray[0][5][a];
                            if (conf > maxStringing) maxStringing = conf;
                        }
                        if (numClasses >= 3) {
                            float conf = outputArray[0][6][a];
                            if (conf > maxZits) maxZits = conf;
                        }
                    }

                    DetectionResult.FailureType highestType = DetectionResult.FailureType.NONE;
                    float highestConf = 0f;
                    
                    if (maxSpaghetti > highestConf) {
                        highestConf = maxSpaghetti;
                        highestType = DetectionResult.FailureType.SPAGHETTI;
                    }
                    if (maxStringing > highestConf) {
                        highestConf = maxStringing;
                        highestType = DetectionResult.FailureType.STRINGING;
                    }
                    if (maxZits > highestConf) {
                        highestConf = maxZits;
                        highestType = DetectionResult.FailureType.ZITS;
                    }

                    return new DetectionResult(maxSpaghetti, maxStringing, maxZits, highestType, highestConf);
                }
            }
        } catch (OrtException | IOException e) {
            logger.error("Error during ONNX inference", e);
            return new DetectionResult(0f, 0f, 0f, DetectionResult.FailureType.NONE, 0f);
        }
    }
}
