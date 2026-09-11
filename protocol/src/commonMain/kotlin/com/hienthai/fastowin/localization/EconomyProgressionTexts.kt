package com.hienthai.fastowin.localization

/**
 * Copy introduced by the economy/progression expansion.
 *
 * This catalog deliberately contains a complete entry for every supported language. Keeping it
 * separate from the older partial catalogs prevents a newly added progression label from silently
 * falling back to English.
 */
internal val economyProgressionTexts: Map<AppLanguage, Map<TextKey, String>> =
    mapOf(
        AppLanguage.ENGLISH to progressionCopy(
            shop = "Gold|Gold Vault|Trade Gems for Gold. Every trade is recorded in your wallet history.|Trade Gems for Gold|Trade {gems} Gems for {gold} Gold?|Not enough Gems.|You received {gold} Gold.|This trade was already completed.|Could not complete the trade.|Gold Bag|Gold Chest|Gold Treasury",
            missions = "Play 1 online match|Play 2 casual matches|Play 3 online matches|Win 1 online match|Play 2 ranked matches|Find 100 correct numbers|Reach 90% accuracy|Win without a mistake|Check in today|Donate 500 Gold|Play 15 matches this week|Win 5 matches this week|Win 3 ranked matches|Find 500 correct numbers|Reach a 3-win streak|Win 3 perfect matches|Donate 2,000 Gold|Donate 5 Gems",
            achievementTitles = "First Battle|Master|Veteran|Conqueror|Undefeated|One Strike|Champion|Divine Eye|Golden Reflex|Godspeed|Wildfire|Immortal|Enduring|Supreme|War God|Glory|Dragon Might|Emperor|Pillar|Peerless",
            achievementDescriptions = "Win your first online match.|Win 10 online matches.|Win 50 online matches.|Win 100 ranked matches.|Reach a 10-win streak.|Win a match without a wrong tap.|Win 10 matches without a wrong tap.|Reach at least 90% accuracy in 10 matches.|Average under 2.5 seconds in 10 matches.|Average under 1.5 seconds in 10 matches.|Check in for 7 days in a row.|Check in for 30 days in a row.|Check in 50 times in total.|Check in 100 times in total.|Reach player level 30.|Join a clan.|Donate 10,000 Gold in total.|Donate 50 Gems in total.|Claim 10 clan mission rewards.|Be in a clan when it reaches level 10.",
            frameNames = "Lightning|Wildfire|Warrior|Veteran|Diamond|Challenger|Glory|Unyielding|Legend|Emperor|Speed Shadow|Champion|Immortal|Dragon Might|Supreme|Peerless",
            titleNames = "First Battle|Godspeed|Undefeated|Veteran|Divine Eye|Pillar|One Strike|Golden Reflex|Master|Conqueror|War God|Speed King",
            challengerUnlock = "Reach the Challenger rank.",
            speedKingUnlock = "Finish a season at rank #1."
        ),
        AppLanguage.VIETNAMESE to progressionCopy(
            shop = "Vàng|Kho Vàng|Đổi Gem lấy Vàng. Mọi giao dịch đều được lưu trong lịch sử tài sản.|Đổi Gem lấy Vàng|Đổi {gems} Gem lấy {gold} Vàng?|Không đủ Gem.|Bạn đã nhận {gold} Vàng.|Giao dịch này đã hoàn tất.|Không thể hoàn tất giao dịch.|Túi Vàng|Rương Vàng|Kho Báu Vàng",
            missions = "Chơi 1 trận online|Chơi 2 trận đấu thường|Chơi 3 trận online|Thắng 1 trận online|Chơi 2 trận đấu hạng|Chọn đúng 100 số|Đạt độ chính xác 90%|Thắng mà không bấm sai|Điểm danh hôm nay|Quyên góp 500 Vàng|Chơi 15 trận trong tuần|Thắng 5 trận trong tuần|Thắng 3 trận đấu hạng|Chọn đúng 500 số|Đạt chuỗi thắng 3|Thắng hoàn hảo 3 trận|Quyên góp 2.000 Vàng|Quyên góp 5 Gem",
            achievementTitles = "Khai Chiến|Cao Thủ|Bách Chiến|Kẻ Chinh Phục|Bất Bại|Nhất Kích|Quán Quân|Mắt Thần|Phản Xạ Vàng|Thần Tốc|Liệt Hỏa|Bất Diệt|Bền Bỉ|Chí Tôn|Chiến Thần|Vinh Quang|Long Uy|Đế Vương|Trụ Cột|Vô Song",
            achievementDescriptions = "Thắng trận online đầu tiên.|Thắng 10 trận online.|Thắng 50 trận online.|Thắng 100 trận đấu hạng.|Đạt chuỗi thắng 10 trận.|Thắng một trận không chọn sai.|Thắng 10 trận không chọn sai.|Đạt ít nhất 90% chính xác trong 10 trận.|Phản ứng trung bình dưới 2,5 giây trong 10 trận.|Phản ứng trung bình dưới 1,5 giây trong 10 trận.|Điểm danh liên tiếp 7 ngày.|Điểm danh liên tiếp 30 ngày.|Điểm danh tổng cộng 50 lần.|Điểm danh tổng cộng 100 lần.|Đạt cấp người chơi 30.|Tham gia một bang.|Quyên góp tổng cộng 10.000 Vàng.|Quyên góp tổng cộng 50 Gem.|Nhận thưởng 10 nhiệm vụ bang.|Thuộc bang khi bang đạt cấp 10.",
            frameNames = "Tia Chớp|Liệt Hỏa|Chiến Binh|Bách Chiến|Kim Cương|Thách Đấu|Vinh Quang|Bất Khuất|Huyền Thoại|Đế Vương|Tốc Ảnh|Quán Quân|Bất Diệt|Long Uy|Chí Tôn|Vô Song",
            titleNames = "Khai Chiến|Thần Tốc|Bất Bại|Bách Chiến|Mắt Thần|Trụ Cột|Nhất Kích|Phản Xạ Vàng|Cao Thủ|Kẻ Chinh Phục|Chiến Thần|Vua Tốc Độ",
            challengerUnlock = "Đạt bậc Thách Đấu.",
            speedKingUnlock = "Kết thúc mùa ở hạng 1."
        ),
        AppLanguage.SIMPLIFIED_CHINESE to progressionCopy(
            shop = "金币|金币宝库|用宝石兑换金币，每笔交易都会记录在资产历史中。|宝石兑换金币|用 {gems} 宝石兑换 {gold} 金币？|宝石不足。|你获得了 {gold} 金币。|该交易已完成。|交易失败。|金币袋|金币宝箱|黄金宝库",
            missions = "进行 1 场在线对战|进行 2 场休闲对战|进行 3 场在线对战|赢得 1 场在线对战|进行 2 场排位赛|正确找到 100 个数字|准确率达到 90%|无失误获胜|今日签到|捐献 500 金币|本周进行 15 场对战|本周赢得 5 场对战|赢得 3 场排位赛|正确找到 500 个数字|达成 3 连胜|完美赢得 3 场对战|捐献 2,000 金币|捐献 5 宝石",
            achievementTitles = "初战|高手|百战|征服者|不败|一击|冠军|神眼|黄金反应|神速|烈火|不朽|坚韧|至尊|战神|荣耀|龙威|帝王|中流砥柱|无双",
            achievementDescriptions = "赢得首场在线对战。|赢得 10 场在线对战。|赢得 50 场在线对战。|赢得 100 场排位赛。|达成 10 连胜。|无误触赢得一场对战。|无误触赢得 10 场对战。|在 10 场对战中达到至少 90% 准确率。|在 10 场对战中平均反应低于 2.5 秒。|在 10 场对战中平均反应低于 1.5 秒。|连续签到 7 天。|连续签到 30 天。|累计签到 50 次。|累计签到 100 次。|玩家等级达到 30。|加入一个公会。|累计捐献 10,000 金币。|累计捐献 50 宝石。|领取 10 次公会任务奖励。|所在公会达到 10 级。",
            frameNames = "闪电|烈火|战士|百战|钻石|挑战者|荣耀|不屈|传奇|帝王|疾影|冠军|不朽|龙威|至尊|无双",
            titleNames = "初战|神速|不败|百战|神眼|中流砥柱|一击|黄金反应|高手|征服者|战神|速度之王",
            challengerUnlock = "达到挑战者段位。",
            speedKingUnlock = "赛季最终排名第 1。"
        ),
        AppLanguage.JAPANESE to progressionCopy(
            shop = "ゴールド|ゴールド保管庫|ジェムをゴールドに交換します。すべての取引は資産履歴に記録されます。|ジェムをゴールドに交換|{gems} ジェムを {gold} ゴールドに交換しますか？|ジェムが足りません。|{gold} ゴールドを獲得しました。|この取引は完了済みです。|取引を完了できませんでした。|ゴールド袋|ゴールド宝箱|黄金の宝庫",
            missions = "オンライン対戦を1回プレイ|カジュアル対戦を2回プレイ|オンライン対戦を3回プレイ|オンライン対戦で1勝|ランク戦を2回プレイ|100個の数字を正解|正確率90%を達成|ミスなしで勝利|今日ログインボーナスを受け取る|500ゴールドを寄付|今週15回対戦|今週5勝|ランク戦で3勝|500個の数字を正解|3連勝を達成|パーフェクト勝利を3回|2,000ゴールドを寄付|5ジェムを寄付",
            achievementTitles = "開戦|達人|百戦|征服者|無敗|一撃|王者|神眼|黄金反射|神速|烈火|不滅|不屈の歩み|至高|戦神|栄光|龍威|帝王|支柱|無双",
            achievementDescriptions = "初めてオンライン対戦に勝利する。|オンライン対戦で10勝する。|オンライン対戦で50勝する。|ランク戦で100勝する。|10連勝を達成する。|ミスなしで1勝する。|ミスなしで10勝する。|10試合で正確率90%以上を達成する。|10試合で平均反応2.5秒未満を達成する。|10試合で平均反応1.5秒未満を達成する。|7日連続でログインする。|30日連続でログインする。|累計50回ログインする。|累計100回ログインする。|プレイヤーレベル30に到達する。|クランに参加する。|累計10,000ゴールドを寄付する。|累計50ジェムを寄付する。|クランミッション報酬を10回受け取る。|所属クランがレベル10に到達する。",
            frameNames = "稲妻|烈火|戦士|百戦|ダイヤモンド|挑戦者|栄光|不屈|伝説|帝王|疾風の影|王者|不滅|龍威|至高|無双",
            titleNames = "開戦|神速|無敗|百戦|神眼|支柱|一撃|黄金反射|達人|征服者|戦神|速度王",
            challengerUnlock = "挑戦者ランクに到達する。",
            speedKingUnlock = "シーズン最終順位1位になる。"
        ),
        AppLanguage.KOREAN to progressionCopy(
            shop = "골드|골드 금고|젬을 골드로 교환합니다. 모든 거래는 자산 기록에 저장됩니다.|젬을 골드로 교환|젬 {gems}개를 골드 {gold}개로 교환할까요?|젬이 부족합니다.|골드 {gold}개를 받았습니다.|이미 완료된 거래입니다.|거래를 완료할 수 없습니다.|골드 주머니|골드 상자|황금 금고",
            missions = "온라인 대전 1회 플레이|일반 대전 2회 플레이|온라인 대전 3회 플레이|온라인 대전 1승|랭크전 2회 플레이|숫자 100개 정답|정확도 90% 달성|실수 없이 승리|오늘 출석|골드 500개 기부|이번 주 15회 플레이|이번 주 5승|랭크전 3승|숫자 500개 정답|3연승 달성|퍼펙트 승리 3회|골드 2,000개 기부|젬 5개 기부",
            achievementTitles = "첫 전투|고수|백전|정복자|무패|일격|챔피언|신의 눈|황금 반사신경|신속|열화|불멸|인내|지존|전쟁의 신|영광|용의 위엄|제왕|기둥|무쌍",
            achievementDescriptions = "첫 온라인 대전에서 승리하세요.|온라인 대전 10승을 달성하세요.|온라인 대전 50승을 달성하세요.|랭크전 100승을 달성하세요.|10연승을 달성하세요.|오답 없이 한 경기를 승리하세요.|오답 없이 10경기를 승리하세요.|10경기에서 정확도 90% 이상을 달성하세요.|10경기에서 평균 반응 2.5초 미만을 달성하세요.|10경기에서 평균 반응 1.5초 미만을 달성하세요.|7일 연속 출석하세요.|30일 연속 출석하세요.|총 50회 출석하세요.|총 100회 출석하세요.|플레이어 레벨 30을 달성하세요.|클랜에 가입하세요.|총 10,000 골드를 기부하세요.|총 50 젬을 기부하세요.|클랜 미션 보상을 10회 받으세요.|소속 클랜이 레벨 10을 달성하세요.",
            frameNames = "번개|열화|전사|백전|다이아몬드|도전자|영광|불굴|전설|제왕|질풍의 그림자|챔피언|불멸|용의 위엄|지존|무쌍",
            titleNames = "첫 전투|신속|무패|백전|신의 눈|기둥|일격|황금 반사신경|고수|정복자|전쟁의 신|속도의 왕",
            challengerUnlock = "도전자 등급을 달성하세요.",
            speedKingUnlock = "시즌 최종 순위 1위를 달성하세요."
        ),
        AppLanguage.SPANISH to progressionCopy(
            shop = "Oro|Bóveda de oro|Cambia gemas por oro. Cada cambio se guarda en el historial de recursos.|Cambiar gemas por oro|¿Cambiar {gems} gemas por {gold} de oro?|No tienes suficientes gemas.|Has recibido {gold} de oro.|Este cambio ya se completó.|No se pudo completar el cambio.|Bolsa de oro|Cofre de oro|Tesoro de oro",
            missions = "Juega 1 partida en línea|Juega 2 partidas casuales|Juega 3 partidas en línea|Gana 1 partida en línea|Juega 2 partidas clasificatorias|Acierta 100 números|Alcanza un 90 % de precisión|Gana sin errores|Regístrate hoy|Dona 500 de oro|Juega 15 partidas esta semana|Gana 5 partidas esta semana|Gana 3 partidas clasificatorias|Acierta 500 números|Logra una racha de 3 victorias|Gana 3 partidas perfectas|Dona 2.000 de oro|Dona 5 gemas",
            achievementTitles = "Primera batalla|Maestro|Veterano|Conquistador|Invicto|Golpe único|Campeón|Ojo divino|Reflejo dorado|Velocidad divina|Fuego salvaje|Inmortal|Perseverante|Supremo|Dios de la guerra|Gloria|Poder del dragón|Emperador|Pilar|Sin rival",
            achievementDescriptions = "Gana tu primera partida en línea.|Gana 10 partidas en línea.|Gana 50 partidas en línea.|Gana 100 partidas clasificatorias.|Logra una racha de 10 victorias.|Gana una partida sin tocar mal.|Gana 10 partidas sin tocar mal.|Logra al menos un 90 % de precisión en 10 partidas.|Promedia menos de 2,5 segundos en 10 partidas.|Promedia menos de 1,5 segundos en 10 partidas.|Regístrate 7 días seguidos.|Regístrate 30 días seguidos.|Regístrate 50 veces en total.|Regístrate 100 veces en total.|Alcanza el nivel de jugador 30.|Únete a un clan.|Dona 10.000 de oro en total.|Dona 50 gemas en total.|Reclama 10 recompensas de misión de clan.|Pertenece a un clan cuando alcance el nivel 10.",
            frameNames = "Relámpago|Fuego salvaje|Guerrero|Veterano|Diamante|Desafiante|Gloria|Indomable|Leyenda|Emperador|Sombra veloz|Campeón|Inmortal|Poder del dragón|Supremo|Sin rival",
            titleNames = "Primera batalla|Velocidad divina|Invicto|Veterano|Ojo divino|Pilar|Golpe único|Reflejo dorado|Maestro|Conquistador|Dios de la guerra|Rey de la velocidad",
            challengerUnlock = "Alcanza el rango Desafiante.",
            speedKingUnlock = "Termina una temporada en el puesto 1."
        ),
        AppLanguage.BRAZILIAN_PORTUGUESE to progressionCopy(
            shop = "Ouro|Cofre de ouro|Troque Gemas por Ouro. Toda troca fica registrada no histórico de recursos.|Trocar Gemas por Ouro|Trocar {gems} Gemas por {gold} de Ouro?|Gemas insuficientes.|Você recebeu {gold} de Ouro.|Esta troca já foi concluída.|Não foi possível concluir a troca.|Bolsa de Ouro|Baú de Ouro|Tesouro de Ouro",
            missions = "Jogue 1 partida online|Jogue 2 partidas casuais|Jogue 3 partidas online|Vença 1 partida online|Jogue 2 partidas ranqueadas|Acerte 100 números|Alcance 90% de precisão|Vença sem errar|Faça check-in hoje|Doe 500 de Ouro|Jogue 15 partidas nesta semana|Vença 5 partidas nesta semana|Vença 3 partidas ranqueadas|Acerte 500 números|Alcance uma sequência de 3 vitórias|Vença 3 partidas perfeitas|Doe 2.000 de Ouro|Doe 5 Gemas",
            achievementTitles = "Primeira Batalha|Mestre|Veterano|Conquistador|Invicto|Golpe Único|Campeão|Olho Divino|Reflexo Dourado|Velocidade Divina|Fogo Vivo|Imortal|Persistente|Supremo|Deus da Guerra|Glória|Poder do Dragão|Imperador|Pilar|Sem Rival",
            achievementDescriptions = "Vença sua primeira partida online.|Vença 10 partidas online.|Vença 50 partidas online.|Vença 100 partidas ranqueadas.|Alcance uma sequência de 10 vitórias.|Vença uma partida sem toque errado.|Vença 10 partidas sem toque errado.|Alcance pelo menos 90% de precisão em 10 partidas.|Tenha média abaixo de 2,5 segundos em 10 partidas.|Tenha média abaixo de 1,5 segundos em 10 partidas.|Faça check-in por 7 dias seguidos.|Faça check-in por 30 dias seguidos.|Faça check-in 50 vezes no total.|Faça check-in 100 vezes no total.|Alcance o nível de jogador 30.|Entre em um clã.|Doe 10.000 de Ouro no total.|Doe 50 Gemas no total.|Resgate 10 recompensas de missão do clã.|Esteja em um clã quando ele alcançar o nível 10.",
            frameNames = "Relâmpago|Fogo Vivo|Guerreiro|Veterano|Diamante|Desafiante|Glória|Indomável|Lenda|Imperador|Sombra Veloz|Campeão|Imortal|Poder do Dragão|Supremo|Sem Rival",
            titleNames = "Primeira Batalha|Velocidade Divina|Invicto|Veterano|Olho Divino|Pilar|Golpe Único|Reflexo Dourado|Mestre|Conquistador|Deus da Guerra|Rei da Velocidade",
            challengerUnlock = "Alcance o ranque Desafiante.",
            speedKingUnlock = "Termine uma temporada em 1º lugar."
        ),
        AppLanguage.FRENCH to progressionCopy(
            shop = "Or|Coffre d'or|Échangez des Gemmes contre de l'Or. Chaque échange est enregistré dans l'historique des ressources.|Échanger des Gemmes contre de l'Or|Échanger {gems} Gemmes contre {gold} Or ?|Pas assez de Gemmes.|Vous avez reçu {gold} Or.|Cet échange a déjà été effectué.|Impossible d'effectuer l'échange.|Sac d'Or|Coffre d'Or|Trésor d'Or",
            missions = "Jouer 1 partie en ligne|Jouer 2 parties normales|Jouer 3 parties en ligne|Gagner 1 partie en ligne|Jouer 2 parties classées|Trouver 100 nombres corrects|Atteindre 90 % de précision|Gagner sans erreur|Se connecter aujourd'hui|Donner 500 Or|Jouer 15 parties cette semaine|Gagner 5 parties cette semaine|Gagner 3 parties classées|Trouver 500 nombres corrects|Atteindre une série de 3 victoires|Gagner 3 parties parfaites|Donner 2 000 Or|Donner 5 Gemmes",
            achievementTitles = "Premier Combat|Maître|Vétéran|Conquérant|Invaincu|Coup Unique|Champion|Œil Divin|Réflexe d'Or|Vitesse Divine|Feu Sauvage|Immortel|Endurant|Suprême|Dieu de la Guerre|Gloire|Puissance du Dragon|Empereur|Pilier|Sans Égal",
            achievementDescriptions = "Gagnez votre première partie en ligne.|Gagnez 10 parties en ligne.|Gagnez 50 parties en ligne.|Gagnez 100 parties classées.|Atteignez une série de 10 victoires.|Gagnez une partie sans erreur.|Gagnez 10 parties sans erreur.|Atteignez au moins 90 % de précision sur 10 parties.|Obtenez une moyenne inférieure à 2,5 secondes sur 10 parties.|Obtenez une moyenne inférieure à 1,5 seconde sur 10 parties.|Connectez-vous 7 jours de suite.|Connectez-vous 30 jours de suite.|Connectez-vous 50 fois au total.|Connectez-vous 100 fois au total.|Atteignez le niveau de joueur 30.|Rejoignez un clan.|Donnez 10 000 Or au total.|Donnez 50 Gemmes au total.|Récupérez 10 récompenses de mission de clan.|Soyez dans un clan lorsqu'il atteint le niveau 10.",
            frameNames = "Éclair|Feu Sauvage|Guerrier|Vétéran|Diamant|Challenger|Gloire|Indomptable|Légende|Empereur|Ombre Rapide|Champion|Immortel|Puissance du Dragon|Suprême|Sans Égal",
            titleNames = "Premier Combat|Vitesse Divine|Invaincu|Vétéran|Œil Divin|Pilier|Coup Unique|Réflexe d'Or|Maître|Conquérant|Dieu de la Guerre|Roi de la Vitesse",
            challengerUnlock = "Atteignez le rang Challenger.",
            speedKingUnlock = "Terminez une saison à la 1re place."
        ),
        AppLanguage.GERMAN to progressionCopy(
            shop = "Gold|Goldtresor|Tausche Juwelen gegen Gold. Jeder Tausch wird im Ressourcenverlauf gespeichert.|Juwelen gegen Gold tauschen|{gems} Juwelen gegen {gold} Gold tauschen?|Nicht genug Juwelen.|Du hast {gold} Gold erhalten.|Dieser Tausch wurde bereits abgeschlossen.|Der Tausch konnte nicht abgeschlossen werden.|Goldbeutel|Goldtruhe|Goldschatz",
            missions = "1 Online-Match spielen|2 normale Matches spielen|3 Online-Matches spielen|1 Online-Match gewinnen|2 Ranglisten-Matches spielen|100 Zahlen richtig finden|90 % Genauigkeit erreichen|Ohne Fehler gewinnen|Heute anmelden|500 Gold spenden|Diese Woche 15 Matches spielen|Diese Woche 5 Matches gewinnen|3 Ranglisten-Matches gewinnen|500 Zahlen richtig finden|Eine Siegesserie von 3 erreichen|3 perfekte Matches gewinnen|2.000 Gold spenden|5 Juwelen spenden",
            achievementTitles = "Erster Kampf|Meister|Veteran|Eroberer|Unbesiegt|Ein Schlag|Champion|Göttliches Auge|Goldener Reflex|Göttertempo|Lauffeuer|Unsterblich|Ausdauernd|Erhaben|Kriegsgott|Ruhm|Drachenmacht|Kaiser|Stütze|Unvergleichlich",
            achievementDescriptions = "Gewinne dein erstes Online-Match.|Gewinne 10 Online-Matches.|Gewinne 50 Online-Matches.|Gewinne 100 Ranglisten-Matches.|Erreiche eine Siegesserie von 10.|Gewinne ein Match ohne Fehlklick.|Gewinne 10 Matches ohne Fehlklick.|Erreiche in 10 Matches mindestens 90 % Genauigkeit.|Erreiche in 10 Matches im Schnitt unter 2,5 Sekunden.|Erreiche in 10 Matches im Schnitt unter 1,5 Sekunden.|Melde dich 7 Tage in Folge an.|Melde dich 30 Tage in Folge an.|Melde dich insgesamt 50-mal an.|Melde dich insgesamt 100-mal an.|Erreiche Spielerlevel 30.|Tritt einem Clan bei.|Spende insgesamt 10.000 Gold.|Spende insgesamt 50 Juwelen.|Hole 10 Clan-Missionsbelohnungen ab.|Sei in einem Clan, wenn er Level 10 erreicht.",
            frameNames = "Blitz|Lauffeuer|Krieger|Veteran|Diamant|Herausforderer|Ruhm|Unbeugsam|Legende|Kaiser|Schattenblitz|Champion|Unsterblich|Drachenmacht|Erhaben|Unvergleichlich",
            titleNames = "Erster Kampf|Göttertempo|Unbesiegt|Veteran|Göttliches Auge|Stütze|Ein Schlag|Goldener Reflex|Meister|Eroberer|Kriegsgott|König der Geschwindigkeit",
            challengerUnlock = "Erreiche den Rang Herausforderer.",
            speedKingUnlock = "Beende eine Saison auf Rang 1."
        ),
        AppLanguage.INDONESIAN to progressionCopy(
            shop = "Emas|Brankas Emas|Tukar Gem dengan Emas. Setiap penukaran tercatat di riwayat aset.|Tukar Gem dengan Emas|Tukar {gems} Gem dengan {gold} Emas?|Gem tidak cukup.|Kamu menerima {gold} Emas.|Penukaran ini sudah selesai.|Penukaran tidak dapat diselesaikan.|Kantong Emas|Peti Emas|Harta Emas",
            missions = "Mainkan 1 laga online|Mainkan 2 laga kasual|Mainkan 3 laga online|Menangkan 1 laga online|Mainkan 2 laga peringkat|Temukan 100 angka dengan benar|Raih akurasi 90%|Menang tanpa kesalahan|Check-in hari ini|Donasikan 500 Emas|Mainkan 15 laga minggu ini|Menangkan 5 laga minggu ini|Menangkan 3 laga peringkat|Temukan 500 angka dengan benar|Raih 3 kemenangan beruntun|Menangkan 3 laga sempurna|Donasikan 2.000 Emas|Donasikan 5 Gem",
            achievementTitles = "Pertempuran Awal|Ahli|Seratus Tempur|Penakluk|Tak Terkalahkan|Satu Serangan|Juara|Mata Dewa|Refleks Emas|Secepat Dewa|Api Membara|Abadi|Tangguh|Tertinggi|Dewa Perang|Kejayaan|Wibawa Naga|Kaisar|Pilar|Tiada Tanding",
            achievementDescriptions = "Menangkan laga online pertamamu.|Menangkan 10 laga online.|Menangkan 50 laga online.|Menangkan 100 laga peringkat.|Raih 10 kemenangan beruntun.|Menangkan satu laga tanpa salah tekan.|Menangkan 10 laga tanpa salah tekan.|Raih akurasi minimal 90% dalam 10 laga.|Raih rata-rata di bawah 2,5 detik dalam 10 laga.|Raih rata-rata di bawah 1,5 detik dalam 10 laga.|Check-in 7 hari berturut-turut.|Check-in 30 hari berturut-turut.|Check-in total 50 kali.|Check-in total 100 kali.|Raih level pemain 30.|Bergabung dengan klan.|Donasikan total 10.000 Emas.|Donasikan total 50 Gem.|Klaim 10 hadiah misi klan.|Berada dalam klan saat mencapai level 10.",
            frameNames = "Kilat|Api Membara|Pejuang|Seratus Tempur|Berlian|Penantang|Kejayaan|Pantang Menyerah|Legenda|Kaisar|Bayangan Cepat|Juara|Abadi|Wibawa Naga|Tertinggi|Tiada Tanding",
            titleNames = "Pertempuran Awal|Secepat Dewa|Tak Terkalahkan|Seratus Tempur|Mata Dewa|Pilar|Satu Serangan|Refleks Emas|Ahli|Penakluk|Dewa Perang|Raja Kecepatan",
            challengerUnlock = "Raih peringkat Penantang.",
            speedKingUnlock = "Akhiri musim di peringkat 1."
        ),
        AppLanguage.THAI to progressionCopy(
            shop = "ทอง|คลังทอง|แลกเจมเป็นทอง ทุกการแลกจะถูกบันทึกในประวัติทรัพย์สิน|แลกเจมเป็นทอง|แลก {gems} เจมเป็น {gold} ทองหรือไม่?|เจมไม่เพียงพอ|คุณได้รับ {gold} ทอง|รายการนี้เสร็จสิ้นแล้ว|ไม่สามารถทำรายการได้|ถุงทอง|หีบทอง|ขุมทรัพย์ทอง",
            missions = "เล่นออนไลน์ 1 นัด|เล่นทั่วไป 2 นัด|เล่นออนไลน์ 3 นัด|ชนะออนไลน์ 1 นัด|เล่นจัดอันดับ 2 นัด|เลือกตัวเลขถูก 100 ครั้ง|ทำความแม่นยำ 90%|ชนะโดยไม่กดผิด|เช็กอินวันนี้|บริจาค 500 ทอง|เล่น 15 นัดในสัปดาห์นี้|ชนะ 5 นัดในสัปดาห์นี้|ชนะจัดอันดับ 3 นัด|เลือกตัวเลขถูก 500 ครั้ง|ชนะต่อเนื่อง 3 นัด|ชนะสมบูรณ์แบบ 3 นัด|บริจาค 2,000 ทอง|บริจาค 5 เจม",
            achievementTitles = "เปิดศึก|ยอดฝีมือ|ร้อยศึก|ผู้พิชิต|ไร้พ่าย|หนึ่งโจมตี|แชมเปียน|เนตรเทพ|ปฏิกิริยาทอง|เทพความเร็ว|เพลิงพิโรธ|อมตะ|มุ่งมั่น|สูงสุด|เทพสงคราม|เกียรติยศ|อำนาจมังกร|จักรพรรดิ|เสาหลัก|ไร้เทียมทาน",
            achievementDescriptions = "ชนะเกมออนไลน์ครั้งแรก|ชนะออนไลน์ 10 นัด|ชนะออนไลน์ 50 นัด|ชนะจัดอันดับ 100 นัด|ชนะต่อเนื่อง 10 นัด|ชนะหนึ่งนัดโดยไม่กดผิด|ชนะ 10 นัดโดยไม่กดผิด|ทำความแม่นยำอย่างน้อย 90% ใน 10 นัด|ทำเวลาเฉลี่ยต่ำกว่า 2.5 วินาทีใน 10 นัด|ทำเวลาเฉลี่ยต่ำกว่า 1.5 วินาทีใน 10 นัด|เช็กอินต่อเนื่อง 7 วัน|เช็กอินต่อเนื่อง 30 วัน|เช็กอินรวม 50 ครั้ง|เช็กอินรวม 100 ครั้ง|ถึงเลเวลผู้เล่น 30|เข้าร่วมแคลน|บริจาคทองรวม 10,000|บริจาคเจมรวม 50|รับรางวัลภารกิจแคลน 10 ครั้ง|อยู่ในแคลนเมื่อแคลนถึงเลเวล 10",
            frameNames = "สายฟ้า|เพลิงพิโรธ|นักรบ|ร้อยศึก|เพชร|ผู้ท้าชิง|เกียรติยศ|ไม่ยอมแพ้|ตำนาน|จักรพรรดิ|เงาวายุ|แชมเปียน|อมตะ|อำนาจมังกร|สูงสุด|ไร้เทียมทาน",
            titleNames = "เปิดศึก|เทพความเร็ว|ไร้พ่าย|ร้อยศึก|เนตรเทพ|เสาหลัก|หนึ่งโจมตี|ปฏิกิริยาทอง|ยอดฝีมือ|ผู้พิชิต|เทพสงคราม|ราชาความเร็ว",
            challengerUnlock = "ถึงแรงก์ผู้ท้าชิง",
            speedKingUnlock = "จบฤดูกาลในอันดับ 1"
        ),
        AppLanguage.RUSSIAN to progressionCopy(
            shop = "Золото|Хранилище золота|Обменивайте кристаллы на золото. Каждый обмен сохраняется в истории ресурсов.|Обменять кристаллы на золото|Обменять {gems} кристаллов на {gold} золота?|Недостаточно кристаллов.|Вы получили {gold} золота.|Этот обмен уже выполнен.|Не удалось выполнить обмен.|Мешок золота|Сундук золота|Золотая сокровищница",
            missions = "Сыграть 1 онлайн-матч|Сыграть 2 обычных матча|Сыграть 3 онлайн-матча|Выиграть 1 онлайн-матч|Сыграть 2 рейтинговых матча|Верно найти 100 чисел|Достичь точности 90 %|Победить без ошибок|Отметиться сегодня|Пожертвовать 500 золота|Сыграть 15 матчей за неделю|Выиграть 5 матчей за неделю|Выиграть 3 рейтинговых матча|Верно найти 500 чисел|Достичь серии из 3 побед|Одержать 3 идеальные победы|Пожертвовать 2 000 золота|Пожертвовать 5 кристаллов",
            achievementTitles = "Первый Бой|Мастер|Ветеран|Завоеватель|Непобедимый|Один Удар|Чемпион|Божественный Взор|Золотая Реакция|Божественная Скорость|Дикое Пламя|Бессмертный|Стойкий|Верховный|Бог Войны|Слава|Мощь Дракона|Император|Опора|Несравненный",
            achievementDescriptions = "Выиграйте первый онлайн-матч.|Выиграйте 10 онлайн-матчей.|Выиграйте 50 онлайн-матчей.|Выиграйте 100 рейтинговых матчей.|Достигните серии из 10 побед.|Победите в матче без ошибочных нажатий.|Победите в 10 матчах без ошибочных нажатий.|Достигните точности не менее 90 % в 10 матчах.|Получите среднее время менее 2,5 секунды в 10 матчах.|Получите среднее время менее 1,5 секунды в 10 матчах.|Отмечайтесь 7 дней подряд.|Отмечайтесь 30 дней подряд.|Отметьтесь 50 раз всего.|Отметьтесь 100 раз всего.|Достигните 30-го уровня игрока.|Вступите в клан.|Пожертвуйте всего 10 000 золота.|Пожертвуйте всего 50 кристаллов.|Получите 10 наград за задания клана.|Состоите в клане, когда он достигнет 10-го уровня.",
            frameNames = "Молния|Дикое Пламя|Воин|Ветеран|Алмаз|Претендент|Слава|Несломленный|Легенда|Император|Быстрая Тень|Чемпион|Бессмертный|Мощь Дракона|Верховный|Несравненный",
            titleNames = "Первый Бой|Божественная Скорость|Непобедимый|Ветеран|Божественный Взор|Опора|Один Удар|Золотая Реакция|Мастер|Завоеватель|Бог Войны|Король Скорости",
            challengerUnlock = "Достигните ранга Претендент.",
            speedKingUnlock = "Завершите сезон на 1-м месте."
        )
    )

