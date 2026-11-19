package com.chatcat.ui.common

import com.chatcat.data.remote.ApiException

/**
 * 所有对外错误文案的唯一出口：界面只认 Kind，永远不展示服务端英文原文或 request_id。
 * 原文与 request_id 只写入 logcat，供排查。
 */
object ErrorMessages {

    fun of(kind: ApiException.Kind): String = when (kind) {
        ApiException.Kind.AUTH -> "API Key 无效或已失效，请检查后重新输入"
        ApiException.Kind.BALANCE -> "账户余额不足，请到开放平台充值"
        ApiException.Kind.CONTENT_RISK -> "这条内容不太适合展开，我们换个话题聊聊吧"
        ApiException.Kind.CONTEXT_LIMIT -> "对话长度已达上限，请开启新对话"
        ApiException.Kind.RATE_LIMIT -> "请求过于频繁，请稍后再试"
        ApiException.Kind.SERVER_BUSY -> "服务繁忙，请稍后再试"
        ApiException.Kind.UPLOAD_TOO_LARGE -> "文件过大，请压缩后重试"
        ApiException.Kind.PARSE_FAILED -> "文件解析失败，请更换文件后重试"
        ApiException.Kind.INVALID_REQUEST -> "请求参数有误，请重试；若持续出现请反馈"
        ApiException.Kind.NETWORK -> "网络异常，请检查连接后重试"
        ApiException.Kind.STREAM_INTERRUPTED -> "生成中断，请重试"
        ApiException.Kind.PROTOCOL -> "服务返回异常，请重试"
        ApiException.Kind.CANCELLED -> "已取消"
        ApiException.Kind.UNKNOWN -> "出了点问题，请稍后重试"
    }

    /** Key 校验场景：措辞更适合首屏。 */
    fun ofVerify(throwable: Throwable): String = when (throwable) {
        is ApiException -> when (throwable.kind) {
            ApiException.Kind.AUTH -> "API Key 无效或已失效，请检查后重新输入"
            ApiException.Kind.BALANCE -> "该账号余额不足，请充值后再试"
            ApiException.Kind.NETWORK -> "网络异常，请检查连接后重试"
            ApiException.Kind.RATE_LIMIT -> "尝试过于频繁，请稍后再试"
            ApiException.Kind.SERVER_BUSY -> "服务繁忙，请稍后再试"
            else -> "验证失败，请稍后重试"
        }
        else -> "网络异常，请检查连接后重试"
    }
}
