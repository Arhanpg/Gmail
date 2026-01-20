package com.example.gmail
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ==================== 1. DATA STRUCTURES & LOGIC ====================

data class SkillNodeData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Color = Color(0xFF00E5FF), // Default Neon Blue
    val children: List<SkillNodeData> = emptyList()
)

data class NodePosition(val id: String, val position: Offset, val radius: Float)

// --- Immutable Tree Modifiers ---

fun addChildToNode(root: SkillNodeData, parentId: String, newChild: SkillNodeData): SkillNodeData {
    if (root.id == parentId) {
        return root.copy(children = root.children + newChild)
    }
    return root.copy(children = root.children.map { child ->
        addChildToNode(child, parentId, newChild)
    })
}

fun deleteNodeById(root: SkillNodeData, nodeIdToDelete: String): SkillNodeData? {
    if (root.id == nodeIdToDelete) return null

    val newChildren = root.children.mapNotNull { child ->
        if (child.id == nodeIdToDelete) {
            null
        } else {
            deleteNodeById(child, nodeIdToDelete)
        }
    }
    return root.copy(children = newChildren)
}

val initialTreeData = SkillNodeData(
    id = "root", name = "CORE", color = Color.White, children = listOf(
        SkillNodeData(name = "CODING", color = Color(0xFFD500F9)),
        SkillNodeData(name = "DESIGN", color = Color(0xFFFFAB00))
    )
)

val PRESET_COLORS = listOf(
    Color(0xFF00E5FF), // Cyan
    Color(0xFFD500F9), // Purple
    Color(0xFFFFAB00), // Orange
    Color(0xFF76FF03), // Green
    Color(0xFFFF1744), // Red
    Color(0xFF2979FF)  // Blue
)

// ==================== 2. MAIN SCREEN ====================

@Composable
fun SkillTreeScreen() {
    var treeData by remember { mutableStateOf(initialTreeData) }

    var showDialog by remember { mutableStateOf(false) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var selectedNodeName by remember { mutableStateOf("") }
    var newSkillName by remember { mutableStateOf("") }
    var newSkillColor by remember { mutableStateOf(PRESET_COLORS[0]) }

    val darkBgStart = Color(0xFF0B0F19)
    val darkBgEnd = Color(0xFF162238)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(darkBgStart, darkBgEnd)))
    ) {
        AnimatedSkillTree(
            data = treeData,
            onNodeClick = { nodeId, nodeName ->
                selectedNodeId = nodeId
                selectedNodeName = nodeName
                newSkillName = ""
                newSkillColor = PRESET_COLORS[0] // Reset color
                showDialog = true
            }
        )

        Text(
            text = "SKILL MATRIX EVOLUTION",
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            letterSpacing = 2.sp
        )

        if (showDialog && selectedNodeId != null) {
            SkillDialog(
                nodeName = selectedNodeName,
                isRoot = selectedNodeId == treeData.id,
                newSkillName = newSkillName,
                selectedColor = newSkillColor,
                onNameChange = { newSkillName = it },
                onColorSelected = { newSkillColor = it },
                onDismiss = { showDialog = false },
                onAddConfirm = {
                    if (newSkillName.isNotBlank()) {
                        val newChild = SkillNodeData(name = newSkillName, color = newSkillColor)
                        treeData = addChildToNode(treeData, selectedNodeId!!, newChild)
                        showDialog = false
                    }
                },
                onDelete = {
                    if (selectedNodeId != treeData.id) {
                        treeData = deleteNodeById(treeData, selectedNodeId!!) ?: treeData
                    }
                    showDialog = false
                }
            )
        }
    }
}

// ==================== 3. CANVAS & ANIMATION ====================

