package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val challengeSharingKeys = listOf(
    TextKey.ChallengeCode, TextKey.ChallengeCodeHint,
    TextKey.PracticeNoElo, TextKey.ShareChallenge,
    TextKey.ReplaySameBoard, TextKey.CreateNewChallenge,
    TextKey.ReturnHome, TextKey.Reaction,
    TextKey.CorrectWrong, TextKey.AveragePerNumber,
    TextKey.HaveChallengeCode, TextKey.PlayChallenge,
    TextKey.InvalidChallengeCode, TextKey.ChallengeModeUnlock,
    TextKey.ChallengeShareText, TextKey.ShareChallengeSheetTitle,
    TextKey.RoomShareText,
)

private fun challengeSharingCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == challengeSharingKeys.size) {
        "Expected ${challengeSharingKeys.size} challenge sharing translations, received ${values.size}."
    }
    return challengeSharingKeys.zip(values).toMap()
}

internal val challengeSharingTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to challengeSharingCopy(
        "挑战码", "好友可以输入此码，游玩同一数字棋盘。", "练习结果不会影响 Elo。", "分享挑战",
        "重玩同一棋盘", "创建新挑战", "返回首页", "反应速度", "正确 / 错误", "每个数字平均用时",
        "有挑战码？", "开始挑战", "挑战码无效或输入有误。", "{mode} 将在 {level} 级解锁。",
        "{practice} • {mode}\n我在 {time} 内获得了 {score} 分。\n打开：{link}\n挑战码：{code}", "分享挑战",
        "在 Fast To Win 中加入“{room}”：\n{link}\n若为私人房间，请输入房主单独发送的密码。",
    ),
    AppLanguage.JAPANESE to challengeSharingCopy(
        "チャレンジコード", "友達がこのコードを入力すると、同じ数字ボードでプレイできます。", "練習結果は Elo に影響しません。", "チャレンジを共有",
        "同じボードでもう一度", "新しいチャレンジを作成", "ホームに戻る", "反応速度", "正解 / ミス", "1数字あたりの平均",
        "チャレンジコードをお持ちですか？", "チャレンジをプレイ", "コードが無効か、入力に誤りがあります。", "{mode} はレベル {level} で解放されます。",
        "{practice} • {mode}\n{time}で{score}ポイントを獲得しました。\n開く：{link}\nコード：{code}", "チャレンジを共有",
        "Fast To Win の「{room}」に参加：\n{link}\n非公開ルームの場合は、ホストから送られたパスワードを入力してください。",
    ),
    AppLanguage.KOREAN to challengeSharingCopy(
        "도전 코드", "친구가 이 코드를 입력하면 같은 숫자 보드에서 플레이할 수 있습니다.", "연습 결과는 Elo에 영향을 주지 않습니다.", "도전 공유",
        "같은 보드 다시 플레이", "새 도전 만들기", "홈으로 돌아가기", "반응 속도", "정답 / 오답", "숫자당 평균",
        "도전 코드가 있나요?", "도전 플레이", "코드가 올바르지 않거나 잘못 입력되었습니다.", "{mode} 모드는 레벨 {level}에서 잠금 해제됩니다.",
        "{practice} • {mode}\n{time} 동안 {score}점을 획득했습니다.\n열기: {link}\n코드: {code}", "도전 공유",
        "Fast To Win에서 ‘{room}’ 방에 참가하세요:\n{link}\n비공개 방은 호스트가 보낸 비밀번호를 입력하세요.",
    ),
    AppLanguage.SPANISH to challengeSharingCopy(
        "Código del reto", "Tus amigos pueden introducir este código para jugar el mismo tablero de números.", "Los resultados de práctica no afectan al Elo.", "COMPARTIR RETO",
        "REPETIR EL MISMO TABLERO", "CREAR NUEVO RETO", "VOLVER AL INICIO", "Reacción", "Aciertos / Errores", "Promedio por número",
        "¿Tienes un código de reto?", "Jugar reto", "El código no es válido o está mal escrito.", "{mode} se desbloquea en el nivel {level}.",
        "{practice} • {mode}\nConseguí {score} puntos en {time}.\nAbrir: {link}\nCódigo: {code}", "Compartir reto",
        "Únete a “{room}” en Fast To Win:\n{link}\nSi es una sala privada, introduce la contraseña enviada por el anfitrión.",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to challengeSharingCopy(
        "Código do desafio", "Seus amigos podem inserir este código para jogar o mesmo tabuleiro numérico.", "Os resultados de treino não afetam o Elo.", "COMPARTILHAR DESAFIO",
        "REPETIR O MESMO TABULEIRO", "CRIAR NOVO DESAFIO", "VOLTAR AO INÍCIO", "Reação", "Acertos / Erros", "Média por número",
        "Tem um código de desafio?", "Jogar desafio", "O código é inválido ou foi digitado incorretamente.", "{mode} é desbloqueado no nível {level}.",
        "{practice} • {mode}\nFiz {score} pontos em {time}.\nAbrir: {link}\nCódigo: {code}", "Compartilhar desafio",
        "Entre em “{room}” no Fast To Win:\n{link}\nSe a sala for privada, digite a senha enviada pelo anfitrião.",
    ),
    AppLanguage.FRENCH to challengeSharingCopy(
        "Code du défi", "Vos amis peuvent saisir ce code pour jouer sur la même grille de nombres.", "Les résultats d’entraînement n’affectent pas l’Elo.", "PARTAGER LE DÉFI",
        "REJOUER LA MÊME GRILLE", "CRÉER UN NOUVEAU DÉFI", "RETOUR À L’ACCUEIL", "Réaction", "Réussites / Erreurs", "Moyenne par nombre",
        "Vous avez un code de défi ?", "Jouer le défi", "Le code est invalide ou mal saisi.", "{mode} se débloque au niveau {level}.",
        "{practice} • {mode}\nJ’ai marqué {score} points en {time}.\nOuvrir : {link}\nCode : {code}", "Partager le défi",
        "Rejoignez « {room} » sur Fast To Win :\n{link}\nPour une salle privée, saisissez le mot de passe envoyé par l’hôte.",
    ),
    AppLanguage.GERMAN to challengeSharingCopy(
        "Herausforderungscode", "Freunde können diesen Code eingeben, um dasselbe Zahlenfeld zu spielen.", "Übungsergebnisse wirken sich nicht auf die Elo aus.", "HERAUSFORDERUNG TEILEN",
        "GLEICHES FELD WIEDERHOLEN", "NEUE HERAUSFORDERUNG ERSTELLEN", "ZUR STARTSEITE", "Reaktionszeit", "Richtig / Falsch", "Durchschnitt pro Zahl",
        "Hast du einen Herausforderungscode?", "Herausforderung spielen", "Der Code ist ungültig oder wurde falsch eingegeben.", "{mode} wird auf Stufe {level} freigeschaltet.",
        "{practice} • {mode}\nIch habe in {time} {score} Punkte erzielt.\nÖffnen: {link}\nCode: {code}", "Herausforderung teilen",
        "Tritt „{room}“ in Fast To Win bei:\n{link}\nGib bei einem privaten Raum das vom Host gesendete Passwort ein.",
    ),
    AppLanguage.INDONESIAN to challengeSharingCopy(
        "Kode tantangan", "Teman dapat memasukkan kode ini untuk memainkan papan angka yang sama.", "Hasil latihan tidak memengaruhi Elo.", "BAGIKAN TANTANGAN",
        "ULANGI PAPAN YANG SAMA", "BUAT TANTANGAN BARU", "KEMBALI KE BERANDA", "Reaksi", "Benar / Salah", "Rata-rata per angka",
        "Punya kode tantangan?", "Mainkan tantangan", "Kode tidak valid atau salah ketik.", "{mode} terbuka pada level {level}.",
        "{practice} • {mode}\nSaya meraih {score} poin dalam {time}.\nBuka: {link}\nKode: {code}", "Bagikan tantangan",
        "Gabung ke “{room}” di Fast To Win:\n{link}\nUntuk ruang privat, masukkan kata sandi yang dikirim host.",
    ),
    AppLanguage.THAI to challengeSharingCopy(
        "รหัสชาเลนจ์", "เพื่อนสามารถกรอกรหัสนี้เพื่อเล่นกระดานตัวเลขเดียวกัน", "ผลการฝึกไม่กระทบ Elo", "แชร์ชาเลนจ์",
        "เล่นกระดานเดิมอีกครั้ง", "สร้างชาเลนจ์ใหม่", "กลับหน้าหลัก", "การตอบสนอง", "ถูก / ผิด", "เฉลี่ยต่อตัวเลข",
        "มีรหัสชาเลนจ์ไหม?", "เล่นชาเลนจ์", "รหัสไม่ถูกต้องหรือพิมพ์ผิด", "โหมด {mode} จะปลดล็อกที่เลเวล {level}",
        "{practice} • {mode}\nฉันทำได้ {score} คะแนนใน {time}\nเปิด: {link}\nรหัส: {code}", "แชร์ชาเลนจ์",
        "เข้าร่วม “{room}” ใน Fast To Win:\n{link}\nหากเป็นห้องส่วนตัว ให้กรอกรหัสผ่านที่โฮสต์ส่งให้",
    ),
    AppLanguage.RUSSIAN to challengeSharingCopy(
        "Код испытания", "Друзья могут ввести этот код, чтобы сыграть на том же поле с числами.", "Результаты тренировки не влияют на Elo.", "ПОДЕЛИТЬСЯ ИСПЫТАНИЕМ",
        "ПОВТОРИТЬ ТО ЖЕ ПОЛЕ", "СОЗДАТЬ НОВОЕ ИСПЫТАНИЕ", "НА ГЛАВНУЮ", "Реакция", "Верно / Ошибка", "Среднее на число",
        "Есть код испытания?", "Пройти испытание", "Код недействителен или введён с ошибкой.", "Режим {mode} откроется на уровне {level}.",
        "{practice} • {mode}\nМой результат: {score} очков за {time}.\nОткрыть: {link}\nКод: {code}", "Поделиться испытанием",
        "Присоединяйтесь к комнате «{room}» в Fast To Win:\n{link}\nДля закрытой комнаты введите пароль, отправленный ведущим.",
    ),
)
