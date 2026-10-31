# Freeform — poprawiony opis i implementacja RoadBuilder

## Co wiadomo o Cities: Skylines II

[Oficjalny dziennik twórców „Road Tools”, 19 czerwca 2023](https://www.paradoxinteractive.com/games/cities-skylines-ii/features/road-tools)
opisuje narzędzia krzywych, przyciąganie do sieci, prowadnice, budowę dróg
równoległych i cofanie kroków prawym przyciskiem myszy. Nie publikuje wzorów
doboru uchwytów, liczby próbek walidacji ani minimalnych promieni dla typów dróg.
Nie uzasadnia też tezy, że pierwszy i drugi klik zawsze wyznaczają końce Freeform.

Poniższy opis jest poprawnym modelem matematycznym i specyfikacją RoadBuilder
inspirowaną sposobem budowania dróg w CS2. Nie jest rekonstrukcją kodu CS2 ani
deklaracją identycznych parametrów jego silnika.

## Geometria segmentu

Oś segmentu jest krzywą Béziera trzeciego stopnia o czterech punktach w przestrzeni:

- `P0` — początek drogi;
- `P3` — koniec drogi;
- `P1`, `P2` — uchwyty sterujące. Zwykle leżą poza osią, ale mogą również leżeć na niej.

Położenie osi dla `0 ≤ t ≤ 1` wynosi:

```text
B(t) = (1−t)³ P0 + 3(1−t)²t P1 + 3(1−t)t² P2 + t³ P3
B′(t) = 3(1−t)²(P1−P0) + 6(1−t)t(P2−P1) + 3t²(P3−P2)
B″(t) = 6(1−t)(P2−2P1+P0) + 6t(P3−2P2+P1)
```

Parametr `t` nie jest odległością: jego równomierny przyrost nie daje równych
odstępów w świecie. RoadBuilder stosuje adaptacyjne próbkowanie i przybliżoną
tablicę długości łuku. Dopiero na końcu zamienia wektorowy pas drogi na bloki
Minecrafta. Podgląd i pakiet budowy używają tych samych czterech punktów.
Silnik nie musi obliczać nieskończenie wielu punktów ani „każdego centymetra”.

## Freehand Draw: trzy kliknięcia

1. Początek drogi.
2. Kierunek wyjścia (odległość kliknięcia nie zmienia kształtu).
3. Koniec drogi i zatwierdzenie podglądu.

P1 i P2 są obliczane automatycznie. Kierunek wyjścia na końcu jest odbiciem
kierunku początkowego względem cięciwy w XZ. Oba ramiona mają tę samą długość
`h = 2d / (3 max(0.5, 1 + dot(entry, chordDirection)))`.
To symetryczna aproksymacja łuku, nie gwarancja stałej krzywizny.
Wysokość jest interpolowana liniowo. Przyciąganie nie nadpisuje stycznych.
Kontynuacja zachowuje punkt końcowy jako nowy początek, ale wymaga wyboru
nowego kierunku i końca. Prawy przycisk cofa krok; Complex Curve pozostaje bez zmian.

## Ograniczenia budowy

Wspólny walidator podglądu i serwera sprawdza krzywe kubiczne, krzywe kwadratowe
po dokładnej konwersji do kubicznych, odcinki MultiCurve oraz ich odsunięcia.
Walidacja odbywa się przed planowaniem bloków i zmianą grafu sieci.
Wewnętrzne łączniki skrzyżowań nie podlegają tym samym limitom budowy segmentu.

Minimalny promień w blokach wynosi `max(szerokość/2 + 0.5, promieńKlasy)`:

| Klasa | Promień klasy |
| --- | ---: |
| Alley | 2 |
| Local | 4 |
| Collector | 6 |
| Arterial | 10 |
| Highway | 16 |

To wartości dobrane do skali RoadBuilder, nie wartości odczytane z CS2.
Margines zależny od szerokości chroni wewnętrzną krawędź przed zawinięciem.
Dla drogi równoległej walidacja konserwatywnie dodaje wartość bezwzględną odsunięcia
do wymaganego promienia krzywej bazowej; może więc odrzucić także niektóre
bezpieczne odsunięcia na zewnątrz zakrętu.

Kontrola promienia wykorzystuje analityczne pochodne i adaptacyjne ograniczenia
przedziałowe oparte na punktach kontrolnych pochodnej w bazie Bernsteina.
Nie sprawdza wyłącznie kilku równomiernych próbek. Punkt stacjonarny, zbyt mały
promień lub brak możliwości potwierdzenia bezpieczeństwa w limicie obliczeń
powoduje `TOO_SHARP_TURN`.

Samoprzecięcie pojedynczej krzywej kubicznej jest wyznaczane z równania
`B(t)=B(u), t≠u` w rzucie XZ. Przecięcie z różnicą wysokości mniejszą niż pół
bloku powoduje `SELF_INTERSECTION`. Przecięcie rzutu dróg na różnych wysokościach
nie jest samo w sobie przecięciem przestrzennym; pozostałe reguły prześwitu nadal
obowiązują. To test osi pojedynczego segmentu, nie pełny test kolizji wszystkich
powierzchni ani wszystkich par odcinków w wielosegmentowym śladzie.

Błędna krzywa pozostaje widoczna na czerwono, a kliknięcie jej nie wysyła.
Serwer ponownie sprawdza geometrię według autorytatywnego presetu, niezależnie
od tego, czy klient wykonał walidację.