@Composable
fun AnimatedSkillTree(
    data: SkillNodeData,
    onNodeClick: (String, String) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val growthProgress = remember { Animatable(0f) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val nodeRegistry = remember { mutableMapOf<String, String>() }
    val nodePositions = remember { mutableListOf<NodePosition>() }

    LaunchedEffect(data) {
        growthProgress.snapTo(0f)
        growthProgress.animateTo(targetValue = 1f, animationSpec = tween(1500, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    offset += pan
                }
            }
            .pointerInput(data) {
                detectTapGestures { tapOffset ->
                    val adjustedTap = (tapOffset - offset) / scale
                    val tappedNode = nodePositions.find { nodePos ->
                        val distance = sqrt(
                            (adjustedTap.x - nodePos.position.x).pow(2) +
                                    (adjustedTap.y - nodePos.position.y).pow(2)
                        )
                        distance <= (nodePos.radius * 1.5f)
                    }
                    tappedNode?.let {
                        val name = nodeRegistry[it.id] ?: "Skill"
                        onNodeClick(it.id, name)
                    }
                }
            }
    ) {
        nodePositions.clear()
        nodeRegistry.clear()

        withTransform({
            scale(scale, pivot = center)
            translate(offset.x, offset.y)
        }) {
            if (canvasSize.width > 0 && canvasSize.height > 0) {
                val rootPos = Offset(canvasSize.width / 2f, canvasSize.height * 0.85f)
                drawTreeRecursive(data, rootPos, -PI.toFloat() / 2, 0, textMeasurer, growthProgress.value, density.density) { id, name, pos, rad ->
                    nodePositions.add(NodePosition(id, pos, rad))
                    nodeRegistry[id] = name
                }
            }
        }
    }
}

fun DrawScope.drawTreeRecursive(
    node: SkillNodeData, startPos: Offset, angle: Float, depth: Int, textMeasurer: TextMeasurer, growthProgress: Float, density: Float, onNodeDrawn: (String, String, Offset, Float) -> Unit
) {
    val baseLength = (if(depth == 0) 300f else 180f) * density
    val currentLength = baseLength * growthProgress
    val nodeRadius = (if(depth == 0) 25f else 18f) * density * (0.5f + 0.5f * growthProgress)
    val strokeWidth = maxOf(2f, (10f - depth * 2f)) * density

    val endPos = Offset(
        startPos.x + (cos(angle.toDouble()) * currentLength).toFloat(),
        startPos.y + (sin(angle.toDouble()) * currentLength).toFloat()
    )

    if (depth > 0) {
        drawLine(node.color.copy(alpha = 0.3f), startPos, endPos, strokeWidth * 3f, cap = StrokeCap.Round, blendMode = BlendMode.Screen)
        drawLine(node.color, startPos, endPos, strokeWidth, cap = StrokeCap.Round)
    }

    val glowBrush = Brush.radialGradient(colors = listOf(node.color, node.color.copy(alpha = 0f)), center = endPos, radius = nodeRadius * 2.5f)
    drawCircle(brush = glowBrush, center = endPos, radius = nodeRadius * 2.5f)
    drawCircle(color = Color.White, center = endPos, radius = nodeRadius * 0.3f)

    onNodeDrawn(node.id, node.name, endPos, nodeRadius)

    if (growthProgress > 0.8f) {
        val textResult = textMeasurer.measure(node.name.uppercase(), TextStyle(color = Color.White, fontSize = (14f - depth).coerceAtLeast(10f).sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 4f)))
        drawText(textResult, topLeft = Offset(endPos.x - textResult.size.width / 2, endPos.y + nodeRadius + 5f))
    }

    if (node.children.isNotEmpty()) {
        val childCount = node.children.size
        val spreadAngle = if (depth == 0) PI / 1.8 else PI / 2.5
        val startAngle = angle - spreadAngle / 2
        val angleStep = if (childCount > 1) spreadAngle / (childCount - 1) else 0.0

        node.children.forEachIndexed { index, child ->
            val childAngle = if (childCount == 1) angle else startAngle + (angleStep * index)
            drawTreeRecursive(child, endPos, childAngle.toFloat(), depth + 1, textMeasurer, growthProgress, density, onNodeDrawn)
        }
    }
}

// ==================== 4. IMPROVED DIALOG UI ====================

@Composable
fun SkillDialog(
    nodeName: String,
    isRoot: Boolean,
    newSkillName: String,
    selectedColor: Color,
    onNameChange: (String) -> Unit,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
    onAddConfirm: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E272E)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(50.dp).background(Color(0xFF00E5FF).copy(alpha = 0.2f), CircleShape).border(2.dp, Color(0xFF00E5FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF00E5FF))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Manage Node", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(nodeName.uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)

                Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 16.dp))

                // --- ADD SECTION ---
                Text("Add New Branch", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newSkillName,
                    onValueChange = onNameChange,
                    label = { Text("Skill Name") },
                    placeholder = { Text("e.g. Jetpack Compose") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- COLOR PICKER ---
                Text("Branch Color", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    PRESET_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color, CircleShape)
                                .border(2.dp, if(selectedColor == color) Color.White else Color.Transparent, CircleShape)
                                .clickable { onColorSelected(color) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onAddConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = newSkillName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("GROW TREE", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                // --- DELETE SECTION ---
                if (!isRoot) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.2f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                        Spacer(Modifier.width(8.dp))
                        Text("PRUNE BRANCH", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}