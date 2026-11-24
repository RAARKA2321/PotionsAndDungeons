package com.example.potionsanddungeons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val panelShape = RoundedCornerShape(18.dp)
private val panelRim = Color(0xFF375060)

@Composable
internal fun MenuPage(
    title: String,
    onBack: () -> Unit,
    scroll: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Назад") }
                Text(title, fontFamily = FontFamily.Serif, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
            footer?.invoke()
        }
    }
}

@Composable
internal fun MenuPanel(
    title: String? = null, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().background(AppColors.panelBg, panelShape).border(1.dp, panelRim, panelShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, color = AppColors.textPrimary, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun MenuLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, color = AppColors.textPrimary)
            if (detail != null) Text(detail, fontSize = AppFonts.legend, color = AppColors.textMuted)
        }
        Text("›", color = AppColors.accent, fontSize = 20.sp)
    }
}

@Composable
internal fun HomeMenu(
    version: String, onVersion: () -> Unit, onResume: (() -> Unit)?, onNewGame: () -> Unit,
    onNetwork: () -> Unit, onTutorial: () -> Unit, onRules: () -> Unit, onCatalog: () -> Unit,
    onLayouts: () -> Unit, onCards: () -> Unit, onAppearance: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(vertical = if (wide) 32.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAppearance, modifier = Modifier.align(Alignment.End)) {
                Text(if (LocalUpdates.current?.hasUpdate == true) "Настройки · есть обновление" else "Настройки")
            }
            HomeHero(wide)
            val panelHeight = if (wide) Modifier.fillMaxHeight() else Modifier
            val play: @Composable () -> Unit = {
                MenuPanel("Играть", panelHeight) {
                    if (onResume != null) {
                        Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Продолжить партию") }
                    }
                    if (onResume == null) {
                        Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("Новая игра") }
                    } else {
                        OutlinedButton(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("Новая игра") }
                    }
                    OutlinedButton(onClick = onNetwork, modifier = Modifier.fillMaxWidth()) { Text("Сетевая игра") }
                }
            }
            val workshop: @Composable () -> Unit = {
                MenuPanel("Мастерская", panelHeight) {
                    MenuLink("Мои поля", onLayouts)
                    MenuLink("Мои карты", onCards)
                }
            }
            val guide: @Composable () -> Unit = {
                MenuPanel("Справочник", panelHeight) {
                    MenuLink("Зелья", onCatalog)
                    MenuLink("Обучение", onTutorial)
                    MenuLink("Как играть", onRules)
                }
            }
            if (wide) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { play() }
                    Box(Modifier.weight(1f)) { workshop() }
                    Box(Modifier.weight(1f)) { guide() }
                }
            } else {
                play()
                workshop()
                guide()
            }
            TextButton(onClick = onVersion, modifier = Modifier.align(Alignment.End)) {
                Text("Версия $version", color = AppColors.textMuted, fontSize = AppFonts.legend)
            }
        }
    }
}

@Composable
private fun HomeHero(wide: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().height(if (wide) 250.dp else 208.dp).clip(shape)
        .background(Brush.horizontalGradient(listOf(Color(0xFF1B3445), Color(0xFF233843), Color(0xFF122331))))
        .border(1.dp, panelRim, shape)) {
        Box(Modifier.align(Alignment.CenterEnd).offset(x = if (wide) (-24).dp else 24.dp)) {
            Box(Modifier.size(if (wide) 320.dp else 200.dp)
                .background(Brush.radialGradient(listOf(Color(0x665FCEAF), Color.Transparent))))
            Box(Modifier.align(Alignment.Center).size(if (wide) 130.dp else 72.dp)) {
                PotionIcon(DeckSource.BASE, 0, Modifier.fillMaxSize())
            }
            Box(Modifier.align(Alignment.BottomEnd).offset(x = if (wide) (-46).dp else (-34).dp,
                y = if (wide) (-42).dp else (-22).dp).size(if (wide) 64.dp else 37.dp)) {
                PotionIcon(DeckSource.DLC, 1, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = if (wide) 32.dp else 18.dp, end = 12.dp)) {
            val titleSize = if (wide) 54.sp else 34.sp
            val lineHeight = if (wide) 57.sp else 38.sp
            Text("Potions and", color = AppColors.textPrimary, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = titleSize, lineHeight = lineHeight)
            Text("Dungeons", color = AppColors.accent, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = titleSize, lineHeight = lineHeight)
            Spacer(Modifier.height(10.dp))
            Text("Настольная игра для 1–4 игроков", color = AppColors.textMuted,
                fontSize = AppFonts.legend)
        }
    }
}
