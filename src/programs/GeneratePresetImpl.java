package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.GeneratePreset;

import java.util.*;

/**
 * Реализация генерации армии компьютера.
 * Алгоритмическая сложность: O(n × m), где n = 4 типа юнитов, m = 11 (максимум юнитов одного типа)
 * В реальности: O(4 × 11 × budget/minCost) = O(44 × 75) = O(3300) операций
 */
public class GeneratePresetImpl implements GeneratePreset {
    private static final int MAX_UNITS_PER_TYPE = 11;
    private static final int FIELD_HEIGHT = 21;
    private static final int FIELD_WIDTH = 3; // Колонки 0, 1, 2 для армии компьютера
    private static final int MAX_TOTAL_UNITS = FIELD_HEIGHT * FIELD_WIDTH; // 63 максимум

    @Override
    public Army generate(List<Unit> unitList, int maxPoints) {
        // Проверка входных данных
        if (unitList == null || unitList.isEmpty() || maxPoints <= 0) {
            return new Army(new ArrayList<>());
        }

        // 1. Расчет коэффициентов эффективности для каждого типа юнитов
        List<UnitEfficiency> efficiencies = calculateEfficiencies(unitList);

        // 2. Сортировка по эффективности (атака/стоимость → здоровье/стоимость)
        efficiencies.sort((e1, e2) -> {
            int cmp = Double.compare(e2.attackRatio, e1.attackRatio);
            return cmp != 0 ? cmp : Double.compare(e2.healthRatio, e1.healthRatio);
        });

        // 3. Жадный алгоритм выбора юнитов
        List<Unit> armyUnits = greedySelection(efficiencies, maxPoints);

        // 4. Распределение координат для армии компьютера (улучшенная версия)
        assignComputerCoordinates(armyUnits);

        // 5. Создание и возврат армии
        Army army = new Army(armyUnits);
        army.setPoints(calculateTotalCost(armyUnits));
        return army;
    }

    private List<UnitEfficiency> calculateEfficiencies(List<Unit> unitList) {
        List<UnitEfficiency> efficiencies = new ArrayList<>(unitList.size());
        for (Unit unit : unitList) {
            double attackRatio = (double) unit.getBaseAttack() / unit.getCost();
            double healthRatio = (double) unit.getHealth() / unit.getCost();
            efficiencies.add(new UnitEfficiency(unit, attackRatio, healthRatio));
        }
        return efficiencies;
    }

    private List<Unit> greedySelection(List<UnitEfficiency> efficiencies, int maxPoints) {
        List<Unit> selected = new ArrayList<>();
        int remainingPoints = maxPoints;
        Map<String, Integer> typeCount = new HashMap<>();

        // Проходим по типам в порядке убывания эффективности
        for (UnitEfficiency eff : efficiencies) {
            Unit template = eff.unit;
            String type = template.getUnitType();
            int cost = template.getCost();

            // Проверяем, не достигли ли лимита для этого типа
            int currentCount = typeCount.getOrDefault(type, 0);
            if (currentCount >= MAX_UNITS_PER_TYPE) continue;

            // Максимальное количество, которое можно купить
            int maxByType = MAX_UNITS_PER_TYPE - currentCount;
            int maxByPoints = remainingPoints / cost;
            int toBuy = Math.min(maxByType, maxByPoints);

            // Добавляем юнитов
            for (int i = 0; i < toBuy; i++) {
                Unit newUnit = createUnitCopy(template, currentCount + i + 1);
                selected.add(newUnit);
                remainingPoints -= cost;
            }

            if (toBuy > 0) {
                typeCount.put(type, currentCount + toBuy);
            }

            // Если потратили все очки, выходим
            if (remainingPoints <= 0) break;
        }

        return selected;
    }

