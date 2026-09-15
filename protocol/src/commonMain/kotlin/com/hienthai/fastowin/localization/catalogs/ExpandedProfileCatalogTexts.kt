package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val expandedProfileKeys = listOf(
    TextKey.ProfileLoadError, TextKey.InviteToClan, TextKey.EditProfile, TextKey.Nickname,
    TextKey.Avatar, TextKey.UploadImage, TextKey.Matches, TextKey.Wins,
    TextKey.FeaturedAchievements, TextKey.BestStreak, TextKey.AverageReactionShort,
    TextKey.PerformanceMilestones, TextKey.WalletHistorySubtitle, TextKey.CheckInHistorySubtitle,
    TextKey.MissionsSubtitle, TextKey.CollectionSubtitle, TextKey.MatchHistory,
    TextKey.LoginDevices, TextKey.AccountSecurity, TextKey.CopyPlayerCode,
    TextKey.ProfilePerformanceHero, TextKey.ProfilePerformanceDescription,
    TextKey.WalletFlowHero, TextKey.WalletFlowDescription, TextKey.AttendanceJourney,
    TextKey.AttendanceJourneyDescription, TextKey.TodayMissions, TextKey.TodayMissionsDescription,
    TextKey.PersonalCollectionHero, TextKey.PersonalCollectionDescription,
    TextKey.RecentCompetitionHero, TextKey.RecentCompetitionDescription,
)

private fun expandedProfileCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == expandedProfileKeys.size) {
        "Expected ${expandedProfileKeys.size} profile translations, received ${values.size}."
    }
    return expandedProfileKeys.zip(values).toMap()
}

