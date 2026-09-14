package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class GuestEntryLocalizationTest {
    @Test
    fun `guest entry and save progress copy resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Hello, {player}!", "Ready to set a new record?", "What should we call you?", "This name is only used for this play session.", "Enter nickname", "Create an account to keep your name, Elo and history on Android/iOS.", "Choose room type", "{player}'s room", "SAVE PROGRESS WITH EMAIL"),
            AppLanguage.VIETNAMESE to listOf("Xin chào, {player}!", "Sẵn sàng phá kỷ lục mới?", "Bạn muốn được gọi là gì?", "Tên này chỉ dùng trong phiên chơi hiện tại.", "Nhập biệt danh", "Tạo tài khoản để giữ tên, Elo và lịch sử trên Android/iOS.", "Chọn loại phòng", "Phòng của {player}", "LƯU TIẾN TRÌNH BẰNG EMAIL"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("你好，{player}！", "准备好刷新纪录了吗？", "怎么称呼你？", "此名称仅用于本次游戏会话。", "输入昵称", "创建账户以在 Android/iOS 上保存名称、Elo 和历史记录。", "选择房间类型", "{player} 的房间", "使用邮箱保存进度"),
            AppLanguage.JAPANESE to listOf("こんにちは、{player}！", "新記録を目指す準備はできましたか？", "お名前を教えてください", "この名前は今回のプレイセッションでのみ使用されます。", "ニックネームを入力", "アカウントを作成すると、Android/iOSで名前、Elo、履歴を保存できます。", "ルームタイプを選択", "{player}のルーム", "メールで進行状況を保存"),
            AppLanguage.KOREAN to listOf("안녕하세요, {player}님!", "새 기록에 도전할 준비가 되었나요?", "어떻게 불러 드릴까요?", "이 이름은 현재 플레이 세션에서만 사용됩니다.", "닉네임 입력", "계정을 만들어 Android/iOS에서 이름, Elo 및 기록을 저장하세요.", "방 유형 선택", "{player}님의 방", "이메일로 진행 상황 저장"),
            AppLanguage.SPANISH to listOf("¡Hola, {player}!", "¿Listo para batir un nuevo récord?", "¿Cómo quieres que te llamemos?", "Este nombre solo se usa durante esta sesión de juego.", "Introduce un apodo", "Crea una cuenta para guardar tu nombre, Elo e historial en Android/iOS.", "Elige el tipo de sala", "Sala de {player}", "GUARDAR PROGRESO CON EMAIL"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Olá, {player}!", "Pronto para bater um novo recorde?", "Como devemos chamar você?", "Este nome é usado apenas nesta sessão de jogo.", "Digite um apelido", "Crie uma conta para salvar seu nome, Elo e histórico no Android/iOS.", "Escolha o tipo de sala", "Sala de {player}", "SALVAR PROGRESSO COM E-MAIL"),
            AppLanguage.FRENCH to listOf("Bonjour, {player} !", "Prêt à battre un nouveau record ?", "Comment doit-on t’appeler ?", "Ce nom est utilisé uniquement pour cette session de jeu.", "Saisis un pseudo", "Crée un compte pour conserver ton nom, ton Elo et ton historique sur Android/iOS.", "Choisis le type de salon", "Salon de {player}", "ENREGISTRER AVEC UN E-MAIL"),
            AppLanguage.GERMAN to listOf("Hallo, {player}!", "Bereit für einen neuen Rekord?", "Wie sollen wir dich nennen?", "Dieser Name wird nur für diese Spielsitzung verwendet.", "Spitzname eingeben", "Erstelle ein Konto, um deinen Namen, dein Elo und deinen Verlauf auf Android/iOS zu speichern.", "Raumtyp auswählen", "{player}s Raum", "FORTSCHRITT PER E-MAIL SPEICHERN"),
            AppLanguage.INDONESIAN to listOf("Halo, {player}!", "Siap mencetak rekor baru?", "Kami harus memanggilmu apa?", "Nama ini hanya digunakan untuk sesi bermain ini.", "Masukkan nama panggilan", "Buat akun untuk menyimpan nama, Elo, dan riwayatmu di Android/iOS.", "Pilih jenis ruang", "Ruang {player}", "SIMPAN PROGRES DENGAN EMAIL"),
            AppLanguage.THAI to listOf("สวัสดี {player}!", "พร้อมสร้างสถิติใหม่หรือยัง?", "อยากให้เราเรียกคุณว่าอะไร?", "ชื่อนี้ใช้เฉพาะในเซสชันการเล่นนี้", "กรอกชื่อเล่น", "สร้างบัญชีเพื่อบันทึกชื่อ Elo และประวัติของคุณบน Android/iOS", "เลือกประเภทห้อง", "ห้องของ {player}", "บันทึกความคืบหน้าด้วยอีเมล"),
            AppLanguage.RUSSIAN to listOf("Привет, {player}!", "Готовы установить новый рекорд?", "Как вас называть?", "Это имя используется только в текущем игровом сеансе.", "Введите ник", "Создайте аккаунт, чтобы сохранить имя, Elo и историю на Android/iOS.", "Выберите тип комнаты", "Комната игрока {player}", "СОХРАНИТЬ ПРОГРЕСС ЧЕРЕЗ EMAIL"),
        )
        val keys = listOf(
            TextKey.GreetingPlayer,
            TextKey.ReadyForRecord,
            TextKey.GuestNameTitle,
            TextKey.GuestNameDescription,
            TextKey.EnterNickname,
            TextKey.SaveNameProgressDescription,
            TextKey.ChooseRoomType,
            TextKey.DefaultRoomName,
            TextKey.SaveProgressEmail,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
