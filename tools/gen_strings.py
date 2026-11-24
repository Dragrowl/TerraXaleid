#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Regenerates the Android string resources for every supported display language.

The Simplified Chinese file is the source of truth. The Traditional Chinese (Taiwan / Hong
Kong) files are derived from it with the character table below plus a small wording table;
the Japanese and English files come from the dictionaries in this file. Run from the repo root:

    python tools/gen_strings.py

Keys missing from a translation fall back to the Simplified Chinese text, so a half-finished
translation never becomes an empty label.
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
BASE = RES / "values" / "strings.xml"

_MISSING_NAMES: set[str] = set()

NEW_SC = {
    "difficulty_classic": "普通",
    "difficulty_expert": "专家",
    "difficulty_master": "大师",
    "difficulty_legendary": "传奇",
    "difficulty_journey": "旅行",
    "size_small": "小世界",
    "size_medium": "中世界",
    "size_large": "大世界",
    "size_huge": "超大世界",
    "evil_corruption": "腐化",
    "evil_crimson": "猩红",
    "root_game_shizuku": "游戏目录 (Shizuku)",
    "root_shizuku_cache": "Shizuku 暂存",
    "root_game_files": "游戏目录 (files)",
    "root_game": "游戏目录",
    "language_title": "语言",
    "language_follow": "跟随系统",
    "language_sc": "简体中文",
    "language_tc": "繁體中文",
    "language_ja": "日本語",
    "language_en": "English",
    "run_reading_world": "读取世界 %1$s",
    "run_generating": "生成地图 %1$d×%2$d",
    "run_verifying": "校验地图载荷",
    "run_too_large": "世界过大，内存不足；请每角色单独处理",
    "run_writing": "写入角色 %1$s",
    "run_no_char_dir": "无法创建角色目录",
    "run_replace_failed": "无法替换旧地图：%1$s",
    "run_write_failed": "无法写入：%1$s",
    "run_push_failed": "已在镜像生成，但写回设备失败：%1$s（exit %2$d）",
    "run_size_mismatch": "（大小校验 %1$s ≠ %2$s）",
    "run_written_back": "已写回设备（原文件备份为 .map.bak）%1$s",
    "run_written_new": "已写回设备（新建地图）%1$s",
    "template_missing": "找不到存档目录",
    "template_label": "模板：%1$s",
    "template_from_character": "角色「%1$s」的地图",
    "template_from_donor": "同尺寸地图头（%1$s）",
    "template_unusable": "找不到可用的地图头：%1$s",
    "template_never_entered": "该世界还没有任何角色进入过。请先用任一角色进入一次世界并退出，让游戏生成地图缓存。",
}

