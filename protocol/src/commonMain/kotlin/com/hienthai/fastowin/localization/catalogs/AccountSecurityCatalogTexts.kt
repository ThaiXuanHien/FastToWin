package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val accountSecurityKeys = listOf(
    TextKey.LogoutDescription, TextKey.AccountSecurityTitle,
    TextKey.AccountSecurityDescription, TextKey.ChangePassword,
    TextKey.CurrentPassword, TextKey.NewPasswordLabel,
    TextKey.ConfirmNewPasswordLabel, TextKey.NewPasswordMustDiffer,
    TextKey.DangerZone, TextKey.DeleteAccountWarning,
    TextKey.PasswordToConfirm, TextKey.RequestDeleteAccount,
    TextKey.LoginDevicesDescription, TextKey.NoActiveSessions,
    TextKey.CurrentDevice, TextKey.LogoutAllDevices,
    TextKey.LogoutAllDevicesTitle, TextKey.LogoutAllDevicesDescription,
    TextKey.RevokeCurrentDeviceDescription, TextKey.RevokeOtherDeviceDescription,
)

private fun accountSecurityCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == accountSecurityKeys.size) {
        "Expected ${accountSecurityKeys.size} account translations, received ${values.size}."
    }
    return accountSecurityKeys.zip(values).toMap()
}

