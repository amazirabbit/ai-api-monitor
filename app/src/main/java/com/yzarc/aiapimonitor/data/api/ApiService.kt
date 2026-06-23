package com.yzarc.aiapimonitor.data.api

import android.util.Log
import com.google.gson.Gson
import com.yzarc.aiapimonitor.BuildConfig
import com.yzarc.aiapimonitor.model.ApiAccount
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.io.IOException
import java.util.concurrent.TimeUnit

// ---- Retrofit Interfaces ----

interface OpenAiApi {
    @GET("v1/dashboard/billing/credit_grants")
    suspend fun getCreditGrants(@Header("Authorization") auth: String): Response<Map<String, Any>>
}

interface DeepSeekApi {
    @GET("user/balance")
    suspend fun getBalance(@Header("Authorization") auth: String): Response<Map<String, Any>>
}

interface OpenRouterApi {
    @GET("api/v1/credits")
    suspend fun getCredits(@Header("Authorization") auth: String): Response<Map<String, Any>>
}

interface KimiApi {
    @GET("v1/users/me/balance")
    suspend fun getBalance(@Header("Authorization") auth: String): Response<Map<String, Any>>
}

// ---- Custom Exceptions ----

class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AuthException(message: String) : Exception(message)
class BalanceParseException(message: String) : Exception(message)

// ---- Factory ----

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val openAi = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .client(client).addConverterFactory(GsonConverterFactory.create())
        .build().create(OpenAiApi::class.java)

    val deepSeek = Retrofit.Builder()
        .baseUrl("https://api.deepseek.com/")
        .client(client).addConverterFactory(GsonConverterFactory.create())
        .build().create(DeepSeekApi::class.java)

    val openRouter = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/")
        .client(client).addConverterFactory(GsonConverterFactory.create())
        .build().create(OpenRouterApi::class.java)

    val kimi = Retrofit.Builder()
        .baseUrl("https://api.moonshot.cn/")
        .client(client).addConverterFactory(GsonConverterFactory.create())
        .build().create(KimiApi::class.java)
}

/** API 余额抓取结果 */
data class BalanceResult(
    val totalBalance: Double,
    val currency: String = "USD",
    val updatedAt: Long = System.currentTimeMillis()
)

// ---- Adapter ----

class BalanceFetcher {
    companion object {
        private const val USD_TO_CNY_RATE = 7.2
    }

    private val gson = Gson()

    suspend fun fetch(account: ApiAccount): BalanceResult {
        return try {
            when (account.platform) {
                "openai" -> fetchOpenAi(account)
                "deepseek" -> fetchDeepSeek(account)
                "openrouter" -> fetchOpenRouter(account)
                "kimi" -> fetchKimi(account)
                else -> throw Exception("不支持的平台: ${account.platform}")
            }
        } catch (e: AuthException) {
            throw e
        } catch (e: NetworkException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException("网络连接失败: ${e.message}", e)
        }
    }

    private suspend fun fetchOpenAi(account: ApiAccount): BalanceResult {
        val auth = "Bearer ${account.apiKey}"
        if (BuildConfig.DEBUG) Log.d("API", "查询 OpenAI 余额...")

        val creditResp = try {
            ApiClient.openAi.getCreditGrants(auth)
        } catch (e: IOException) {
            throw NetworkException("OpenAI 网络请求失败: ${e.message}", e)
        }

        if (creditResp.code() in listOf(401, 403)) {
            throw AuthException("OpenAI Key 无效 (${creditResp.code()})")
        }
        if (!creditResp.isSuccessful) {
            throw NetworkException("OpenAI 接口异常 ${creditResp.code()}: ${creditResp.errorBody()?.string()}")
        }

        val grantsRaw = creditResp.body() ?: throw BalanceParseException("OpenAI 返回空响应")
        val grants = gson.toJsonTree(grantsRaw).asJsonObject
        val granted = grants.get("total_granted")?.asDouble ?: 0.0
        val used = grants.get("total_used")?.asDouble ?: 0.0

        val totalBalance = (granted - used) * USD_TO_CNY_RATE
        return BalanceResult(totalBalance = maxOf(0.0, totalBalance), currency = "CNY")
    }

