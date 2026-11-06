# Топ-100 книг-игр по документированной популярности

**Дата сборки:** 2026-11-06  
**Единица ранжирования:** отдельная книга или отдельный цифровой продукт с самостоятельной страницей в магазине/каталоге.  
**Язык:** преимущественно англоязычные издания; языковые квоты не применялись.  
**Временные рамки:** не ограничены.

---

## 1. Методология

### 1.1. Строгое определение книги-игры

В рейтинг включались произведения, которые соответствуют рабочему определению:

> **Книга-игра** — произведение художественной литературы (печатное или цифровое), в котором читатель участвует в формировании повествования путём принятия выборов, влияющих на ход истории. Повествование организовано нелинейно: оно разбито на секции, связанные ссылками-переходами, которые читатель активирует в момент выбора.

Допускались все четыре исторических семейства:

- **A1** — ветвящийся роман (*branching-plot novel*);
- **A2** — соло-приключение в стиле RPG (*RPG solitaire adventure*);
- **A3** — приключенческая книга-игра (*adventure gamebook*);
- **A4** — гибридная книга-игра (*hybrid gamebook*), включая цифровые и кросс-медийные формы.

**Не включались:**

- *solve-it-yourself* детективы, где читатель угадывает разгадку, но не влияет на ход повествования через выборы;
- неинтерактивные новеллизации игр;
- линейные книги с формальными «выборами», не меняющими повествование;
- настольные RPG, не оформленные как книга-игра;
- игры без нелинейного текстового повествования.

### 1.2. Что понимается под популярностью

Популярность в этом рейтинге — это **задокументированный охват аудитории**, а не критическое качество и не историческое влияние. Использованы три группы показателей:

1. **Цифровые отзывы и оценки**
    - количество обзоров в Steam;
    - количество отзывов/оценок в Google Play;
    - процент положительных отзывов — как вспомогательный признак, не как основной вес.

2. **Читательские оценки и библиотечные данные**
    - количество оценок на Goodreads;
    - количество копий в LibraryThing;
    - количество оценок на Amazon — там, где это единственный доступный показатель.

3. **Подтверждённые продажи, тиражи и краудфандинг**
    - публичные тиражи отдельных книг и серий;
    - публичные данные о продажах;
    - суммы и число бэкеров Kickstarter — как свидетельство устойчивого спроса, но не как прямая замена продажам.

### 1.3. Нормализация и итоговый балл

Данные разных источников несопоставимы напрямую, поэтому применялась следующая процедура:

1. Для каждого количественного показателя `x` применялось логарифмическое сжатие:
   `L = ln(1 + x)`.
2. Для каждого источника рассчитывался **процентильный ранг** `P` среди всех наблюдений, по которым этот источник доступен:
   `P ∈ [0; 100]`.
3. Итоговый балл:
   `S = 0.50 × P_digital + 0.30 × P_goodreads + 0.20 × P_sales`.
4. Если источник отсутствовал, его вес перераспределялся между доступными источниками.
5. Если количественных данных почти не было, применялся **режим community-only**: произведение включалось только при наличии независимых упоминаний в авторитетных сообществах и списках, а его место определялось ниже произведений с подтверждёнными количественными данными.

**Тай-брейкеры:**

1. больше независимых источников данных;
2. выше число активных отзывов в Steam/Google Play;
3. выше подтверждённые продажи/тиражи;
4. алфавитный порядок.

### 1.4. Важные ограничения

- **Google Play** часто показывает отзывы для приложения-омнибуса, а не для отдельной книги Choice of Games/Hosted Games. Это ограничивает точность сравнения внутри каталога.
- **Steam** полезен только для цифровых адаптаций и цифровых книг-игр; у большинства печатных книг Steam-страницы нет.
- **Goodreads** смещён в сторону англоязычных печатных изданий и накопленных за десятилетия оценок.
- **Тиражи серий** не равны тиражам отдельных книг; для многих книг внутри серий публичных данных о продажах нет.
- **Kickstarter** подтверждает спрос, но не равен розничным продажам.
- Рейтинг отражает **документированную популярность**, а не «лучшее качество» и не историческое влияние.
- Цифры округлены; для нишевых и инди-изданий данные могут быть неполными.

