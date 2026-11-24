package com.chatcat.ui.preview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatcat.domain.model.BlockType
import com.chatcat.domain.model.MessageStatus
import com.chatcat.domain.model.Role
import com.chatcat.domain.model.UiBlock
import com.chatcat.domain.model.UiMessage
import com.chatcat.ui.chat.ChatChrome
import com.chatcat.ui.chat.Composer
import com.chatcat.ui.chat.MessageItem
import com.chatcat.ui.chat.rememberChatListController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 渲染预览（开发者模式）。
 *
 * 界面与真实对话页一致，但**不发任何请求**：本地按脚本流式「吐字」，
 * 用来验证 Markdown / 代码块 / 表格 / 公式 / 搜索胶囊 / 长思考 / 错误卡片等渲染，
 * 避免每次调试都花 token 打真实接口。
 */
@Composable
fun PreviewScreen(
    thinkingDisplay: String,
    onBack: () -> Unit,
) {
    val messages = remember { mutableStateListOf<UiMessage>() }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var seq by remember { mutableStateOf(0) }

    fun stop() {
        job?.cancel()
        job = null
        streaming = false
    }

    fun run(scenario: PreviewScenario) {
        stop()
        seq += 1
        val userId = "u-$seq"
        val botId = "a-$seq"
        val prompt = scenario.prompt.ifBlank { "（预览）${scenario.title}" }
        messages += UiMessage(
            id = userId,
            role = Role.USER,
            status = MessageStatus.DONE,
            blocks = listOf(UiBlock("$userId-0", 0, BlockType.TEXT, prompt)),
            createdAt = System.currentTimeMillis(),
        )
        messages += UiMessage(
            id = botId,
            role = Role.ASSISTANT,
            status = MessageStatus.PENDING,
            createdAt = System.currentTimeMillis(),
        )
        streaming = true
        job = scope.launch {
            val nodes = mutableListOf<Node>()
            val startedAt = System.currentTimeMillis()
            suspend fun push(status: MessageStatus = MessageStatus.STREAMING, done: Boolean = false) {
                val index = messages.indexOfFirst { it.id == botId }
                if (index < 0) return
                messages[index] = messages[index].copy(
                    status = status,
                    blocks = nodes.toBlocks(botId),
                    thinkingMs = if (done) System.currentTimeMillis() - startedAt else 0L,
                )
                // 滚动交给 ChatListController：与对话页同一套「贴底 + 缓动」规则
            }

            runCatching {
                scenario.script(
                    { chunk -> nodes.onThink(chunk) },
                    { nodes.onCloseThink() },
                    { payload -> nodes.onTool(payload) },
                    { chunk -> nodes.onText(chunk) },
                    { ms ->
                        push(status = MessageStatus.STREAMING)
                        delay(ms)
                    },
                )
            }
            push(status = MessageStatus.DONE, done = true)
            streaming = false
        }
    }

    // 系统返回：回到上一级（之前没接管，会直接退出应用回到桌面）
    BackHandler {
        stop()
        onBack()
    }

    // 首次进入给一个默认场景，省得面对空页面
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) run(PreviewScenarios.webSearch)
    }

    val controller = rememberChatListController(sessionKey = "preview")
    // 换场景时跳到最新一条（用户可能正翻着上一轮的内容）
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) controller.scrollToLatest(animated = false)
    }

    ChatChrome(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { stop(); onBack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "渲染预览",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        "本地模拟数据 · 不请求接口 · 不消耗 token",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        bottomBar = {
            Column {
                // 场景按钮：点一下就跑对应脚本
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PreviewScenarios.all.forEach { scenario ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50),
                            onClick = { run(scenario) },
                            enabled = !streaming,
                        ) {
                            Text(
                                scenario.title,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Composer(
                    showDisclaimer = false,
                    input = input,
                    streaming = streaming,
                    thinking = false,
                    search = false,
                    attachments = emptyList(),
                    balanceYuan = null,
                    balanceWarn = 10f,
                    onOpenBalance = {},
                    onInput = { input = it },
                    onSend = {
                        val text = input.trim()
                        if (text.isNotEmpty() && !streaming) {
                            input = ""
                            run(PreviewScenarios.match(text))
                        }
                    },
                    onStop = { stop() },
                    onToggleThinking = {},
                    onToggleSearch = {},
                    onAddFiles = {},
                    onTakePhoto = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                )
                Text(
                    "发送任意内容会按关键词挑一个最接近的内置场景（含「网页」「代码」「表」「公式」「错误」等）。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            state = controller.listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 与对话页同一套规则：底部锚定，新内容从下往上长
            reverseLayout = true,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(messages.asReversed(), key = { it.id }) { message ->
                MessageItem(
                    message = message,
                    thinkingDisplay = thinkingDisplay,
                    feedback = 0,
                    onRetry = {},
                    onRegenerate = {},
                    isLast = message.id == messages.lastOrNull()?.id,
                    onContinue = {},
                    onSwitchVersion = { _, _ -> },
                    onFeedback = { _, _ -> },
                    onShowUsage = {},
                    onSelectText = { _ -> },
                    onEdit = { _ -> },
                    onPreviewImage = { _ -> },
                )
            }
        }
    }
}

// ---------------- 场景脚本 ----------------

private sealed class Node {
    class Think(val sb: StringBuilder = StringBuilder(), var closed: Boolean = false) : Node()
    class Tool(val payload: String) : Node()
    class Text(val sb: StringBuilder = StringBuilder()) : Node()
}

private fun MutableList<Node>.onThink(chunk: String) {
    val last = lastOrNull()
    if (last is Node.Think && !last.closed) last.sb.append(chunk) else add(Node.Think(StringBuilder(chunk)))
}

private fun MutableList<Node>.onCloseThink() {
    (lastOrNull() as? Node.Think)?.closed = true
}

private fun MutableList<Node>.onTool(payload: String) {
    add(Node.Tool(payload))
}

private fun MutableList<Node>.onText(chunk: String) {
    val last = lastOrNull()
    if (last is Node.Text) last.sb.append(chunk) else add(Node.Text(StringBuilder(chunk)))
}

private fun List<Node>.toBlocks(messageId: String): List<UiBlock> = mapIndexed { index, node ->
    when (node) {
        is Node.Think -> UiBlock(
            id = "$messageId-t$index",
            ordinal = index,
            type = BlockType.THINKING,
            text = node.sb.toString(),
            closed = node.closed,
        )
        is Node.Tool -> UiBlock(
            id = "$messageId-tool$index",
            ordinal = index,
            type = BlockType.TOOL,
            text = "",
            payload = node.payload,
            closed = true,
        )
        is Node.Text -> UiBlock(
            id = "$messageId-x$index",
            ordinal = index,
            type = BlockType.TEXT,
            text = node.sb.toString(),
            closed = false,
        )
    }
}

/** 一个预览场景：标题 + 触发词 + 脚本（脚本里用 [pause] 控制吐字节奏） */
class PreviewScenario(
    val title: String,
    val prompt: String,
    val keywords: List<String> = emptyList(),
    val script: suspend (
        onThink: (String) -> Unit,
        onCloseThink: () -> Unit,
        onTool: (String) -> Unit,
        onText: (String) -> Unit,
        pause: suspend (Long) -> Unit,
    ) -> Unit,
)

object PreviewScenarios {

    private val SEARCH_PAYLOAD = """
{"type":"web_search_tool_result","content":[
 {"title":"自主智能无人系统（清华大学出版社）","url":"https://www.tup.tsinghua.edu.cn/books/1","snippet":"以自主智能无人系统为主线，覆盖感知、决策与控制。","site_name":"清华大学出版社"},
 {"title":"无人系统 环境感知与规划控制","url":"https://www.tongji.edu.cn/p/3","snippet":"配套实验较多，适合作课程教材。","site_name":"同济大学"},
 {"title":"移动机器人技术开发与应用","url":"https://www.cmpbook.com/p/4","snippet":"偏工程实践，可作为补充读物。","site_name":"机工教育"}]}
""".trim()

    private val FETCH_PAYLOAD = """
{"type":"web_fetch_tool_result","content":{"type":"web_fetch_result","url":"https://www.tup.tsinghua.edu.cn/books/1","title":"清华大学出版社--图书详情"}}
""".trim()

    // 思考块按 12 字一块 / 60ms 投递：既像真实 token 流，又能在 7~8 秒内看完整段
    private const val THINK_CHUNK = 12
    private const val THINK_GAP = 60L
    private const val ANSWER_GAP = 40L

    /** 约 1500 字的思考链：用于检验限高框跟随、长文本解析成本与末尾收束 */
    private val LONG_THINKING = listOf(
        "先把问题定义清楚：用户要的不是「更快」，而是「看起来连续」，这两件事不是一回事。\n",
        "现在的症状是每帧新增字数在 1 到 300 之间波动，画面上就会出现憋一段、再冒一段。\n",
        "拆开看，一次上屏只涉及三件事：数据到达的频率、每次绘制新增多少字符、滚动要不要跟着走。\n",
        "数据到达控制不了，服务端按 token 发，网络还会抖动，所以只能在上屏这一层做缓冲。\n",
        "缓冲有两条路：按固定间隔节流，或者按积压量自适应。前者实现简单，后者手感更好。\n",
        "固定间隔的问题很明显：突发时一次要吐很多字，间隔越稳，单次跳变越大。\n",
        "自适应的问题是参数难定：放太快会糊成一片，放太慢又永远追不上，看起来像卡住。\n",
        "折中的思路是先算积压，再按「希望多少帧追平」反推每帧字数，最后加一个上下限。\n",
        "上下限很关键。上限太大等于没缓冲，下限太小在长答案上会欠账，越积越多。\n",
        "按 60fps 算，20 帧大约是 330 毫秒，这个时长在感知上刚好是连续，而不是一顿一顿。\n",
        "所以上限取 6 到 8 个字比较合适，再多就会出现肉眼可见的整块跳变。\n",
        "接下来是 Markdown 解析，这才是真正的成本大头，而且成本随文本长度线性增长。\n",
        "如果每次文本变化都重新解析整段，长答案必然会在某个体量上突然卡住，这不是概率问题。\n",
        "可行的办法是按块缓存：只有正在增长的最后一个块需要重新解析，前面的块结果不变。\n",
        "块内还要再分一层，段落级的行内解析结果可以缓存，键直接用段落原文。\n",
        "缓存必须设上限，否则长对话会把内存吃掉；按段落数和总字符数双限比较稳。\n",
        "另一类坑是未闭合语法：流到一半的围栏或粗体标记会被渲染成字面量，然后突然跳变成样式。\n",
        "这个可以临时闭合：渲染前补齐未闭合的围栏、行内代码、粗体和删除线，只影响渲染。\n",
        "补齐结果不能写回原文，否则下一帧解析会拿到被污染的文本，越补越乱。\n",
        "然后是滚动。滚动做不好，前面再顺也会看起来顿，因为它决定视线跟着什么走。\n",
        "贴底判定不能用「当前是否在底部」，因为内容一长高，这个条件立刻就不成立了。\n",
        "应该看用户意图：手指一拖动就停止跟随，松手时如果已经在底部，再恢复跟随。\n",
        "程序化跟随本身也要缓动，直接赋值会把一次大跳变成瞬间位移，观感很差。\n",
        "缓动可以按剩余距离比例走：距离大时走得快，接近底部时收敛，视觉上自然收尾。\n",
        "还有一点容易忽略：跟随应该用延迟请求滚动，而不是每帧起协程强制走一遍布局。\n",
        "强制布局会打断 Compose 的帧预算，token 越密越容易掉帧，这正是上一版卡死的直接原因。\n",
        "另外要区分两种场景：限高窗口是取景框，不接受手势，永远跟随；全展开才把控制权交给用户。\n",
        "两种场景混在一起写，就会出现「用户想往上翻，却被拽回底部」这种最难受的体验。\n",
        "最后定验收标准。不看平均帧率，平均帧率会把长卡顿抹平，看不出问题。\n",
        "改看两条硬指标：有没有超过 100 毫秒的画面空白，有没有单帧超过 30 字的跳变。\n",
        "这两条都过了，剩下的就是参数微调，属于手感问题，不是架构问题，可以交给开发者模式。\n",
    )

    private val LONG_THINKING_ANSWER = """
想清楚了，结论分四层：

1. **解耦到达与上屏**：网络怎么给不重要，屏幕按固定节奏取，取多少由积压量决定
2. **解析按块缓存**：只重算正在增长的尾块，前序块复用结果，成本与答案长度脱钩
3. **滚动看意图不看位置**：拖动即暂停，回底才恢复，程序化跟随一律走缓动
4. **验收看硬指标**：100ms 空白与 30 字跳变，两个都不许出现

否则参数再调也只是把抖动挪到别的地方。
    """.trimIndent()

    /** 约 2200 字的长回答：标题、列表、表格、代码、引用、分割线全带上 */
    private val LONG_ANSWER = """
## 结论

把「数据到达」和「字符上屏」解耦，只改渲染层，协议与后端都不用动。

## 一、问题定位

现在的症状可以拆成三层，它们的成因并不相同：

1. **憋**：一次到 300 字，屏幕长时间不动
2. **冒**：下一帧一次性出现 300 字，视线被拽走
3. **抖**：滚动与渲染抢同一帧，答案越长越明显

前两条属于「节奏」，第三条属于「成本」。只调节奏不降成本，长答案上一定会复发。

## 二、节奏：按积压量算步长

每帧先算积压，再按希望的追平帧数反推步长：

```kotlin
val backlog = full.length - shown
val frames = if (streaming) drainFrames else drainFrames / 3
val step = ceil(backlog / frames.toFloat()).toInt().coerceIn(1, maxStep)
shown = (shown + step).coerceAtMost(full.length)
```

三个参数决定手感：

| 参数 | 作用 | 默认值 | 调大的后果 |
| --- | --- | --- | --- |
| `drainFrames` | 多少帧追平积压 | 20 | 更从容，但落后更多 |
| `maxStep` | 单帧最多放几个字 | 6 | 追得更快，但会出现整块跳变 |
| 收尾倍率 | 流结束后加速排空 | 3 | 收得干脆，但末尾会一闪而过 |

经验值：`maxStep` 超过 8，肉眼就能看出「一块一块」；低于 4，在长答案上永远追不平。

## 三、成本：解析按块缓存

真正拖垮帧率的是 Markdown 解析，不是绘制。要点只有一条：**别重新解析没变的文本**。

- 块级：只有尾块在增长，前序块复用已解析结果
- 段落级：行内解析按段落原文做键缓存，长短文本都受益
- 上限：段落数与总字符数双限，防止长对话吃内存

另外，流到一半的未闭合语法要临时补齐，否则会先露出 `**` 再跳变成粗体。

> 补齐只作用于渲染用的副本，不能写回原文。写回会让下一帧的缓存键变化，命中率直接归零。

## 四、滚动：看意图，不看位置

「当前是否贴底」这个判定在流式场景里几乎永远为假，因为内容一长高就不满足了。

正确的做法是看交互：

1. 手指拖动开始 → 立即停止跟随
2. 拖动结束 → 若已回到底部，恢复跟随
3. 程序化跟随 → 按剩余距离比例缓动，不要直接赋值

## 五、验收

不要看平均帧率，它会把长卡顿平均掉。改看两条硬指标：

- 任意时刻不出现超过 100ms 的画面空白
- 单帧新增不超过 30 字（含首帧）

两条都过，再谈手感微调。手感属于偏好，架构属于对错，顺序不能颠倒。
    """.trimIndent()

    /** 联网：思考 → 搜索胶囊 → 再思考 → 带角标的正文 */
    val webSearch = PreviewScenario(
        title = "网页搜索",
        prompt = "推荐几本无人系统教材，并说明取舍。",
        keywords = listOf("网页", "搜索", "联网", "资料"),
        script = { think, closeThink, tool, text, pause ->
            think("用户问的是教材推荐，先确认版本与配套资源。")
            pause(160)
            think("这类问题需要看三件事：理论深度、配套实验、出版时间。")
            pause(160)
            closeThink()
            tool(SEARCH_PAYLOAD)
            pause(200)
            think("搜到三本候选，清华那本偏理论，同济那本有配套实验。")
            pause(160)
            closeThink()
            val body = buildString {
                append("综合来看，建议**先读同济那本打基础**，再用清华的补理论。[1](https://www.tongji.edu.cn/p/3)\n\n")
                append("| 阶段 | 时长 | 每周投入 |\n| --- | --- | --- |\n")
                append("| 第 1-2 周 | 2 周 | 6-8h |\n| 第 3-4 周 | 2 周 | 10h |\n\n")
                append("1. 先通读目录，标记重点章节\n2. 每章做完配套实验\n3. 用清华那本补概念\n\n")
                append("> 如果时间只有一个月，优先同济那本，清华那本挑三章。[2](https://www.tup.tsinghua.edu.cn/books/1)")
            }
            // 模拟 Flash 的突发投递：一次 30-60 字
            body.chunked(48).forEach { part ->
                text(part)
                pause(70)
            }
        },
    )

    /**
     * 长思考、无搜索：验证限高窗口的连续上顶。
     *
     * 内容要够长（约 1500 字）才有对比意义：限高框只有约 6 行可视区，
     * 若只有几句，窗口还没开始滚就结束了，看不出跟随时序是否对。
     */
    val longThinking = PreviewScenario(
        title = "长思考",
        prompt = "帮我把这个方案想透，不要联网。",
        keywords = listOf("长思考", "思考", "推理", "think"),
        script = { think, closeThink, _, text, pause ->
            // 按 ~12 字一块投递、60ms 一块：约 7 秒的连续思考流，
            // 够看清限高框有没有一路跟着走；一次性整段灌进去等于没测
            LONG_THINKING.joinToString("").chunked(THINK_CHUNK).forEach { part ->
                think(part)
                pause(THINK_GAP)
            }
            closeThink()
            pause(300)
            LONG_THINKING_ANSWER.chunked(16).forEach { part ->
                text(part)
                pause(ANSWER_GAP)
            }
        },
    )

    /** 长回答：验证长文 Markdown 的解析成本、滚动跟随与末段稳定性 */
    val longAnswer = PreviewScenario(
        title = "长回答",
        prompt = "把方案写完整些，要能直接照着做。",
        keywords = listOf("长回答", "长文", "完整", "详细", "long", "answer"),
        script = { _, _, _, text, pause ->
            LONG_ANSWER.chunked(28).forEach { part ->
                text(part)
                pause(ANSWER_GAP)
            }
        },
    )

    /** Markdown 全量：标题/列表/引用/行内代码/链接/分割线 */
    val markdown = PreviewScenario(
        title = "Markdown",
        prompt = "把 Markdown 的各种写法都渲染一遍。",
        keywords = listOf("md", "markdown", "排版"),
        script = { _, _, _, text, pause ->
            val body = """
# 一级标题
## 二级标题

这一段包含 **加粗**、*斜体*、`行内代码` 和 [链接](https://example.com)。

---

- 无序列表第一项
- 第二项，带 `code`
- 第三项

1. 有序列表第一项
2. 第二项

> 引用块：这里是一段引用的说明文字。

~~删除线~~ 与普通文本混排。
            """.trimIndent()
            body.chunked(40).forEach { text(it); pause(60) }
        },
    )

    /** 代码块：带语言与三个操作按钮 */
    val codeBlock = PreviewScenario(
        title = "代码块",
        prompt = "给一段 Kotlin 与 Python 代码。",
        keywords = listOf("代码", "code", "kotlin", "python"),
        script = { _, _, _, text, pause ->
            text("先看 Kotlin 版：\n\n```kotlin\nfun revealStep(backlog: Int, frames: Int): Int =\n    ceil(backlog / frames.toFloat()).toInt().coerceIn(1, 8)\n```\n\n")
            pause(120)
            text("再看 Python 版（等价的节奏控制）：\n\n```python\nimport math\n\ndef reveal_step(backlog: int, frames: int) -> int:\n    return max(1, min(8, math.ceil(backlog / frames)))\n```")
        },
    )

    /** 表格：宽表格横向滚动 + 表头固定 */
    val table = PreviewScenario(
        title = "表格",
        prompt = "给一个表格，列多一点。",
        keywords = listOf("表", "table"),
        script = { _, _, _, text, pause ->
            text("先给一个普通表格：\n\n| 阶段 | 时长 | 每周投入 | 目标 | 产出 |\n| --- | --- | --- | --- | --- |\n| 第 1-2 周 | 2 周 | 6-8h | 建立框架 | 目录笔记 |\n| 第 3-4 周 | 2 周 | 10h | 动手实验 | 实验报告 |\n| 第 5 周 | 1 周 | 4h | 复盘 | 总结 |\n\n")
            pause(120)
            text("宽表格会横向滚动，按钮固定在右上角。")
        },
    )

    /** 公式：行内与块级 */
    val math = PreviewScenario(
        title = "公式",
        prompt = "给几个公式，含行内与块级。",
        keywords = listOf("公式", "math", "latex"),
        script = { _, _, _, text, pause ->
            text("行内公式：质能方程 \$E = mc^2\$ 与二次方程 \$ax^2+bx+c=0\$。\n\n")
            pause(120)
            text("块级公式：\n\n\$\$\n\\int_{0}^{1} x^2 \\, dx = \\frac{1}{3}\n\$\$\n\n")
            pause(120)
            text("矩阵也可以：\n\n\$\$\n\\begin{pmatrix} a & b \\\\ c & d \\end{matrix}\n\$\$")
        },
    )

    /** 拒答/提示卡片：验证 NoticeCard 与错误卡片外观 */
    val refusal = PreviewScenario(
        title = "错误/拒答",
        prompt = "触发一个失败与一条提示。",
        keywords = listOf("错误", "失败", "拒答", "error"),
        script = { think, closeThink, _, text, pause ->
            think("先试着给出答案，但这次请求不完整。")
            pause(150)
            closeThink()
            text("这一条用来检查**提示卡片**的样式，正文仍会正常渲染。")
        },
    )

    val all = listOf(webSearch, longThinking, longAnswer, markdown, codeBlock, table, math, refusal)

    fun match(input: String): PreviewScenario {
        val lower = input.lowercase()
        return all.firstOrNull { s -> s.keywords.any { lower.contains(it.lowercase()) } }
            ?: markdown
    }
}