    private suspend fun fetchDeepSeek(account: ApiAccount): BalanceResult {
        if (BuildConfig.DEBUG) Log.d("API", "查询 DeepSeek 余额...")
        val resp = try {
            ApiClient.deepSeek.getBalance("Bearer ${account.apiKey}")
        } catch (e: IOException) {
            throw NetworkException("DeepSeek 网络请求失败: ${e.message}", e)
        }

        if (resp.code() in listOf(401, 403)) {
            throw AuthException("DeepSeek Key 无效 (${resp.code()})")
        }
        if (!resp.isSuccessful) {
            throw NetworkException("DeepSeek 接口异常 ${resp.code()}: ${resp.errorBody()?.string()}")
        }

        val data = resp.body() ?: throw BalanceParseException("DeepSeek 返回空响应")
        val root = gson.toJsonTree(data).asJsonObject
        val infos = root.getAsJsonArray("balance_infos")
        if (infos == null || infos.size() == 0) {
            throw BalanceParseException("DeepSeek balance_infos 为空")
        }
        val info = infos[0].asJsonObject
        val balance = info.get("total_balance")?.asDouble ?: throw BalanceParseException("DeepSeek total_balance 字段缺失")
        val currency = info.get("currency")?.asString ?: "CNY"

        return BalanceResult(totalBalance = maxOf(0.0, balance), currency = currency)
    }

    private suspend fun fetchKimi(account: ApiAccount): BalanceResult {
        if (BuildConfig.DEBUG) Log.d("API", "查询 Kimi 余额...")
        val resp = try {
            ApiClient.kimi.getBalance("Bearer ${account.apiKey}")
        } catch (e: IOException) {
            throw NetworkException("Kimi 网络请求失败: ${e.message}", e)
        }

        if (resp.code() in listOf(401, 403)) {
            throw AuthException("Kimi Key 无效 (${resp.code()})")
        }
        if (!resp.isSuccessful) {
            throw NetworkException("Kimi 接口异常 ${resp.code()}: ${resp.errorBody()?.string()}")
        }

        val data = resp.body() ?: throw BalanceParseException("Kimi 返回空响应")
        val root = gson.toJsonTree(data).asJsonObject
        val dataObj = root.getAsJsonObject("data") ?: throw BalanceParseException("Kimi data 字段缺失")
        val availableBalance = dataObj.get("available_balance")?.asDouble ?: throw BalanceParseException("Kimi available_balance 字段缺失")

        return BalanceResult(totalBalance = maxOf(0.0, availableBalance), currency = "CNY")
    }

    private suspend fun fetchOpenRouter(account: ApiAccount): BalanceResult {
        if (BuildConfig.DEBUG) Log.d("API", "查询 OpenRouter 余额...")
        val resp = try {
            ApiClient.openRouter.getCredits("Bearer ${account.apiKey}")
        } catch (e: IOException) {
            throw NetworkException("OpenRouter 网络请求失败: ${e.message}", e)
        }

        if (resp.code() in listOf(401, 403)) {
            throw AuthException("OpenRouter Key 无效 (${resp.code()})")
        }
        if (!resp.isSuccessful) {
            throw NetworkException("OpenRouter 接口异常 ${resp.code()}: ${resp.errorBody()?.string()}")
        }

        val data = resp.body() ?: throw BalanceParseException("OpenRouter 返回空响应")
        val root = gson.toJsonTree(data).asJsonObject
        val creditsData = root.getAsJsonObject("data")
        val credits: Double = if (creditsData != null) {
            val totalCredits = creditsData.get("total_credits")?.asDouble ?: 0.0
            val totalUsage = creditsData.get("total_usage")?.asDouble ?: 0.0
            totalCredits - totalUsage
        } else {
            root.get("credits")?.asDouble ?: root.get("total_credits")?.asDouble ?: throw BalanceParseException("OpenRouter credits 字段缺失")
        }

        val totalBalance = credits * USD_TO_CNY_RATE
        return BalanceResult(totalBalance = maxOf(0.0, totalBalance), currency = "CNY")
    }

    suspend fun checkKey(account: ApiAccount): Pair<Boolean, String?> = try {
        fetch(account)
        Pair(true, null)
    } catch (e: AuthException) {
        Log.w("API", "Key 检测: 认证失败 ${account.displayName} - ${e.message}")
        Pair(false, "auth")
    } catch (e: NetworkException) {
        Log.w("API", "Key 检测: 网络异常 ${account.displayName} - ${e.message}")
        Pair(false, "network")
    } catch (e: BalanceParseException) {
        Log.w("API", "Key 检测: 解析异常 ${account.displayName} - ${e.message}")
        Pair(false, "parse")
    } catch (e: Exception) {
        Log.w("API", "Key 检测: 未知异常 ${account.displayName} - ${e.message}")
        Pair(false, "unknown")
    }
}