# Simplified → Traditional character table, limited to the characters this app uses.
# Two-character groups: "简繁简繁…", whitespace between groups is ignored.
_TRANS_PAIRS = """
败敗 办辦 备備 闭閉 哔嗶 编編 变變 标標 别別 并並 仓倉 测測 处處 创創 带帶 单單 档檔
点點 动動 独獨 读讀 发發 访訪 复復 盖蓋 个個 给給 关關 观觀 过過 号號 后後 换換 会會
获獲 击擊 机機 继繼 检檢 简簡 渐漸 将將 结結 进進 经經 镜鏡 旧舊 开開 库庫 来來 里裡
链鏈 两兩 领領 录錄 没沒 难難 内內 启啟 浅淺 桥橋 请請 权權 确確 认認 删刪 设設 时時
随隨 态態 体體 统統 图圖 为為 问問 无無 务務 戏戲 项項 销銷 携攜 写寫 须須 续續 选選
验驗 样樣 页頁 应應 于於 与與 语語 运運 载載 择擇 张張 这這 装裝 状狀 准準 万萬 业業
丰豐 临臨 举舉 义義 乌烏 亚亞 产產 仅僅 从從 众眾 优優 伟偉 传傳 伤傷 价價 余餘 侧側
养養 兽獸 军軍 农農 决決 况況 冻凍 净淨 减減 几幾 凤鳳 凭憑 凯凱 刘劉 则則 刚剛 剧劇
劝勸 势勢 区區 医醫 华華 协協 卖賣 卫衛 历歷 压壓 县縣 参參 双雙 叙敘 虽雖 险險 阳陽
阴陰 阵陣 阶階 陆陸 陈陳 隐隱 难難 页頁 顺順 须須 预預 领領 颜顏 风風 飞飛 饭飯 饰飾
馆館 骏駿 骤驟 齐齊 龄齡 拨撥 拥擁 护護 报報 挂掛 较較 输輸 边邊 远遠 违違 适適 递遞
锁鎖 键鍵 阅閱 门門 问問 间間 队隊 际際 龙龍 兴興 学學 实實 宝寶 审審 导導 尽盡 层層
属屬 岁歲 师師 帐帳 广廣 庆慶 废廢 弃棄 弹彈 归歸 当當 录錄 彻徹 径徑 忆憶 忧憂 怀懷
态態 总總 恶惡 恼惱 悬懸 惊驚 惧懼 惨慘 愤憤 愿願 战戰 户戶 执執 扩擴 扬揚 扫掃 抢搶
护護 报報 担擔 拥擁 择擇 挂掛 挤擠 挥揮 损損 换換 据據 掷擲 揽攬 摄攝 摊攤 撑撐 撤撤
机機 杀殺 杂雜 权權 条條 来來 极極 构構 枪槍 标標 树樹 样樣 档檔 桥橋 检檢 楼樓 横橫
欢歡 欧歐 归歸 残殘 毁毀 气氣 汉漢 汤湯 沟溝 没沒 沪滬 泪淚 泼潑 泽澤 洁潔 洒灑 测測
浏瀏 浓濃 润潤 涨漲 渐漸 湾灣 满滿 滤濾 滨濱 滚滾 满滿 灯燈 灵靈 灾災 炼煉 烟煙 烦煩
热熱 烧燒 营營 爱愛 牵牽 犹猶 独獨 狱獄 猎獵 献獻 环環 现現 玛瑪 画畫 畅暢 疗療 症症
痒癢 皱皺 盘盤 着著 确確 码碼 础礎 硕碩 积積 称稱 稳穩 穷窮 窗窗 竞競 笔筆 筑築 签簽
简簡 算算 类類 粮糧 紧緊 红紅 约約 级級 纪紀 纯純 纸紙 线線 练練 组組 细細 织織 终終
经經 绑綁 绕繞 绘繪 给給 络絡 绝絕 统統 继繼 绩績 维維 缩縮 网網 罗羅 罚罰 义義 习習
翻翻 联聯 声聲 职職 听聽 肃肅 胜勝 脑腦 脸臉 腊臘 腾騰 舰艦 艺藝 节節 苍蒼 苏蘇 苹蘋
荐薦 药藥 获獲 营營 萧蕭 蓝藍 虑慮 虽雖 蚀蝕 蚁蟻 蛮蠻 补補 装裝 规規 视視 览覽 觉覺
触觸 计計 订訂 认認 讨討 让讓 训訓 议議 记記 讲講 许許 论論 设設 访訪 证證 评評 识識
诉訴 词詞 译譯 试試 诗詩 诚誠 话話 询詢 该該 详詳 误誤 说說 请請 诸諸 读讀 课課 谁誰
谈談 谋謀 谓謂 谜謎 谱譜 贝貝 负負 贡貢 财財 责責 贤賢 败敗 货貨 质質 贩販 贪貪 购購
贯貫 贱賤 贴貼 贵貴 买買 贷貸 费費 贺賀 贼賊 资資 赏賞 赔賠 赚賺 赛賽 赞讚 赠贈 赢贏
赵趙 趋趨 赶趕 车車 转轉 轮輪 软軟 轻輕 载載 较較 辅輔 辆輛 辈輩 辉輝 辑輯 输輸 辞辭
边邊 达達 迁遷 过過 运運 还還 进進 远遠 违違 连連 迟遲 适適 选選 递遞 逻邏 遗遺 邮郵
乡鄉 书書 买買 乱亂 争爭 于於 亏虧 云雲 亚亞 亩畝 亲親 亿億 仅僅 从從 仑侖 仓倉 仪儀
们們 价價 众眾 优優 会會 伞傘 伟偉 伤傷 伪偽 体體 余餘 佣傭 侠俠 侣侶 侥僥 侦偵 侧側
侨僑 侩儈 侪儕 侬儂 俣俁 俦儔 俨儼 俩倆 俪儷 俭儉 债債 倾傾 偬傯 偻僂 偾僨 偿償 傥儻
傧儐 储儲 傩儺 儿兒 兑兌 兖兗 党黨 兰蘭 关關 兴興 兹茲 养養 兽獸 冁囅 内內 冈岡 册冊
写寫 军軍 农農 冯馮 冲沖 决決 况況 冻凍 净淨 凄淒 凉涼 减減 凑湊 凛凜 凤鳳 凫鳧 凭憑
凯凱 击擊 凿鑿 刍芻 划劃 刘劉 则則 刚剛 创創 删刪 刽劊 刿劌 剀剴 剂劑 剐剮 剑劍 剥剝
剧劇 劝勸 办辦 务務 劢勱 动動 励勵 劲勁 劳勞 势勢 勋勳 勐猛 勚勩 匀勻 匦匭 匮匱 区區
医醫 华華 协協 单單 卖賣 卢盧 卤滷 卧臥 卫衛 却卻 厂廠 厅廳 历歷 厉厲 压壓 厌厭 厍厙
厕廁 厢廂 厣厴 厦廈 厨廚 厩廄 厮廝 县縣 参參 双雙 发發 变變 叙敘 叠疊 叶葉 号號 叹嘆
叽嘰 吁籲 后後 听聽 启啟 吴吳 呐吶 呒嘸 呓囈 呕嘔 呖嚦 呗唄 员員 呙咼 呛嗆 呜嗚 咏詠
咙嚨 咛嚀 咝噝 咤吒 咸鹹 响響 哑啞 哒噠 哓嘵 哔嗶 哕噦 哗嘩 哙噲 哜嚌 哝噥 哟喲 唛嘜
唠嘮 唡啢 唢嗩 唤喚 啧嘖 啬嗇 啭囀 啮嚙 啰囉 啴嘽 啸嘯 喷噴 喽嘍 喾嚳 嗫囁 嗳噯 嘘噓
嘤嚶 嘱囑 噜嚕 噼劈 嚣囂 团團 园園 囱囪 围圍 囵圇 国國 图圖 圆圓 圣聖 圹壙 场場 坂阪
坏壞 块塊 坚堅 坛壇 坝壩 坞塢 坟墳 坠墜 垄壟 垅壠 垆壚 垒壘 垦墾 垩堊 垫墊 垭埡 垯墶
垱壋 垲塏 垴堖 埘塒 埙塤 埚堝 埯垵 堑塹 堕墮 墙牆 壮壯 声聲 壳殼 壶壺 壸壼 处處 备備
复復 够夠 头頭 夸誇 夹夾 夺奪 奁奩 奂奐 奋奮 奖獎 奥奧 妆妝 妇婦 妈媽 妩嫵 妪嫗 妫媯
姗姍 姜薑 娄婁 娅婭 娆嬈 娇嬌 娈孌 娱娛 娲媧 娴嫻 婳嫿 婴嬰 婵嬋 婶嬸 嫒嬡 嫔嬪 嫱嬙
嬷嬤 孙孫 学學 孪孿 宁寧 宝寶 实實 宠寵 审審 宪憲 宫宮 宽寬 宾賓 寝寢 对對 寻尋 导導
寿壽 将將 尔爾 尘塵 尝嘗 尧堯 尴尷 尸屍 尽盡 层層 屃屓 屉屜 届屆 属屬 屡屢 屦屨 屿嶼
岁歲 岂豈 岖嶇 岗崗 岘峴 岚嵐 岛島 岭嶺 岽崬 岿巋 峃嶨 峄嶧 峡峽 峣嶢 峤嶠 峥崢 峦巒
崂嶗 崃崍 崄嶮 崭嶄 嵘嶸 嵚嶔 嵛崳 嵝嶁 巅巔 巩鞏 巯巰 币幣 帅帥 师師 帏幃 帐帳 帘簾
帜幟 带帶 帧幀 帮幫 帱幬 帻幘 帼幗 幂冪 幞襆 干幹 并並 广廣 庄莊 庆慶 庐廬 庑廡 库庫
应應 庙廟 庞龐 废廢 庼廎 廪廩 开開 异異 弃棄 张張 弥彌 弦弦 弯彎 弹彈 强強 归歸 当當
录錄 彦彥 彻徹 径徑 徕徠 忆憶 忏懺 忧憂 忾愾 怀懷 态態 怂慫 怃憮 怄慪 怅悵 怆愴 怜憐
总總 怼懟 怿懌 恋戀 恒恆 恳懇 恶惡 恸慟 恹懨 恺愷 恻惻 恼惱 恽惲 悦悅 悫愨 悬懸 悭慳
悯憫 惊驚 惧懼 惨慘 惩懲 惫憊 惬愜 惭慚 惮憚 惯慣 愠慍 愤憤 愦憒 愿願 慑懾 懑懣 懒懶
懔懍 戆戇 戋戔 戏戲 戗戧 战戰 戬戩 户戶 扑撲 执執 扩擴 扪捫 扫掃 扬揚 扰擾 抚撫 抛拋
抟摶 抠摳 抡掄 抢搶 护護 报報 担擔 拟擬 拢攏 拣揀 拥擁 拦攔 拧擰 拨撥 择擇 挂掛 挚摯
挛攣 挜掗 挝撾 挞撻 挟挾 挠撓 挡擋 挢撟 挣掙 挤擠 挥揮 挦撏 捞撈 损損 捡撿 换換 捣搗
据據 掳擄 掴摑 掷擲 掸撣 掺摻 掼摜 揽攬 揿撳 搀攙 搁擱 搂摟 搅攪 携攜 摄攝 摅攄 摆擺
摇搖 摈擯 摊攤 撄攖 撑撐 撵攆 撷擷 撸摝 撺攛 擞擻 攒攢 敌敵 敛斂 数數 斋齋 斓斕 斗鬥
斩斬 断斷 无無 旧舊 时時 旷曠 昙曇 昼晝 显顯 晋晉 晒曬 晓曉 晔曄 晕暈 晖暉 暂暫 暧曖
术術 朴樸 机機 杀殺 杂雜 权權 条條 来來 杨楊 杩榪 杰傑 极極 构構 枞樅 枢樞 枣棗 枥櫪
枧梘 枨棖 枪槍 枫楓 枭梟 柜櫃 柠檸 柽檉 栀梔 栅柵 标標 栈棧 栉櫛 栊櫳 栋棟 栌櫨 栎櫟
栏欄 树樹 栖棲 样樣 栾欒 桠椏 桡橈 桢楨 档檔 桤榿 桥橋 桦樺 桧檜 桨槳 桩樁 梦夢 梼檮
梾棶 梿槤 检檢 棁梲 棂欞 椁槨 椟櫝 椠槧 椤欏 椭橢 楼樓 榄欖 榅榲 榇櫬 榈櫚 榉櫸 槚檟
槛檻 槟檳 槠櫧 横橫 樯檣 樱櫻 橥櫫 橱櫥 橹櫓 橼櫞 檩檁 欢歡 欤歟 欧歐 歼殲 殁歿 殇殤
残殘 殒殞 殓殮 殚殫 殡殯 殴毆 毁毀 毂轂 毕畢 毙斃 毡氈 毵毿 气氣 氢氫 氩氬 氲氳 汇匯
汉漢 汤湯 汹洶 沟溝 没沒 沣灃 沤漚 沥瀝 沦淪 沧滄 沨渢 沩潙 沪滬 泞濘 泪淚 泶澩 泷瀧
泸瀘 泺濼 泻瀉 泼潑 泽澤 泾涇 洁潔 洒灑 洼窪 浃浹 浅淺 浆漿 浇澆 浈湞 浊濁 测測 浍澮
济濟 浏瀏 浐滻 浑渾 浒滸 浓濃 浔潯 浕濜 涂塗 涌湧 涛濤 涝澇 涞淶 涟漣 涠潿 涡渦 涣渙
涤滌 润潤 涧澗 涨漲 涩澀 淀澱 渊淵 渌淥 渍漬 渎瀆 渐漸 渑澠 渔漁 渖瀋 渗滲 温溫 湾灣
湿濕 溃潰 溅濺 溆漵 溇漊 满滿 滗潷 滤濾 滥濫 滦灤 滨濱 滩灘 滪澦 漤濫 潆瀠 潇瀟 潋瀲
潍濰 潜潛 潴瀦 澜瀾 濑瀨 濒瀕 灏灝 灭滅 灯燈 灵靈 灾災 灿燦 炀煬 炉爐 炖燉 炜煒 炝熗
点點 炼煉 炽熾 烁爍 烂爛 烃烴 烛燭 烦煩 烧燒 烨燁 烩燴 烫燙 烬燼 热熱 焕煥 焖燜 焘燾
煅煆 煳糊 熘溜 爱愛 爷爺 牍牘 牦犛 牵牽 牺犧 犊犢 状狀 犷獷 犸獁 犹猶 狈狽 狍麅 狝獮
狞獰 独獨 狭狹 狮獅 狯獪 狰猙 狱獄 狲猻 猃獫 猎獵 猕獼 猡玀 猪豬 猫貓 猬蝟 献獻 獭獺
玑璣 玛瑪 玮瑋 环環 现現 玱瑲 玺璽 珐琺 珑瓏 珰璫 珲琿 琏璉 琐瑣 琼瓊 瑶瑤 瑷璦 璎瓔
瓒瓚 瓯甌 电電 画畫 畅暢 畴疇 疖癤 疗療 疟瘧 疠癘 疡瘍 疬癧 疭瘲 疮瘡 疯瘋 疱皰 疴痾
痈癰 痉痙 痒癢 痖瘂 痨癆 痪瘓 痫癇 瘅癉 瘆瘮 瘗瘞 瘪癟 瘫癱 瘾癮 瘿癭 癞癩 癣癬 癫癲
皑皚 皱皺 皲皸 盏盞 盐鹽 监監 盖蓋 盗盜 盘盤 眍瞘 眦眥 眬矓 睁睜 睐睞 睑瞼 瞒瞞 瞩矚
矫矯 矶磯 矾礬 矿礦 砀碭 码碼 砖磚 砗硨 砚硯 砜碸 砺礪 砻礱 砾礫 础礎 硁硜 硕碩 硖硤
硗磽 硙磑 硚礄 确確 硷鹼 碍礙 碛磧 碜磣 碱鹼 礼禮 祎禕 祢禰 祯禎 祷禱 祸禍 禀稟 禄祿
禅禪 离離 秃禿 秆稈 种種 积積 称稱 秽穢 稆穭 税稅 稣穌 稳穩 穑穡 穷窮 窃竊 窍竅 窑窯
窜竄 窝窩 窥窺 窦竇 竖豎 竞競 笃篤 笋筍 笔筆 笕筧 笺箋 笼籠 笾籩 筑築 筚篳 筛篩 筜簹
筝箏 筹籌 签簽 简簡 箓籙 箦簀 箧篋 箨籜 箩籮 箪簞 箫簫 篑簣 篓簍 篮籃 篱籬 簖籪 籁籟
籴糴 类類 籼秈 粜糶 粝糲 粤粵 粪糞 粮糧 糁糝 糇餱 紧緊 絷縶 纟糹 纠糾 纡紆 红紅 纣紂
纤纖 纥紇 约約 级級 纨紈 纩纊 纪紀 纫紉 纬緯 纭紜 纯純 纰紕 纱紗 纲綱 纳納 纴紝 纵縱
纶綸 纷紛 纸紙 纹紋 纺紡 纻紵 纽紐 纾紓 线線 绀紺 绁紲 绂紱 练練 组組 绅紳 细細 织織
终終 绉縐 绊絆 绋紼 绌絀 绍紹 绎繹 经經 绐紿 绑綁 绒絨 结結 绔絝 绕繞 绖絰 绗絎 绘繪
给給 绚絢 绛絳 络絡 绝絕 绞絞 统統 绠綆 绡綃 绢絹 绣繡 绤綌 绥綏 绦絛 继繼 绨綈 绩績
绪緒 绫綾 续續 绮綺 绯緋 绰綽 绱緔 绲緄 绳繩 维維 绵綿 绶綬 绷繃 绸綢 绹綯 绺綹 绻綣
综綜 绽綻 绾綰 绿綠 缀綴 缁緇 缂緙 缃緗 缄緘 缅緬 缆纜 缇緹 缈緲 缉緝 缋繢 缌緦 缍綞
缎緞 缏緶 缑緱 缒緙 缓緩 缔締 缕縷 编編 缗緡 缘緣 缙縉 缚縛 缛縟 缜縝 缝縫 缟縞 缠纏
缡縭 缢縊 缣縑 缤繽 缥縹 缦縵 缧縲 缨纓 缩縮 缪繆 缫繅 缬纈 缭繚 缮繕 缯繒 缰韁 缱繾
缲繰 缳繯 缴繳 缵纘 罂罌 网網 罗羅 罚罰 罢罷 罴羆 羁羈 羟羥 羡羨 翘翹 耢耮 耧耬 耸聳
耻恥 聂聶 聋聾 职職 聍聹 联聯 聩聵 聪聰 肃肅 肠腸 肤膚 肮骯 肴餚 肾腎 肿腫 胀脹 胁脅
胆膽 胜勝 胧朧 胨腖 胪臚 胫脛 胶膠 脉脈 脍膾 脏臟 脐臍 脑腦 脓膿 脔臠 脚腳 脱脫 脶腡
脸臉 腊臘 腘膕 腭齶 腻膩 腼靦 腽膃 腾騰 膑臏 臜臢 舆輿 舣艤 舰艦 舱艙 舻艫 艰艱 艳艷
艺藝 节節 芈羋 芗薌 芜蕪 苁蓯 苇葦 苈藶 苋莧 苌萇 苍蒼 苎苧 苏蘇 苘檾 苹蘋 茎莖 茏蘢
茑蔦 茔塋 茕煢 茧繭 荆荊 荐薦 荙薘 荚莢 荛蕘 荜蓽 荞蕎 荟薈 荠薺 荡蕩 荣榮 荤葷 荥滎
荦犖 荧熒 荨蕁 荩藎 荪蓀 荫蔭 荬蕒 荭葒 荮縐 药藥 莅蒞 莱萊 莲蓮 莳蒔 莴萵 莶薟 获獲
莸蕕 莹瑩 莺鶯 莼蒓 萚蘀 萝蘿 萤螢 营營 萦縈 萧蕭 萨薩 葱蔥 蒇蕆 蒉蕢 蒋蔣 蒌蔞 蓝藍
蓟薊 蓠蘺 蓣蕷 蓥鎣 蓦驀 蔷薔 蔹蘞 蔺藺 蔼藹 蕲蘄 薮藪 藓蘚 蘖櫱 虏虜 虑慮 虚虛 虫蟲
虬虯 虮蟣 虽雖 虾蝦 虿蠆 蚀蝕 蚁蟻 蚂螞 蚕蠶 蛊蠱 蛎蠣 蛏蟶 蛮蠻 蛰蟄 蛱蛺 蛲蟯 蛳螄
蛴蠐 蜕蛻 蜗蝸 蜡蠟 蝇蠅 蝈蟈 蝉蟬 蝎蠍 蝼螻 蝾蠑 螨蟎 蟏蠨 衅釁 衔銜 补補 衬襯 衮袞
袄襖 袅裊 袆褘 袜襪 袭襲 袯襏 装裝 裆襠 裢褳 裣襝 裤褲 裥襇 褛褸 褴襤 见見 观觀 觃覎
规規 觅覓 视視 觇覘 览覽 觉覺 觊覬 觋覡 觌覿 觎覦 觏覯 觐覲 觑覷 觞觴 触觸 觯觶 詟讋
誉譽 誊謄 讠訁 计計 订訂 讣訃 认認 讥譏 讦訐 讧訌 讨討 让讓 讪訕 讫訖 讬託 训訓 议議
讯訊 记記 讱訒 讲講 讳諱 讴謳 讵詎 讶訝 讷訥 许許 讹訛 论論 讻訩 讼訟 讽諷 设設 访訪
诀訣 证證 诂詁 诃訶 评評 诅詛 识識 诇詗 诈詐 诉訴 诊診 诋詆 诌謅 词詞 诎詘 诏詔 译譯
诒詒 诓誆 诔誄 试試 诖詿 诗詩 诘詰 诙詼 诚誠 诛誅 诜詵 话話 诞誕 诟詬 诠詮 诡詭 询詢
诣詣 诤諍 该該 详詳 诧詫 诨諢 诩詡 诪譸 诫誡 诬誣 语語 诮誚 误誤 诰誥 诱誘 诲誨 诳誑
说說 诵誦 诶誒 请請 诸諸 诹諏 诺諾 读讀 诼諑 诽誹 课課 诿諉 谀諛 谁誰 谂諗 调調 谄諂
谅諒 谆諄 谇誶 谈談 谊誼 谋謀 谌諶 谍諜 谎謊 谏諫 谐諧 谑謔 谒謁 谓謂 谔諤 谕諭 谖諼
谗讒 谘諮 谙諳 谚諺 谛諦 谜謎 谝諞 谟謨 谠讜 谡謖 谢謝 谣謠 谤謗 谥謚 谦謙 谧謐 谨謹
谩謾 谪謫 谫譾 谬謬 谭譚 谮譖 谯譙 谰讕 谱譜 谲譎 谳讞 谴譴 谵譫 谶讖 谷穀 豮豶 贝貝
贞貞 负負 贠貟 贡貢 财財 责責 贤賢 败敗 账賬 货貨 质質 贩販 贪貪 贫貧 贬貶 购購 贮貯
贯貫 贰貳 贱賤 贲賁 贳貰 贴貼 贵貴 贶貺 贷貸 贸貿 费費 贺賀 贻貽 贼賊 贽贄 贾賈 贿賄
赀貲 赁賃 赂賂 赃贓 资資 赅賅 赆贐 赇賕 赈賑 赉賚 赊賒 赋賦 赌賭 赍齎 赎贖 赏賞 赐賜
赑贔 赒賙 赓賡 赔賠 赕賧 赖賴 赗賵 赘贅 赙賻 赚賺 赛賽 赜賾 赝贗 赞讚 赟贇 赠贈 赡贍
赢贏 赣贛 赵趙 赶趕 趋趨 趱趲 趸躉 跃躍 跄蹌 跖蹠 跞躒 践踐 跶躂 跷蹺 跸蹕 跹躚 跻躋
踊踴 踌躊 踪蹤 踬躓 踯躑 蹑躡 蹒蹣 蹰躕 蹿躥 躏躪 躜躦 躯軀 车車 轧軋 轨軌 轩軒 轪軑
轫軔 转轉 轭軛 轮輪 软軟 轰轟 轱軲 轲軻 轳轤 轴軸 轵軹 轶軼 轷軤 轸軫 轹轢 轺軺 轻輕
轼軾 载載 轾輊 轿轎 辀輈 辁輇 辂輅 较較 辄輒 辅輔 辆輛 辇輦 辈輩 辉輝 辊輥 辋輞 辌輬
辍輟 辎輜 辏輳 辐輻 辑輯 辒轀 输輸 辔轡 辕轅 辖轄 辗輾 辘轆 辙轍 辚轔 辞辭 辩辯 辫辮
边邊 辽遼 达達 迁遷 过過 迈邁 运運 还還 这這 进進 远遠 违違 连連 迟遲 迩邇 迳逕 迹跡
适適 选選 逊遜 递遞 逦邐 逻邏 遗遺 遥遙 邓鄧 邝鄺 邬鄔 邮郵 邹鄒 邺鄴 邻鄰 郁鬱 郏郟
郐鄶 郑鄭 郓鄆 郦酈 郧鄖 郸鄲 酝醞 酱醬 酽釅 酾釃 酿釀 释釋 里裡 鉴鑒 銮鑾 錾鏨 鍪鏊
钅釒 钆釓 钇釔 针針 钉釘 钊釗 钋釙 钌釕 钍釷 钎釺 钏釧 钐釤 钑鈒 钒釩 钓釣 钔鍆 钕釹
钖鍚 钗釵 钘鈃 钙鈣 钚鈈 钛鈦 钜鉅 钝鈍 钞鈔 钟鐘 钠鈉 钡鋇 钢鋼 钣鈑 钤鈐 钥鑰 钦欽
钧鈞 钨鎢 钩鉤 钪鈧 钫鈁 钬鈥 钭鈄 钮鈕 钯鈀 钰鈺 钱錢 钲鉦 钳鉗 钴鈷 钵缽 钶鈳 钷鉕
钸鈽 钹鈸 钺鉞 钻鑽 钼鉬 钽鉭 钾鉀 钿鈿 铀鈾 铁鐵 铂鉑 铃鈴 铄鑠 铅鉛 铆鉚 铈鈰 铉鉉
铊鉈 铋鉍 铌鈮 铍鈹 铎鐸 铏鉶 铐銬 铑銠 铒鉺 铓鋩 铔錏 铕銪 铖鋮 铗鋏 铘鋣 铙鐃 铚銍
铛鐺 铜銅 铝鋁 铞銱 铟銦 铠鎧 铡鍘 铢銖 铣銑 铤鋌 铥銩 铦銛 铧鏵 铨銓 铩鎩 铪鉿 铫銚
铬鉻 铭銘 铮錚 铯銫 铰鉸 铱銥 铲鏟 铳銃 铴鐋 铵銨 银銀 铷銣 铸鑄 铹鐒 铺鋪 铻鋙 铼錸
铽鋱 链鏈 铿鏗 销銷 锁鎖 锂鋰 锃鋥 锄鋤 锅鍋 锆鋯 锇鋨 锈鏽 锉銼 锊鋝 锋鋒 锌鋅 锍鋶
锎鐦 锏鐧 锐銳 锑銻 锒鋃 锓鋟 锔鋦 锕錒 锖錆 锗鍺 错錯 锚錨 锛錛 锜錡 锝鍀 锞錁 锟錕
锠錩 锡錫 锢錮 锣鑼 锤錘 锥錐 锦錦 锧鑕 锨鍁 锩錈 锪鍃 锫錇 锬錟 锭錠 键鍵 锯鋸 锰錳
锱錙 锲鍥 锳鍈 锴鍇 锵鏘 锶鍶 锷鍔 锸鍤 锹鍬 锺鍾 锻鍛 锼鎪 锽鍠 锾鍰 锿鎄 镀鍍 镁鎂
镂鏤 镃鎡 镄鐨 镅鎇 镆鏌 镇鎮 镈鎛 镉鎘 镊鑷 镋鎲 镌鐫 镍鎳 镎鎿 镏鎦 镐鎬 镑鎊 镒鎰
镓鎵 镔鑌 镕鎔 镖鏢 镗鏜 镘鏝 镙鏍 镚鏰 镛鏞 镜鏡 镝鏑 镞鏃 镟鏇 镠鏐 镡鐔 镢镢 镣鐐
镤鏷 镥鑥 镦鐓 镧鑭 镨鐠 镩鑹 镪鏹 镫鐙 镬鑊 镭鐳 镮鐶 镯鐲 镰鐮 镱鐿 镲鑔 镳鑣 镴鑞
镵鑱 镶鑲 长長 门門 闩閂 闪閃 闫閆 闭閉 问問 闯闖 闰閏 闱闈 闲閒 闳閎 间間 闵閔 闶閌
闷悶 闸閘 闹鬧 闺閨 闻聞 闼闥 闽閩 闾閭 闿闓 阀閥 阁閣 阂閡 阃閫 阄鬮 阅閱 阆閬 阇闍
阈閾 阉閹 阊閶 阋鬩 阌閿 阍閽 阎閻 阏閼 阐闡 阑闌 阒闃 阔闊 阕闋 阖闔 阗闐 阙闕 阚闞
队隊 阳陽 阴陰 阵陣 阶階 际際 陆陸 陇隴 陈陳 陉陘 陕陝 陧隉 陨隕 险險 随隨 隐隱 隶隸
隽雋 难難 雏雛 雠讎 雳靂 雾霧 霁霽 霉黴 霭靄 靓靚 静靜 靥靨 鞑韃 鞒鞽 鞯韉 韦韋 韧韌
韨韍 韩韓 韪韙 韫韞 韬韜 韵韻 页頁 顶頂 顷頃 顸頇 项項 顺順 须須 顼頊 顽頑 顾顧 顿頓
颀頎 颁頒 颂頌 颃頏 预預 颅顱 领領 颇頗 颈頸 颉頡 颊頰 颋頲 颌頜 颍潁 颎熲 颏頦 颐頤
频頻 颒頮 颓頹 颔頷 颕頴 颖穎 颗顆 题題 颙顒 颚顎 颛顓 颜顏 额額 颞顳 颟顢 颠顛 颡顙
颢顥 颤顫 颥顬 颦顰 颧顴 风風 飏颺 飐颮 飑颮 飒颯 飓颶 飔颸 飕颼 飖颻 飗飀 飘飄 飙飆
飚飈 飞飛 飨饗 餍饜 饣飠 饤飣 饥飢 饦飥 饧餳 饨飩 饩餼 饪飪 饫飫 饬飭 饭飯 饮飲 饯餞
饰飾 饱飽 饲飼 饳飿 饴飴 饵餌 饶饒 饷餉 饸餄 饹餎 饺餃 饻餏 饼餅 饽餑 饾餖 饿餓 馀餘
馁餒 馂餕 馃餜 馄餛 馅餡 馆館 馇餷 馈饋 馉餶 馊餿 馋饞 馌饁 馍饃 馎餺 馏餾 馐饈 馑饉
馒饅 馓饊 馔饌 馕饢 马馬 驭馭 驮馱 驯馴 驰馳 驱驅 驲馹 驳駁 驴驢 驵駔 驶駛 驷駟 驸駙
驹駒 驺騶 驻駐 驼駝 驽駑 驾駕 驿驛 骀駘 骁驍 骂罵 骃駰 骄驕 骅驊 骆駱 骇駭 骈駢 骉驫
骊驪 骋騁 验驗 骍騂 骎駸 骏駿 骐騏 骑騎 骒騍 骓騅 骔騌 骕驌 骖驂 骗騙 骘騭 骙騤 骚騷
骛騖 骜驁 骝騮 骞騫 骟騸 骠驃 骡騾 骢驄 骣驏 骤驟 骥驥 骧驤 髅髏 髋髖 髌髕 鬓鬢 魇魘
魉魎 鱼魚 鱽魛 鱾魢 鱿魷 鲀魨 鲁魯 鲂魴 鲃䰾 鲄魺 鲅鮁 鲆鮃 鲇鯰 鲈鱸 鲉鮋 鲊鮓 鲋鮒
鲌鮊 鲍鮑 鲎鱟 鲏鮍 鲐鮐 鲑鮭 鲒鮚 鲓鮳 鲔鮪 鲕鮞 鲖鮦 鲗鰂 鲘鮜 鲙鱠 鲚鱭 鲛鮫 鲜鮮
鲝鮺 鲞鯗 鲟鱘 鲠鯁 鲡鱺 鲢鰱 鲣鰹 鲤鯉 鲥鰣 鲦鰷 鲧鯀 鲨鯊 鲩鯇 鲪鮶 鲫鯽 鲬鯒 鲭鯖
鲮鯪 鲯鯕 鲰鯫 鲱鯡 鲲鯤 鲳鯧 鲴鯝 鲵鯢 鲶鯰 鲷鯛 鲸鯨 鲹鰺 鲺鯴 鲻鯔 鲼鱝 鲽鰈 鲾鰏
鲿鱨 鳀鯷 鳁鰮 鳂鰃 鳃鰓 鳄鱷 鳅鰍 鳆鰒 鳇鰉 鳈鰁 鳉鱂 鳊鯿 鳋鰠 鳌鰲 鳍鰭 鳎鰨 鳏鰥
鳐鰩 鳑鰟 鳒鰜 鳓鰳 鳔鰾 鳕鱈 鳖鱉 鳗鰻 鳘鰵 鳙鱅 鳚䲁 鳛鰼 鳜鱖 鳝鱔 鳞鱗 鳟鱒 鳠鱯
鳡鱤 鳢鱧 鳣鱣 鸟鳥 鸠鳩 鸡雞 鸢鳶 鸣鳴 鸥鷗 鸦鴉 鸨鴇 鸩鴆 鸪鴣 鸫鶇 鸬鸕 鸭鴨 鸮鴞
鸯鴦 鸰鴒 鸱鴟 鸲鴝 鸳鴛 鸴鷽 鸵鴕 鸶鷥 鸷鷙 鸸鴯 鸹鴰 鸺鵂 鸻鴴 鸼鵃 鸽鴿 鸾鸞 鸿鴻
鹀鵐 鹁鵓 鹂鸝 鹃鵑 鹄鵠 鹅鵝 鹆鵒 鹇鷳 鹈鵜 鹉鵡 鹊鵲 鹋鶓 鹌鵪 鹍鵾 鹎鵯 鹏鵬 鹐鵮
鹑鶉 鹒鶊 鹓鵷 鹔鷫 鹕鶘 鹖鶡 鹗鶚 鹘鶻 鹙鶖 鹚鶿 鹛鶥 鹜鶩 鹝鷊 鹞鷂 鹟鶲 鹠鶹 鹡鶺
鹢鷁 鹣鶼 鹤鶴 鹥鷖 鹦鸚 鹧鷓 鹨鷚 鹩鷯 鹪鷦 鹫鷲 鹬鷸 鹭鷺 鹮䴉 鹯鸇 鹰鷹 鹱鸌 鹲鸏
鹳鸛 鹴鸘 鹾鹺 麦麥 麸麩 黄黃 黉黌 黡黶 黩黷 黪黲 黾黽 鼋黿 鼍鼉 鼗鞀 鼹鼴 齄齇 齑齏
齑齏 龀齔 龃齟 龄齡 龅齙 龆齠 龇齜 龈齦 龉齬 龊齪 龋齲 龌齷 龙龍 龚龔 龛龕 龟龜
"""

