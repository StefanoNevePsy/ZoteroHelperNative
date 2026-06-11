package com.example.zoterohelpernative.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null
)

data class GeminiFunctionCall(
    val name: String?,
    val args: Map<String, Any?>?
)

data class GeminiFunctionResponse(
    val name: String,
    val response: Map<String, Any?>
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

// Schema types use the v1beta uppercase convention: OBJECT, STRING, INTEGER...
data class GeminiSchema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null,
    val enum: List<String>? = null
)

data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: GeminiSchema? = null
)

data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null
)

data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String?
)

data class GeminiErrorBody(
    val code: Int?,
    val message: String?,
    val status: String?
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val error: GeminiErrorBody?
)

interface GeminiApiService {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
