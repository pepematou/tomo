package eu.kanade.presentation.updates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.library.LibraryUpdateIssues
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Error
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun UpdateIssuesBanner(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(MaterialTheme.padding.medium),
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = pluralStringResource(MR.plurals.update_issues_count, count, count),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = MaterialTheme.padding.medium),
            )
        }
    }
}

/** Issues grouped by reason, each group with the one action that fixes it. */
@Composable
fun UpdateIssuesDialog(
    issues: List<LibraryUpdateIssues.Issue>,
    onDismissRequest: () -> Unit,
    onOpenManga: (LibraryUpdateIssues.Issue) -> Unit,
    onOpenWebView: (LibraryUpdateIssues.Issue) -> Unit,
    onMigrate: (mangaIds: List<Long>) -> Unit,
    onGoToExtensions: () -> Unit,
) {
    val groups = issues.groupBy { it.reason }.toList()
    AlertDialog(
        title = { Text(text = stringResource(MR.strings.update_issues_title)) },
        text = {
            LazyColumn {
                groups.forEach { (reason, group) ->
                    item(key = "header-$reason") {
                        IssueGroupHeader(
                            reason = reason,
                            action = group.first().action,
                            onMigrate = { onMigrate(group.map { it.mangaId }) },
                            onGoToExtensions = onGoToExtensions,
                        )
                    }
                    items(group, key = { it.mangaId }) { issue ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenManga(issue) }
                                .padding(vertical = MaterialTheme.padding.extraSmall),
                        ) {
                            Text(text = issue.title, style = MaterialTheme.typography.bodyMedium)
                            if (issue.webViewUrl != null) {
                                TextButton(onClick = { onOpenWebView(issue) }) {
                                    Text(text = stringResource(MR.strings.action_open_in_web_view))
                                }
                            }
                        }
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_close))
            }
        },
    )
}

@Composable
private fun IssueGroupHeader(
    reason: String,
    action: LibraryUpdateIssues.Action,
    onMigrate: () -> Unit,
    onGoToExtensions: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = MaterialTheme.padding.medium)) {
        Text(
            text = reason,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        when (action) {
            LibraryUpdateIssues.Action.MIGRATE -> TextButton(onClick = onMigrate) {
                Text(stringResource(MR.strings.tomo_issue_migrate_all))
            }
            LibraryUpdateIssues.Action.INSTALL_EXTENSION -> TextButton(onClick = onGoToExtensions) {
                Text(stringResource(MR.strings.tomo_issue_go_to_extensions))
            }
            else -> {}
        }
    }
}
