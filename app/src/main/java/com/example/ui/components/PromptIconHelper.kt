package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class NamedMaterialIcon(val id: String, val label: String, val icon: ImageVector)

val CuratedMaterialIcons = listOf(
    NamedMaterialIcon("icon:chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    NamedMaterialIcon("icon:summarize", "Summarize", Icons.Default.Description),
    NamedMaterialIcon("icon:translate", "Translate", Icons.Default.Language),
    NamedMaterialIcon("icon:edit", "Edit", Icons.Default.Edit),
    NamedMaterialIcon("icon:explain", "Explain", Icons.Default.Lightbulb),
    NamedMaterialIcon("icon:code", "Code", Icons.Default.Code),
    NamedMaterialIcon("icon:ideas", "Ideas", Icons.Default.Palette),
    NamedMaterialIcon("icon:analyze", "Analyze", Icons.Default.Analytics),
    NamedMaterialIcon("icon:book", "Book", Icons.Default.Book),
    NamedMaterialIcon("icon:search", "Search", Icons.Default.Search),
    NamedMaterialIcon("icon:science", "Science", Icons.Default.Science),
    NamedMaterialIcon("icon:school", "School", Icons.Default.School),
    NamedMaterialIcon("icon:image", "Image", Icons.Default.Image),
    NamedMaterialIcon("icon:audio", "Audio", Icons.Default.Headphones),
    NamedMaterialIcon("icon:legal", "Legal", Icons.Default.Gavel),
    NamedMaterialIcon("icon:medical", "Medical", Icons.Default.LocalHospital),
    NamedMaterialIcon("icon:finance", "Finance", Icons.Default.AttachMoney),
    NamedMaterialIcon("icon:sparkles", "Creative", Icons.Default.AutoAwesome),
    NamedMaterialIcon("icon:star", "Favorite", Icons.Default.Star),
    NamedMaterialIcon("icon:task", "Task", Icons.Default.TaskAlt),
    NamedMaterialIcon("icon:build", "Tools", Icons.Default.Build),
    NamedMaterialIcon("icon:forum", "Forum", Icons.Default.Forum),
    NamedMaterialIcon("icon:email", "Email", Icons.Default.Email),
    NamedMaterialIcon("icon:rocket", "Fast", Icons.Default.RocketLaunch),
    NamedMaterialIcon("icon:psychology", "Think", Icons.Default.Psychology),
    NamedMaterialIcon("icon:voice", "Voice", Icons.Default.RecordVoiceOver),
    NamedMaterialIcon("icon:fitness", "Health", Icons.Default.FitnessCenter)
)

/**
 * Maps any icon identifier or legacy emoji to a pure Material ImageVector.
 * Strictly no emojis are displayed anywhere in the application.
 */
fun mapIconStringToMaterial(iconString: String): ImageVector {
    if (iconString.startsWith("icon:")) {
        val found = CuratedMaterialIcons.firstOrNull { it.id == iconString }
        if (found != null) return found.icon
    }
    return when (iconString) {
        "📝", "📄", "📑", "📋", "📁", "🗂️", "📜" -> Icons.Default.Description
        "🌐", "🗣️", "🌍", "🗺️" -> Icons.Default.Language
        "✏️", "✍️", "✒️", "🖋️" -> Icons.Default.Edit
        "💡", "🧠", "🎯", "⚡" -> Icons.Default.Lightbulb
        "💻", "🖥️", "⚙️", "🛠️" -> Icons.Default.Code
        "🎨", "✨", "🌟", "🔮" -> Icons.Default.Palette
        "📊", "📈", "📉", "🔬" -> Icons.Default.Analytics
        "💬", "🗨️", "🗯️", "💭" -> Icons.AutoMirrored.Filled.Chat
        "📚", "📖", "📕", "📗" -> Icons.Default.Book
        "🔍", "🔎", "🧭", "🔭" -> Icons.Default.Search
        "🧪", "🧬", "⚛️" -> Icons.Default.Science
        "🎓", "🎒", "🏫", "📐" -> Icons.Default.School
        "🖼️", "📷", "🖌️" -> Icons.Default.Image
        "🎧", "🎙️", "🎵", "📻" -> Icons.Default.Headphones
        "⚖️", "🏛️", "🔒" -> Icons.Default.Gavel
        "🩺", "💊", "🩹", "🏥" -> Icons.Default.LocalHospital
        "💰", "💳", "💵", "🪙" -> Icons.Default.AttachMoney
        else -> Icons.AutoMirrored.Filled.Chat
    }
}

@Composable
fun PromptIconDisplay(
    iconString: String,
    size: Dp = 28.dp,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier
) {
    val vector = mapIconStringToMaterial(iconString)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.85f)
        )
    }
}
