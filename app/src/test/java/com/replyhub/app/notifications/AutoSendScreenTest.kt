package com.replyhub.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSendScreenTest {

    @Test
    fun `click target is the node itself or its nearest clickable ancestor`() {
        // 0 root(clickable) -> 1 row -> 2 label ; 3 deep chain beyond depth
        val parents = intArrayOf(-1, 0, 1, 2, 3, 4, 5)
        val clickable = booleanArrayOf(true, false, false, false, false, false, false)

        val targets = AutoSendScreen.clickTargets(parents, clickable)

        assertEquals(0, targets[0])
        assertEquals(0, targets[2])
        assertNull("ancestor beyond max depth must not be used", targets[6])
    }

    @Test
    fun `message input is the bottom-most usable editable`() {
        val nodes = listOf(
            node(bounds(0, 150, 1080, 260), className = "android.widget.EditText"),
            node(bounds(150, 2200, 850, 2300), isEditable = true),
            node(bounds(150, 2310, 850, 2330), isEditable = true, isEnabled = false),
            node(bounds(0, 0, 0, 0), isEditable = true),
        )

        assertEquals(1, AutoSendScreen.findMessageInput(nodes))
    }

    @Test
    fun `no input when the window exposes nothing editable`() {
        val nodes = listOf(node(bounds(0, 0, 1080, 2340)))

        assertNull(AutoSendScreen.findMessageInput(nodes))
        assertEquals(0, AutoSendScreen.countEditables(nodes))
    }

    @Test
    fun `root-only window is opaque but a real screen is not`() {
        val hidden = listOf(node(bounds(0, 0, 0, 0)))
        val chatList = List(40) { row -> node(bounds(0, row * 50, 1080, row * 50 + 48), text = "row") }
        val tinyChat = listOf(
            node(bounds(0, 0, 1080, 2340)),
            node(bounds(150, 2200, 850, 2300), isEditable = true),
        )

        assertTrue(AutoSendScreen.isOpaqueWindow(hidden))
        assertFalse(AutoSendScreen.isOpaqueWindow(chatList))
        assertFalse("a window with an input is never opaque", AutoSendScreen.isOpaqueWindow(tinyChat))
    }

    @Test
    fun `send button on the input row wins over a chat bubble that says send`() {
        val nodes = listOf(
            node(bounds(600, 1900, 1000, 1980), text = "전송", clickTargetIndex = 0),
            node(bounds(150, 2200, 850, 2300), isEditable = true),
            node(bounds(870, 2205, 1060, 2295), text = "전송", clickTargetIndex = 2),
        )

        assertEquals(2, AutoSendScreen.findSendButton(nodes, inputIndex = 1))
    }

    @Test
    fun `chat bubble alone is never treated as the send button`() {
        val nodes = listOf(
            node(bounds(600, 1900, 1000, 1980), text = "Send", clickTargetIndex = 0),
            node(bounds(150, 2200, 850, 2300), isEditable = true),
        )

        assertNull(AutoSendScreen.findSendButton(nodes, inputIndex = 1))
    }

    @Test
    fun `send label inside a clickable button layout clicks the layout`() {
        val nodes = listOf(
            node(bounds(150, 2200, 850, 2300), isEditable = true),
            node(bounds(870, 2205, 1060, 2295), clickTargetIndex = 1),
            node(bounds(900, 2220, 1030, 2280), text = "发送", clickTargetIndex = 1),
        )

        assertEquals(1, AutoSendScreen.findSendButton(nodes, inputIndex = 0))
    }

    @Test
    fun `container that wraps the input is not clicked as the send button`() {
        val nodes = listOf(
            node(bounds(0, 2180, 1080, 2320), clickTargetIndex = 0),
            node(bounds(150, 2200, 850, 2300), isEditable = true, clickTargetIndex = 0),
            node(bounds(870, 2205, 1060, 2295), text = "Send", clickTargetIndex = 0),
        )

        assertNull(AutoSendScreen.findSendButton(nodes, inputIndex = 1))
    }

    @Test
    fun `button aligned to the bottom of a tall multi-line input still counts`() {
        val nodes = listOf(
            node(bounds(150, 1800, 850, 2300), isEditable = true),
            node(bounds(870, 2210, 1060, 2300), contentDescription = "Send", clickTargetIndex = 1),
        )

        assertEquals(1, AutoSendScreen.findSendButton(nodes, inputIndex = 0))
    }

    @Test
    fun `reply is detected while typed and cleared after sending`() {
        val reply = "Thanks, see you at the station at 7pm tomorrow!"
        val typed = node(bounds(150, 2200, 850, 2300), text = reply, isEditable = true)
        val cleared = node(bounds(150, 2200, 850, 2300), text = "", isEditable = true)
        val hint = node(
            bounds(150, 2200, 850, 2300),
            text = "메시지 입력",
            isEditable = true,
            isShowingHint = true,
        )

        assertTrue(AutoSendScreen.inputShowsReply(typed, reply))
        assertFalse(AutoSendScreen.inputShowsReply(cleared, reply))
        assertFalse(AutoSendScreen.inputShowsReply(hint, reply))
    }

    @Test
    fun `hidden input text never counts as showing the reply`() {
        val hidden = node(bounds(150, 2200, 850, 2300), text = null, isEditable = true)

        assertFalse(AutoSendScreen.inputShowsReply(hidden, "hello"))
    }

    private fun bounds(left: Int, top: Int, right: Int, bottom: Int) =
        NodeBounds(left, top, right, bottom)

    private fun node(
        bounds: NodeBounds,
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null,
        isEditable: Boolean = false,
        isEnabled: Boolean = true,
        isShowingHint: Boolean = false,
        clickTargetIndex: Int? = null,
    ) = ScreenNode(
        text = text,
        contentDescription = contentDescription,
        className = className,
        bounds = bounds,
        isEditable = isEditable,
        isEnabled = isEnabled,
        isShowingHint = isShowingHint,
        clickTargetIndex = clickTargetIndex,
    )
}
