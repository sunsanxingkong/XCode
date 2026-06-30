package com.xc.code.ai
import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
data class AiConfig(
    val apiUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o"
)
data class ChatMessage(
    val role: String,
    val content: String
)
object ai_api_client {
    private const val PREFS_NAME = "ai_config"
    private const val KEY_URL = "api_url"
    private const val KEY_KEY = "api_key"
    private const val KEY_MODEL = "model_name"
    private val gson = Gson()
    fun save_config(ctx: Context, config: AiConfig) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, config.apiUrl.trim())
            .putString(KEY_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.modelName.trim())
            .apply()
    }
    fun load_config(ctx: Context): AiConfig {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AiConfig(
            apiUrl = prefs.getString(KEY_URL, "") ?: "",
            apiKey = prefs.getString(KEY_KEY, "") ?: "",
            modelName = prefs.getString(KEY_MODEL, "gpt-4o") ?: "gpt-4o"
        )
    }
    fun has_config(ctx: Context): Boolean {
        val cfg = load_config(ctx)
        return cfg.apiUrl.isNotBlank() && cfg.apiKey.isNotBlank()
    }
    suspend fun chat_completion(
        ctx: Context,
        messages: List<ChatMessage>,
        on_stream: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = load_config(ctx)
        if (config.apiUrl.isBlank() || config.apiKey.isBlank()) {
            return@withContext Result.failure(Exception("请先在设置中配置 AI API"))
        }
        try {
            val url = URL(config.apiUrl.trimEnd('/') + "/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            conn.doOutput = true
            conn.connectTimeout = 60000
            conn.readTimeout = 60000
            val body = mapOf(
                "model" to config.modelName,
                "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
                "stream" to false
            )
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(gson.toJson(body))
            writer.flush()
            writer.close()
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errStream = BufferedReader(InputStreamReader(conn.errorStream))
                val err = errStream.readText()
                errStream.close()
                return@withContext Result.failure(Exception("API 返回错误 $responseCode: $err"))
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            reader.close()
            conn.disconnect()
            @Suppress("UNCHECKED_CAST")
            val json = gson.fromJson(response, Map::class.java) as Map<String, Any?>
            val choices = json["choices"] as? List<Map<String, Any?>>
            val message = choices?.firstOrNull()?.get("message") as? Map<String, Any?>
            val content = message?.get("content") as? String
            if (content != null) {
                Result.success(content)
            } else {
                Result.failure(Exception("API 返回格式异常"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun generate_code(
        ctx: Context,
        prompt: String,
        language: String = ""
    ): Result<String> {
        val langHint = if (language.isNotBlank()) " in $language" else ""
        val messages = listOf(
            ChatMessage("system", "你是一个代码助手。请只返回代码，不要包含额外说明。"),
            ChatMessage("user", "请帮我写一段代码$langHint：\n\n$prompt")
        )
        return chat_completion(ctx, messages)
    }
}
