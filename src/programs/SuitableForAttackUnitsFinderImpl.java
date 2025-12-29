package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.SuitableForAttackUnitsFinder;

import java.util.*;

/**
 * Определение доступных для атаки юнитов.
 * Алгоритмическая сложность: O(n × m), где n = количество юнитов, m = 3 ряда
 * Фактически O(3 × n) = O(n), что соответствует требованиям O(n·m) где m=3
 */
public class SuitableForAttackUnitsFinderImpl implements SuitableForAttackUnitsFinder {

    @Override
    public List<Unit> getSuitableUnits(List<List<Unit>> unitsByRow, boolean isLeftArmyTarget) {
        // Проверка входных данных
        if (unitsByRow == null || unitsByRow.size() != 3) {
            return Collections.emptyList();
        }

        List<Unit> suitableUnits = new ArrayList<>();

        // Определяем направление проверки "закрытости" согласно заданию:
        // - isLeftArmyTarget=true: атакуем левую армию (компьютер), проверяем закрытость справа
        // - isLeftArmyTarget=false: атакуем правую армию (игрока), проверяем закрытость слева

        // Для каждого ряда (фиксировано 3 ряда)
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            List<Unit> currentRow = unitsByRow.get(rowIndex);
            if (currentRow == null || currentRow.isEmpty()) continue;

            // Находим доступных юнитов в этом ряду
            List<Unit> availableInRow = findAvailableUnitsInRow(unitsByRow, rowIndex, isLeftArmyTarget);
            suitableUnits.addAll(availableInRow);
        }

        return suitableUnits;
    }

    /**
     * Находит доступных юнитов в одном ряду
     * Юнит доступен, если в соседнем ряду (слева или справа) на той же Y координате нет юнита
     */
    private List<Unit> findAvailableUnitsInRow(List<List<Unit>> allRows, int currentRowIndex,
                                               boolean isLeftArmyTarget) {
        List<Unit> available = new ArrayList<>();
        List<Unit> currentRow = allRows.get(currentRowIndex);

        // Определяем индекс соседнего ряда для проверки "закрытости"
        int checkRowIndex;
        if (isLeftArmyTarget) {
            // Атакуем левую армию (компьютер) → проверяем, не закрыт ли справа
            checkRowIndex = currentRowIndex + 1;
        } else {
            // Атакуем правую армию (игрока) → проверяем, не закрыт ли слева
            checkRowIndex = currentRowIndex - 1;
        }

        // Если соседний ряд существует, собираем занятые Y позиции
        Set<Integer> occupiedYInNeighbor = new HashSet<>();
        if (checkRowIndex >= 0 && checkRowIndex < allRows.size()) {
            List<Unit> neighborRow = allRows.get(checkRowIndex);
            if (neighborRow != null) {
                for (Unit unit : neighborRow) {
                    if (unit != null && unit.isAlive()) {
                        occupiedYInNeighbor.add(unit.getyCoordinate());
                    }
                }
            }
        }

        // Проверяем юнитов в текущем ряду
        for (Unit unit : currentRow) {
            if (unit != null && unit.isAlive()) {
                int yPos = unit.getyCoordinate();

                // Юнит доступен, если в соседнем ряду на той же Y нет юнита
                boolean isAvailable = !occupiedYInNeighbor.contains(yPos);

                if (isAvailable) {
                    available.add(unit);
                }
            }
        }

        return available;
    }
}