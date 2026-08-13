package com.qingheng.weight.meal

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class CodexTaskPollingOptions(
    val intervalMillis: Long = 1_000,
    val timeoutMillis: Long = 300_000,
) {
    init {
        require(intervalMillis >= 0) { "轮询间隔不得为负数" }
        require(timeoutMillis > 0) { "超时时间必须大于零" }
    }
}

/**
 * Client for codex-task 0.2.8's asynchronous `/v1/text` contract.
 *
 * [serviceToken] must come from user configuration/secure storage. It is only placed in the
 * Authorization header and is never included in source constants, logs, or exception messages.
 */
class CodexTaskClient(
    serviceBaseUrl: String,
    private val serviceToken: String,
    private val httpClient: OkHttpClient = defaultCodexTaskHttpClient(),
    private val polling: CodexTaskPollingOptions = CodexTaskPollingOptions(),
) {
    private val baseUrl: HttpUrl = serviceBaseUrl.trimEnd('/').toHttpUrlOrNull()
        ?.let { parsed ->
            parsed.newBuilder()
                .encodedPath(parsed.encodedPath.trimEnd('/') + "/")
                .query(null)
                .fragment(null)
                .build()
        }
        ?: throw CodexTaskException.Configuration("CodexTask 服务地址格式无效")
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    init {
        if (serviceToken.isBlank()) throw CodexTaskException.Configuration("CodexTask Token 不能为空")
    }

    /** Reads an Android `content://` image, submits it, polls the job, and parses the result. */
    suspend fun analyze(contentResolver: ContentResolver, imageUri: Uri): MealAnalysis {
        if (imageUri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw CodexTaskException.Image("请选择 content:// 类型的食物图片")
        }
        val bytes = withContext(Dispatchers.IO) {
            val stream = try {
                contentResolver.openInputStream(imageUri)
            } catch (error: Exception) {
                throw CodexTaskException.Image("读取食物图片失败", error)
            } ?: throw CodexTaskException.Image("无法打开所选食物图片")
            stream.use(::readLimitedImage)
        }
        val mimeType = resolveMimeType(contentResolver.getType(imageUri), bytes)
        val upload = prepareImageForUpload(bytes, mimeType)
        return analyzeImage(upload.bytes, upload.mimeType)
    }

    internal suspend fun analyzeImage(
        bytes: ByteArray,
        mimeType: String,
        fileName: String = "meal${MIME_EXTENSIONS[mimeType] ?: ".jpg"}",
    ): MealAnalysis {
        if (bytes.isEmpty()) throw CodexTaskException.Image("食物图片内容为空")
        if (bytes.size > MAX_IMAGE_BYTES) throw CodexTaskException.Image("食物图片不能超过 20 MiB")
        if (mimeType !in MIME_EXTENSIONS) throw CodexTaskException.Image("图片格式仅支持 PNG、JPEG、WebP 或 GIF")

        val receipt = submitText(bytes, mimeType, fileName)
        val text = awaitResult(receipt)
        return try {
            MealAnalysisParser.parse(text)
        } catch (error: MealAnalysisParseException) {
            throw CodexTaskException.Protocol("营养分析结果解析失败：${error.message}", error)
        }
    }

    private suspend fun submitText(bytes: ByteArray, mimeType: String, fileName: String): JobReceipt {
        val body = JSONObject().apply {
            put("prompt", ANALYSIS_PROMPT)
            put("backend", "direct")
            put("reasoning", "medium")
            put("schema", JSONObject(MEAL_SCHEMA))
            put("images", JSONArray().put(JSONObject().apply {
                put("name", fileName)
                put("mimeType", mimeType)
                put("dataBase64", Base64.getEncoder().encodeToString(bytes))
            }))
        }
        val json = executeJson(
            Request.Builder()
                .url(resolveSameOrigin("/v1/text"))
                .post(body.toString().toRequestBody(jsonMediaType))
                .build(),
        )
        val jobId = json.optString("jobId").trim()
        val statusUrl = json.optString("statusUrl").trim()
        if (jobId.isEmpty() || statusUrl.isEmpty()) {
            throw CodexTaskException.Protocol("CodexTask 提交响应缺少 jobId 或 statusUrl")
        }
        return JobReceipt(jobId, statusUrl)
    }

    private suspend fun awaitResult(receipt: JobReceipt): String {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(polling.timeoutMillis)
        while (true) {
            if (System.nanoTime() >= deadline) {
                throw CodexTaskException.Timeout("食物识别超时（任务 ${receipt.jobId}）")
            }
            val snapshot = executeJson(
                Request.Builder().url(resolveSameOrigin(receipt.statusUrl)).get().build(),
            )
            when (val outcome = CodexTaskProtocol.parseSnapshot(snapshot)) {
                JobOutcome.Pending -> {
                    val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                    if (remainingMillis <= 0) {
                        throw CodexTaskException.Timeout("食物识别超时（任务 ${receipt.jobId}）")
                    }
                    delay(minOf(polling.intervalMillis, remainingMillis))
                }
                is JobOutcome.Completed -> return outcome.text
                is JobOutcome.NeedsInput -> throw CodexTaskException.NeedsInput(
                    "模型需要补充信息：${outcome.questions.joinToString("；")}",
                    outcome.questions,
                )
                is JobOutcome.Failed -> throw CodexTaskException.Remote(
                    "食物识别失败 [${outcome.code}]：${outcome.message}",
                    outcome.code,
                )
                is JobOutcome.Cancelled -> throw CodexTaskException.Cancelled(
                    "食物识别已取消 [${outcome.code}]：${outcome.message}",
                )
            }
        }
    }

    private suspend fun executeJson(original: Request): JSONObject = withContext(Dispatchers.IO) {
        val request = original.newBuilder()
            .header("Authorization", "Bearer $serviceToken")
            .header("Accept", "application/json")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMessage = runCatching {
                        JSONObject(responseText).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty().ifBlank { "服务请求失败" }
                    throw CodexTaskException.Http(
                        "CodexTask 服务返回 HTTP ${response.code}：$serverMessage",
                        response.code,
                    )
                }
                try {
                    JSONObject(responseText)
                } catch (error: JSONException) {
                    throw CodexTaskException.Protocol("CodexTask 服务返回了无效 JSON", error)
                }
            }
        } catch (error: CodexTaskException) {
            throw error
        } catch (error: IOException) {
            throw CodexTaskException.Network("连接 CodexTask 服务失败：${error.message ?: "网络异常"}", error)
        }
    }

    /** Prevents a malicious/incorrect statusUrl from receiving the Bearer token. */
    private fun resolveSameOrigin(pathOrUrl: String): HttpUrl {
        val value = pathOrUrl.trim()
        val servicePrefix = baseUrl.encodedPath.trimEnd('/')
        val resolved = value.toHttpUrlOrNull() ?: when {
            servicePrefix.isNotEmpty() && value.startsWith("$servicePrefix/") -> {
                baseUrl.newBuilder().encodedPath("/").build().resolve(value.trimStart('/'))
            }
            else -> baseUrl.resolve(value.trimStart('/'))
        } ?: throw CodexTaskException.Protocol("CodexTask 返回的 statusUrl 无效")
        if (resolved.scheme != baseUrl.scheme || resolved.host != baseUrl.host || resolved.port != baseUrl.port) {
            throw CodexTaskException.Protocol("CodexTask 返回的 statusUrl 不属于已配置服务")
        }
        return resolved
    }

    private fun readLimitedImage(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = try {
                input.read(buffer)
            } catch (error: IOException) {
                throw CodexTaskException.Image("读取食物图片失败", error)
            }
            if (count < 0) break
            total += count
            if (total > MAX_IMAGE_BYTES) throw CodexTaskException.Image("食物图片不能超过 20 MiB")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun resolveMimeType(reportedType: String?, bytes: ByteArray): String {
        val normalized = reportedType?.lowercase()?.substringBefore(';')?.trim()
        if (normalized in MIME_EXTENSIONS) return normalized!!
        return when {
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            bytes.startsWith(0x52, 0x49, 0x46, 0x46) && bytes.size >= 12 &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "image/webp"
            bytes.startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            else -> throw CodexTaskException.Image("无法识别图片格式，仅支持 PNG、JPEG、WebP 或 GIF")
        }
    }

    private fun prepareImageForUpload(bytes: ByteArray, mimeType: String): UploadImage {
        if (bytes.size <= COMPRESSION_THRESHOLD_BYTES) return UploadImage(bytes, mimeType)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return UploadImage(bytes, mimeType)

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_UPLOAD_DIMENSION * 2) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return UploadImage(bytes, mimeType)
        val scale = (MAX_UPLOAD_DIMENSION.toDouble() / maxOf(decoded.width, decoded.height)).coerceAtMost(1.0)
        val resized = if (scale < 1.0) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else decoded
        return try {
            val output = ByteArrayOutputStream()
            if (!resized.compress(Bitmap.CompressFormat.JPEG, JPEG_UPLOAD_QUALITY, output)) {
                UploadImage(bytes, mimeType)
            } else {
                val compressed = output.toByteArray()
                if (compressed.size < bytes.size) UploadImage(compressed, "image/jpeg")
                else UploadImage(bytes, mimeType)
            }
        } finally {
            if (resized !== decoded) resized.recycle()
            decoded.recycle()
        }
    }

    private fun ByteArray.startsWith(vararg signature: Int): Boolean =
        size >= signature.size && signature.indices.all { this[it].toInt() and 0xFF == signature[it] }

    private data class JobReceipt(val jobId: String, val statusUrl: String)
    private data class UploadImage(val bytes: ByteArray, val mimeType: String)

    companion object {
        private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
        private const val COMPRESSION_THRESHOLD_BYTES = 1_500_000
        private const val MAX_UPLOAD_DIMENSION = 1_600
        private const val JPEG_UPLOAD_QUALITY = 85
        private val MIME_EXTENSIONS = mapOf(
            "image/png" to ".png",
            "image/jpeg" to ".jpg",
            "image/webp" to ".webp",
            "image/gif" to ".gif",
        )

        internal const val ANALYSIS_PROMPT = """
你是饮食照片结构化记录器。识别照片中肉眼可见、具有独立食用意义的全部主要食物，并返回严格符合 JSON Schema 的结果。

结构化规则：
1. dishes 是便于用户阅读的菜品或组合名称。
2. foods 必须把复合菜拆成适合跨餐统计的主要食物；同一餐同一种食物只保留一项。
3. foods[].name 使用简短、稳定的中文通用食物名，不包含烹饪方式、份量、品牌或形容词。例如“煎鸡蛋/荷包蛋”统一为“鸡蛋”，“清炒生菜”统一为“生菜”，“酸菜配菜”统一为“酸菜”。
4. foods[].displayName 描述照片中实际形态，例如“煎鸡蛋”“叉烧”。如果加工后名称比原料更适合日常阅读，可保留在 displayName，但 name 仍使用基础食物名，例如叉烧的 name 为“猪肉”、displayName 为“叉烧”。
5. 只记录照片可辨认的主要食物；忽略油、盐、酱汁、葱姜蒜、香辛料等微量配料，不凭空补充看不见的食材。
6. estimatedGrams 与 caloriesKcal 都是照片中可食部分的一人份估算区间，min 不得大于 max。每项热量之和与总热量应基本一致。
7. confidence 表示图片识别把握，范围 0 到 1；不确定时降低 confidence，不要编造精确值。
8. 蛋白质、碳水、脂肪返回整餐估算区间。advice 用中文给一条简短、可执行的建议。
9. 只输出 JSON，不要 Markdown、代码围栏或额外说明。
"""

        private const val MEAL_SCHEMA = """
{
  "type": "object",
  "properties": {
    "dishes": {"type": "array", "items": {"type": "string"}, "minItems": 1},
    "foods": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "properties": {
          "name": {"type": "string", "minLength": 1},
          "displayName": {"type": "string", "minLength": 1},
          "category": {"type": "string", "enum": ["主食", "肉蛋水产", "蔬菜", "水果", "奶豆", "坚果", "饮品", "其他"]},
          "estimatedGrams": {"${'$'}ref": "#/${'$'}defs/range"},
          "caloriesKcal": {"${'$'}ref": "#/${'$'}defs/range"},
          "confidence": {"type": "number", "minimum": 0, "maximum": 1}
        },
        "required": ["name", "displayName", "category", "estimatedGrams", "caloriesKcal", "confidence"],
        "additionalProperties": false
      }
    },
    "caloriesKcal": {"${'$'}ref": "#/${'$'}defs/range"},
    "proteinGrams": {"${'$'}ref": "#/${'$'}defs/range"},
    "carbohydrateGrams": {"${'$'}ref": "#/${'$'}defs/range"},
    "fatGrams": {"${'$'}ref": "#/${'$'}defs/range"},
    "advice": {"type": "string", "minLength": 1}
  },
  "required": ["dishes", "foods", "caloriesKcal", "proteinGrams", "carbohydrateGrams", "fatGrams", "advice"],
  "additionalProperties": false,
  "${'$'}defs": {
    "range": {
      "type": "object",
      "properties": {"min": {"type": "number", "minimum": 0}, "max": {"type": "number", "minimum": 0}},
      "required": ["min", "max"],
      "additionalProperties": false
    }
  }
}
"""
    }
}