_TRANS = {}
for _group in _TRANS_PAIRS.split():
    if len(_group) == 2:
        _TRANS[_group[0]] = _group[1]
    elif len(_group) % 2 == 0:
        for _i in range(0, len(_group), 2):
            _TRANS[_group[_i]] = _group[_i + 1]

TW_WORDS = {
    "文件": "檔案",
    "目录": "目錄",
    "保存位置": "儲存位置",
    "默认": "預設",
    "设置": "設定",
    "搜索": "搜尋",
    "信息": "資訊",
}
HK_WORDS_UNUSED = {
    "文件": "檔案",
    "目录": "資料夾",
    "默认": "預設",
    "设置": "設定",
    "搜索": "搜尋",
    "信息": "資訊",
    "网络": "網絡",
}

# Keys whose text is a language name and therefore must not be converted.
NO_CONVERT = {"language_sc", "language_tc", "language_ja", "language_en"}

EN = {
    "app_name": "terramap",
    "screen_title": "Terraria maps",
    "perm_title": "All-files access is required",
    "perm_body": "This tool only touches save files: it reads Worlds/*.wld and rewrites Players/<character>/<world>.map.\nSince Android 11, reaching the game's Android/data folder requires the \"All files access\" permission.",
    "perm_button": "Grant access",
    "perm_granted": "Access granted",
    "perm_check_again": "I granted it, check again",
    "menu_more": "More",
    "menu_settings": "Settings",
    "menu_restore": "Backups and restore",
    "menu_refresh": "Refresh",
    "settings_title": "Settings",
    "action_back": "Back",
    "theme_title": "Appearance",
    "theme_subtitle": "Pick a colour scheme; the switch is immediate and cross-fades.",
    "theme_auto": "System",
    "theme_light": "Light",
    "theme_dark": "Dark",
    "dark_variant_standard": "Standard dark",
    "dark_variant_oled": "OLED black",
    "about_title": "About",
    "about_version": "Version %1$s",
    "about_publisher": "Published by Dragr0wl",
    "about_license": "GPL-3.0-or-later",
    "about_github": "GitHub",
    "about_bilibili": "Bilibili",
    "about_link_failed": "No app can open this link",
    "behavior_title": "Behaviour",
    "behavior_exit_title": "End the process when leaving for the home screen",
    "worlds_title": "Choose a world",
    "worlds_hint": "Save location",
    "worlds_empty": "No .wld saves in this location",
    "worlds_search_hint": "Search world names",
    "worlds_search_none": "No matching worlds",
    "worlds_empty_no_root": "No save folder found.\n\nSince Android 11 a normal app cannot list another app's Android/data folder - not even with the \"All files access\" permission. Three workarounds:\n\n1. Copy the saves to /sdcard/Download/TerrariaSaves/ (Worlds and Players subfolders) and this page lists them directly;\n2. Use reveal.py + deploy.py from the PC repository to write into the game folder over adb;\n3. A rooted device can use the su bridge (later version).",
    "worlds_reload": "Refresh",
    "worlds_select_all": "Select all",
    "worlds_select_none": "Select none",
    "worlds_issues": "%1$d files could not be read",
    "action_reveal": "Reveal selected",
    "chars_title": "Choose a character",
    "chars_hint": "Maps are stored per world and character; everything is revealed by default.",
    "chars_confirm": "Reveal %1$d characters",
    "chars_new_map": "Will be created",
    "run_title": "Revealing",
    "done_title": "Done",
    "done_undo": "Undo this run",
    "done_close": "Close",
    "status_success": "Success",
    "status_skipped": "Skipped",
    "status_failed": "Failed",
    "label_prehard_short": "Pre-hardmode",
    "label_hardmode_short": "Hardmode",
    "label_journeys_end": "Moon Lord defeated",
    "close_game_warning": "Quit the world and close the game before changing anything; the game writes its own map back on exit and overwrites this tool's work.",
    "back": "Back",
    "about_check": "Core self-test",
    "about_check_ok": "Core self-test passed",
    "about_check_fail": "Core self-test failed",
    "shizuku_title": "Read the game folder with Shizuku",
    "shizuku_body": "Since Android 11 an app cannot list another app's Android/data folder, not even with all-files access. With Shizuku installed and running, granting it here lets terramap read the saves and write them back directly, with no copying to a portable folder.",
    "shizuku_state_running": "Shizuku is running; you can grant access.",
    "shizuku_state_absent": "No Shizuku service detected: install Shizuku and start it over adb or root first.",
    "shizuku_button": "Grant Shizuku",
    "shizuku_banner_title": "The game folder can be read directly",
    "restore_title": "Backups and restore",
    "restore_hint": "Every map gets a backup before it is overwritten (.map.bak, older ones numbered .bak.1, .bak.2). Select several to restore, delete only the backups, or delete a map so the game regenerates it fully unexplored.",
    "restore_chip_backups": "%1$d backups",
    "restore_newest": "Newest backup %1$s · %2$s",
    "restore_selected": "%1$d selected",
    "restore_action": "Restore selected backups",
    "restore_delete_backups": "Delete selected backups",
    "restore_delete_map": "Delete map (all black)",
    "restore_empty": "No map files in this location",
    "restore_running": "Restoring backups",
    "restore_deleting": "Deleting",
    "restore_result": "Restore result",
    "restore_result_delete": "Delete result",
    "overwrite_title": "Overwrite again?",
    "overwrite_body": "These characters already have a backup from last time: %1$s",
    "overwrite_checkbox": "Delete the previous backup",
    "overwrite_warning": "If checked, the previous backup is replaced by the current pre-overwrite state and cannot be recovered.",
    "overwrite_continue": "Overwrite anyway",
    "cancel": "Cancel",
    "close": "Close",
    "restore_delete_map_title": "Delete the map?",
    "restore_delete_map_body": "This deletes %1$d selected map files; the game regenerates them fully unexplored. It cannot be undone (map files are not backed up).",
    "difficulty_classic": "Classic",
    "difficulty_expert": "Expert",
    "difficulty_master": "Master",
    "difficulty_legendary": "Legendary",
    "difficulty_journey": "Journey",
    "size_small": "Small",
    "size_medium": "Medium",
    "size_large": "Large",
    "size_huge": "Huge",
    "evil_corruption": "Corruption",
    "evil_crimson": "Crimson",
    "root_game_shizuku": "Game folder (Shizuku)",
    "root_shizuku_cache": "Shizuku cache",
    "root_game_files": "Game folder (files)",
    "root_game": "Game folder",
    "language_title": "Language",
    "language_follow": "System",
    "language_sc": "简体中文",
    "language_tc": "繁體中文",
    "language_ja": "日本語",
    "language_en": "English",
    "run_reading_world": "Reading world %1$s",
    "run_generating": "Building map %1$d×%2$d",
    "run_verifying": "Verifying map payload",
    "run_too_large": "World too large: out of memory. Handle one character at a time.",
    "run_writing": "Writing character %1$s",
    "run_no_char_dir": "Could not create the character folder",
    "run_replace_failed": "Could not replace the old map: %1$s",
    "run_write_failed": "Could not write: %1$s",
    "run_push_failed": "Built in the mirror but writing back failed: %1$s (exit %2$d)",
    "run_size_mismatch": " (size check %1$s ≠ %2$s)",
    "run_written_back": "Written back to the device (original kept as .map.bak)%1$s",
    "run_written_new": "Written back to the device (new map)%1$s",
    "template_missing": "Save folder not found",
    "template_label": "Template: %1$s",
    "template_from_character": "Map of character \"%1$s\"",
    "template_from_donor": "Map header of the same size (%1$s)",
    "template_unusable": "No usable map header: %1$s",
    "template_never_entered": "No character has entered this world yet. Enter it once with any character and quit so the game writes its map cache.",
    "worlds_search_hint": "搜索世界名称",
    "worlds_search_none": "没有匹配的世界",
}

