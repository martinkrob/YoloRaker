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
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiDetector {

    private static final Logger logger = LoggerFactory.getLogger(AiDetector.class);

    private final String modelName;
    private final ModelService modelService;
    private OrtEnvironment env;
    private OrtSession session;
    private boolean modelLoaded = false;

    public AiDetector(String modelName, ModelService modelService) {
        this.modelName = modelName;
        this.modelService = modelService;
        initModel();
    }

    private void initModel() {
        try {
            InputStream is = null;
            if ("INBUILT".equals(modelName) || modelName == null || modelName.isEmpty()) {
                is = getClass().getResourceAsStream("/models/yolov11-3d-print-failure-detection.onnx");
                if (is == null) {
                    logger.warn("ONNX model file not found in resources (/models/). AI detection will be disabled.");
                    return;
                }
                logger.info("Loading INBUILT ONNX model from classpath");
            } else {
                String absPath = modelService.getModelAbsolutePath(modelName);
                if (absPath != null) {
                    File f = new File(absPath);
                    if (f.exists()) {
                        is = new FileInputStream(f);
                        logger.info("Loading custom ONNX model from filesystem: {}", absPath);
                    } else {
                        logger.error("Custom ONNX model not found: {}. Falling back to INBUILT.", absPath);
                        is = getClass().getResourceAsStream("/models/yolov11-3d-print-failure-detection.onnx");
                    }
                }
            }

            if (is != null) {
                byte[] modelBytes = is.readAllBytes();
                is.close();
                this.env = OrtEnvironment.getEnvironment();
                this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
                this.modelLoaded = true;
                logger.info("ONNX model loaded successfully");
            }
        } catch (OrtException | IOException e) {
            logger.error("Failed to load ONNX model", e);
        }
    }

    // synchronized so that close() (triggered from the web thread when a printer is edited/deleted)
    // cannot free the native OrtSession while an inference is in flight.
    public synchronized DetectionResult detect(byte[] imageBytes) {
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
                    // Standard Ultralytics YOLOv8/v11 detect export (nms=False):
                    // output shape is [1, 4+numClasses, numAnchors] (channels-first, e.g. [1, 7, 8400]).
                    // Row 0..3 = bbox, then one row per class score (already sigmoid-activated).
                    // For this model class order is {0: spaghetti, 1: stringing, 2: zits}, i.e. rows 4/5/6.
                    // We only need "is this class present anywhere", so we take the max score over all anchors
                    // (no NMS required). We also tolerate the transposed [1, numAnchors, 4+numClasses] layout
                    // that some custom exports produce.

                    OnnxTensor output = (OnnxTensor) result.get(0);
                    Object rawValue = output.getValue();
                    if (!(rawValue instanceof float[][][])) {
                        logger.warn("Unexpected ONNX output type {} for model {}. Skipping detection.",
                                rawValue != null ? rawValue.getClass().getSimpleName() : "null", modelName);
                        return new DetectionResult(0f, 0f, 0f, DetectionResult.FailureType.NONE, 0f);
                    }
                    float[][][] outputArray = (float[][][]) rawValue;

                    if (outputArray.length == 0 || outputArray[0].length == 0 || outputArray[0][0].length == 0) {
                        return new DetectionResult(0f, 0f, 0f, DetectionResult.FailureType.NONE, 0f);
                    }

                    int dimA = outputArray[0].length;       // channels-first: 4+numClasses; transposed: numAnchors
                    int dimB = outputArray[0][0].length;     // channels-first: numAnchors;   transposed: 4+numClasses
                    // The feature dimension (4+numClasses) is small; the anchor dimension is large.
                    boolean channelsFirst = dimA <= dimB;
                    int numFeatures = channelsFirst ? dimA : dimB;
                    int numAnchors = channelsFirst ? dimB : dimA;
                    int numClasses = numFeatures - 4;

                    if (numClasses < 1) {
                        logger.warn("Model {} produced {} feature rows (need >= 5). Skipping detection.", modelName, numFeatures);
                        return new DetectionResult(0f, 0f, 0f, DetectionResult.FailureType.NONE, 0f);
                    }

                    float maxSpaghetti = 0f;
                    float maxStringing = 0f;
                    float maxZits = 0f;

                    for (int a = 0; a < numAnchors; a++) {
                        // Class 0 (spaghetti) -> feature row 4, class 1 (stringing) -> 5, class 2 (zits) -> 6
                        if (numClasses >= 1) {
                            float conf = channelsFirst ? outputArray[0][4][a] : outputArray[0][a][4];
                            if (conf > maxSpaghetti) maxSpaghetti = conf;
                        }
                        if (numClasses >= 2) {
                            float conf = channelsFirst ? outputArray[0][5][a] : outputArray[0][a][5];
                            if (conf > maxStringing) maxStringing = conf;
                        }
                        if (numClasses >= 3) {
                            float conf = channelsFirst ? outputArray[0][6][a] : outputArray[0][a][6];
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

    /**
     * Releases the native ONNX session. The shared OrtEnvironment is a JVM-wide
     * singleton and is intentionally left open for other detectors.
     */
    public synchronized void close() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (OrtException e) {
            logger.warn("Failed to close ONNX session for model {}", modelName, e);
        } finally {
            modelLoaded = false;
        }
    }
}
