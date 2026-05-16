package com.example.carcopilot.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.Severity
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

@Composable
fun MechanicDraftScreen(
    issue: Issue,
    onBack: () -> Unit,
    onHomeTab: () -> Unit,
    onHistoryTab: () -> Unit,
) {
    val context = LocalContext.current
    val draft = issue.mechanicDraft.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarCopilotColors.PhoneBg),
    ) {
        TopBar(left = TopBarLeft.Back(onBack = onBack))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp),
        ) {
            AnimatedAIStrip(
                state = SynthesisState.Ready(
                    synthesis = "Here's a message you can send to a shop. They'll know exactly what to look at — most won't charge a diagnostic fee if you come in with this.",
                    goodNews = null,
                    isFallback = false,
                ),
                label = "Drafted for you",
                severity = Severity.warning,
            )
            DraftCard(draft = draft)
            Spacer(Modifier.height(20.dp))
            PrimaryCta(
                label = "Looks good — open Messages →",
                onClick = { shareDraft(context, draft) },
            )
            Spacer(Modifier.height(10.dp))
            GhostCta(
                label = "Edit before sending",
                onClick = {
                    copyDraft(context, draft)
                    Toast.makeText(
                        context,
                        "Copied — paste it somewhere you can edit.",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
        BottomTabBar(
            selected = Tab.Home,
            onSelect = { tab ->
                when (tab) {
                    Tab.Home -> onHomeTab()
                    Tab.History -> onHistoryTab()
                }
            },
        )
    }
}

@Composable
private fun DraftCard(draft: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarCopilotColors.PhoneCard)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) {
        DraftMetaRow()
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CarCopilotColors.Line),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = draft,
            style = CarCopilotTypography.CardSubtitle.copy(lineHeight = 22.4.sp),
            color = CarCopilotColors.Text,
        )
    }
}

@Composable
private fun DraftMetaRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(CarCopilotColors.Accent),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "FROM CAR",
                style = CarCopilotTypography.TabLabel,
                color = CarCopilotColors.TextMute,
            )
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(CarCopilotColors.TextMute),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "COPILOT",
                style = CarCopilotTypography.TabLabel,
                color = CarCopilotColors.TextMute,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "EDITABLE",
            style = CarCopilotTypography.TabLabel,
            color = CarCopilotColors.TextMute,
        )
    }
}

@Composable
private fun PrimaryCta(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarCopilotColors.Accent)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CarCopilotTypography.CtaButton,
            color = CarCopilotColors.AccentDeep,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GhostCta(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CarCopilotColors.LineBright, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CarCopilotTypography.CtaButton,
            color = CarCopilotColors.MetaBold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun shareDraft(context: Context, draft: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, draft)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun copyDraft(context: Context, draft: String) {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Mechanic draft", draft))
}
