package com.replyhub.app.notifications

internal data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val isEmpty: Boolean
        get() = right <= left || bottom <= top

    fun overlapsVertically(other: NodeBounds, tolerance: Int): Boolean =
        top < other.bottom + tolerance && bottom > other.top - tolerance

    fun contains(other: NodeBounds): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

/** 접근성 노드에서 판단에 필요한 속성만 옮겨 담은 스냅샷. 화면 내용은 판단 중에만 메모리에 머문다. */
internal data class ScreenNode(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val bounds: NodeBounds,
    val isEditable: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisible: Boolean = true,
    val isShowingHint: Boolean = false,
    /** 자신 또는 가장 가까운 클릭 가능한 조상의 인덱스. */
    val clickTargetIndex: Int? = null,
)

internal object AutoSendScreen {
    /** parents[i]는 i번 노드의 부모 인덱스(루트는 -1). 결과[i]는 i번 노드를 누를 때 실제로 클릭할 노드. */
    fun clickTargets(
        parents: IntArray,
        clickable: BooleanArray,
        maxDepth: Int = MAX_CLICKABLE_ANCESTOR_DEPTH,
    ): List<Int?> = parents.indices.map { start ->
        var current = start
        var depth = 0
        var found: Int? = null
        while (current >= 0 && depth < maxDepth) {
            if (clickable[current]) {
                found = current
                break
            }
            current = parents[current]
            depth += 1
        }
        found
    }

    fun countEditables(nodes: List<ScreenNode>): Int = nodes.count(::isUsableInput)

    /** 루트만 있고 자식이 없는 창: 앱이 접근성 서비스에 화면 구조를 공개하지 않는 경우. */
    fun isOpaqueWindow(nodes: List<ScreenNode>): Boolean =
        nodes.size <= OPAQUE_NODE_LIMIT && countEditables(nodes) == 0

    // 채팅 입력창은 화면 하단에 있으므로 가장 아래 노드를 골라 상단 검색창 오인을 피한다.
    fun findMessageInput(nodes: List<ScreenNode>): Int? =
        nodes.indices
            .filter { isUsableInput(nodes[it]) }
            .maxByOrNull { nodes[it].bounds.bottom }

    /**
     * 입력창과 같은 줄(세로로 겹치는 영역)에서만 전송 버튼을 찾는다.
     * 대화 기록 속 "전송" 같은 말풍선이나, 입력창까지 감싼 컨테이너를 누르지 않기 위함이다.
     */
    fun findSendButton(nodes: List<ScreenNode>, inputIndex: Int): Int? {
        val input = nodes[inputIndex].bounds
        return nodes.indices.asSequence()
            .filter { it != inputIndex }
            .filter { index ->
                val node = nodes[index]
                node.isVisible && node.isEnabled && !node.bounds.isEmpty &&
                    node.bounds.overlapsVertically(input, ROW_TOLERANCE_PX) &&
                    (
                        AutoSendHeuristics.isSendViewId(node.viewId) ||
                            AutoSendHeuristics.isSendLabel(node.contentDescription) ||
                            AutoSendHeuristics.isSendLabel(node.text)
                        )
            }
            .mapNotNull { nodes[it].clickTargetIndex }
            .firstOrNull { target ->
                target != inputIndex && !nodes[target].bounds.contains(input)
            }
    }

    /** 입력창 텍스트가 접근성으로 노출될 때만 답장이 남아 있는지 판단할 수 있다. */
    fun inputShowsReply(input: ScreenNode, reply: String): Boolean {
        if (input.isShowingHint) return false
        val current = input.text?.trim().orEmpty()
        val probe = reply.trim().take(VERIFY_PREFIX_LENGTH)
        return current.isNotEmpty() && probe.isNotEmpty() && current.contains(probe)
    }

    private fun isUsableInput(node: ScreenNode): Boolean =
        (node.isEditable || AutoSendHeuristics.isEditableClassName(node.className)) &&
            node.isEnabled && node.isVisible && !node.bounds.isEmpty

    const val MAX_CLICKABLE_ANCESTOR_DEPTH = 4
    private const val OPAQUE_NODE_LIMIT = 2
    private const val ROW_TOLERANCE_PX = 16
    private const val VERIFY_PREFIX_LENGTH = 24
}