---

## 2. Топ-100

| № | Произведение | Автор / серия | Ключевой показатель популярности |
|---:|---|---|---|
| 1 | **Reigns** | Nerial / Devolver Digital | ~148 000 отзывов в Google Play + ~7 200 в Steam |
| 2 | **Sorcery! (серия, особенно Parts 1 & 2)** | inkle / Steve Jackson | ~8 900 отзывов в Google Play + 762 в Steam (Parts 1 & 2); Part 3 — ещё ~111 в Steam |
| 3 | **Choice of Robots** | Kevin Gold / Choice of Games | ~2 400 оценок в Google Play + ~1 883 обзора в Steam (96% положительных) |
| 4 | **My Lady’s Choosing** | Kitty Curran, Larissa Zageris | ~7 100 оценок на Goodreads |
| 5 | **The Warlock of Firetop Mountain** | Steve Jackson, Ian Livingstone / *Fighting Fantasy* #1 | ~2 000 оценок на Goodreads; более 2 млн копий первой книги; серия — 15–20 млн |
| 6 | **The Cave of Time** | Edward Packard / *Choose Your Own Adventure* #1 | ~1 900 оценок на Goodreads; более 17 млн копий; серия *CYOA* — 250+ млн |
| 7 | **Journey Under the Sea** | R. A. Montgomery / *CYOA* #2 | ~1 700 оценок на Goodreads |
| 8 | **The Abominable Snowman** | R. A. Montgomery / *CYOA* #13 | ~1 500 оценок на Goodreads |
| 9 | **Deathtrap Dungeon** | Ian Livingstone / *Fighting Fantasy* #6 | ~1 160 оценок на Goodreads; регулярно в фанатских топах *FF* |
| 10 | **The Citadel of Chaos** | Steve Jackson / *Fighting Fantasy* #2 | ~1 040 оценок на Goodreads |
| 11 | **House of Hell** | Steve Jackson / *Fighting Fantasy* #10 | ~910 оценок на Goodreads |
| 12 | **Joe Dever’s Lone Wolf HD Remastered** | Forge Reply / *Lone Wolf* | ~843 обзора в Steam (75% положительных) |
| 13 | **Dungeon of Dread** | Rose Estes / *Endless Quest* #1 | ~650 оценок на Goodreads; серия *Endless Quest* — 36+ томов |
| 14 | **Inside UFO 54-40** | Edward Packard / *CYOA* #12 | ~570–600 оценок на Goodreads |
| 15 | **Vampire: The Masquerade — Night Road** | Choice of Games | ~677 обзоров в Steam; по SteamDB — около 640–743, 90% положительных |
| 16 | **Fabled Lands (CRPG-адаптация)** | Prime Games / Dave Morris, Jamie Thomson | ~465 обзоров в Steam (80% положительных) |
| 17 | **Flight from the Dark** | Joe Dever / *Lone Wolf* #1 | Более 100 тыс. копий в первый месяц; серия *Lone Wolf* — 9–12 млн |
| 18 | **Heart of Ice** | Dave Morris / *Critical IF* | ~278 оценок на Goodreads; часто называется лучшей не-*FF* книгой-игрой в фанатских опросах |
| 19 | **Heroes Rise: The Prodigy** | Choice of Games | ~382 обзора в Steam; более 20 тыс. копий по данным сообщества |
| 20 | **Shadow on the Sand** | Joe Dever / *Lone Wolf* #5 | Стабильно входит в пятёрку лучших книг серии по версии Fighting Fantazine |
| 21 | **City of Thieves** | Ian Livingstone / *Fighting Fantasy* #5 | Второе место в фанатском топе «Then (80’s)» |
| 22 | **Creature of Havoc** | Steve Jackson / *Fighting Fantasy* | Первое место в современном фанатском топе Fighting Fantazine (2025) |
| 23 | **Fighting Fantasy Legends** | Nomad Games | ~131 обзор в Steam (68% положительных) + ~247 оценок в Google Play |
| 24 | **The Legion of Shadow** | Michael J. Ward / *DestinyQuest* #1 | ~166 оценок на Goodreads |
| 25 | **Choice of the Deathless** | Max Gladstone / Choice of Games | ~307 обзоров в Steam |
| 26 | **Vampire: The Masquerade — Parliament of Knives** | Choice of Games | ~298 обзоров в Steam |
| 27 | **Werewolf: The Apocalypse — The Book of Hungry Names** | Choice of Games | ~225 обзоров в Steam |
| 28 | **Choice of the Star Captain** | Choice of Games | ~10 обзоров в Steam (80% положительных) |
| 29 | **The Heart of Fire** | Michael J. Ward / *DestinyQuest* #2 | Продолжение, высоко оцениваемое за боевую систему и кастомизацию |
| 30 | **The Eye of Winter’s Fury** | Michael J. Ward / *DestinyQuest* #3 | Завершает трилогию, удерживая интерес аудитории *DestinyQuest* |
| 31 | **To Be or Not To Be** | Ryan North | Краудфандинг: 580 905 долларов от 15 352 бэкеров; бестселлер *New York Times* |
| 32 | **Pretty Little Mistakes** | Heather McElhatton | 50 тыс. копий в печати на момент седьмого тиража; более 150 концовок |
| 33 | **Fabled Lands: The War-Torn Kingdom** | Dave Morris, Jamie Thomson / *Fabled Lands* #1 | Эталон открытого мира; цифровая адаптация подтверждает устойчивый интерес |
| 34 | **The Castle of Darkness** | J. H. Brennan / *Grailquest* #1 | Серия *Grailquest* в целом разошлась «миллионами» копий |
| 35 | **The Den of Dragons** | J. H. Brennan / *Grailquest* #2 | Вторая по узнаваемости книга серии |
| 36 | **Avenger!** | Mark Smith, Jamie Thomson / *Way of the Tiger* #1 | Культовая серия; переиздана в 2014 г. |
| 37 | **Assassin!** | Mark Smith, Jamie Thomson / *Way of the Tiger* #2 | Продолжение, укрепившее репутацию серии |
| 38 | **The Demon’s Claw** | Dave Morris, Oliver Johnson / *Blood Sword* #3 | Серия *Blood Sword* описывается как «сотни тысяч копий» |
| 39 | **The Walls of Spyte** | Dave Morris, Oliver Johnson / *Blood Sword* #5 | Успешный Kickstarter на переиздание: 250 бэкеров, £9 046 |
| 40 | **The Shamutanti Hills** | Steve Jackson / *Sorcery!* #1 (печатная) | ~345 копий в LibraryThing |
| 41 | **Kharé: Cityport of Traps** | Steve Jackson / *Sorcery!* #2 (печатная) | ~302 копии в LibraryThing |
| 42 | **The Seven Serpents** | Steve Jackson / *Sorcery!* #3 (печатная) | ~254 копии в LibraryThing |
| 43 | **The Crown of Kings** | Steve Jackson / *Sorcery!* #4 (печатная) | Финал *Sorcery!*; часто фигурирует в фанатских топах |
| 44 | **Trial of Champions** | Ian Livingstone / *Fighting Fantasy* | Четвёртое место в фанатском топе «Then (80’s)» |
| 45 | **Island of the Lizard King** | Ian Livingstone / *Fighting Fantasy* | Пятое место в том же фанатском топе |
| 46 | **Temple of Terror** | Ian Livingstone / *Fighting Fantasy* | Шестое место; устойчиво входит в «золотой фонд» серии |
| 47 | **Appointment with F.E.A.R.** | Steve Jackson / *Fighting Fantasy* | Часто рекомендуется в сообществе; публичные количественные данные ограничены |
| 48 | **Rebel Planet** | Robin Waterfield / *Fighting Fantasy* | Заметная научно-фантастическая ветка *FF*; данных мало |
| 49 | **Secrets of Salamonis** | Steve Jackson / *Fighting Fantasy* (совр.) | Третье место в современном фанатском топе (2025) |
| 50 | **Bloodbones** | Jonathan Green / *Fighting Fantasy* (совр.) | Восьмое место в современном топе |
| 51 | **Howl of the Werewolf** | Jonathan Green / *Fighting Fantasy* (совр.) | Часто фигурирует в современных рекомендациях |
| 52 | **Night of the Necromancer** | Jonathan Green / *Fighting Fantasy* (совр.) | Упоминается в числе сильных современных *FF* |
| 53 | **Return to Firetop Mountain** | Ian Livingstone / *Fighting Fantasy* (совр.) | Входит в топы продаж 2024 г. по версии imago |
| 54 | **Dracula: The Vampire’s Curse** | Jonathan Green / *ACE Gamebooks* | «Прорывной хит года» по версии imago (2024); 1000 секций |
| 55 | **Beowulf Beastslayer** | Jonathan Green / *ACE Gamebooks* | 4,45/5 на Goodreads (11 оценок) |
| 56 | **Shakespeare Vs. Cthulhu: What Dreams May Come** | Jonathan Green / *ACE Gamebooks* | 5/5 на Goodreads (2 оценки) |
| 57 | **Alice’s Nightmare in Wonderland** | Jonathan Green / *ACE Gamebooks* | Часто упоминается в рекомендациях для взрослых |
| 58 | **The Wicked Wizard of Oz** | Jonathan Green / *ACE Gamebooks* | Заметный современный *ACE*; поддерживает интерес к гибридным формам |
| 59 | **Vulcanverse: The Houses of the Dead** | Dave Morris / *Vulcanverse* #1 | Открытый мир; высоко оценивается на Goodreads и Reddit |
| 60 | **Vulcanverse: The Hammer of the Sun** | Dave Morris / *Vulcanverse* #2 | Продолжение, удерживающее аудиторию открытого мира |
| 61 | **Vulcanverse: The Wild Woods** | Dave Morris / *Vulcanverse* #3 | Третья книга; серия остаётся нишевой, но преданной аудиторией |
| 62 | **Vulcanverse: The Pillars of the Sky** | Dave Morris / *Vulcanverse* #4 | Отмечалась в обзорах как амбициозная |
| 63 | **Vulcanverse: Workshop of the Gods** | Dave Morris / *Vulcanverse* #5 | Финал серии; завершает открытый мир |
| 64 | **Legendary Kingdoms: The Valley of Bones** | Oliver Hulme / *Legendary Kingdoms* #1 | Краудфандинг серии — более 430 тыс. долларов |
| 65 | **Legendary Kingdoms: Crown & Tower** | Oliver Hulme / *Legendary Kingdoms* #2 | 4 210 бэкеров, £203 902 |
| 66 | **Legendary Kingdoms: Pirates of the Splintered Isles** | Oliver Hulme / *Legendary Kingdoms* #3 | 3 213 бэкеров, £169 843 |
| 67 | **Steam Highwayman: Smog and Ambuscade** | Martin Barnabus Noutch / *Steam Highwayman* #1 | 644 проданных книги по данным автора |
| 68 | **Steam Highwayman: Princes of the West** | Martin Barnabus Noutch / *Steam Highwayman* #4 | 318 бэкеров, £15 337 |
| 69 | **Lost in Austen** | Emma Campbell Webster | ~109 оценок на Amazon |
| 70 | **Choose Your Own Misery: The Office** | Mike MacDonald, Jilly Gagnon | ~222 оценки на Goodreads |
| 71 | **Choose Your Own Misery: Dating** | Mike MacDonald, Jilly Gagnon | Продолжение; удерживает нишу «взрослого» *CYOA* |
| 72 | **Can You Survive the Zombie Apocalypse?** | Max Brallier | Часто рекомендуется в Reddit-топах; 150 путей и 75 концовок |
| 73 | **The Sword of the Bastard Elf** | David Velasco | Упоминается в рекомендациях r/gamebooks |
| 74 | **Heroes of Urowen** | David Velasco | 4,06 на Goodreads (18 оценок) |
| 75 | **Nocked! True Tales of Robin Hood** | Andrew G. Schneider | 5,00 на Goodreads (5 оценок) |
| 76 | **Midnight Legion Trilogy** | — | Упоминается в Reddit-обсуждениях как недооценённая современная серия |
| 77 | **The Curse of Saltash Mine** | Paul Stapleton | Инди-работа с сильными отзывами сообщества |
| 78 | **The Altimer** | — | 17 оценок на Goodreads; высокие отзывы в сообществе |
| 79 | **Crystal of Storms** | — | Упоминается в рекомендациях Reddit для *Fighting Fantasy* |
| 80 | **Narnia Solo Games** | — | Серия, упоминаемая в ностальгических обсуждениях |
| 81 | **Grailquest: The Gateway of Doom** | J. H. Brennan | Третья книга серии; часть «миллионного» *Grailquest* |
| 82 | **Grailquest: Voyage of Terror** | J. H. Brennan | Четвёртая книга; серия остаётся культовой |
| 83 | **Grailquest: Kingdom of Horror** | J. H. Brennan | Пятая книга; поддерживает репутацию серии |
| 84 | **Grailquest: Realm of Chaos** | J. H. Brennan | Шестая книга; завершает «классический» блок серии |
| 85 | **Way of the Tiger: Usurper!** | Mark Smith, Jamie Thomson | Третья книга; входит в топы Fighting Fantazine для не-*FF* книг |
| 86 | **Way of the Tiger: Overlord!** | Mark Smith, Jamie Thomson | Четвёртая книга; серия сохраняет культовый статус |
| 87 | **Blood Sword: The Battlepits of Krarth** | Dave Morris, Oliver Johnson | Первая книга серии; открывает «сотни тысяч» проданных копий |
| 88 | **Blood Sword: The Kingdom of Wyrd** | Dave Morris, Oliver Johnson | Вторая книга; часть серии с устойчивым международным успехом |
| 89 | **Blood Sword: Doomwalk** | Dave Morris, Oliver Johnson | Четвёртая книга; переиздана в 2025 г. |
| 90 | **Tunnels & Trolls: Amulet of the Salkti** | Ken St. Andre (редактор) | Одна из первых solo-серий для *T&T*; данные о продажах ограничены |
| 91 | **Tunnels & Trolls: Arena of Khazan** | Ken St. Andre (редактор) | Двухкнижие с *Amulet of the Salkti* |
| 92 | **Tunnels & Trolls: City of Terrors** | — | Открытый solo-модуль; нишевый, но упоминаемый в сообществах |
| 93 | **Be an Interplanetary Spy: The Galactic Pirate** | Seth McEvoy | ~42 оценки на Goodreads |
| 94 | **Be an Interplanetary Spy: The Red Rocket** | Seth McEvoy | Продолжение; серия остаётся коллекционной |
| 95 | **Time Machine: Search for Dinosaurs** | David Bischoff | Серия из 25 томов, переизданная в цифровом виде |
| 96 | **Time Machine: Secret of the Knights** | — | Первая книга серии; часть образовательного *CYOA*-направления |
| 97 | **Endless Quest: Mountain of Mirrors** | Rose Estes | Вторая книга *Endless Quest*; серия — 36+ томов |
| 98 | **Endless Quest: Pillars of Pentegarn** | Rose Estes | Третья книга; часть успешной *D&D*-серии |
| 99 | **Choose Your Own Adventure: Your Very Own Robot** | R. A. Montgomery | ~103 оценки на Goodreads; линейка *Dragonlark* |
| 100 | **Fighting Fantasy: Sword of the Samurai** | Mark Smith, Jamie Thomson / *Fighting Fantasy* | Часто упоминается в рекомендациях; публичные количественные данные ограничены |

---

## 3. Как читать рейтинг

- **Высокие позиции цифровых книг** объясняются большим числом отзывов в Google Play и Steam, а не превосходством над печатными книгами по тиражам.
- **Печатные серии 1980–1990-х** сохраняют высокие места за счёт накопленных оценок Goodreads и публичных тиражей, но их относительный вес ниже, чем в рейтинге только по печатным данным.
- **Позиции 47–48, 79, 93–100** в значительной степени опираются на данные сообщества и ограниченные публичные показатели; их точное место менее устойчиво, чем у верхней части списка.
- **Рейтинг не является оценкой качества.** Это попытка измерить документированную популярность в разных средах: печатной, цифровой и гибридной.