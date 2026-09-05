package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val indonesianCatalog = LocalizationCatalog(
    language = AppLanguage.INDONESIAN,
    texts = mapOf(
        TextKey.Back to "Kembali",
        TextKey.Cancel to "Batal",
        TextKey.Confirm to "Konfirmasi",
        TextKey.Retry to "Coba lagi",
        TextKey.Close to "Tutup",
        TextKey.Save to "Simpan",
        TextKey.Delete to "Hapus",
        TextKey.Loading to "Memuat…",
        TextKey.UnknownError to "Terjadi kesalahan. Silakan coba lagi.",
        TextKey.WelcomePlayer to "Halo, {player}!"
    ),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.OTHER to "{count} pemain"
        )
    )
)
