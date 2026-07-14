package com.example.mallar.ui.chatbot

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.data.AStarPath
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.data.Place
import com.example.mallar.ui.localization.NavigationState
import kotlinx.coroutines.launch

private fun getCategoryLabelEn(category: String): String {
    return when (category) {
        "Dining" -> "dining"
        "Fashion" -> "fashion"
        "Perfumes& Cosmetics" -> "perfume and cosmetics"
        "Jewellery" -> "jewellery"
        "Pharmacy" -> "pharmacy"
        else -> category.lowercase()
    }
}

private fun getCategoryLabelAr(category: String): String {
    return when (category) {
        "Dining" -> "المطاعم والكافيهات"
        "Fashion" -> "الملابس والموضة"
        "Perfumes& Cosmetics" -> "العطور ومستحضرات التجميل"
        "Jewellery" -> "المجوهرات والساعات"
        "Pharmacy" -> "الصيدلية"
        else -> category
    }
}

// Design tokens matching the rest of the app
private val ChatTeal       = Color(0xFF009688)
private val ChatTealLight  = Color(0xFFE0F2F1)
private val ChatBotBg      = Color(0xFFF5F5F5)
private val ChatUserBg     = ChatTeal
private val ChatUserText   = Color.White
private val ChatBotText    = Color(0xFF212121)
private val ChatSheetBg    = Color.White

