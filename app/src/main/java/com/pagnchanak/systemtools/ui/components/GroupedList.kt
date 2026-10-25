package com.pagnchanak.systemtools.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space between rows of a segmented group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val GroupGap: Dp = ListItemDefaults.SegmentedGap

/**
 * Row [index] of [count] in an Expressive segmented list: large outer corners
 * on the group, small corners between rows. Place rows [GroupGap] apart.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupedListItem(
    index: Int,
    count: Int,
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = headline,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = headline,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
