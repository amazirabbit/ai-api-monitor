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

class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AuthException(message: String) : Exception(message)
class BalanceParseException(message: String) : Exception(message)

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()
    val openAi = Retrofit.Builder().baseUrl("https://api.openai.com/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(OpenAiApi::class.java)
    val deepSeek = Retrofit.Builder().baseUrl("https://api.deepseek.com/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(DeepSeekApi::class.java)
    val openRouter = Retrofit.Builder().baseUrl("https://openrouter.ai/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(OpenRouterApi::class.java)
    val kimi = Retrofit.Builder().baseUrl("https://api.moonshot.cn/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(KimiApi::class.java)
}

data class BalanceResult(val totalBalance: Double, val currency: String = "USD", val updatedAt: Long = System.currentTimeMillis())

class BalanceFetcher {
    companion object { private const val USD_TO_CNY_RATE = 7.2 }
    private val gson = Gson()

    suspend fun fetch(account: ApiAccount): BalanceResult {
        return try {
            when (account.platform) {
                "openai" -> fetchOpenAi(account)
                "deepseek" -> fetchDeepSeek(account)
                "openrouter" -> fetchOpenRouter(account)
                "kimi" -> fetchKimi(account)
                else -> throw Exception("unsupported platform")
            }
        } catch (e: AuthException) { throw e }
        catch (e: NetworkException) { throw e }
        catch (e: IOException) { throw NetworkException("network error", e) }
    }

    private suspend fun fetchOpenAi(account: ApiAccount): BalanceResult {
        val auth = "Bearer \${account.apiKey}"
        val creditResp = try { ApiClient.openAi.getCreditGrants(auth) } catch (e: IOException) { throw NetworkException("OpenAI request failed", e) }
        if (creditResp.code() in listOf(401, 403)) throw AuthException("OpenAI Key invalid")
        if (!creditResp.isSuccessful) throw NetworkException("OpenAI API error")
        val grantsRaw = creditResp.body() ?: throw BalanceParseException("OpenAI empty response")
        val grants = gson.toJsonTree(grantsRaw).asJsonObject
        val granted = grants.get("total_granted")?.asDouble ?: 0.0
        val used = grants.get("total_used")?.asDouble ?: 0.0
        val totalBalance = (granted - used) * USD_TO_CNY_RATE
        return BalanceResult(totalBalance = maxOf(0.0, totalBalance), currency = "CNY")
    }

    private suspend fun fetchDeepSeek(account: ApiAccount): BalanceResult {
        val resp = try { ApiClient.deepSeek.getBalance("Bearer \${account.apiKey}") } catch (e: IOException) { throw NetworkException("DeepSeek request failed", e) }
        if (resp.code() in listOf(401, 403)) throw AuthException("DeepSeek Key invalid")
        if (!resp.isSuccessful) throw NetworkException("DeepSeek API error")
        val data = resp.body() ?: throw BalanceParseException("DeepSeek empty response")
        val root = gson.toJsonTree(data).asJsonObject
        val infos = root.getAsJsonArray("balance_infos") ?: throw BalanceParseException("DeepSeek balance_infos empty")
        val info = infos[0].asJsonObject
        val balance = info.get("total_balance")?.asDouble ?: throw BalanceParseException("DeepSeek total_balance missing")
        val currency = info.get("currency")?.asString ?: "CNY"
        return BalanceResult(totalBalance = maxOf(0.0, balance), currency = currency)
    }

    private suspend fun fetchKimi(account: ApiAccount): BalanceResult {
        val resp = try { ApiClient.kimi.getBalance("Bearer \${account.apiKey}") } catch (e: IOException) { throw NetworkException("Kimi request failed", e) }
        if (resp.code() in listOf(401, 403)) throw AuthException("Kimi Key invalid")
        if (!resp.isSuccessful) throw NetworkException("Kimi API error")
        val data = resp.body() ?: throw BalanceParseException("Kimi empty response")
        val root = gson.toJsonTree(data).asJsonObject
        val dataObj = root.getAsJsonObject("data") ?: throw BalanceParseException("Kimi data field missing")
        val availableBalance = dataObj.get("available_balance")?.asDouble ?: throw BalanceParseException("Kimi available_balance missing")
        return BalanceResult(totalBalance = maxOf(0.0, availableBalance), currency = "CNY")
    }

    private suspend fun fetchOpenRouter(account: ApiAccount): BalanceResult {
        val resp = try { ApiClient.openRouter.getCredits("Bearer \${account.apiKey}") } catch (e: IOException) { throw NetworkException("OpenRouter request failed", e) }
        if (resp.code() in listOf(401, 403)) throw AuthException("OpenRouter Key invalid")
        if (!resp.isSuccessful) throw NetworkException("OpenRouter API error")
        val data = resp.body() ?: throw BalanceParseException("OpenRouter empty response")
        val root = gson.toJsonTree(data).asJsonObject
        val creditsData = root.getAsJsonObject("data")
        val credits: Double = if (creditsData != null) {
            val tc = creditsData.get("total_credits")?.asDouble ?: 0.0
            val tu = creditsData.get("total_usage")?.asDouble ?: 0.0
            tc - tu
        } else {
            root.get("credits")?.asDouble ?: root.get("total_credits")?.asDouble ?: throw BalanceParseException("OpenRouter credits missing")
        }
        val totalBalance = credits * USD_TO_CNY_RATE
        return BalanceResult(totalBalance = maxOf(0.0, totalBalance), currency = "CNY")
    }

    suspend fun checkKey(account: ApiAccount): Pair<Boolean, String?> = try {
        fetch(account); Pair(true, null)
    } catch (e: AuthException) { Pair(false, "auth") }
    catch (e: NetworkException) { Pair(false, "network") }
    catch (e: BalanceParseException) { Pair(false, "parse") }
    catch (e: Exception) { Pair(false, "unknown") }
}
