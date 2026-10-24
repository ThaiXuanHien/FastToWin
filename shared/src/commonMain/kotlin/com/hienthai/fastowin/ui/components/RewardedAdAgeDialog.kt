package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.RewardedAdAgeBand

@Composable
fun RewardedAdAgeDialog(onSelected: (RewardedAdAgeBand?) -> Unit) {
    ArcadeDialog(
        title = localized(TextKey.RewardedAdAgeTitle),
        subtitle = localized(TextKey.RewardedAdAgeMessage),
        onDismissRequest = { onSelected(null) },
        scrollableContent = true,
        modifier = Modifier.testTag("rewarded_ad_age_dialog")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                RewardedAdAgeBand.UNDER_13 to TextKey.RewardedAdAgeUnder13,
                RewardedAdAgeBand.AGE_13_15 to TextKey.RewardedAdAge13To15,
                RewardedAdAgeBand.AGE_16_17 to TextKey.RewardedAdAge16To17,
                RewardedAdAgeBand.ADULT to TextKey.RewardedAdAge18Plus
            ).forEach { (band, key) ->
                ArcadeActionButton(label = localized(key), onClick = { onSelected(band) },
                    style = ArcadeActionStyle.OUTLINE, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("rewarded_age_${band.name}"))
            }
            ArcadeActionButton(label = localized(TextKey.Cancel), onClick = { onSelected(null) },
                style = ArcadeActionStyle.OUTLINE, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        }
    }
}
