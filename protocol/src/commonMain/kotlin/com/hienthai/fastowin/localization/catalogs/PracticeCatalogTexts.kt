package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val practiceKeys = listOf(
    TextKey.PracticeHeader, TextKey.OfflineNoElo, TextKey.CorrectBoardCompleted,
    TextKey.CorrectNextTarget, TextKey.WrongNeedTarget, TextKey.Target,
    TextKey.Score, TextKey.Remaining, TextKey.Time, TextKey.Lives,
    TextKey.ComboLabel, TextKey.Pace, TextKey.EndPractice,
    TextKey.FoundAllNumbers, TextKey.SurvivalEnded, TextKey.SpeedUpEnded,
    TextKey.TimeBonusEnded, TextKey.TimeAttackEnded, TextKey.ChallengeEnded,
    TextKey.Completed, TextKey.PracticeCompletionSummary,
    TextKey.PracticeHeroTitle, TextKey.PracticeHeroDescription,
    TextKey.PracticeNoServerNeeded, TextKey.StartNewPractice,
)

private fun practiceCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == practiceKeys.size) {
        "Expected ${practiceKeys.size} practice translations, received ${values.size}."
    }
    return practiceKeys.zip(values).toMap()
}

internal val practiceTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to practiceCopy(
        "练习 · {mode}", "离线 · 不影响 Elo", "正确，数字板已完成",
        "正确，下一个目标 {target}", "错误，请找出 {target}", "目标",
        "得分", "剩余", "时间", "生命", "连击", "节奏", "结束",
        "你找到了全部 50 个数字", "3 次点错机会已用完", "未能在限时内找到下一个目标",
        "累积时间已用完", "60 秒已结束", "挑战结束", "已完成",
        "{message}。获得 {score} 分 · {mode}。", "离线练习",
        "每天训练反应速度，Elo 不受影响。", "不影响 Elo，无需连接服务器。", "开始新的练习",
    ),
    AppLanguage.JAPANESE to practiceCopy(
        "練習 · {mode}", "オフライン · Eloに影響なし", "正解、ボードを完了しました",
        "正解、次の目標は {target}", "不正解、{target} を探してください", "目標",
        "得点", "残り", "時間", "ライフ", "コンボ", "ペース", "終了",
        "50個の数字をすべて見つけました", "3回のミスを使い切りました", "次の目標を制限時間内に見つけられませんでした",
        "累積時間がなくなりました", "60秒が経過しました", "チャレンジ終了", "完了",
        "{message}。{score}点 · {mode}。", "オフライン練習",
        "Eloに影響を与えず、毎日反応速度を鍛えましょう。", "Eloへの影響もサーバー接続も不要です。", "新しい練習を始める",
    ),
    AppLanguage.KOREAN to practiceCopy(
        "연습 · {mode}", "오프라인 · Elo 변동 없음", "정답입니다. 숫자판을 완료했어요",
        "정답! 다음 목표는 {target}", "오답입니다. {target}을(를) 찾으세요", "목표",
        "점수", "남음", "시간", "기회", "콤보", "속도", "종료",
        "숫자 50개를 모두 찾았습니다", "오답 기회 3번을 모두 사용했습니다", "제한 시간 안에 다음 목표를 찾지 못했습니다",
        "누적 시간이 모두 소진되었습니다", "60초가 지났습니다", "도전 종료", "완료",
        "{message}. {score}점 · {mode}.", "오프라인 연습",
        "Elo에 영향 없이 매일 반응 속도를 훈련하세요.", "Elo 변동도 서버 연결도 필요 없습니다.", "새 연습 시작",
    ),
    AppLanguage.SPANISH to practiceCopy(
        "Práctica · {mode}", "Sin conexión · No afecta al Elo", "Correcto, tablero completado",
        "Correcto, siguiente objetivo: {target}", "Incorrecto, busca el {target}", "Objetivo",
        "Puntuación", "Restante", "Tiempo", "Vidas", "Combo", "Ritmo", "TERMINAR",
        "Encontraste los 50 números", "Agotaste tus 3 oportunidades de error", "Se acabó el tiempo para el siguiente objetivo",
        "Se agotó el tiempo acumulado", "Se acabaron los 60 segundos", "Desafío terminado", "COMPLETADO",
        "{message}. {score} puntos · {mode}.", "PRÁCTICA SIN CONEXIÓN",
        "Entrena tus reflejos cada día sin afectar al Elo.", "No afecta al Elo ni necesita conexión al servidor.", "Iniciar otra práctica",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to practiceCopy(
        "Treino · {mode}", "Offline · Sem impacto no Elo", "Correto, tabuleiro concluído",
        "Correto, próximo alvo: {target}", "Errado, encontre {target}", "Alvo",
        "Pontuação", "Restante", "Tempo", "Vidas", "Combo", "Ritmo", "ENCERRAR",
        "Você encontrou todos os 50 números", "Você usou as 3 chances de erro", "O tempo para o próximo alvo acabou",
        "Seu tempo acumulado acabou", "Os 60 segundos terminaram", "Desafio encerrado", "CONCLUÍDO",
        "{message}. {score} pontos · {mode}.", "TREINO OFFLINE",
        "Treine seus reflexos todos os dias sem afetar o Elo.", "Sem impacto no Elo e sem precisar de conexão com o servidor.", "Iniciar novo treino",
    ),
    AppLanguage.FRENCH to practiceCopy(
        "Entraînement · {mode}", "Hors ligne · Aucun effet sur l'Elo", "Bien joué, grille terminée",
        "Bien joué, prochaine cible : {target}", "Erreur, trouvez {target}", "Cible",
        "Score", "Restant", "Temps", "Vies", "Combo", "Rythme", "TERMINER",
        "Vous avez trouvé les 50 nombres", "Vous avez utilisé vos 3 chances d'erreur", "Le temps imparti pour la prochaine cible est écoulé",
        "Votre temps cumulé est écoulé", "Les 60 secondes sont écoulées", "Défi terminé", "TERMINÉ",
        "{message}. {score} points en mode {mode}.", "ENTRAÎNEMENT HORS LIGNE",
        "Entraînez vos réflexes chaque jour sans modifier votre Elo.", "Aucun effet sur l'Elo et aucune connexion au serveur nécessaire.", "Nouvel entraînement",
    ),
    AppLanguage.GERMAN to practiceCopy(
        "Übung · {mode}", "Offline · Kein Einfluss auf Elo", "Richtig, Zahlenfeld abgeschlossen",
        "Richtig, nächstes Ziel: {target}", "Falsch, finde {target}", "Ziel",
        "Punkte", "Verbleibend", "Zeit", "Leben", "Kombos", "Tempo", "BEENDEN",
        "Du hast alle 50 Zahlen gefunden", "Du hast alle 3 Fehlversuche verbraucht", "Die Zeit für das nächste Ziel ist abgelaufen",
        "Deine gesammelte Zeit ist abgelaufen", "Die 60 Sekunden sind um", "Herausforderung beendet", "ABGESCHLOSSEN",
        "{message}. {score} Punkte · {mode}.", "OFFLINE-ÜBUNG",
        "Trainiere jeden Tag deine Reaktion, ohne dein Elo zu beeinflussen.", "Kein Einfluss auf Elo und keine Serververbindung nötig.", "Neue Übung starten",
    ),
    AppLanguage.INDONESIAN to practiceCopy(
        "Latihan · {mode}", "Luring · Tidak memengaruhi Elo", "Benar, papan selesai",
        "Benar, target berikutnya {target}", "Salah, cari {target}", "Target",
        "Skor", "Tersisa", "Waktu", "Nyawa", "Kombo", "Tempo", "SELESAI",
        "Kamu menemukan semua 50 angka", "Ketiga kesempatan salah sudah habis", "Waktu untuk target berikutnya habis",
        "Waktu akumulasimu habis", "60 detik telah berakhir", "Tantangan berakhir", "SELESAI",
        "{message}. {score} poin · {mode}.", "LATIHAN LURING",
        "Latih reaksimu setiap hari tanpa memengaruhi Elo.", "Tidak memengaruhi Elo dan tidak memerlukan koneksi server.", "Mulai latihan baru",
    ),
    AppLanguage.THAI to practiceCopy(
        "ฝึกซ้อม · {mode}", "ออฟไลน์ · ไม่กระทบ Elo", "ถูกต้อง ทำกระดานสำเร็จแล้ว",
        "ถูกต้อง เป้าหมายถัดไปคือ {target}", "ผิด ลองหา {target}", "เป้าหมาย",
        "คะแนน", "คงเหลือ", "เวลา", "ชีวิต", "คอมโบ", "จังหวะ", "จบการฝึก",
        "คุณหาตัวเลขครบทั้ง 50 ตัวแล้ว", "ใช้โอกาสกดผิดครบ 3 ครั้งแล้ว", "หมดเวลาก่อนพบเป้าหมายถัดไป",
        "เวลาสะสมหมดแล้ว", "ครบ 60 วินาทีแล้ว", "จบการท้าทาย", "สำเร็จ",
        "{message} ได้ {score} คะแนน · {mode}", "ฝึกซ้อมออฟไลน์",
        "ฝึกการตอบสนองทุกวันโดยไม่กระทบ Elo", "ไม่กระทบ Elo และไม่ต้องเชื่อมต่อเซิร์ฟเวอร์", "เริ่มฝึกซ้อมใหม่",
    ),
    AppLanguage.RUSSIAN to practiceCopy(
        "Тренировка · {mode}", "Офлайн · Без влияния на Elo", "Верно, поле завершено",
        "Верно, следующая цель: {target}", "Ошибка, найдите {target}", "Цель",
        "Очки", "Осталось", "Время", "Жизни", "Комбо", "Темп", "ЗАВЕРШИТЬ",
        "Вы нашли все 50 чисел", "Все 3 попытки ошибиться исчерпаны", "Время на поиск следующей цели истекло",
        "Накопленное время истекло", "60 секунд истекли", "Испытание завершено", "ЗАВЕРШЕНО",
        "{message}. {score} очков · {mode}.", "ТРЕНИРОВКА ОФЛАЙН",
        "Тренируйте реакцию каждый день без влияния на Elo.", "Не влияет на Elo и не требует подключения к серверу.", "Начать новую тренировку",
    ),
)
