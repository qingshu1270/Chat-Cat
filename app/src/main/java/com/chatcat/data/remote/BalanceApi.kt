package com.chatcat.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** 官方「查询余额」GET /user/balance 的响应（docs: api-docs.deepseek.com/zh-cn/api/get-user-balance）。 */
@Serializable
data class BalanceInfo(
    @SerialName("currency") val currency: String = "",
    @SerialName("total_balance") val totalBalance: String = "",
    @SerialName("granted_balance") val grantedBalance: String = "",
    @SerialName("topped_up_balance") val toppedUpBalance: String = "",
)

@Serializable
data class BalanceResponse(
    @SerialName("is_available") val isAvailable: Boolean = false,
    @SerialName("balance_infos") val balanceInfos: List<BalanceInfo> = emptyList(),
) {
    /** 取 CNY（若无则取第一条），无数据时返回 null */
    fun primary(): BalanceInfo? =
        balanceInfos.firstOrNull { it.currency.equals("CNY", true) } ?: balanceInfos.firstOrNull()
}

/**
 * 余额查询：GET {baseUrl}/user/balance，Authorization: Bearer（与 Messages 的 x-api-key 不同）。
 * 只负责 HTTP 与解析，错误统一走 ApiException，UI 只认 Kind。
 */
class BalanceApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(baseUrl: String, apiKey: String): BalanceResponse = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/user/balance"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(ApiException.Kind.NETWORK, e.message ?: "网络连接失败")
        }
        response.use {
            if (!it.isSuccessful) {
                val text = it.body?.string().orEmpty()
                throw ApiException.fromHttp(it.code, text)
            }
            val body = it.body?.string().orEmpty()
            runCatching { json.decodeFromString<BalanceResponse>(body) }
                .getOrElse { throw ApiException(ApiException.Kind.PROTOCOL, "余额数据解析失败") }
        }
    }
}