private data class ProgressionCopy(
    val shop: String,
    val missions: String,
    val achievementTitles: String,
    val achievementDescriptions: String,
    val frameNames: String,
    val titleNames: String,
    val challengerUnlock: String,
    val speedKingUnlock: String
)

private fun progressionCopy(
    shop: String,
    missions: String,
    achievementTitles: String,
    achievementDescriptions: String,
    frameNames: String,
    titleNames: String,
    challengerUnlock: String,
    speedKingUnlock: String
) = ProgressionCopy(
    shop,
    missions,
    achievementTitles,
    achievementDescriptions,
    frameNames,
    titleNames,
    challengerUnlock,
    speedKingUnlock
).toTextMap()

private fun ProgressionCopy.toTextMap(): Map<TextKey, String> = buildMap {
    putParts(SHOP_KEYS, shop)
    putParts(MISSION_KEYS, missions)
    putParts(ACHIEVEMENT_TITLE_KEYS, achievementTitles)
    putParts(ACHIEVEMENT_DESCRIPTION_KEYS, achievementDescriptions)
    putParts(FRAME_NAME_KEYS, frameNames)
    putParts(TITLE_NAME_KEYS, titleNames)

    val descriptions = ACHIEVEMENT_DESCRIPTION_KEYS.associateWith { key -> getValue(key) }
    FRAME_UNLOCK_KEYS.zip(
        listOf(9, 10, 1, 2, 3, null, 15, 4, 14, 17, 8, 6, 11, 16, 13, 19)
    ).forEach { (key, achievementIndex) ->
        put(key, achievementIndex?.let { descriptions.getValue(ACHIEVEMENT_DESCRIPTION_KEYS[it]) } ?: challengerUnlock)
    }
    TITLE_UNLOCK_KEYS.zip(listOf(0, 9, 4, 2, 7, 18, 5, 8, 1, 3, 14, null))
        .forEach { (key, achievementIndex) ->
            put(key, achievementIndex?.let { descriptions.getValue(ACHIEVEMENT_DESCRIPTION_KEYS[it]) } ?: speedKingUnlock)
        }
}