JA = {
    "app_name": "terramap",
    "screen_title": "Terraria マップ",
    "perm_title": "「すべてのファイルへのアクセス」が必要です",
    "perm_body": "このツールはセーブデータのみを扱います。Worlds/*.wld を読み、Players/キャラ/<世界>.map を書き換えます。\nAndroid 11 以降、ゲームの Android/data にアクセスするには「すべてのファイルへのアクセス」権限が必要です。",
    "perm_button": "権限を許可",
    "perm_granted": "アクセス権を取得しました",
    "perm_check_again": "許可したので再確認",
    "menu_more": "その他",
    "menu_settings": "設定",
    "menu_restore": "バックアップと復元",
    "menu_refresh": "更新",
    "settings_title": "設定",
    "action_back": "戻る",
    "theme_title": "外観",
    "theme_subtitle": "配色を選択。切り替えは即時反映されフェードします。",
    "theme_auto": "システムに従う",
    "theme_light": "ライト",
    "theme_dark": "ダーク",
    "dark_variant_standard": "標準ダーク",
    "dark_variant_oled": "OLED ブラック",
    "about_title": "このアプリについて",
    "about_version": "バージョン %1$s",
    "about_publisher": "公開者 Dragr0wl",
    "about_license": "GPL-3.0-or-later",
    "about_github": "GitHub",
    "about_bilibili": "ビリビリ",
    "about_link_failed": "このリンクを開けるアプリがありません",
    "behavior_title": "動作",
    "behavior_exit_title": "ホームに戻ったらプロセスを終了",
    "worlds_title": "ワールドを選択",
    "worlds_hint": "保存場所",
    "worlds_empty": "この場所に .wld のセーブがありません",
    "worlds_search_hint": "ワールド名を検索",
    "worlds_search_none": "一致するワールドがありません",
    "worlds_empty_no_root": "セーブフォルダが見つかりません。\n\nAndroid 11 以降、一般アプリは他のアプリの Android/data を一覧できません（「すべてのファイルへのアクセス」でも不可）。回避策は 3 つあります。\n\n1. セーブを /sdcard/Download/TerrariaSaves/ にコピーする（Worlds と Players のサブフォルダ）、この画面に直接表示されます。\n2. PC リポジトリの reveal.py + deploy.py を使い、adb 経由でゲームフォルダに直接書き込む。\n3. root 化済み端末なら su ブリッジ（後のバージョン）。",
    "worlds_reload": "更新",
    "worlds_select_all": "すべて選択",
    "worlds_select_none": "選択解除",
    "worlds_issues": "%1$d 個のファイルを読み取れません",
    "action_reveal": "選択を明らかにする",
    "chars_title": "キャラクターを選択",
    "chars_hint": "マップは「ワールド × キャラクター」ごとに保存されます。既定ではすべて明らかにします。",
    "chars_confirm": "%1$d 体を明らかにする",
    "chars_new_map": "新規作成",
    "run_title": "処理中",
    "done_title": "完了",
    "done_undo": "今回を元に戻す",
    "done_close": "閉じる",
    "status_success": "成功",
    "status_skipped": "スキップ",
    "status_failed": "失敗",
    "label_prehard_short": "ハードモード前",
    "label_hardmode_short": "ハードモード",
    "label_journeys_end": "ムーンロード撃破済み",
    "close_game_warning": "変更前にワールドを終了し、ゲームを閉じてください。終了時にゲームがマップを書き戻し、本ツールの変更を上書きします。",
    "back": "戻る",
    "about_check": "コア自己診断",
    "about_check_ok": "コア自己診断に成功",
    "about_check_fail": "コア自己診断に失敗",
    "shizuku_title": "Shizuku でゲームフォルダを読む",
    "shizuku_body": "Android 11 以降、アプリは他のアプリの Android/data を一覧できません（すべてのファイルへのアクセスでも不可）。Shizuku を導入して起動していれば、ここで許可するとセーブを直接読み書きでき、ポータブルフォルダへのコピーが不要になります。",
    "shizuku_state_running": "Shizuku は動作中です。許可できます。",
    "shizuku_state_absent": "Shizuku が見つかりません。導入し、adb または root で起動してください。",
    "shizuku_button": "Shizuku を許可",
    "shizuku_banner_title": "ゲームフォルダを直接読めます",
    "restore_title": "バックアップと復元",
    "restore_hint": "上書き前に必ずバックアップを作ります（.map.bak、古いものは .bak.1、.bak.2）。複数選択して復元、バックアップのみ削除、またはマップを削除して完全未探索の状態に戻せます。",
    "restore_chip_backups": "バックアップ %1$d 件",
    "restore_newest": "最新のバックアップ %1$s · %2$s",
    "restore_selected": "%1$d 件選択",
    "restore_action": "選択したバックアップを復元",
    "restore_delete_backups": "選択したバックアップを削除",
    "restore_delete_map": "マップを削除（全黒）",
    "restore_empty": "この場所にマップファイルがありません",
    "restore_running": "バックアップを復元中",
    "restore_deleting": "削除中",
    "restore_result": "復元結果",
    "restore_result_delete": "削除結果",
    "overwrite_title": "再度上書きしますか？",
    "overwrite_body": "次のキャラクターには前回のバックアップがあります: %1$s",
    "overwrite_checkbox": "前回のバックアップを削除",
    "overwrite_warning": "チェックすると前回のバックアップは今回の上書き前の状態に置き換えられ、復元できなくなります。",
    "overwrite_continue": "上書きする",
    "cancel": "キャンセル",
    "close": "閉じる",
    "restore_delete_map_title": "マップを削除しますか？",
    "restore_delete_map_body": "選択した %1$d 件のマップを削除します。ゲーム内では完全未探索の全黒状態として再生成されます。元に戻せません（マップの控えは残しません）。",
    "difficulty_classic": "クラシック",
    "difficulty_expert": "エキスパート",
    "difficulty_master": "マスター",
    "difficulty_legendary": "レジェンダリー",
    "difficulty_journey": "ジャーニー",
    "size_small": "小",
    "size_medium": "中",
    "size_large": "大",
    "size_huge": "特大",
    "evil_corruption": "汚染",
    "evil_crimson": "クリムゾン",
    "root_game_shizuku": "ゲームフォルダ (Shizuku)",
    "root_shizuku_cache": "Shizuku 一時",
    "root_game_files": "ゲームフォルダ (files)",
    "root_game": "ゲームフォルダ",
    "language_title": "言語",
    "language_follow": "システムに従う",
    "language_sc": "简体中文",
    "language_tc": "繁體中文",
    "language_ja": "日本語",
    "language_en": "English",
    "run_reading_world": "ワールド %1$s を読み込み",
    "run_generating": "マップを生成 %1$d×%2$d",
    "run_verifying": "マップの内容を検証",
    "run_too_large": "ワールドが大きすぎてメモリ不足です。キャラクターごとに実行してください。",
    "run_writing": "キャラクター %1$s に書き込み",
    "run_no_char_dir": "キャラクターフォルダを作成できません",
    "run_replace_failed": "古いマップを置換できません: %1$s",
    "run_write_failed": "書き込めません: %1$s",
    "run_push_failed": "ミラーには生成しましたが、本体への書き戻しに失敗: %1$s（exit %2$d）",
    "run_size_mismatch": "（サイズ検証 %1$s ≠ %2$s）",
    "run_written_back": "本体に書き戻しました（元ファイルは .map.bak）%1$s",
    "run_written_new": "本体に書き戻しました（新規マップ）%1$s",
    "template_missing": "セーブフォルダが見つかりません",
    "template_label": "テンプレート: %1$s",
    "template_from_character": "キャラ「%1$s」のマップ",
    "template_from_donor": "同サイズのマップヘッダー（%1$s）",
    "template_unusable": "使用できるマップヘッダーがありません: %1$s",
    "template_never_entered": "まだどのキャラクターもこのワールドに入っていません。どれかで一度入って終了し、マップキャッシュを作らせてください。",
}


