package li.songe.gkd.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.store.AiConfig
import li.songe.gkd.store.storeFlow
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0f,
    val top_p: Float = 1f,
    val max_tokens: Int = 4096,
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val temperature: Float = 0f,
    val top_p: Float = 1f,
    val max_tokens: Int = 4096,
)

object AiRuleGenerator {

    val isGenerating = MutableStateFlow(false)
    val pendingCount = MutableStateFlow(0)

    private val taskQueue = Channel<Long>(Channel.UNLIMITED)

    init {
        appScope.launchTry {
            for (snapshotId in taskQueue) {
                processRule(snapshotId)
            }
        }
    }

    private var cachedPrompt: String? = null

    private suspend fun loadPrompt(): String = withContext(Dispatchers.IO) {
        cachedPrompt?.let { return@withContext it }
        val text = app.assets.open("gkd-rule-generator-prompt.md").bufferedReader().readText()
        cachedPrompt = text
        text
    }

    fun fixApiUrl(url: String, protocol: String): String {
        var fixed = url.trim().trimEnd('/')
        // If URL already contains a full path, don't modify
        if (fixed.contains("/chat/completions") || fixed.contains("/messages")) {
            return fixed
        }
        // If URL already ends with /v1, keep it
        if (fixed.endsWith("/v1")) {
            return fixed
        }
        // If URL already contains /v1 somewhere, don't add it again
        if (fixed.contains("/v1/")) {
            return fixed
        }
        return "$fixed/v1"
    }

    private fun buildApiEndpoint(config: AiConfig): String {
        val baseUrl = fixApiUrl(config.apiUrl, config.protocol)
        return when (config.protocol) {
            "anthropic" -> "$baseUrl/messages"
            else -> "$baseUrl/chat/completions"
        }
    }

