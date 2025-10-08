package com.ygmpkk.codesearch.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP-based embedding model implementation
 * Calls remote embedding API endpoints to generate embeddings
 */
public class HttpEmbeddingModel extends EmbeddingModel {
    private static final Logger logger = LogManager.getLogger(HttpEmbeddingModel.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final String apiUrl;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    /**
     * Constructor for HTTP embedding model
     * @param modelName Name of the model
     * @param apiUrl URL of the embedding API endpoint
     * @param apiKey API key for authentication (can be null if not required)
     * @param embeddingDimension Dimension of the embedding vectors
     */
    public HttpEmbeddingModel(String modelName, String apiUrl, String apiKey, int embeddingDimension) {
        super(modelName, embeddingDimension);
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }
    
    /**
     * Constructor with default embedding dimension
     * @param modelName Name of the model
     * @param apiUrl URL of the embedding API endpoint
     * @param apiKey API key for authentication
     */
    public HttpEmbeddingModel(String modelName, String apiUrl, String apiKey) {
        this(modelName, apiUrl, apiKey, 768);
    }
    
    /**
     * Constructor without API key for public endpoints
     * @param modelName Name of the model
     * @param apiUrl URL of the embedding API endpoint
     */
    public HttpEmbeddingModel(String modelName, String apiUrl) {
        this(modelName, apiUrl, null, 768);
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("Initializing HTTP embedding model: {}", modelName);
        logger.info("API URL: {}", apiUrl);
        
        // Test the connection with a simple ping or metadata request
        // For now, just log that we're ready
        logger.info("HTTP embedding model initialized successfully");
    }
    
    @Override
    public float[] generateEmbedding(String content) throws Exception {
        logger.debug("Generating embedding via HTTP API");
        
        // Create request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);
        requestBody.addProperty("input", content);
        
        String jsonBody = gson.toJson(requestBody);
        
        // Build request
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(jsonBody, JSON));
        
        // Add API key if provided
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }
        
        Request request = requestBuilder.build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new IOException("HTTP request failed with code " + response.code() + ": " + errorBody);
            }
            
            // Parse response
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            // Extract embedding from response
            // Assuming standard OpenAI-like API response format:
            // { "data": [{ "embedding": [...] }] }
            float[] embedding = extractEmbedding(jsonResponse);
            
            // Normalize the embedding
            float norm = 0;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            
            if (norm > 0) {
                for (int i = 0; i < embedding.length; i++) {
                    embedding[i] /= norm;
                }
            }
            
            return embedding;
        } catch (IOException e) {
            logger.error("Failed to generate embedding via HTTP: {}", e.getMessage());
            throw new Exception("Failed to generate embedding via HTTP: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract embedding array from API response
     * Supports multiple response formats
     */
    private float[] extractEmbedding(JsonObject response) throws Exception {
        // Try OpenAI-style format: { "data": [{ "embedding": [...] }] }
        if (response.has("data")) {
            JsonArray data = response.getAsJsonArray("data");
            if (data.size() > 0) {
                JsonObject firstItem = data.get(0).getAsJsonObject();
                if (firstItem.has("embedding")) {
                    JsonArray embeddingArray = firstItem.getAsJsonArray("embedding");
                    return jsonArrayToFloatArray(embeddingArray);
                }
            }
        }
        
        // Try direct format: { "embedding": [...] }
        if (response.has("embedding")) {
            JsonArray embeddingArray = response.getAsJsonArray("embedding");
            return jsonArrayToFloatArray(embeddingArray);
        }
        
        // Try nested format: { "embeddings": [[...]] }
        if (response.has("embeddings")) {
            JsonArray embeddings = response.getAsJsonArray("embeddings");
            if (embeddings.size() > 0) {
                JsonArray embeddingArray = embeddings.get(0).getAsJsonArray();
                return jsonArrayToFloatArray(embeddingArray);
            }
        }
        
        throw new Exception("Could not extract embedding from API response. Response format not recognized.");
    }
    
    /**
     * Convert JSON array to float array
     */
    private float[] jsonArrayToFloatArray(JsonArray jsonArray) {
        float[] result = new float[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = jsonArray.get(i).getAsFloat();
        }
        return result;
    }
    
    @Override
    public void close() throws Exception {
        logger.info("Closing HTTP embedding model");
        // OkHttpClient doesn't need explicit closing in most cases
        // But we can shutdown the executor service if needed
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