private fun MutableMap<TextKey, String>.putParts(keys: List<TextKey>, values: String) {
    val parts = values.split('|')
    require(parts.size == keys.size) { "Expected ${keys.size} translations, got ${parts.size}" }
    keys.zip(parts).forEach { (key, value) -> put(key, value) }
}

private val SHOP_KEYS: List<TextKey> get() = listOf(
    TextKey.GoldTab, TextKey.GoldVault, TextKey.GoldVaultDescription,
    TextKey.ExchangeGemsForGold, TextKey.GoldExchangeConfirmation, TextKey.NotEnoughGems,
    TextKey.GoldExchangeGranted, TextKey.GoldExchangeAlreadyGranted, TextKey.GoldExchangeFailed,
    TextKey.GoldOfferBagName, TextKey.GoldOfferChestName, TextKey.GoldOfferVaultName
)

private val MISSION_KEYS: List<TextKey> get() = listOf(
    TextKey.MissionPlayOne, TextKey.MissionCasualTwo, TextKey.MissionPlayThree,
    TextKey.MissionWinOne, TextKey.MissionRankedTwo, TextKey.MissionDailyCorrectHundred,
    TextKey.MissionAccuracyNinety, TextKey.MissionDailyPerfectWin, TextKey.MissionDailyCheckIn,
    TextKey.MissionDonateGoldFiveHundred, TextKey.MissionWeeklyPlayFifteen,
    TextKey.MissionWeeklyWinFive, TextKey.MissionWeeklyRankedWinThree,
    TextKey.MissionWeeklyCorrectFiveHundred, TextKey.MissionWeeklyStreakThree,
    TextKey.MissionWeeklyPerfectThree, TextKey.MissionWeeklyDonateGoldTwoThousand,
    TextKey.MissionWeeklyDonateGemsFive
)