    private fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(0, TimeUnit.SECONDS) // no timeout for AI response
                    writeTimeout(30, TimeUnit.SECONDS)
                }
            }
        }
    }

    private fun buildRequestBody(config: AiConfig, content: String, maxTokensOverride: Int? = null): String {
        val maxTokens = maxTokensOverride ?: config.maxTokens
        return when (config.protocol) {
            "anthropic" -> json.encodeToString(
                AnthropicRequest(
                    model = config.model,
                    messages = listOf(AnthropicMessage("user", content)),
                    temperature = config.temperature,
                    top_p = config.topP,
                    max_tokens = maxTokens,
                )
            )
            else -> json.encodeToString(
                ChatRequest(
                    model = config.model,
                    messages = listOf(ChatMessage("user", content)),
                    temperature = config.temperature,
                    top_p = config.topP,
                    max_tokens = maxTokens,
                )
            )
        }
    }

    suspend fun testConnection(config: AiConfig): Result<String> = runCatching {
        val httpClient = createHttpClient()
        try {
            val endpoint = buildApiEndpoint(config)
            val response = httpClient.post(endpoint) {
                when (config.protocol) {
                    "anthropic" -> {
                        header("x-api-key", config.apiKey)
                        header("anthropic-version", "2023-06-01")
                    }
                    else -> header("Authorization", "Bearer ${config.apiKey}")
                }
                contentType(ContentType.Application.Json)
                setBody(buildRequestBody(config, "hi", maxTokensOverride = 1))
            }
            response.bodyAsText()
        } finally {
            httpClient.close()
        }
    }

    suspend fun fetchModelList(config: AiConfig): Result<List<String>> = runCatching {
        val baseUrl = fixApiUrl(config.apiUrl, config.protocol)
        val modelsEndpoint = "$baseUrl/models"
        val httpClient = createHttpClient()
        try {
            val response = httpClient.get(modelsEndpoint) {
                when (config.protocol) {
                    "anthropic" -> {
                        header("x-api-key", config.apiKey)
                        header("anthropic-version", "2023-06-01")
                    }
                    else -> header("Authorization", "Bearer ${config.apiKey}")
                }
            }
            val body = response.bodyAsText()
            val jsonElement = json.parseToJsonElement(body)
            val data = jsonElement.jsonObject["data"]
                ?: throw Exception("No data field in response")
            data.jsonArray.mapNotNull { element ->
                try {
                    element.jsonObject["id"]?.jsonPrimitive?.content
                } catch (_: Exception) {
                    null
                }
            }
        } finally {
            httpClient.close()
        }
    }

    suspend fun generateRule(snapshotId: Long) {
        val config = storeFlow.value.aiConfig
        if (config.apiUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
            toast("请先配置 AI 规则设置")
            return
        }
        pendingCount.value++
        taskQueue.send(snapshotId)
        if (isGenerating.value) {
            toast("AI 任务已加入队列（排队中：${pendingCount.value}）")
        }
    }

    private suspend fun processRule(snapshotId: Long) {
        val config = storeFlow.value.aiConfig
        isGenerating.value = true
        try {
            toast("AI 正在生成规则...", forced = true)
            val prompt = loadPrompt()
            val snapshotJson = withContext(Dispatchers.IO) {
                SnapshotExt.snapshotFile(snapshotId).readText()
            }
            val userContent = "$prompt\n$snapshotJson"
            LogUtils.d("AI generateRule: prompt length=${prompt.length}, snapshot length=${snapshotJson.length}, total=${userContent.length}")

            val result = callAiApi(config, userContent)
            LogUtils.d("AI extracted content (first 2000 chars): ${result.take(2000)}")
            val ruleText = extractContent(result)
            LogUtils.d("AI after extractContent (first 2000 chars): ${ruleText.take(2000)}")

            val parsed = parseAndValidateRule(ruleText)
            if (parsed == null) {
                toast("AI 生成的规则 JSON 不合法，重试中...")
                val retryResult = callAiApi(config, userContent)
                val retryText = extractContent(retryResult)
                val retryParsed = parseAndValidateRule(retryText)
                if (retryParsed == null) {
                    toast("AI 生成规则失败：JSON 解析错误")
                    return
                }
                insertRuleToSubscription(retryParsed)
            } else {
                insertRuleToSubscription(parsed)
            }
            toast("AI 规则生成成功并已添加到本地规则")
        } catch (e: Exception) {
            toast("AI 生成规则失败：${e.message}")
            LogUtils.d("AI rule generation failed", e)
        } finally {
            pendingCount.value--
            isGenerating.value = pendingCount.value > 0
        }
    }

    private suspend fun callAiApi(config: AiConfig, userContent: String): String {
        val endpoint = buildApiEndpoint(config)
        val requestBody = buildRequestBody(config, userContent)
        LogUtils.d("AI Request endpoint: $endpoint")
        LogUtils.d("AI Request body (first 2000 chars): ${requestBody.take(2000)}")
        val httpClient = createHttpClient()
        try {
            val response = httpClient.post(endpoint) {
                when (config.protocol) {
                    "anthropic" -> {
                        header("x-api-key", config.apiKey)
                        header("anthropic-version", "2023-06-01")
                    }
                    else -> header("Authorization", "Bearer ${config.apiKey}")
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val body = response.bodyAsText()
            LogUtils.d("AI Response (first 3000 chars): ${body.take(3000)}")
            val jsonElement = json.parseToJsonElement(body)

            return when (config.protocol) {
                "anthropic" -> {
                    val contentArr = jsonElement.jsonObject["content"]
                        ?: throw Exception("No content in Anthropic response")
                    contentArr.jsonArray.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                        ?: throw Exception("No text in Anthropic content")
                }
                else -> {
                    val choices = jsonElement.jsonObject["choices"]
                        ?: throw Exception("No choices in OpenAI response")
                    choices.jsonArray.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                        ?: throw Exception("No content in OpenAI response")
                }
            }
        } finally {
            httpClient.close()
        }
    }

    private fun extractContent(text: String): String {
        var content = text.trim()
        // Try to extract JSON from markdown code block anywhere in the text
        val codeBlockRegex = Regex("```(?:json5?)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val match = codeBlockRegex.find(content)
        if (match != null) {
            content = match.groupValues[1].trim()
        } else {
            // Remove markdown code block markers at start/end
            if (content.startsWith("```json5") || content.startsWith("```json") || content.startsWith("```")) {
                content = content.removePrefix("```json5").removePrefix("```json").removePrefix("```")
            }
            if (content.endsWith("```")) {
                content = content.removeSuffix("```")
            }
            content = content.trim()
        }
        // If content doesn't start with '{', try to find the first '{' and last '}'
        if (!content.startsWith("{")) {
            val firstBrace = content.indexOf('{')
            val lastBrace = content.lastIndexOf('}')
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                content = content.substring(firstBrace, lastBrace + 1)
            }
        }
        return content
    }

    private fun parseAndValidateRule(text: String): RawSubscription? {
        // First try parsing as full subscription
        try {
            return RawSubscription.parse(text, json5 = true)
        } catch (_: Exception) {}
        try {
            return RawSubscription.parse(text, json5 = false)
        } catch (_: Exception) {}

        // AI typically outputs an App-level JSON (id=packageName, groups=[...])
        // Wrap it into a subscription structure
        try {
            val jsonElement = json.parseToJsonElement(text)
            val app = RawSubscription.parseApp(jsonElement.jsonObject)
            LogUtils.d("AI parsed as RawApp: id=${app.id}, groups=${app.groups.size}")
            // Build a minimal subscription containing this app
            return RawSubscription(
                id = -1L,
                name = "AI Generated",
                version = 1,
                apps = listOf(app),
            )
        } catch (e: Exception) {
            LogUtils.d("AI parse as app failed: ${e.message}")
            LogUtils.d("AI failed text (first 500 chars): ${text.take(500)}")
            return null
        }
    }

    private fun insertRuleToSubscription(newSubs: RawSubscription) {
        val currentSubs = subsMapFlow.value[LOCAL_SUBS_ID] ?: return
        val newApp = newSubs.apps.firstOrNull() ?: return
        val existingApp = currentSubs.apps.find { it.id == newApp.id }

        // Reassign group keys to avoid conflicts with existing groups
        val existingKeys = existingApp?.groups?.map { it.key }?.toSet() ?: emptySet()
        var nextKey = (existingKeys.maxOrNull() ?: -1) + 1
        val fixedGroups = newApp.groups.map { group ->
            val newKey = nextKey++
            group.copy(key = newKey)
        }
        val fixedApp = newApp.copy(groups = fixedGroups)

        val updatedSubs = if (existingApp != null) {
            val updatedApp = existingApp.copy(
                groups = existingApp.groups + fixedGroups
            )
            currentSubs.copy(
                apps = currentSubs.apps.map { if (it.id == updatedApp.id) updatedApp else it }
            )
        } else {
            currentSubs.copy(
                apps = (currentSubs.apps + fixedApp).filterIfNotAll { it.groups.isNotEmpty() }
            )
        }
        updateSubscription(updatedSubs)
    }
}