def read_base() -> list[tuple[str, str]]:
    tree = ET.parse(BASE)
    entries: list[tuple[str, str]] = []
    seen: set[str] = set()
    for node in tree.getroot():
        if node.tag != "string" or "name" not in node.attrib:
            continue
        name = node.attrib["name"]
        if name in seen:
            continue
        seen.add(name)
        entries.append((name, node.text or ""))
    for name, text in NEW_SC.items():
        if name not in seen:
            seen.add(name)
            entries.append((name, text))
    return entries


def xml_escape(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return text.replace('"', '\\"').replace("'", "\\'")


def write_resources(path: Path, entries: list[tuple[str, str]], texts: dict[str, str]) -> int:
    missing = 0
    global _MISSING_NAMES
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for name, original in entries:
        value = texts.get(name)
        if value is None:
            value = original
            missing += 1
            _MISSING_NAMES.add(name)
        lines.append(f'    <string name="{name}">{xml_escape(value)}</string>')
    lines.append("</resources>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return missing


def to_traditional(text: str, words: dict[str, str]) -> str:
    for source, target in words.items():
        text = text.replace(source, target)
    return "".join(_TRANS.get(ch, ch) for ch in text)


def main() -> int:
    entries = read_base()
    base_map = dict(entries)

    write_resources(BASE, entries, base_map)
    problems = write_resources(RES / "values-en" / "strings.xml", entries, EN)
    problems += write_resources(RES / "values-ja" / "strings.xml", entries, JA)

    for folder, words in (("values-zh-rTW", TW_WORDS),):
        converted = {
            name: (text if name in NO_CONVERT else to_traditional(text, words))
            for name, text in entries
        }
        problems += write_resources(RES / folder / "strings.xml", entries, converted)

    print(f"keys      {len(entries)}")
    print(f"fallbacks {problems} {sorted(_MISSING_NAMES)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