internal val accountSecurityTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to accountSecurityCopy(
        "此设备将返回登录页面。", "账户安全", "修改密码或管理账户删除。", "修改密码", "当前密码", "新密码",
        "确认新密码", "新密码必须与当前密码不同。", "危险操作", "删除账户将永久移除你的资料、Elo、历史记录和成就。",
        "输入密码以确认", "我要删除我的账户", "查看并撤销不再使用的登录会话。", "没有找到有效的登录会话。",
        "此设备", "退出所有设备", "退出所有设备？", "所有会话（包括此设备）都将被撤销，你需要重新登录。",
        "此设备将返回登录页面。", "{device} 上的会话将立即被撤销。",
    ),
    AppLanguage.JAPANESE to accountSecurityCopy(
        "この端末ではログイン画面に戻ります。", "アカウントのセキュリティ", "パスワードの変更やアカウント削除を管理できます。", "パスワードを変更", "現在のパスワード", "新しいパスワード",
        "新しいパスワードを確認", "新しいパスワードは現在のものと異なる必要があります。", "危険な操作", "アカウントを削除すると、プロフィール、Elo、履歴、実績は完全に消去されます。",
        "確認のためパスワードを入力", "アカウントを削除する", "使わなくなったログインセッションを確認して無効にできます。", "有効なログインセッションがありません。",
        "この端末", "すべての端末からログアウト", "すべての端末からログアウトしますか？", "この端末を含むすべてのセッションが無効になり、再ログインが必要です。",
        "この端末ではログイン画面に戻ります。", "{device} のセッションは直ちに無効になります。",
    ),
    AppLanguage.KOREAN to accountSecurityCopy(
        "이 기기에서 로그인 화면으로 돌아갑니다.", "계정 보안", "비밀번호를 변경하거나 계정 삭제를 관리하세요.", "비밀번호 변경", "현재 비밀번호", "새 비밀번호",
        "새 비밀번호 확인", "새 비밀번호는 현재 비밀번호와 달라야 합니다.", "주의 구역", "계정을 삭제하면 프로필, Elo, 기록, 업적이 영구적으로 삭제됩니다.",
        "확인을 위해 비밀번호 입력", "내 계정 삭제하기", "더 이상 사용하지 않는 로그인 세션을 확인하고 해제하세요.", "활성 로그인 세션이 없습니다.",
        "이 기기", "모든 기기에서 로그아웃", "모든 기기에서 로그아웃하시겠습니까?", "이 기기를 포함한 모든 세션이 해제되며 다시 로그인해야 합니다.",
        "이 기기에서 로그인 화면으로 돌아갑니다.", "{device}의 세션이 즉시 해제됩니다.",
    ),
    AppLanguage.SPANISH to accountSecurityCopy(
        "Volverás a la pantalla de inicio de sesión en este dispositivo.", "SEGURIDAD DE LA CUENTA", "Cambia tu contraseña o gestiona la eliminación de la cuenta.", "Cambiar contraseña", "Contraseña actual", "Nueva contraseña",
        "Confirmar nueva contraseña", "La nueva contraseña debe ser distinta de la actual.", "Zona de peligro", "Eliminar la cuenta borra para siempre tu perfil, Elo, historial y logros.",
        "Introduce tu contraseña para confirmar", "QUIERO ELIMINAR MI CUENTA", "Revisa y revoca las sesiones que ya no uses.", "No se encontraron sesiones activas.",
        "Este dispositivo", "Cerrar sesión en todos los dispositivos", "¿CERRAR SESIÓN EN TODOS LOS DISPOSITIVOS?", "Se revocarán todas las sesiones, incluida esta, y tendrás que iniciar sesión de nuevo.",
        "Volverás a la pantalla de inicio de sesión en este dispositivo.", "La sesión de {device} se revocará de inmediato.",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to accountSecurityCopy(
        "Você voltará à tela de login neste dispositivo.", "SEGURANÇA DA CONTA", "Altere sua senha ou gerencie a exclusão da conta.", "Alterar senha", "Senha atual", "Nova senha",
        "Confirmar nova senha", "A nova senha deve ser diferente da atual.", "Área de risco", "Excluir sua conta remove permanentemente seu perfil, Elo, histórico e conquistas.",
        "Digite a senha para confirmar", "QUERO EXCLUIR MINHA CONTA", "Confira e revogue sessões que você não usa mais.", "Nenhuma sessão ativa encontrada.",
        "Este dispositivo", "Sair de todos os dispositivos", "SAIR DE TODOS OS DISPOSITIVOS?", "Todas as sessões, inclusive esta, serão revogadas e você precisará entrar novamente.",
        "Você voltará à tela de login neste dispositivo.", "A sessão em {device} será revogada imediatamente.",
    ),
    AppLanguage.FRENCH to accountSecurityCopy(
        "Vous reviendrez à l’écran de connexion sur cet appareil.", "SÉCURITÉ DU COMPTE", "Changez votre mot de passe ou gérez la suppression du compte.", "Changer le mot de passe", "Mot de passe actuel", "Nouveau mot de passe",
        "Confirmer le nouveau mot de passe", "Le nouveau mot de passe doit être différent de l’actuel.", "Zone sensible", "Supprimer votre compte efface définitivement votre profil, Elo, historique et succès.",
        "Saisissez votre mot de passe pour confirmer", "JE VEUX SUPPRIMER MON COMPTE", "Vérifiez et révoquez les sessions que vous n’utilisez plus.", "Aucune session active trouvée.",
        "Cet appareil", "Déconnecter tous les appareils", "DÉCONNECTER TOUS LES APPAREILS ?", "Toutes les sessions, y compris celle-ci, seront révoquées et vous devrez vous reconnecter.",
        "Vous reviendrez à l’écran de connexion sur cet appareil.", "La session sur {device} sera immédiatement révoquée.",
    ),
    AppLanguage.GERMAN to accountSecurityCopy(
        "Auf diesem Gerät kehrst du zur Anmeldeseite zurück.", "KONTOSICHERHEIT", "Ändere dein Passwort oder verwalte die Kontolöschung.", "Passwort ändern", "Aktuelles Passwort", "Neues Passwort",
        "Neues Passwort bestätigen", "Das neue Passwort muss sich vom aktuellen unterscheiden.", "Gefahrenbereich", "Beim Löschen deines Kontos werden Profil, Elo, Verlauf und Erfolge dauerhaft entfernt.",
        "Zur Bestätigung Passwort eingeben", "ICH MÖCHTE MEIN KONTO LÖSCHEN", "Prüfe und widerrufe Sitzungen, die du nicht mehr verwendest.", "Keine aktiven Anmeldesitzungen gefunden.",
        "Dieses Gerät", "Auf allen Geräten abmelden", "AUF ALLEN GERÄTEN ABMELDEN?", "Alle Sitzungen, auch auf diesem Gerät, werden widerrufen. Danach musst du dich erneut anmelden.",
        "Auf diesem Gerät kehrst du zur Anmeldeseite zurück.", "Die Sitzung auf {device} wird sofort widerrufen.",
    ),
    AppLanguage.INDONESIAN to accountSecurityCopy(
        "Kamu akan kembali ke layar masuk di perangkat ini.", "KEAMANAN AKUN", "Ubah kata sandi atau kelola penghapusan akun.", "Ubah kata sandi", "Kata sandi saat ini", "Kata sandi baru",
        "Konfirmasi kata sandi baru", "Kata sandi baru harus berbeda dari yang sekarang.", "Area berisiko", "Menghapus akun akan menghapus profil, Elo, riwayat, dan pencapaian secara permanen.",
        "Masukkan kata sandi untuk konfirmasi", "SAYA INGIN MENGHAPUS AKUN", "Tinjau dan cabut sesi yang tidak digunakan lagi.", "Tidak ada sesi masuk yang aktif.",
        "Perangkat ini", "Keluar dari semua perangkat", "KELUAR DARI SEMUA PERANGKAT?", "Semua sesi, termasuk perangkat ini, akan dicabut dan kamu harus masuk lagi.",
        "Kamu akan kembali ke layar masuk di perangkat ini.", "Sesi di {device} akan segera dicabut.",
    ),
    AppLanguage.THAI to accountSecurityCopy(
        "อุปกรณ์นี้จะกลับไปหน้าเข้าสู่ระบบ", "ความปลอดภัยบัญชี", "เปลี่ยนรหัสผ่านหรือจัดการการลบบัญชี", "เปลี่ยนรหัสผ่าน", "รหัสผ่านปัจจุบัน", "รหัสผ่านใหม่",
        "ยืนยันรหัสผ่านใหม่", "รหัสผ่านใหม่ต้องต่างจากรหัสผ่านปัจจุบัน", "การดำเนินการเสี่ยง", "การลบบัญชีจะลบโปรไฟล์ Elo ประวัติ และความสำเร็จของคุณอย่างถาวร",
        "กรอกรหัสผ่านเพื่อยืนยัน", "ฉันต้องการลบบัญชี", "ตรวจสอบและยกเลิกเซสชันที่ไม่ใช้อีกแล้ว", "ไม่พบเซสชันที่กำลังใช้งาน",
        "อุปกรณ์นี้", "ออกจากระบบทุกอุปกรณ์", "ออกจากระบบทุกอุปกรณ์หรือไม่", "เซสชันทั้งหมด รวมถึงอุปกรณ์นี้ จะถูกยกเลิกและคุณต้องเข้าสู่ระบบอีกครั้ง",
        "อุปกรณ์นี้จะกลับไปหน้าเข้าสู่ระบบ", "เซสชันบน {device} จะถูกยกเลิกทันที",
    ),
    AppLanguage.RUSSIAN to accountSecurityCopy(
        "На этом устройстве откроется экран входа.", "БЕЗОПАСНОСТЬ АККАУНТА", "Измените пароль или управляйте удалением аккаунта.", "Изменить пароль", "Текущий пароль", "Новый пароль",
        "Подтвердить новый пароль", "Новый пароль должен отличаться от текущего.", "Опасные действия", "Удаление аккаунта навсегда удалит ваш профиль, Elo, историю и достижения.",
        "Введите пароль для подтверждения", "Я ХОЧУ УДАЛИТЬ АККАУНТ", "Проверьте и отзовите сеансы, которыми больше не пользуетесь.", "Активные сеансы входа не найдены.",
        "Это устройство", "Выйти на всех устройствах", "ВЫЙТИ НА ВСЕХ УСТРОЙСТВАХ?", "Все сеансы, включая этот, будут отозваны. Вам потребуется войти снова.",
        "На этом устройстве откроется экран входа.", "Сеанс на {device} будет немедленно отозван.",
    ),
)
