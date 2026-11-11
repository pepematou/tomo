package eu.kanade.presentation.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(MaterialTheme.padding.medium),
        ) {
            Icon(imageVector = MaterialSymbols.Rounded.Error, contentDescription = null)
            Text(
                text = pluralStringResource(MR.plurals.update_issues_count, count, count),
                modifier = Modifier.padding(start = MaterialTheme.padding.medium),
            )
        }
    }
}

@Composable
fun UpdateIssuesDialog(
    issues: List<LibraryUpdateIssues.Issue>,
    onDismissRequest: () -> Unit,
    onOpenManga: (LibraryUpdateIssues.Issue) -> Unit,
    onOpenWebView: (LibraryUpdateIssues.Issue) -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(MR.strings.update_issues_title)) },
        text = {
            LazyColumn {
                items(issues, key = { it.mangaId }) { issue ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenManga(issue) }
                            .padding(vertical = MaterialTheme.padding.small),
                    ) {
                        Text(text = issue.title, fontWeight = FontWeight.SemiBold)
                        Text(text = issue.reason, style = MaterialTheme.typography.bodySmall)
                        if (issue.webViewUrl != null) {
                            TextButton(onClick = { onOpenWebView(issue) }) {
                                Text(text = stringResource(MR.strings.action_open_in_web_view))
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
