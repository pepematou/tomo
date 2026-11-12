package eu.kanade.presentation.more.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.SheetPrimaryButton
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.tomo.tomoContentWidth
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Welcome of the dark glass design in three steps (RF-41): where the library lives and importing a Mihon backup,
 * adding an extension repository, and letting updates run on their own.
 */
@Composable
internal fun TomoOnboarding(
    onComplete: () -> Unit,
    onRestoreBackup: () -> Unit,
    onAddRepo: () -> Unit,
) {
    val storage = remember { StorageStep() }
    val permissions = remember { PermissionStep() }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val isLast = step == STEPS - 1
    val canContinue = step != 0 || storage.isComplete

    BackHandler(enabled = step != 0) { step-- }

    TomoBackground {
        Column(
            modifier = Modifier
                .tomoContentWidth()
                .systemBarsPadding()
                .padding(Tomo.screenPadding),
        ) {
            Text(
                text = stringResource(MR.strings.tomo_welcome_title),
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                repeat(STEPS) { index ->
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (index == step) 24.dp else 6.dp)
                            .background(if (index <= step) Tomo.gold else Tomo.glassStrong, CircleShape),
                    )
                }
            }
            AnimatedContent(targetState = step, label = "welcomeStep", modifier = Modifier.weight(1f)) { current ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    val (title, body) = when (current) {
                        0 -> MR.strings.tomo_welcome_library_title to MR.strings.tomo_welcome_library_body
                        1 -> MR.strings.tomo_welcome_extensions_title to MR.strings.tomo_welcome_extensions_body
                        else -> MR.strings.tomo_welcome_updates_title to MR.strings.tomo_welcome_updates_body
                    }
                    Text(text = stringResource(title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = stringResource(body), fontSize = 15.sp, lineHeight = 22.sp, color = Tomo.textSecondary)
                    if (current != 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glass(RoundedCornerShape(22.dp))
                                .padding(vertical = 8.dp),
                        ) {
                            if (current == 0) storage.Content() else permissions.Content()
                        }
                    }
                    when (current) {
                        // Importing ends the welcome, so it waits until the storage folder is chosen
                        0 -> GlassAction(
                            stringResource(MR.strings.tomo_welcome_import_backup),
                            onRestoreBackup,
                            enabled = storage.isComplete,
                        )
                        1 -> GlassAction(stringResource(MR.strings.tomo_welcome_add_repo), onAddRepo)
                    }
                }
            }
            SheetPrimaryButton(
                text = stringResource(if (isLast) MR.strings.tomo_welcome_start else MR.strings.onboarding_action_next),
                enabled = canContinue,
                onClick = { if (isLast) onComplete() else step++ },
            )
        }
    }
}

@Composable
private fun GlassAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .glass(CircleShape, fill = Tomo.glassStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Tomo.text else Tomo.textFaint,
        )
    }
}

private const val STEPS = 3