internal val expandedProfileTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to expandedProfileCopy(
        "无法加载个人资料。请检查网络连接后重试。", "邀请加入公会", "编辑资料", "昵称",
        "头像", "上传图片", "对局", "胜利", "精选成就", "最佳连胜", "平均反应时间",
        "表现与已解锁的里程碑", "金币、宝石和经验值", "日历与连续签到里程碑",
        "追踪进度并领取奖励", "头像框与称号", "对局历史", "已登录设备", "账户安全", "复制玩家编号",
        "你的表现", "追踪各模式下的速度、准确率与进度。", "资源流动",
        "每次获得或使用金币、宝石和经验值都会记录在这里。", "签到旅程",
        "查看连续签到、历史记录和签到里程碑。", "今日任务", "完成挑战、领取奖励并提升等级。",
        "你的风采", "装备已解锁的头像框与称号。", "对局历史", "查看结果与详细表现。",
    ),
    AppLanguage.JAPANESE to expandedProfileCopy(
        "プロフィールを読み込めませんでした。接続を確認して再試行してください。", "ギルドに招待", "プロフィールを編集", "ニックネーム",
        "アバター", "画像をアップロード", "対戦数", "勝利数", "注目の実績", "最高連勝", "平均反応時間",
        "パフォーマンスと解除済みのマイルストーン", "ゴールド・ジェム・XP", "カレンダーと連続ログインの節目",
        "進捗を確認して報酬を受け取る", "フレームと称号", "対戦履歴", "ログイン端末", "アカウントのセキュリティ", "プレイヤーコードをコピー",
        "あなたの成績", "各モードの速度、正確さ、進捗を確認できます。", "資産の流れ",
        "ゴールド、ジェム、XPの獲得と消費はすべてここに記録されます。", "ログインの歩み",
        "連続記録、履歴、ログインの節目を確認できます。", "今日のミッション", "チャレンジを達成し、報酬を受け取ってレベルアップしましょう。",
        "あなただけのスタイル", "解除したフレームと称号を装備しましょう。", "対戦履歴", "結果と詳しい成績を確認できます。",
    ),
    AppLanguage.KOREAN to expandedProfileCopy(
        "프로필을 불러올 수 없습니다. 연결을 확인하고 다시 시도하세요.", "길드에 초대", "프로필 수정", "별명",
        "아바타", "이미지 업로드", "경기", "승리", "주요 업적", "최고 연승", "평균 반응 시간",
        "경기 성과와 해금한 이정표", "골드, 젬, XP", "달력과 연속 출석 이정표",
        "진행도를 확인하고 보상을 받으세요", "프레임과 칭호", "경기 기록", "로그인한 기기", "계정 보안", "플레이어 코드 복사",
        "나의 경기 성과", "모든 모드에서 속도, 정확도, 진행도를 확인하세요.", "재화 흐름",
        "골드, 젬, XP의 획득과 사용 내역이 모두 여기에 기록됩니다.", "출석 여정",
        "연속 출석, 기록, 출석 이정표를 확인하세요.", "오늘의 임무", "도전을 완료하고 보상을 받아 레벨을 올리세요.",
        "나만의 스타일", "해금한 프레임과 칭호를 장착하세요.", "경기 기록", "결과와 자세한 경기 성과를 확인하세요.",
    ),
    AppLanguage.SPANISH to expandedProfileCopy(
        "No se pudo cargar el perfil. Comprueba tu conexión e inténtalo de nuevo.", "INVITAR AL CLAN", "Editar perfil", "Apodo",
        "Avatar", "Subir imagen", "Partidas", "Victorias", "Logros destacados", "Mejor racha", "Reacción media",
        "Rendimiento e hitos desbloqueados", "Oro, gemas y XP", "Calendario e hitos de racha",
        "Sigue tu progreso y reclama recompensas", "Marcos y títulos", "Historial de partidas", "Dispositivos con sesión iniciada", "Seguridad de la cuenta", "Copiar código de jugador",
        "Tu rendimiento", "Sigue tu velocidad, precisión y progreso en cada modo.", "Movimiento de recursos",
        "Cada ganancia o gasto de oro, gemas y XP queda registrado aquí.", "Tu camino de asistencia",
        "Consulta tus rachas, historial e hitos de asistencia.", "Misiones de hoy", "Completa desafíos, reclama recompensas y sube de nivel.",
        "Tu estilo propio", "Equipa los marcos y títulos desbloqueados.", "Historial de partidas", "Consulta los resultados y tu rendimiento detallado.",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to expandedProfileCopy(
        "Não foi possível carregar o perfil. Verifique sua conexão e tente novamente.", "CONVIDAR PARA O CLÃ", "Editar perfil", "Apelido",
        "Avatar", "Enviar imagem", "Partidas", "Vitórias", "Conquistas em destaque", "Melhor sequência", "Reação média",
        "Desempenho e marcos desbloqueados", "Ouro, Gemas e XP", "Calendário e marcos de sequência",
        "Acompanhe o progresso e resgate recompensas", "Molduras e títulos", "Histórico de partidas", "Dispositivos conectados", "Segurança da conta", "Copiar código do jogador",
        "Seu desempenho", "Acompanhe velocidade, precisão e progresso em cada modo.", "Fluxo de recursos",
        "Cada ganho ou gasto de Ouro, Gemas e XP é registrado aqui.", "Jornada de presença",
        "Veja sequências, histórico e marcos de presença.", "Missões de hoje", "Conclua desafios, resgate recompensas e suba de nível.",
        "Seu estilo", "Equipe molduras e títulos desbloqueados.", "Histórico de partidas", "Confira resultados e desempenho detalhado.",
    ),
    AppLanguage.FRENCH to expandedProfileCopy(
        "Impossible de charger le profil. Vérifiez votre connexion et réessayez.", "INVITER DANS LE CLAN", "Modifier le profil", "Pseudo",
        "Avatar", "Importer une image", "Parties", "Victoires", "Succès en vedette", "Meilleure série", "Réaction moyenne",
        "Performances et paliers débloqués", "Or, gemmes et XP", "Calendrier et paliers de série",
        "Suivez votre progression et récupérez des récompenses", "Cadres et titres", "Historique des parties", "Appareils connectés", "Sécurité du compte", "Copier le code joueur",
        "Vos performances", "Suivez votre vitesse, votre précision et votre progression dans chaque mode.", "Mouvements de ressources",
        "Chaque gain ou dépense d’or, de gemmes et d’XP est enregistré ici.", "Parcours d’assiduité",
        "Consultez vos séries, votre historique et vos paliers de présence.", "Missions du jour", "Relevez des défis, récupérez des récompenses et montez de niveau.",
        "Votre style", "Équipez les cadres et titres débloqués.", "Historique des parties", "Consultez les résultats et les performances détaillées.",
    ),
    AppLanguage.GERMAN to expandedProfileCopy(
        "Das Profil konnte nicht geladen werden. Prüfe deine Verbindung und versuche es erneut.", "IN CLAN EINLADEN", "Profil bearbeiten", "Spitzname",
        "Avatar", "Bild hochladen", "Spiele", "Siege", "Besondere Erfolge", "Beste Serie", "Durchschn. Reaktion",
        "Leistung und freigeschaltete Meilensteine", "Gold, Juwelen und XP", "Kalender und Serienmeilensteine",
        "Verfolge deinen Fortschritt und hole Belohnungen ab", "Rahmen und Titel", "Spielverlauf", "Angemeldete Geräte", "Kontosicherheit", "Spielercode kopieren",
        "Deine Leistung", "Verfolge Tempo, Genauigkeit und Fortschritt in jedem Modus.", "Ressourcenverlauf",
        "Jeder Gewinn und jede Ausgabe von Gold, Juwelen und XP wird hier erfasst.", "Dein Check-in-Weg",
        "Sieh dir Serien, Verlauf und Check-in-Meilensteine an.", "Heutige Missionen", "Schließe Herausforderungen ab, hole Belohnungen und steige auf.",
        "Dein Stil", "Rüste freigeschaltete Rahmen und Titel aus.", "Spielverlauf", "Sieh dir Ergebnisse und detaillierte Leistungen an.",
    ),
    AppLanguage.INDONESIAN to expandedProfileCopy(
        "Profil tidak dapat dimuat. Periksa koneksi lalu coba lagi.", "UNDANG KE KLAN", "Edit profil", "Nama panggilan",
        "Avatar", "Unggah gambar", "Pertandingan", "Kemenangan", "Pencapaian unggulan", "Rentetan terbaik", "Rata-rata reaksi",
        "Performa dan tonggak yang terbuka", "Emas, Gem, dan XP", "Kalender dan tonggak rentetan",
        "Pantau progres dan klaim hadiah", "Bingkai dan gelar", "Riwayat pertandingan", "Perangkat yang masuk", "Keamanan akun", "Salin kode pemain",
        "Performa kamu", "Pantau kecepatan, akurasi, dan progres di setiap mode.", "Aliran sumber daya",
        "Setiap perolehan atau penggunaan Emas, Gem, dan XP dicatat di sini.", "Perjalanan kehadiran",
        "Lihat rentetan, riwayat, dan tonggak kehadiran.", "Misi hari ini", "Selesaikan tantangan, klaim hadiah, dan naik level.",
        "Gaya khasmu", "Pakai bingkai dan gelar yang sudah terbuka.", "Riwayat pertandingan", "Lihat hasil dan performa secara rinci.",
    ),
    AppLanguage.THAI to expandedProfileCopy(
        "โหลดโปรไฟล์ไม่ได้ ตรวจสอบการเชื่อมต่อแล้วลองอีกครั้ง", "เชิญเข้ากิลด์", "แก้ไขโปรไฟล์", "ชื่อเล่น",
        "อวาตาร์", "อัปโหลดรูปภาพ", "แมตช์", "ชัยชนะ", "ความสำเร็จเด่น", "สถิติชนะต่อเนื่องสูงสุด", "เวลาตอบสนองเฉลี่ย",
        "ผลงานและเป้าหมายที่ปลดล็อกแล้ว", "ทอง เจม และ XP", "ปฏิทินและเป้าหมายการเช็กอินต่อเนื่อง",
        "ติดตามความคืบหน้าและรับรางวัล", "กรอบและฉายา", "ประวัติการแข่ง", "อุปกรณ์ที่เข้าสู่ระบบ", "ความปลอดภัยบัญชี", "คัดลอกรหัสผู้เล่น",
        "ผลงานของคุณ", "ติดตามความเร็ว ความแม่นยำ และความคืบหน้าในแต่ละโหมด", "ความเคลื่อนไหวของทรัพยากร",
        "ทุกครั้งที่ได้รับหรือใช้ทอง เจม และ XP จะถูกบันทึกไว้ที่นี่", "เส้นทางการเช็กอิน",
        "ดูสถิติต่อเนื่อง ประวัติ และเป้าหมายการเช็กอิน", "ภารกิจวันนี้", "ทำภารกิจ รับรางวัล และเพิ่มเลเวล",
        "สไตล์ของคุณ", "สวมกรอบและฉายาที่ปลดล็อกแล้ว", "ประวัติการแข่ง", "ดูผลการแข่งขันและผลงานโดยละเอียด",
    ),
    AppLanguage.RUSSIAN to expandedProfileCopy(
        "Не удалось загрузить профиль. Проверьте соединение и повторите попытку.", "ПРИГЛАСИТЬ В КЛАН", "Изменить профиль", "Псевдоним",
        "Аватар", "Загрузить изображение", "Матчи", "Победы", "Особые достижения", "Лучшая серия", "Средняя реакция",
        "Результаты и открытые этапы", "Золото, самоцветы и опыт", "Календарь и этапы серии",
        "Следите за прогрессом и получайте награды", "Рамки и звания", "История матчей", "Устройства с активным входом", "Безопасность аккаунта", "Скопировать код игрока",
        "Ваши результаты", "Следите за скоростью, точностью и прогрессом в каждом режиме.", "Движение ресурсов",
        "Каждое получение и расход золота, самоцветов и опыта записывается здесь.", "Путь отметок",
        "Просматривайте серии, историю и этапы отметок.", "Сегодняшние задания", "Выполняйте испытания, получайте награды и повышайте уровень.",
        "Ваш стиль", "Выбирайте открытые рамки и звания.", "История матчей", "Смотрите результаты и подробную статистику.",
    ),
)