    private Unit createUnitCopy(Unit template, int index) {
        // КРИТИЧЕСКИ ВАЖНО: "Archer 1" (с пробелом) для корректной работы игры

        // Исправлено: всегда создаем новые HashMap, даже если исходные null
        Map<String, Double> attackBonuses = template.getAttackBonuses() != null ?
                new HashMap<>(template.getAttackBonuses()) : new HashMap<>();

        Map<String, Double> defenceBonuses = template.getDefenceBonuses() != null ?
                new HashMap<>(template.getDefenceBonuses()) : new HashMap<>();

        return new Unit(
                template.getUnitType() + " " + index,
                template.getUnitType(),
                template.getHealth(),
                template.getBaseAttack(),
                template.getCost(),
                template.getAttackType(),
                attackBonuses,
                defenceBonuses,
                0, 0
        );
    }

    private void assignComputerCoordinates(List<Unit> units) {
        if (units == null || units.isEmpty()) return;

        // Улучшенное распределение координат:
        // 1. Группируем юнитов по типам для лучшего визуального представления
        // 2. Распределяем равномерно по 3 колонкам (0, 1, 2)
        // 3. В каждой колонке размещаем не более 21 юнита (высота поля)

        // Группировка по типам
        Map<String, List<Unit>> unitsByType = new LinkedHashMap<>();
        for (Unit unit : units) {
            unitsByType.computeIfAbsent(unit.getUnitType(), k -> new ArrayList<>()).add(unit);
        }

        // Подсчет общего количества юнитов каждого типа
        List<Map.Entry<String, List<Unit>>> typeEntries = new ArrayList<>(unitsByType.entrySet());

        // Распределение по колонкам
        int currentColumn = 0;
        int currentRow = 0;

        for (Map.Entry<String, List<Unit>> entry : typeEntries) {
            List<Unit> typeUnits = entry.getValue();

            // Размещаем юнитов этого типа
            for (Unit unit : typeUnits) {
                unit.setxCoordinate(currentColumn);
                unit.setyCoordinate(currentRow);

                // Переходим на следующую строку
                currentRow++;

                // Если достигли дна колонки, переходим на следующую колонку
                if (currentRow >= FIELD_HEIGHT) {
                    currentRow = 0;
                    currentColumn++;

                    // Если все колонки заполнены, начинаем с первой колонки
                    // (в теории не должно случиться, т.к. максимум 44 юнита < 63)
                    if (currentColumn >= FIELD_WIDTH) {
                        currentColumn = 0;
                    }
                }
            }

            // После размещения всех юнитов типа, начинаем новую колонку для следующего типа
            // Это создает визуальное разделение между типами
            currentColumn++;
            currentRow = 0;

            // Если вышли за пределы колонок, возвращаемся к началу
            if (currentColumn >= FIELD_WIDTH) {
                currentColumn = 0;
            }
        }

        // Валидация: убедимся, что все координаты в допустимых пределах
        validateCoordinates(units);
    }

    private void validateCoordinates(List<Unit> units) {
        for (Unit unit : units) {
            int x = unit.getxCoordinate();
            int y = unit.getyCoordinate();

            if (x < 0 || x >= FIELD_WIDTH) {
                System.err.println("Предупреждение: юнит " + unit.getName() +
                        " имеет недопустимую X-координату: " + x);
                unit.setxCoordinate(Math.max(0, Math.min(FIELD_WIDTH - 1, x)));
            }

            if (y < 0 || y >= FIELD_HEIGHT) {
                System.err.println("Предупреждение: юнит " + unit.getName() +
                        " имеет недопустимую Y-координату: " + y);
                unit.setyCoordinate(Math.max(0, Math.min(FIELD_HEIGHT - 1, y)));
            }
        }
    }

    private int calculateTotalCost(List<Unit> units) {
        int total = 0;
        for (Unit unit : units) {
            total += unit.getCost();
        }
        return total;
    }

    // Вспомогательный класс для хранения эффективности юнита
    private static class UnitEfficiency {
        final Unit unit;
        final double attackRatio;
        final double healthRatio;

        UnitEfficiency(Unit unit, double attackRatio, double healthRatio) {
            this.unit = unit;
            this.attackRatio = attackRatio;
            this.healthRatio = healthRatio;
        }
    }
}