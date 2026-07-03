package com.example.zoterohelpernative.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * OpenAI-compatible client for build.nvidia.com (NVIDIA NIM).
 * Base URL: https://integrate.api.nvidia.com/
 * The model list comes from GET /v1/models, so the selector in the chat
 * updates itself as NVIDIA adds or removes models.
 */

data class OpenAIModel(
    val id: String
)

data class OpenAIModelsResponse(
    val data: List<OpenAIModel>?
)

data class OpenAIFunctionDef(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)

data class OpenAIToolDef(
    val type: String = "function",
    val function: OpenAIFunctionDef
)

data class OpenAIFunctionCall(
    val name: String?,
    val arguments: String? // JSON-encoded arguments
)

data class OpenAIToolCall(
    val id: String?,
    val type: String?,
    val function: OpenAIFunctionCall?
)

data class OpenAIMessage(
    val role: String,
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<OpenAIToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null
)

data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val tools: List<OpenAIToolDef>? = null,
    val temperature: Float? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false
)

data class OpenAIChoice(
    val message: OpenAIMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class OpenAIErrorBody(
    val message: String?,
    val code: Any?
)

data class OpenAIChatResponse(
    val choices: List<OpenAIChoice>?,
    val error: OpenAIErrorBody?
)

interface NvidiaApiService {

    @GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): Response<OpenAIModelsResponse>

    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: OpenAIChatRequest
    ): Response<OpenAIChatResponse>
}