internal fun defaultCodexTaskHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(2, TimeUnit.MINUTES)
    .readTimeout(1, TimeUnit.MINUTES)
    .build()

internal sealed interface JobOutcome {
    data object Pending : JobOutcome
    data class Completed(val text: String) : JobOutcome
    data class NeedsInput(val questions: List<String>) : JobOutcome
    data class Failed(val code: String, val message: String) : JobOutcome
    data class Cancelled(val code: String, val message: String) : JobOutcome
}

internal object CodexTaskProtocol {
    private val pendingStatuses = setOf("queued", "running")

    fun parseSnapshot(snapshot: JSONObject): JobOutcome {
        val status = snapshot.optString("status").trim()
        if (status in pendingStatuses) return JobOutcome.Pending
        val result = snapshot.optJSONObject("result")
            ?: throw CodexTaskException.Protocol("CodexTask 终态响应缺少 result")
        return when (status) {
            "completed" -> {
                val text = result.optString("text").trim()
                if (text.isEmpty()) throw CodexTaskException.Protocol("CodexTask 完成结果缺少 text")
                JobOutcome.Completed(text)
            }
            "needs_input" -> {
                val array = result.optJSONArray("questions")
                val questions = if (array == null) emptyList() else buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
                    }
                }
                JobOutcome.NeedsInput(questions.ifEmpty { listOf("请补充食物份量或拍摄信息") })
            }
            "failed" -> {
                val error = result.optJSONObject("error")
                JobOutcome.Failed(
                    error?.optString("code")?.ifBlank { "UNKNOWN" } ?: "UNKNOWN",
                    error?.optString("message")?.ifBlank { "服务未提供错误详情" } ?: "服务未提供错误详情",
                )
            }
            "cancelled" -> {
                val error = result.optJSONObject("error")
                JobOutcome.Cancelled(
                    error?.optString("code")?.ifBlank { "CANCELLED" } ?: "CANCELLED",
                    error?.optString("message")?.ifBlank { "任务已取消" } ?: "任务已取消",
                )
            }
            else -> throw CodexTaskException.Protocol("CodexTask 返回了未知任务状态：${status.ifEmpty { "空" }}")
        }
    }
}

sealed class CodexTaskException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Configuration(message: String) : CodexTaskException(message)
    class Image(message: String, cause: Throwable? = null) : CodexTaskException(message, cause)
    class Network(message: String, cause: Throwable? = null) : CodexTaskException(message, cause)
    class Http(message: String, val statusCode: Int) : CodexTaskException(message)
    class Protocol(message: String, cause: Throwable? = null) : CodexTaskException(message, cause)
    class Remote(message: String, val code: String) : CodexTaskException(message)
    class NeedsInput(message: String, val questions: List<String>) : CodexTaskException(message)
    class Cancelled(message: String) : CodexTaskException(message)
    class Timeout(message: String) : CodexTaskException(message)
}
