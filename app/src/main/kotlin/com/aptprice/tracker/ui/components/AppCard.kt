package com.aptprice.tracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.ui.theme.AppShape

/**
 * 목록·시트에서 쓰는 기본 카드.
 *
 * 그림자 대신 **머리카락 두께의 테두리**로 경계를 만든다. 그림자를 쓰면 카드가
 * 여러 장 겹칠 때 회색 얼룩처럼 번져 보이고, 다크 모드에서는 아예 보이지 않는다.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppShape.Card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)

    Column(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        content = content,
    )
}
