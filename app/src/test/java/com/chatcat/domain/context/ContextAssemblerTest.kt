package com.chatcat.domain.context

import com.chatcat.domain.model.BlockType
import com.chatcat.domain.model.MessageStatus
import com.chatcat.domain.model.Role
import com.chatcat.domain.model.UiAttachment
import com.chatcat.domain.model.UiBlock
import com.chatcat.domain.model.UiMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextAssemblerTest {

    private val assembler = ContextAssembler()

    private fun user(id: String, text: String) = UiMessage(
        id = id,
        role = Role.USER,
        status = MessageStatus.DONE,
        blocks = listOf(UiBlock("$id-b", 0, BlockType.TEXT, text)),
    )

    private fun assistant(id: String, text: String, status: MessageStatus = MessageStatus.DONE) =
        UiMessage(
            id = id,
            role = Role.ASSISTANT,
            status = status,
            stopReason = if (status == MessageStatus.DONE) "end_turn" else null,
            blocks = listOf(
                UiBlock("$id-t", 0, BlockType.THINKING, "内部推理"),
                UiBlock("$id-b", 1, BlockType.TEXT, text),
            ),
        )

    private fun JsonObject.messages(): JsonArray = this["messages"]!!.jsonArray

    @Test
    fun `drops unpaired rounds and historical thinking`() {
        val history = listOf(
            user("u1", "第一个问题"),
            assistant("a1", "第一个回答"),
            user("u2", "没有回答的提问"),
        )

        val body = assembler.buildRequest(
            history = history,
            question = "新问题",
            model = "deepseek-flash",
            thinking = true,
            search = false,
            reminder = "2026-11-17,星期一,上海,Android,中文",
            enableTitle = true,
        )

        val messages = body.messages()
        assertEquals(4, messages.size)
        assertEquals("latest_reminder", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("第一个问题", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("第一个回答", messages[2].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("新问题", messages[3].jsonObject["content"]!!.jsonPrimitive.content)
        assertTrue(body["title"] != null)
    }

    @Test
    fun `skips refusal and failed assistants`() {
        val refused = assistant("a1", "", MessageStatus.REFUSED).copy(stopReason = "refusal")
        val failed = assistant("a2", "半截回答", MessageStatus.FAILED)
        val history = listOf(user("u1", "问"), refused, user("u2", "再问"), failed)

        val messages = assembler.buildRequest(
            history, "最后的问题", "deepseek-flash", false, false, null, false,
        ).messages()

        assertEquals(1, messages.size)
        assertEquals("最后的问题", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reminder uses App platform and five fields`() {
        val reminder = ContextAssembler.reminder()
        val fields = reminder.split(",")
        assertEquals(5, fields.size)
        assertEquals("App", fields[3])
        assertEquals("中文", fields[4])
        assertTrue(fields[0].matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `declares tools and thinking flag`() {
        val disabled = assembler.buildRequest(
            emptyList(), "hi", "deepseek-flash", false, false, null, false,
        )
        assertEquals(
            "disabled",
            disabled["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
        assertEquals(1, (disabled["tools"] as JsonArray).size)

        val enabled = assembler.buildRequest(
            emptyList(), "hi", "deepseek-flash", true, true, null, false,
        )
        assertEquals(
            "enabled",
            enabled["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
        assertEquals(2, (enabled["tools"] as JsonArray).size)
        assertTrue(enabled["title"] == null)
        assertEquals(
            "high",
            enabled["safety_check"]!!.jsonObject["level"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `prefill appends assistant prefix after the question`() {
        val body = assembler.buildRequest(
            history = emptyList(),
            question = "问题",
            model = "deepseek-flash",
            thinking = false,
            search = false,
            reminder = null,
            enableTitle = false,
            prefill = ContextAssembler.Prefill("前半段"),
        )
        val messages = body.messages()
        assertEquals(2, messages.size)
        val last = messages.last().jsonObject
        assertEquals("assistant", last["role"]!!.jsonPrimitive.content)
        assertEquals("前半段", last["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `thinking prefill keeps thinking block and signature`() {
        val body = assembler.buildRequest(
            history = emptyList(),
            question = "问题",
            model = "deepseek-flash",
            thinking = true,
            search = false,
            reminder = null,
            enableTitle = false,
            prefill = ContextAssembler.Prefill("答案", "推理", "sig-1"),
        )
        val content = body.messages().last().jsonObject["content"]!!.jsonArray
        assertEquals("thinking", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("sig-1", content[0].jsonObject["signature"]!!.jsonPrimitive.content)
        assertEquals("text", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("答案", content[1].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `attachments become file blocks before the text`() {
        val files = listOf(
            UiAttachment("a1", "x.png", "image/png", 10, isImage = true, fileId = "file-img"),
            UiAttachment("a2", "y.pdf", "application/pdf", 20, isImage = false, fileId = "file-doc"),
            // 没有 file_id（导入历史）不应进入请求
            UiAttachment("a3", "z.bin", "", 30, isImage = false, fileId = null),
        )
        val body = assembler.buildRequest(
            history = emptyList(),
            question = "看图",
            model = "deepseek-flash",
            thinking = false,
            search = false,
            reminder = null,
            enableTitle = false,
            attachments = files,
        )
        val content = body.messages().last().jsonObject["content"]!!.jsonArray
        assertEquals(3, content.size)
        assertEquals("image", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "file-img",
            content[0].jsonObject["source"]!!.jsonObject["file_id"]!!.jsonPrimitive.content,
        )
        assertEquals("document", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("text", content[2].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("看图", content[2].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `history keeps attachments of earlier turns`() {
        val files = listOf(
            UiAttachment("a1", "y.pdf", "application/pdf", 20, isImage = false, fileId = "file-doc"),
        )
        val history = listOf(
            user("u1", "看这份文件").copy(attachments = files),
            assistant("a1", "已阅读"),
        )
        val body = assembler.buildRequest(
            history = history,
            question = "总结",
            model = "deepseek-flash",
            thinking = false,
            search = false,
            reminder = null,
            enableTitle = false,
        )
        val first = body.messages()[0].jsonObject
        val content = first["content"]!!.jsonArray
        assertEquals("document", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("text", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(ContextAssembler.hasFileBlocks(history, emptyList()))
    }
}
