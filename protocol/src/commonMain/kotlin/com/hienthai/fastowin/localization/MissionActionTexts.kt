package com.hienthai.fastowin.localization

private val missionActionKeys=listOf(TextKey.MissionActionPLAY,TextKey.MissionActionWIN,TextKey.MissionActionCORRECT,TextKey.MissionActionACCURACY,TextKey.MissionActionPERFECT_WIN,TextKey.MissionActionWIN_STREAK,TextKey.MissionActionCHECK_IN,TextKey.MissionActionDONATE_GOLD,TextKey.MissionActionDONATE_GEMS)
private fun missionCopy(vararg copy:String)=missionActionKeys.zip(copy.toList()).toMap()
internal val missionActionTexts=mapOf(
    AppLanguage.ENGLISH to missionCopy("Play {target} matches","Win {target} matches","Find {target} correct numbers","Reach {accuracy}% accuracy in {target} matches","Win {target} perfect matches","Reach a {target}-win streak","Check in {target} times","Donate {target} Gold","Donate {target} Gems"),
    AppLanguage.VIETNAMESE to missionCopy("Chơi {target} trận","Thắng {target} trận","Chọn đúng {target} số","Đạt độ chính xác {accuracy}% trong {target} trận","Thắng hoàn hảo {target} trận","Đạt chuỗi thắng {target}","Điểm danh {target} lần","Quyên góp {target} Vàng","Quyên góp {target} Gem"),
    AppLanguage.SIMPLIFIED_CHINESE to missionCopy("进行 {target} 场对局","赢得 {target} 场对局","选对 {target} 个数字","在 {target} 场对局达到 {accuracy}% 准确率","完美获胜 {target} 场","达到 {target} 连胜","签到 {target} 次","捐赠 {target} 金币","捐赠 {target} 宝石"),
    AppLanguage.JAPANESE to missionCopy("{target} 回対戦する","{target} 回勝利する","数字を {target} 回正しく選ぶ","{target} 回の対戦で正解率 {accuracy}% を達成","{target} 回完全勝利する","{target} 連勝を達成","{target} 回チェックインする","{target} ゴールド寄付する","{target} ジェム寄付する"),
    AppLanguage.KOREAN to missionCopy("{target}회 대전하기","{target}회 승리하기","숫자 {target}개 맞히기","{target}회 대전에서 정확도 {accuracy}% 달성","{target}회 완벽하게 승리하기","{target}연승 달성","{target}회 출석하기","골드 {target}개 기부","보석 {target}개 기부"),
    AppLanguage.SPANISH to missionCopy("Juega {target} partidas","Gana {target} partidas","Elige {target} números correctos","Alcanza {accuracy}% de precisión en {target} partidas","Gana {target} partidas perfectas","Alcanza {target} victorias seguidas","Registra tu entrada {target} veces","Dona {target} de oro","Dona {target} gemas"),
    AppLanguage.BRAZILIAN_PORTUGUESE to missionCopy("Jogue {target} partidas","Vença {target} partidas","Escolha {target} números corretos","Atinja {accuracy}% de precisão em {target} partidas","Vença {target} partidas perfeitas","Alcance {target} vitórias seguidas","Faça check-in {target} vezes","Doe {target} de ouro","Doe {target} gemas"),
    AppLanguage.FRENCH to missionCopy("Jouer {target} parties","Gagner {target} parties","Choisir {target} nombres corrects","Atteindre {accuracy}% de précision dans {target} parties","Gagner {target} parties parfaites","Atteindre {target} victoires consécutives","Se connecter {target} fois","Donner {target} pièces d'or","Donner {target} gemmes"),
    AppLanguage.GERMAN to missionCopy("Spiele {target} Partien","Gewinne {target} Partien","Wähle {target} richtige Zahlen","Erreiche {accuracy}% Genauigkeit in {target} Partien","Gewinne {target} perfekte Partien","Erreiche {target} Siege in Folge","Melde dich {target}-mal an","Spende {target} Gold","Spende {target} Edelsteine"),
    AppLanguage.INDONESIAN to missionCopy("Mainkan {target} pertandingan","Menangkan {target} pertandingan","Pilih {target} angka dengan benar","Raih akurasi {accuracy}% dalam {target} pertandingan","Menangkan {target} pertandingan sempurna","Raih {target} kemenangan beruntun","Absen {target} kali","Sumbangkan {target} Emas","Sumbangkan {target} Permata"),
    AppLanguage.THAI to missionCopy("เล่น {target} แมตช์","ชนะ {target} แมตช์","เลือกตัวเลขถูก {target} ครั้ง","ทำความแม่นยำ {accuracy}% ใน {target} แมตช์","ชนะแบบสมบูรณ์ {target} แมตช์","ชนะติดต่อกัน {target} ครั้ง","เช็กอิน {target} ครั้ง","บริจาคทอง {target}","บริจาคอัญมณี {target}"),
    AppLanguage.RUSSIAN to missionCopy("Сыграть {target} матчей","Выиграть {target} матчей","Выбрать {target} правильных чисел","Достичь точности {accuracy}% в {target} матчах","Выиграть {target} идеальных матчей","Достичь серии из {target} побед","Отметиться {target} раз","Пожертвовать {target} золота","Пожертвовать {target} самоцветов")
).mapValues { (language,copy) -> copy + (TextKey.MissionClanStreak to when(language) {
    AppLanguage.ENGLISH -> "{target} members reach a {streak}-win streak"
    AppLanguage.VIETNAMESE -> "{target} thành viên đạt chuỗi {streak} trận thắng"
    AppLanguage.SIMPLIFIED_CHINESE -> "{target} 名成员达到 {streak} 连胜"
    AppLanguage.JAPANESE -> "{target} 人が {streak} 連勝を達成"
    AppLanguage.KOREAN -> "회원 {target}명이 {streak}연승 달성"
    AppLanguage.SPANISH -> "{target} miembros logran {streak} victorias seguidas"
    AppLanguage.BRAZILIAN_PORTUGUESE -> "{target} membros alcançam {streak} vitórias seguidas"
    AppLanguage.FRENCH -> "{target} membres atteignent {streak} victoires consécutives"
    AppLanguage.GERMAN -> "{target} Mitglieder erreichen {streak} Siege in Folge"
    AppLanguage.INDONESIAN -> "{target} anggota meraih {streak} kemenangan beruntun"
    AppLanguage.THAI -> "สมาชิก {target} คนชนะติดต่อกัน {streak} ครั้ง"
    AppLanguage.RUSSIAN -> "{target} участников достигают серии из {streak} побед"
}) }