private val ACHIEVEMENT_TITLE_KEYS: List<TextKey> get() = listOf(
    TextKey.AchievementFirstWinTitle, TextKey.AchievementWins10Title,
    TextKey.AchievementWins50Title, TextKey.AchievementRankedWins100Title,
    TextKey.AchievementWinStreak10Title, TextKey.AchievementPerfectMatch1Title,
    TextKey.AchievementPerfectMatches10Title, TextKey.AchievementAccuracy90TenTitle,
    TextKey.AchievementResponse2500TenTitle, TextKey.AchievementResponse1500TenTitle,
    TextKey.AchievementCheckinStreak7Title, TextKey.AchievementCheckinStreak30Title,
    TextKey.AchievementCheckins50Title, TextKey.AchievementCheckins100Title,
    TextKey.AchievementPlayerLevel30Title, TextKey.AchievementClanJoinedTitle,
    TextKey.AchievementClanGold10000Title, TextKey.AchievementClanGems50Title,
    TextKey.AchievementClanQuests10Title, TextKey.AchievementClanLevel10Title
)

private val ACHIEVEMENT_DESCRIPTION_KEYS: List<TextKey> get() = listOf(
    TextKey.AchievementFirstWinDescription, TextKey.AchievementWins10Description,
    TextKey.AchievementWins50Description, TextKey.AchievementRankedWins100Description,
    TextKey.AchievementWinStreak10Description, TextKey.AchievementPerfectMatch1Description,
    TextKey.AchievementPerfectMatches10Description, TextKey.AchievementAccuracy90TenDescription,
    TextKey.AchievementResponse2500TenDescription, TextKey.AchievementResponse1500TenDescription,
    TextKey.AchievementCheckinStreak7Description, TextKey.AchievementCheckinStreak30Description,
    TextKey.AchievementCheckins50Description, TextKey.AchievementCheckins100Description,
    TextKey.AchievementPlayerLevel30Description, TextKey.AchievementClanJoinedDescription,
    TextKey.AchievementClanGold10000Description, TextKey.AchievementClanGems50Description,
    TextKey.AchievementClanQuests10Description, TextKey.AchievementClanLevel10Description
)

