package com.attendance.facerecogntion;


import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

public class FaceNetTranslator implements Translator<Image, float[]> {

    @Override
    public NDList processInput(TranslatorContext ctx, Image input) {
        NDManager manager = ctx.getNDManager();

        // Convert the image to an NDArray
        NDArray array = input.toNDArray(manager);

        // Resize the image to 160x160 using NDImageUtils
        array = NDImageUtils.resize(array, 160, 160);

        // Normalize pixel values from [0, 255] to [0, 1] and convert type to FLOAT32
        array = array.toType(DataType.FLOAT32, false).div(255f);

        // Expand dimensions to add a batch dimension: [height, width, channels] -> [1, height, width, channels]
        array = array.expandDims(0);

        return new NDList(array);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        // Extract the single output NDArray representing the embedding vector
        NDArray embedding = list.singletonOrThrow();
        float[] embeddingArray = embedding.toFloatArray();
        
        // Apply L2 normalization to ensure values are within [-1, 1] range
        double norm = 0.0;
        for (float val : embeddingArray) {
            norm += val * val;
        }
        norm = Math.sqrt(norm);
        
        // Normalize the embedding manually
        if (norm > 0) {
            for (int i = 0; i < embeddingArray.length; i++) {
                embeddingArray[i] /= (float)norm;
            }
        }
        
        return embeddingArray;
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }
}