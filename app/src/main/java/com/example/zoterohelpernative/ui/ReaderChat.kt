package com.example.zoterohelpernative.ui

import com.example.zoterohelpernative.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The reader's AI chat: Gemini and NVIDIA NIM providers, the model selector,
 * tool calls (highlights, notes) and saving answers as notes.
 * Extracted from ReaderViewModel; the code is unchanged. Document-specific
 * operations are provided by the reader through the constructor lambdas.
 */
class ReaderChat(
    private val state: MutableStateFlow<ReaderState>,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    /** Full document text (cached by the reader). */
    private val documentText: suspend () -> String,
    /** Executes the create_highlight tool. */
    private val createHighlight: suspend (Map<String, Any?>?) -> Map<String, Any?>,
    /** Executes the save_note tool. */
    private val saveNote: suspend (Map<String, Any?>?) -> Map<String, Any?>,
    /** Transient message for the user (sync banner). */
    private val showMessage: (String) -> Unit
) {
    companion object {
        /** Highlight colors the AI may choose, by name, as Zotero hex values. */
        val HIGHLIGHT_COLORS = mapOf(
        "yellow" to "#ffd400",
        "red" to "#ff6666",
        "green" to "#5fb236",
        "blue" to "#2ea8e5",
        "purple" to "#a28ae5",
        "magenta" to "#e56eee",
        "orange" to "#f19837",
        "gray" to "#aaaaaa"
        )
    }

    // ---- AI Chat (Gemini) ----

    // LLM calls carry the whole document and can take minutes to answer: the
    // default OkHttp 10s read timeout kills every real request with "timeout".
    private val aiHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
            .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .build()
    }

    private val geminiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(aiHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.example.zoterohelpernative.data.GeminiApiService::class.java)
    }

    private val nvidiaService by lazy {
        Retrofit.Builder()
            .baseUrl("https://integrate.api.nvidia.com/")
            .client(aiHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.example.zoterohelpernative.data.NvidiaApiService::class.java)
    }

    fun setChatProvider(provider: String) {
        scope.launch { settingsRepository.saveChatProvider(provider) }
        if (provider == "nvidia" && state.value.nvidiaModels.isEmpty()) {
            refreshNvidiaModels()
        }
    }

    fun setNvidiaModel(model: String) {
        scope.launch { settingsRepository.saveNvidiaModel(model) }
    }

    // The selector is self-updating: the list always comes live from /v1/models
    fun refreshNvidiaModels() {
        scope.launch {
            val apiKey = settingsRepository.nvidiaApiKey.firstOrNull() ?: return@launch
            if (apiKey.isBlank()) return@launch
            state.update { it.copy(isLoadingNvidiaModels = true) }
            try {
                val response = nvidiaService.listModels("Bearer $apiKey")
                val models = response.body()?.data
                    ?.map { it.id }
                    ?.distinct()
                    ?.sorted()
                    ?: emptyList()
                if (models.isNotEmpty()) {
                    state.update { it.copy(nvidiaModels = models) }
                    // Keep the current selection if still available, otherwise pick a default
                    val selected = state.value.selectedNvidiaModel
                    if (selected.isBlank() || selected !in models) {
                        val default = models.firstOrNull { it.contains("llama-3.3-70b-instruct") }
                            ?: models.firstOrNull { it.contains("instruct") }
                            ?: models.first()
                        settingsRepository.saveNvidiaModel(default)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                state.update { it.copy(isLoadingNvidiaModels = false) }
            }
        }
    }


    fun clearChat() {
        state.update { it.copy(chatMessages = emptyList()) }
    }

    private val highlightColorMap = HIGHLIGHT_COLORS

    private fun chatTools(): List<com.example.zoterohelpernative.data.GeminiTool> {
        return listOf(
            com.example.zoterohelpernative.data.GeminiTool(
                functionDeclarations = listOf(
                    com.example.zoterohelpernative.data.GeminiFunctionDeclaration(
                        name = "create_highlight",
                        description = "Crea un'evidenziazione permanente nel PDF su un passaggio del documento. " +
                            "Usala quando l'utente chiede di evidenziare, marcare o segnare passaggi del testo. " +
                            "Puoi chiamarla più volte per evidenziare più passaggi.",
                        parameters = com.example.zoterohelpernative.data.GeminiSchema(
                            type = "OBJECT",
                            properties = mapOf(
                                "quote" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "STRING",
                                    description = "Citazione ESATTA e contigua copiata letteralmente dal testo del documento, " +
                                        "tra 5 e 300 caratteri. Non parafrasare e non attraversare i marcatori [Pagina N]."
                                ),
                                "page" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "INTEGER",
                                    description = "Numero della pagina in cui si trova la citazione, come indicato dai marcatori [Pagina N]."
                                ),
                                "color" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "STRING",
                                    description = "Colore dell'evidenziazione (opzionale).",
                                    enum = highlightColorMap.keys.toList()
                                ),
                                "comment" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "STRING",
                                    description = "Breve nota da allegare all'evidenziazione (opzionale)."
                                )
                            ),
                            required = listOf("quote", "page")
                        )
                    ),
                    com.example.zoterohelpernative.data.GeminiFunctionDeclaration(
                        name = "save_note",
                        description = "Salva una nota permanente su Zotero allegata a questo documento. " +
                            "Usala quando l'utente chiede di salvare un riassunto, una sintesi o degli appunti sul documento.",
                        parameters = com.example.zoterohelpernative.data.GeminiSchema(
                            type = "OBJECT",
                            properties = mapOf(
                                "content_html" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "STRING",
                                    description = "Contenuto della nota in HTML semplice: <h1>, <h2>, <p>, <b>, <i>, <ul>, <li>, <blockquote>. " +
                                        "Inizia con un titolo <h1>."
                                )
                            ),
                            required = listOf("content_html")
                        )
                    )
                )
            )
        )
    }

    fun sendChatMessage(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || state.value.isChatSending) return

        state.update {
            it.copy(
                chatMessages = it.chatMessages + ChatMessage("user", text),
                isChatSending = true
            )
        }

        scope.launch {
            try {
                val fullText = documentText()
                // Gemini Flash has a ~1M-token context; many NIM models stop at 128k tokens
                val providerLimit = if (state.value.chatProvider == "nvidia") 100_000 else 400_000
                val docText = if (fullText.length > providerLimit) fullText.take(providerLimit) else fullText
                val isTruncated = fullText.length > docText.length || fullText.length >= 400_000

                val systemPrompt = buildString {
                    append("Sei un assistente di ricerca accademica integrato in un lettore PDF. ")
                    append("Rispondi in modo conciso e nella lingua dell'utente, basandoti sul documento fornito. ")
                    append("Se l'informazione non è nel documento, dillo esplicitamente. ")
                    append("Quando citi un passaggio, indica la pagina (i marcatori [Pagina N] delimitano le pagine). ")
                    append("Se l'utente chiede di evidenziare passaggi, usa lo strumento create_highlight con citazioni esatte ")
                    append("copiate dal documento; al termine riassumi brevemente cosa hai evidenziato. ")
                    append("Se l'utente chiede di salvare un riassunto o degli appunti su Zotero, usa lo strumento save_note.\n\n")
                    if (docText.isBlank()) {
                        append("ATTENZIONE: non è stato possibile estrarre testo dal documento (potrebbe essere una scansione).")
                    } else {
                        append("=== TESTO DEL DOCUMENTO ===\n")
                        append(docText)
                        if (isTruncated) {
                            append("\n\n[NOTA: il documento continua oltre questo punto ma il testo è stato troncato. ")
                            append("Se l'utente chiede delle parti finali, avvisalo di questo limite.]")
                        }
                    }
                }

                if (state.value.chatProvider == "nvidia") {
                    runNvidiaChat(systemPrompt)
                } else {
                    runGeminiChat(systemPrompt)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val message = if (e is java.net.SocketTimeoutException) {
                    "Il modello non ha risposto in tempo. Sui documenti molto lunghi può volerci qualche minuto: riprova."
                } else {
                    "Errore di rete: ${e.localizedMessage ?: "sconosciuto"}"
                }
                appendChatError(message)
            } finally {
                state.update { it.copy(isChatSending = false) }
            }
        }
    }

    private suspend fun runGeminiChat(systemPrompt: String) {
                val apiKey = settingsRepository.geminiApiKey.firstOrNull()
                if (apiKey.isNullOrBlank()) {
                    appendChatError("Imposta la API key di Gemini nelle Impostazioni per usare la chat.")
                    return
                }

                val contents = state.value.chatMessages
                    .filter { !it.isError }
                    .map {
                        com.example.zoterohelpernative.data.GeminiContent(
                            role = it.role,
                            parts = listOf(com.example.zoterohelpernative.data.GeminiPart(text = it.text))
                        )
                    }
                    .toMutableList()

                val systemInstruction = com.example.zoterohelpernative.data.GeminiContent(
                    parts = listOf(com.example.zoterohelpernative.data.GeminiPart(text = systemPrompt))
                )

                var finalText: String? = null
                var highlightsCreated = 0

                // Tool-use loop: the model may request highlights before answering
                for (round in 0 until 4) {
                    val request = com.example.zoterohelpernative.data.GeminiRequest(
                        contents = contents,
                        systemInstruction = systemInstruction,
                        tools = chatTools()
                    )
                    val response = geminiService.generateContent("gemini-flash-latest", apiKey, request)
                    val body = response.body()

                    if (!response.isSuccessful) {
                        val errorBody = try {
                            response.errorBody()?.string()?.let { raw ->
                                com.google.gson.Gson().fromJson(raw, com.example.zoterohelpernative.data.GeminiResponse::class.java)
                            }
                        } catch (e: Exception) { null }
                        val reason = errorBody?.error?.message ?: body?.error?.message ?: "HTTP ${response.code()}"
                        appendChatError("Errore Gemini: $reason")
                        return
                    }

                    val content = body?.candidates?.firstOrNull()?.content
                    if (content == null) {
                        appendChatError("Errore Gemini: risposta vuota (${body?.error?.message ?: "nessun candidato"})")
                        return
                    }

                    val functionCalls = content.parts.mapNotNull { it.functionCall }
                    val textParts = content.parts.mapNotNull { it.text }.joinToString("")

                    if (functionCalls.isEmpty()) {
                        finalText = textParts
                        break
                    }

                    // Execute the requested tool calls, then send the results back
                    contents += com.example.zoterohelpernative.data.GeminiContent(role = "model", parts = content.parts)
                    val responseParts = functionCalls.map { call ->
                        val result = when (call.name) {
                            "create_highlight" -> createHighlight(call.args).also {
                                if (it["success"] == true) highlightsCreated++
                            }
                            "save_note" -> saveNote(call.args)
                            else -> mapOf("success" to false, "error" to "Funzione sconosciuta: ${call.name}")
                        }
                        com.example.zoterohelpernative.data.GeminiPart(
                            functionResponse = com.example.zoterohelpernative.data.GeminiFunctionResponse(
                                name = call.name ?: "create_highlight",
                                response = result
                            )
                        )
                    }
                    contents += com.example.zoterohelpernative.data.GeminiContent(role = "user", parts = responseParts)
                }

                val answer = finalText?.takeIf { it.isNotBlank() }
                    ?: if (highlightsCreated > 0) {
                        "Ho creato $highlightsCreated evidenziazion${if (highlightsCreated == 1) "e" else "i"} nel documento."
                    } else null

                if (answer != null) {
                    state.update {
                        it.copy(chatMessages = it.chatMessages + ChatMessage("model", answer.trim()))
                    }
                } else {
                    appendChatError("Errore Gemini: nessuna risposta ricevuta.")
                }
    }

    private suspend fun runNvidiaChat(systemPrompt: String) {
        val apiKey = settingsRepository.nvidiaApiKey.firstOrNull()
        if (apiKey.isNullOrBlank()) {
            appendChatError("Imposta la API key di NVIDIA nelle Impostazioni per usare questo provider.")
            return
        }
        val model = state.value.selectedNvidiaModel
        if (model.isBlank()) {
            appendChatError("Seleziona un modello NVIDIA dal menu in alto nella chat.")
            return
        }

        val auth = "Bearer $apiKey"
        val gson = com.google.gson.Gson()

        val messages = mutableListOf(
            com.example.zoterohelpernative.data.OpenAIMessage("system", systemPrompt)
        )
        state.value.chatMessages.filter { !it.isError }.forEach {
            messages += com.example.zoterohelpernative.data.OpenAIMessage(
                role = if (it.role == "model") "assistant" else "user",
                content = it.text
            )
        }

        // Not every NIM model supports tool calling: on a 400 retry without tools
        var useTools = true
        var finalText: String? = null
        var highlightsCreated = 0
        var round = 0

        while (round < 5) {
            round++
            val request = com.example.zoterohelpernative.data.OpenAIChatRequest(
                model = model,
                messages = messages,
                tools = if (useTools) openAiTools() else null,
                temperature = 0.3f,
                maxTokens = 4096
            )
            val response = nvidiaService.chatCompletions(auth, request)

            if (!response.isSuccessful) {
                val rawError = try { response.errorBody()?.string() } catch (e: Exception) { null }
                if (useTools && response.code() == 400) {
                    useTools = false
                    continue
                }
                val detail = rawError?.take(200)?.replace("\n", " ") ?: ""
                appendChatError("Errore NVIDIA (HTTP ${response.code()}): $detail")
                return
            }

            val message = response.body()?.choices?.firstOrNull()?.message
            if (message == null) {
                appendChatError("Errore NVIDIA: risposta vuota.")
                return
            }

            val toolCalls = message.toolCalls.orEmpty()
            if (toolCalls.isEmpty()) {
                finalText = message.content
                break
            }

            messages += message
            for (call in toolCalls) {
                val argsMap: Map<String, Any?> = try {
                    gson.fromJson(
                        call.function?.arguments ?: "{}",
                        object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                    )
                } catch (e: Exception) {
                    emptyMap()
                }
                val result = when (call.function?.name) {
                    "create_highlight" -> createHighlight(argsMap).also {
                        if (it["success"] == true) highlightsCreated++
                    }
                    "save_note" -> saveNote(argsMap)
                    else -> mapOf("success" to false, "error" to "Funzione sconosciuta: ${call.function?.name}")
                }
                messages += com.example.zoterohelpernative.data.OpenAIMessage(
                    role = "tool",
                    content = gson.toJson(result),
                    toolCallId = call.id
                )
            }
        }

        val answer = finalText?.takeIf { it.isNotBlank() }
            ?: if (highlightsCreated > 0) {
                "Ho creato $highlightsCreated evidenziazion${if (highlightsCreated == 1) "e" else "i"} nel documento."
            } else null

        if (answer != null) {
            state.update {
                it.copy(chatMessages = it.chatMessages + ChatMessage("model", answer.trim()))
            }
        } else {
            appendChatError("Errore NVIDIA: nessuna risposta ricevuta.")
        }
    }

    // OpenAI-format mirror of chatTools(), for NVIDIA NIM models
    private fun openAiTools(): List<com.example.zoterohelpernative.data.OpenAIToolDef> = listOf(
        com.example.zoterohelpernative.data.OpenAIToolDef(
            function = com.example.zoterohelpernative.data.OpenAIFunctionDef(
                name = "create_highlight",
                description = "Crea un'evidenziazione permanente nel PDF su un passaggio del documento. " +
                    "Usala quando l'utente chiede di evidenziare, marcare o segnare passaggi del testo. " +
                    "Puoi chiamarla più volte per evidenziare più passaggi.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "quote" to mapOf(
                            "type" to "string",
                            "description" to "Citazione ESATTA e contigua copiata letteralmente dal testo del documento, " +
                                "tra 5 e 300 caratteri. Non parafrasare e non attraversare i marcatori [Pagina N]."
                        ),
                        "page" to mapOf(
                            "type" to "integer",
                            "description" to "Numero della pagina in cui si trova la citazione, come indicato dai marcatori [Pagina N]."
                        ),
                        "color" to mapOf(
                            "type" to "string",
                            "description" to "Colore dell'evidenziazione (opzionale).",
                            "enum" to highlightColorMap.keys.toList()
                        ),
                        "comment" to mapOf(
                            "type" to "string",
                            "description" to "Breve nota da allegare all'evidenziazione (opzionale)."
                        )
                    ),
                    "required" to listOf("quote", "page")
                )
            )
        ),
        com.example.zoterohelpernative.data.OpenAIToolDef(
            function = com.example.zoterohelpernative.data.OpenAIFunctionDef(
                name = "save_note",
                description = "Salva una nota permanente su Zotero allegata a questo documento. " +
                    "Usala quando l'utente chiede di salvare un riassunto, una sintesi o degli appunti sul documento.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "content_html" to mapOf(
                            "type" to "string",
                            "description" to "Contenuto della nota in HTML semplice: <h1>, <h2>, <p>, <b>, <i>, <ul>, <li>, <blockquote>. " +
                                "Inizia con un titolo <h1>."
                        )
                    ),
                    "required" to listOf("content_html")
                )
            )
        )
    )

    private fun appendChatError(message: String) {
        state.update {
            it.copy(chatMessages = it.chatMessages + ChatMessage("model", message, isError = true))
        }
    }

    /** Saves an AI answer as a Zotero child note of the document's parent item. */
    fun saveChatMessageAsNote(index: Int) {
        val message = state.value.chatMessages.getOrNull(index) ?: return
        if (message.role != "model" || message.isError || message.savedAsNote || message.isSavingNote) return

        updateChatMessage(index) { it.copy(isSavingNote = true) }
        scope.launch {
            try {
                val result = saveNote(mapOf("content_html" to chatTextToHtml(message.text)))
                if (result["success"] == true) {
                    updateChatMessage(index) { it.copy(isSavingNote = false, savedAsNote = true) }
                } else {
                    updateChatMessage(index) { it.copy(isSavingNote = false) }
                    showMessage("Salvataggio nota fallito: ${result["error"] ?: "errore sconosciuto"}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateChatMessage(index) { it.copy(isSavingNote = false) }
                showMessage("Salvataggio nota fallito: ${e.localizedMessage ?: "errore di rete"}")
            }
        }
    }

    private fun updateChatMessage(index: Int, transform: (ChatMessage) -> ChatMessage) {
        state.update { s ->
            s.copy(chatMessages = s.chatMessages.mapIndexed { i, m -> if (i == index) transform(m) else m })
        }
    }

    // Chat answers are plain text with light markdown: escape HTML, keep
    // paragraphs/line breaks and translate **bold** so the Zotero note stays readable
    private fun chatTextToHtml(text: String): String {
        fun escape(s: String) = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val date = java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        val body = text.trim()
            .split(Regex("\n{2,}"))
            .joinToString("") { paragraph ->
                val html = escape(paragraph)
                    .replace(Regex("""\*\*(.+?)\*\*"""), "<b>$1</b>")
                    .replace("\n", "<br/>")
                "<p>$html</p>"
            }
        return "<h1>Nota AI ($date)</h1>$body"
    }
}