private val FRAME_NAME_KEYS: List<TextKey> get() = listOf(
    TextKey.FrameLightningName, TextKey.FrameWildfireName, TextKey.FrameWarriorName,
    TextKey.FrameVeteranName, TextKey.FrameDiamondName, TextKey.FrameChallengerName,
    TextKey.FrameGloryName, TextKey.FrameUnyieldingName, TextKey.FrameLegendName,
    TextKey.FrameEmperorName, TextKey.FrameSpeedShadowName, TextKey.FrameChampionName,
    TextKey.FrameImmortalName, TextKey.FrameDragonMightName, TextKey.FrameSupremeName,
    TextKey.FramePeerlessName
)

private val FRAME_UNLOCK_KEYS: List<TextKey> get() = listOf(
    TextKey.FrameLightningUnlockDescription, TextKey.FrameWildfireUnlockDescription,
    TextKey.FrameWarriorUnlockDescription, TextKey.FrameVeteranUnlockDescription,
    TextKey.FrameDiamondUnlockDescription, TextKey.FrameChallengerUnlockDescription,
    TextKey.FrameGloryUnlockDescription, TextKey.FrameUnyieldingUnlockDescription,
    TextKey.FrameLegendUnlockDescription, TextKey.FrameEmperorUnlockDescription,
    TextKey.FrameSpeedShadowUnlockDescription, TextKey.FrameChampionUnlockDescription,
    TextKey.FrameImmortalUnlockDescription, TextKey.FrameDragonMightUnlockDescription,
    TextKey.FrameSupremeUnlockDescription, TextKey.FramePeerlessUnlockDescription
)

