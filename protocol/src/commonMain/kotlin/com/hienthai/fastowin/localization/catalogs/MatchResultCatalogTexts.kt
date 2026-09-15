package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val matchResultKeys = listOf(
    TextKey.DrawResultDescription, TextKey.VictoryResultDescription,
    TextKey.OpposingTeamVictoryDescription, TextKey.OpponentVictoryDescription,
    TextKey.MatchResultTitle, TextKey.ShareResult, TextKey.ShareResultError,
    TextKey.PlayerYou, TextKey.ResultLabel, TextKey.PlacementProgress,
    TextKey.TournamentFinishedDescription, TextKey.BracketUpdatedDescription,
    TextKey.ViewBracket, TextKey.YourSummary, TextKey.AverageReaction,
    TextKey.Accuracy, TextKey.CorrectWrongDuration, TextKey.PaceAnalysis,
    TextKey.InsufficientPaceAnalysis, TextKey.PaceSegmentHint,
)

private fun matchResultCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == matchResultKeys.size) {
        "Expected ${matchResultKeys.size} result translations, received ${values.size}."
    }
    return matchResultKeys.zip(values).toMap()
}

internal val matchResultTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to matchResultCopy(
        "双方最终得分相同。", "你赢得了这场对局！", "对方队伍获胜。", "{player} 赢得了这场对局。",
        "对局结果", "分享结果", "无法生成分享图片，请重试。", "{player}（你）", "结果", "定位赛 {played}/{required}",
        "赛事已结束。打开对阵表查看冠军。", "对阵表已更新，下一场比赛将自动创建。", "查看对阵表",
        "你的战绩", "平均反应时间", "准确率", "正确 {correct} • 错误 {wrong} • {duration}", "速度分析",
        "数据不足，无法确定最快和最慢的阶段。", "每个阶段最多包含你找到的 10 个数字。",
    ),
    AppLanguage.JAPANESE to matchResultCopy(
        "両者は同じ得点で終了しました。", "対戦に勝利しました！", "相手チームの勝利です。", "{player} が勝利しました。",
        "対戦結果", "結果を共有", "共有画像を作成できませんでした。再試行してください。", "{player}（あなた）", "結果", "認定戦 {played}/{required}",
        "大会が終了しました。トーナメント表で優勝者を確認してください。", "トーナメント表を更新しました。次の試合は自動で作成されます。", "トーナメント表を見る",
        "あなたの成績", "平均反応時間", "正確率", "正解 {correct} • ミス {wrong} • {duration}", "ペース分析",
        "最速と最遅の区間を特定するにはデータが足りません。", "各区間には、見つけた数字が最大10個含まれます。",
    ),
    AppLanguage.KOREAN to matchResultCopy(
        "양쪽이 같은 점수로 경기를 마쳤습니다.", "경기에서 승리했습니다!", "상대 팀이 승리했습니다.", "{player}님이 승리했습니다.",
        "경기 결과", "결과 공유", "공유 이미지를 만들지 못했습니다. 다시 시도하세요.", "{player}(나)", "결과", "배치 경기 {played}/{required}",
        "대회가 끝났습니다. 대진표에서 우승자를 확인하세요.", "대진표가 갱신되었습니다. 다음 경기는 자동 생성됩니다.", "대진표 보기",
        "나의 요약", "평균 반응 시간", "정확도", "정답 {correct} • 오답 {wrong} • {duration}", "속도 분석",
        "가장 빠르고 느린 구간을 찾기에는 데이터가 부족합니다.", "각 구간에는 찾은 숫자가 최대 10개 포함됩니다.",
    ),
    AppLanguage.SPANISH to matchResultCopy(
        "Ambos lados terminaron con la misma puntuación.", "¡Ganaste la partida!", "El equipo contrario ganó.", "{player} ganó la partida.",
        "Resultado de la partida", "COMPARTIR RESULTADO", "No se pudo crear la imagen para compartir. Inténtalo de nuevo.", "{player} (tú)", "RESULTADO", "Clasificación {played}/{required}",
        "El torneo ha terminado. Abre el cuadro para ver al campeón.", "El cuadro se actualizó. La siguiente partida se creará automáticamente.", "Ver cuadro",
        "Tu resumen", "Reacción media", "Precisión", "Aciertos {correct} • Errores {wrong} • {duration}", "Análisis del ritmo",
        "No hay datos suficientes para identificar los tramos más rápidos y lentos.", "Cada tramo contiene hasta 10 números que encontraste.",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to matchResultCopy(
        "Os dois lados terminaram com a mesma pontuação.", "Você venceu a partida!", "O time adversário venceu.", "{player} venceu a partida.",
        "Resultado da partida", "COMPARTILHAR RESULTADO", "Não foi possível criar a imagem de compartilhamento. Tente novamente.", "{player} (você)", "RESULTADO", "Colocação {played}/{required}",
        "O torneio terminou. Abra a chave para ver o campeão.", "A chave foi atualizada. A próxima partida será criada automaticamente.", "Ver chave",
        "Seu resumo", "Reação média", "Precisão", "Acertos {correct} • Erros {wrong} • {duration}", "Análise de ritmo",
        "Não há dados suficientes para identificar os trechos mais rápidos e mais lentos.", "Cada trecho contém até 10 números que você encontrou.",
    ),
    AppLanguage.FRENCH to matchResultCopy(
        "Les deux camps ont terminé avec le même score.", "Vous avez gagné la partie !", "L’équipe adverse a gagné.", "{player} a gagné la partie.",
        "Résultat du match", "PARTAGER LE RÉSULTAT", "Impossible de créer l’image à partager. Réessayez.", "{player} (vous)", "RÉSULTAT", "Matchs de placement : {played}/{required}",
        "Le tournoi est terminé. Ouvrez le tableau pour voir le champion.", "Le tableau est mis à jour. Le prochain match sera créé automatiquement.", "Voir le tableau",
        "Votre bilan", "Réaction moyenne", "Précision", "Correct {correct} • Faux {wrong} • {duration}", "Analyse du rythme",
        "Données insuffisantes pour déterminer les phases les plus rapides et les plus lentes.", "Chaque phase contient au plus 10 nombres que vous avez trouvés.",
    ),
    AppLanguage.GERMAN to matchResultCopy(
        "Beide Seiten haben mit derselben Punktzahl abgeschlossen.", "Du hast das Spiel gewonnen!", "Das gegnerische Team hat gewonnen.", "{player} hat das Spiel gewonnen.",
        "Spielergebnis", "ERGEBNIS TEILEN", "Das Bild zum Teilen konnte nicht erstellt werden. Versuche es erneut.", "{player} (du)", "ERGEBNIS", "Platzierung {played}/{required}",
        "Das Turnier ist beendet. Öffne den Spielplan, um den Sieger zu sehen.", "Der Spielplan wurde aktualisiert. Das nächste Spiel wird automatisch erstellt.", "Spielplan ansehen",
        "Deine Zusammenfassung", "Durchschn. Reaktion", "Genauigkeit", "Richtig {correct} • Falsch {wrong} • {duration}", "Tempoanalyse",
        "Zu wenig Daten, um die schnellsten und langsamsten Abschnitte zu bestimmen.", "Jeder Abschnitt enthält bis zu 10 von dir gefundene Zahlen.",
    ),
    AppLanguage.INDONESIAN to matchResultCopy(
        "Kedua pihak selesai dengan skor yang sama.", "Kamu memenangkan pertandingan!", "Tim lawan menang.", "{player} memenangkan pertandingan.",
        "Hasil pertandingan", "BAGIKAN HASIL", "Tidak dapat membuat gambar untuk dibagikan. Coba lagi.", "{player} (kamu)", "HASIL", "Penempatan {played}/{required}",
        "Turnamen selesai. Buka bagan untuk melihat juaranya.", "Bagan diperbarui. Pertandingan berikutnya akan dibuat otomatis.", "Lihat bagan",
        "Ringkasanmu", "Rata-rata reaksi", "Akurasi", "Benar {correct} • Salah {wrong} • {duration}", "Analisis tempo",
        "Data tidak cukup untuk menentukan bagian tercepat dan terlambat.", "Setiap bagian berisi hingga 10 angka yang kamu temukan.",
    ),
    AppLanguage.THAI to matchResultCopy(
        "ทั้งสองฝ่ายจบด้วยคะแนนเท่ากัน", "คุณชนะการแข่งขัน!", "ทีมฝ่ายตรงข้ามชนะ", "{player} ชนะการแข่งขัน",
        "ผลการแข่งขัน", "แชร์ผล", "สร้างภาพสำหรับแชร์ไม่ได้ โปรดลองอีกครั้ง", "{player} (คุณ)", "ผล", "จัดอันดับ {played}/{required}",
        "ทัวร์นาเมนต์จบแล้ว เปิดสายแข่งเพื่อดูแชมป์", "อัปเดตสายแข่งแล้ว แมตช์ถัดไปจะสร้างอัตโนมัติ", "ดูสายแข่ง",
        "สรุปของคุณ", "การตอบสนองเฉลี่ย", "ความแม่นยำ", "ถูก {correct} • ผิด {wrong} • {duration}", "วิเคราะห์จังหวะ",
        "ข้อมูลไม่พอจะระบุช่วงที่เร็วและช้าที่สุด", "แต่ละช่วงมีตัวเลขที่คุณพบได้สูงสุด 10 ตัว",
    ),
    AppLanguage.RUSSIAN to matchResultCopy(
        "Обе стороны набрали одинаковое количество очков.", "Вы выиграли матч!", "Команда соперника победила.", "{player} выиграл матч.",
        "Результат матча", "ПОДЕЛИТЬСЯ РЕЗУЛЬТАТОМ", "Не удалось создать изображение для публикации. Повторите попытку.", "{player} (вы)", "РЕЗУЛЬТАТ", "Квалификация {played}/{required}",
        "Турнир завершён. Откройте сетку, чтобы увидеть чемпиона.", "Сетка обновлена. Следующий матч будет создан автоматически.", "Посмотреть сетку",
        "Ваш результат", "Средняя реакция", "Точность", "Верно {correct} • Ошибок {wrong} • {duration}", "Анализ темпа",
        "Недостаточно данных, чтобы определить самые быстрые и медленные отрезки.", "Каждый отрезок содержит до 10 найденных вами чисел.",
    ),
)