// ─────────────────────────────────────────────────────────────────────────────
// Chat Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChatBottomSheet(
    graph: MallGraph?,
    onDismiss: () -> Unit,
    onPathFound: (AStarPath) -> Unit,      // called when a valid path is computed
    onStartNavigation: ((Boolean) -> Unit)? = null  // true = AR, false = static map
) {
    val messages    = remember { mutableStateListOf<ChatMessage>() }
    var inputText   by remember { mutableStateOf("") }
    var isThinking  by remember { mutableStateOf(false) }
    var lastRecommendedStores by remember { mutableStateOf<List<GraphNode>>(emptyList()) }
    var pendingDestinationNode by remember { mutableStateOf<GraphNode?>(null) }

    val listState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()
    val keyboard    = LocalSoftwareKeyboardController.current

    // Greeting on first open
    LaunchedEffect(Unit) {
        messages += ChatMessage(
            sender = MessageSender.BOT,
            text   = "👋 مرحباً! أنا مساعد المول.\n" +
                    "اسألني زي: «ازاي اروح زارا من برشكا»\n\n" +
                    "Hello! Ask me like: «How do I go to Zara from Bershka»"
        )
    }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank() || isThinking) return

        inputText  = ""
        keyboard?.hide()

        messages += ChatMessage(sender = MessageSender.USER, text = text)
        isThinking = true

        scope.launch {
            kotlinx.coroutines.delay(600)   // brief "thinking" pause for UX

            val g = graph
            if (g == null) {
                messages += ChatMessage(
                    sender = MessageSender.BOT,
                    text   = "⚠️ Mall map is still loading, please try again in a moment."
                )
                isThinking = false
                return@launch
            }

            val arabic  = isArabicInput(text)

            // Helper to update navigation state and trigger callbacks
            fun updateNavigationState(startNode: GraphNode, destNode: GraphNode, path: AStarPath?) {
                path?.let { p ->
                    val startPlace = Place(
                        id = startNode.shopId ?: startNode.id,
                        brand = startNode.shopName ?: "Start",
                        x = startNode.x.toInt(),
                        y = startNode.y.toInt(),
                        logo = startNode.logo ?: "",
                        category = startNode.category,
                        floor = startNode.floor
                    )
                    val destPlace = Place(
                        id = destNode.shopId ?: destNode.id,
                        brand = destNode.shopName ?: "Destination",
                        x = destNode.x.toInt(),
                        y = destNode.y.toInt(),
                        logo = destNode.logo ?: "",
                        category = destNode.category,
                        floor = destNode.floor
                    )
                    val distM = (p.totalDistancePx * 0.05).toInt().coerceAtLeast(1)
                    NavigationState.startPlace = startPlace
                    NavigationState.selectedPlace = destPlace
                    NavigationState.aStarPath = p
                    NavigationState.estimatedDistance = distM
                    NavigationState.estimatedMinutes = (distM / 80f).coerceIn(1f, 20f).toInt()
                    onPathFound(p)
                }
            }

            // Helper to calculate A* directions and add message to chat
            fun routeNavigation(startNode: GraphNode, destNode: GraphNode) {
                val result = DirectionsGenerator.generate(g, startNode, destNode, arabic)
                val navPrompt = if (result.path != null) {
                    if (arabic) "\n\n🗺️ اضغط «عرض الخريطة»"
                    else "\n\n🗺️ Press «Show Map»"
                } else ""

                messages += ChatMessage(
                    sender  = MessageSender.BOT,
                    text    = result.responseText + navPrompt,
                    mapPath = result.path,
                    action  = if (result.path != null) "SHOW_MAP" else null
                )

                updateNavigationState(startNode, destNode, result.path)
            }

            // Helper to handle navigation flow when destination node is selected
            fun handleNavigationRequest(destNode: GraphNode) {
                val currentStartPlace = NavigationState.startPlace
                if (currentStartPlace != null) {
                    val startNode = g.nodes.firstOrNull { it.shopId == currentStartPlace.id }
                    if (startNode != null) {
                        routeNavigation(startNode, destNode)
                        return
                    }
                }

                // If start place is not set, prompt the user for it
                pendingDestinationNode = destNode
                messages += ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (arabic)
                        "تمام! وديني لـ *${destNode.shopName}*. بس قولي، هتبدأ من أي محل؟"
                    else
                        "Sure! Navigating to *${destNode.shopName}*. But first, what store are you starting from?"
                )
            }

            // 1. Check if we are waiting for a starting store
            val currentPendingDest = pendingDestinationNode
            if (currentPendingDest != null) {
                val matchedStart = ChatInputParser.matchNode(g, text)
                if (matchedStart != null) {
                    pendingDestinationNode = null
                    routeNavigation(matchedStart, currentPendingDest)
                } else {
                    messages += ChatMessage(
                        sender = MessageSender.BOT,
                        text = if (arabic)
                            "😕 مش لاقي «$text» في المول. ياريت تكتب اسم محل البداية صح."
                        else
                            "😕 Couldn't find «$text» in the mall. Please enter a valid starting store."
                    )
                }
                isThinking = false
                return@launch
            }

            // 2. Check if user responded "yes" or "no" after recommendations
            val isYes = text.lowercase() in setOf("yes", "y", "sure", "yeah", "ok", "okay", "نعم", "ايوه", "اه", "يا ريت", "ماشي", "تمام")
            val isNo = text.lowercase() in setOf("no", "n", "nope", "لا", "مش عايز", "لا شكرا")

            if (isYes && lastRecommendedStores.isNotEmpty()) {
                val recommended = lastRecommendedStores
                lastRecommendedStores = emptyList() // clear context
                if (recommended.size == 1) {
                    handleNavigationRequest(recommended[0])
                } else {
                    messages += ChatMessage(
                        sender = MessageSender.BOT,
                        text = if (arabic)
                            "تمام! تحب تروح لـ أنهي واحد منهم؟ اكتب اسم المحل."
                        else
                            "Sure! Which one of them would you like to navigate to? Please type the store name."
                    )
                }
                isThinking = false
                return@launch
            }

            if (isNo && lastRecommendedStores.isNotEmpty()) {
                lastRecommendedStores = emptyList() // clear context
                messages += ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (arabic)
                        "تحت أمرك! لو محتاج أي مساعدة تانية، أنا هنا."
                    else
                        "You're welcome! Let me know if you need anything else."
                )
                isThinking = false
                return@launch
            }

            // 3. Check if query matches a category recommendation request
            val category = ChatInputParser.detectCategory(text)
            if (category != null) {
                val matchingNodes = g.nodes.filter { it.category == category && it.shopName != null }
                val currentStartPlace = NavigationState.startPlace
                val startNode = if (currentStartPlace != null) {
                    g.nodes.firstOrNull { it.shopId == currentStartPlace.id }
                } else null

                val sortedNodes = if (startNode != null) {
                    matchingNodes.sortedBy { node ->
                        val dx = node.x - startNode.x
                        val dy = node.y - startNode.y
                        dx * dx + dy * dy
                    }
                } else {
                    matchingNodes
                }

                val distinctStores = sortedNodes
                    .distinctBy { it.shopName?.lowercase()?.trim() }
                    .take(5)

                lastRecommendedStores = distinctStores

                if (distinctStores.isEmpty()) {
                    messages += ChatMessage(
                        sender = MessageSender.BOT,
                        text = if (arabic)
                            "😔 للأسف، ملقتش أي محلات في قسم ${getCategoryLabelAr(category)}."
                        else
                            "😔 Sorry, I couldn't find any stores in the ${getCategoryLabelEn(category)} category."
                    )
                } else if (distinctStores.size == 1) {
                    val storeName = distinctStores[0].shopName!!
                    messages += ChatMessage(
                        sender = MessageSender.BOT,
                        text = if (arabic)
                            "المحل المتاح في قسم ${getCategoryLabelAr(category)} هو:\n• $storeName\n\nتحب أوجهك ليه؟"
                        else
                            "The available store in ${getCategoryLabelEn(category)} is:\n• $storeName\n\nWould you like me to navigate you to it?"
                    )
                } else {
                    val listText = distinctStores.mapIndexed { idx, node -> "${idx + 1}. ${node.shopName}" }.joinToString("\n")
                    messages += ChatMessage(
                        sender = MessageSender.BOT,
                        text = if (arabic)
                            "دي خيارات ${getCategoryLabelAr(category)} المتاحة:\n\n$listText\n\nتحب أوجهك لأي مكان منهم؟"
                        else
                            "Here are some ${getCategoryLabelEn(category)} options:\n\n$listText\n\nWould you like me to navigate you to one of these places?"
                    )
                }
                isThinking = false
                return@launch
            }

            // 4. Try parsing as a route navigation request (from START to DEST)
            val parsed = ChatInputParser.parse(text)
            if (parsed != null) {
                lastRecommendedStores = emptyList() // clear recommendation context
                val startNode = ChatInputParser.matchNode(g, parsed.startName)
                val destNode  = ChatInputParser.matchNode(g, parsed.destName)

                if (startNode == null) {
                    messages += ChatMessage(
                        sender = MessageSender.BOT,
                        text   = if (arabic)
                            "😕 مش لاقي «${parsed.startName}» في المول. تأكد من الاسم."
                        else
                            "😕 Couldn't find «${parsed.startName}» in the mall. Check the store name."
                    )
                } else if (destNode == null) {
                    messages += ChatMessage(
                        sender = MessageSender.BOT,
                        text   = if (arabic)
                            "😕 مش لاقي «${parsed.destName}» في المول. تأكد من الاسم."
                        else
                            "😕 Couldn't find «${parsed.destName}» in the mall. Check the store name."
                    )
                } else {
                    routeNavigation(startNode, destNode)
                }
                isThinking = false
                return@launch
            }

            // 5. Try parsing as a single-destination navigation request (e.g. "Take me to Zara")
            val candidateName = ChatInputParser.parseSingleDestination(text)
            if (candidateName != null) {
                val destNode = ChatInputParser.matchNode(g, candidateName)
                if (destNode != null) {
                    lastRecommendedStores = emptyList() // clear recommendation context
                    handleNavigationRequest(destNode)
                    isThinking = false
                    return@launch
                }
            }

            // 6. Default fallback
            messages += ChatMessage(
                sender = MessageSender.BOT,
                text   = if (arabic)
                    "🤔 مش فاهم. جرب تقول: «عايز اكل»، «فين زارا»، أو «ازاي اروح زارا من برشكا»"
                else
                    "🤔 I didn't understand. Try asking: «I want food», «Take me to Zara», or «Navigate from Bershka to Zara»"
            )
            isThinking = false
        }
    }

    // ── Sheet container ───────────────────────────────────────────────────────
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f),
        shape  = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color  = ChatSheetBg,
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            ChatHeader(onDismiss = onDismiss)

            HorizontalDivider(color = Color(0xFFEEEEEE))

            // ── Messages list ─────────────────────────────────────────────────
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding      = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg, onStartNavigation = onStartNavigation)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (isThinking) {
                    item { ThinkingIndicator() }
                }
            }

            // ── Input bar ─────────────────────────────────────────────────────
            ChatInputBar(
                value     = inputText,
                onChange  = { inputText = it },
                onSend    = ::sendMessage,
                isLoading = isThinking
            )

            // Bottom system bar padding
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Robot icon in teal circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ChatTeal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text       = "Mall Assistant",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = Color.Black
            )
            Text(
                text     = "Ask me where you want to go",
                fontSize = 12.sp,
                color    = Color.Gray
            )
        }

        // Drag handle line
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFFDDDDDD))
        )

        Spacer(Modifier.width(12.dp))

        // Close button
        IconButton(
            onClick  = onDismiss,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close chat",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat bubble
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage, onStartNavigation: ((Boolean) -> Unit)? = null) {
    val isUser = message.sender == MessageSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            // Bot avatar
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ChatTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    BubbleContent(text = message.text, isUser = false)
                    
                    if (message.action == "SHOW_MAP" && onStartNavigation != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onStartNavigation(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = ChatTeal),
                            modifier = Modifier.padding(start = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🗺️ Show Map", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            BubbleContent(text = message.text, isUser = true)
        }
    }
}