private val TITLE_NAME_KEYS: List<TextKey> get() = listOf(
    TextKey.TitleFirstBattleName, TextKey.TitleGodspeedName, TextKey.TitleUndefeatedName,
    TextKey.TitleVeteranName, TextKey.TitleDivineEyeName, TextKey.TitlePillarName,
    TextKey.TitleOneStrikeName, TextKey.TitleGoldenReflexName, TextKey.TitleMasterName,
    TextKey.TitleConquerorName, TextKey.TitleWarGodName, TextKey.TitleSpeedKingName
)

private val TITLE_UNLOCK_KEYS: List<TextKey> get() = listOf(
    TextKey.TitleFirstBattleUnlockDescription, TextKey.TitleGodspeedUnlockDescription,
    TextKey.TitleUndefeatedUnlockDescription, TextKey.TitleVeteranUnlockDescription,
    TextKey.TitleDivineEyeUnlockDescription, TextKey.TitlePillarUnlockDescription,
    TextKey.TitleOneStrikeUnlockDescription, TextKey.TitleGoldenReflexUnlockDescription,
    TextKey.TitleMasterUnlockDescription, TextKey.TitleConquerorUnlockDescription,
    TextKey.TitleWarGodUnlockDescription, TextKey.TitleSpeedKingUnlockDescription
)