@Composable
private fun BubbleContent(text: String, isUser: Boolean) {
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(
                RoundedCornerShape(
                    topStart    = if (isUser) 18.dp else 4.dp,
                    topEnd      = if (isUser) 4.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd   = 18.dp
                )
            )
            .background(if (isUser) ChatUserBg else ChatBotBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Render **bold** markdown-style (*word*)
        val annotated = buildAnnotatedString {
            var remaining = text
            while (remaining.isNotEmpty()) {
                val starIdx = remaining.indexOf('*')
                if (starIdx == -1) {
                    append(remaining)
                    break
                }
                append(remaining.substring(0, starIdx))
                val closeIdx = remaining.indexOf('*', starIdx + 1)
                if (closeIdx == -1) {
                    append(remaining.substring(starIdx))
                    break
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(remaining.substring(starIdx + 1, closeIdx))
                }
                remaining = remaining.substring(closeIdx + 1)
            }
        }
        Text(
            text     = annotated,
            color    = if (isUser) ChatUserText else ChatBotText,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Thinking indicator (animated dots)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ChatTeal),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(ChatBotBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ChatTeal.copy(alpha = alpha - (i * 0.1f).coerceAtLeast(0f)))
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Input bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value          = value,
                onValueChange  = onChange,
                modifier       = Modifier.weight(1f),
                placeholder    = {
                    Text(
                        "Where to go…",
                        color    = Color.Gray,
                        fontSize = 14.sp
                    )
                },
                shape          = RoundedCornerShape(24.dp),
                singleLine     = true,
                colors         = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ChatTeal,
                    unfocusedBorderColor = Color(0xFFDDDDDD)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            Spacer(Modifier.width(10.dp))

            // Send button
            FloatingActionButton(
                onClick        = onSend,
                containerColor = if (value.isNotBlank() && !isLoading) ChatTeal else Color(0xFFBDBDBD),
                contentColor   = Color.White,
                shape          = CircleShape,
                elevation      = FloatingActionButtonDefaults.elevation(0.dp),
                modifier       = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    modifier    = Modifier.size(20.dp)
                )
            }
        }
    }
}