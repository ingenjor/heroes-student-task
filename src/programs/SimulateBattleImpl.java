package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.PrintBattleLog;
import com.battle.heroes.army.programs.SimulateBattle;
import com.battle.heroes.util.GameSpeedUtil;

import java.util.*;

/**
 * Симуляция пошагового боя между армиями.
 * Алгоритмическая сложность: O(r × n log n), где n - общее число юнитов, r - число раундов
 * В худшем случае r ≤ n → O(n² log n)
 * - Сортировка: O(n log n) каждый раунд
 * - Обработка ходов: O(n) каждый раунд
 * - Всего раундов: O(n) в худшем случае
 */
public class SimulateBattleImpl implements SimulateBattle {
    // Зависимости будут устанавливаться через рефлексию игрой
    private PrintBattleLog printBattleLog;
    private GameSpeedUtil gameSpeedUtil;

    // Конструктор без параметров для рефлексии
    public SimulateBattleImpl() {
    }

    // Сеттеры для инъекции зависимостей (игра вызовет их через рефлексию)
    public void setPrintBattleLog(PrintBattleLog printBattleLog) {
        this.printBattleLog = printBattleLog;
    }

    public void setGameSpeedUtil(GameSpeedUtil gameSpeedUtil) {
        this.gameSpeedUtil = gameSpeedUtil;
    }

    @Override
    public void simulate(Army playerArmy, Army computerArmy) throws InterruptedException {
        // Проверка входных данных
        if (playerArmy == null || computerArmy == null) {
            throw new IllegalArgumentException("Армии не могут быть null");
        }

        // Объединяем все юниты для удобства обработки
        List<Unit> allUnits = new ArrayList<>();
        allUnits.addAll(playerArmy.getUnits());
        allUnits.addAll(computerArmy.getUnits());

        int round = 1;
        final int MAX_ROUNDS = 200; // Уменьшено для безопасности, но достаточно для любых армий

        System.out.println("=== НАЧАЛО БОЯ ===");

        // Главный цикл боя
        while (round <= MAX_ROUNDS) {
            // Проверка прерывания потока
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Бой прерван пользователем");
                return;
            }

            // 1. Получаем живых юнитов в начале раунда
            List<Unit> aliveUnits = getAliveUnits(allUnits);

            // Проверяем условия окончания боя
            if (aliveUnits.isEmpty()) {
                System.out.println("Все юниты погибли!");
                break;
            }

            boolean playerAlive = hasAliveUnits(playerArmy.getUnits());
            boolean computerAlive = hasAliveUnits(computerArmy.getUnits());

            if (!playerAlive || !computerAlive) {
                // Одна из армий уничтожена
                announceBattleResult(playerArmy, computerArmy);
                break;
            }

            System.out.println("\n--- Раунд " + round + " ---");
            System.out.println("Живых юнитов: " + aliveUnits.size());

            // 2. СОРТИРОВКА ПО УБЫВАНИЮ АТАКИ (ТРЕБОВАНИЕ ЗАДАНИЯ)
            // При равной атаке сортируем по имени для детерминированности
            aliveUnits.sort((u1, u2) -> {
                int attackDiff = Integer.compare(u2.getBaseAttack(), u1.getBaseAttack());
                if (attackDiff != 0) return attackDiff;
                // При одинаковой атаке сортируем по имени
                return u1.getName().compareTo(u2.getName());
            });

            // 3. Создаем копию списка для очереди ходов текущего раунда
            List<Unit> turnQueue = new ArrayList<>(aliveUnits);

            // 4. Каждый юнит в очереди делает ход
            int turnIndex = 0;
            while (turnIndex < turnQueue.size()) {
                // Проверка прерывания потока
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("Бой прерван пользователем во время хода");
                    return;
                }

                Unit attacker = turnQueue.get(turnIndex);

                // Пропускаем, если юнит умер до своего хода (в этом же раунде)
                if (attacker == null || !attacker.isAlive()) {
                    turnIndex++;
                    continue;
                }

                // Выполняем атаку
                try {
                    Unit target = attacker.getProgram().attack();

                    // Логирование атаки (ТРЕБОВАНИЕ ЗАДАНИЯ)
                    if (target != null) {
                        // Если printBattleLog установлен, используем его, иначе логируем в консоль
                        if (printBattleLog != null) {
                            printBattleLog.printBattleLog(attacker, target);
                        } else {
                            // Фоллбэк логгирование для отладки
                            System.out.println("[LOG] " + attacker.getName() + " атаковал " + target.getName());
                        }

                        System.out.println(attacker.getName() + " атаковал " + target.getName());

                        // Проверяем, умерла ли цель
                        if (!target.isAlive()) {
                            System.out.println(target.getName() + " погиб!");

                            // УДАЛЕНИЕ ПАВШЕГО ЮНИТА ИЗ ОЧЕРЕДИ ХОДОВ (ТРЕБОВАНИЕ ЗАДАНИЯ)
                            // Ищем цель в оставшейся части очереди
                            for (int i = turnIndex + 1; i < turnQueue.size(); i++) {
                                if (turnQueue.get(i) == target) {
                                    turnQueue.remove(i);
                                    break;
                                }
                            }
                        }
                    } else {
                        System.out.println(attacker.getName() + " не нашёл цель для атаки");
                    }

                    // Пауза для визуализации (если установлена скорость)
                    if (gameSpeedUtil != null && gameSpeedUtil.getGameSpeed() != null && gameSpeedUtil.getGameSpeed() > 0) {
                        Thread.sleep(gameSpeedUtil.getGameSpeed());
                    } else if (gameSpeedUtil != null && gameSpeedUtil.getGameSpeed() != null) {
                        // Если скорость = 0, не спим
                    } else {
                        // Если gameSpeedUtil не установлен, небольшая пауза для читаемости
                        Thread.sleep(50);
                    }

                } catch (InterruptedException e) {
                    // Пробрасываем прерывание дальше
                    System.out.println("Симуляция боя прервана");
                    throw e;
                } catch (Exception e) {
                    System.err.println("Ошибка при атаке " + attacker.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }

                turnIndex++;
            }

            // 5. Проверка окончания боя после раунда
            playerAlive = hasAliveUnits(playerArmy.getUnits());
            computerAlive = hasAliveUnits(computerArmy.getUnits());

            if (!playerAlive || !computerAlive) {
                announceBattleResult(playerArmy, computerArmy);
                break;
            }

            // Статистика после раунда
            System.out.println("После раунда " + round + ":");
            System.out.println("  Игрок: " + countAliveUnits(playerArmy.getUnits()) + " юнитов");
            System.out.println("  Компьютер: " + countAliveUnits(computerArmy.getUnits()) + " юнитов");

            round++;
        }

        // Если достигли максимального количества раундов
        if (round > MAX_ROUNDS) {
            System.out.println("\nБой остановлен после " + MAX_ROUNDS + " раундов (превышен лимит)");
            announceBattleResult(playerArmy, computerArmy);
        }
    }

    // Вспомогательные методы
    private List<Unit> getAliveUnits(List<Unit> units) {
        List<Unit> alive = new ArrayList<>();
        for (Unit unit : units) {
            if (unit != null && unit.isAlive()) {
                alive.add(unit);
            }
        }
        return alive;
    }

    private boolean hasAliveUnits(List<Unit> units) {
        for (Unit unit : units) {
            if (unit != null && unit.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private int countAliveUnits(List<Unit> units) {
        int count = 0;
        for (Unit unit : units) {
            if (unit != null && unit.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private void announceBattleResult(Army playerArmy, Army computerArmy) {
        int playerAlive = countAliveUnits(playerArmy.getUnits());
        int computerAlive = countAliveUnits(computerArmy.getUnits());

        System.out.println("\n=== ИТОГИ БОЯ ===");
        System.out.println("Армия игрока: " + playerAlive + " выживших");
        System.out.println("Армия компьютера: " + computerAlive + " выживших");

        if (playerAlive == 0 && computerAlive == 0) {
            System.out.println("НИЧЬЯ! Все юниты погибли.");
        } else if (playerAlive > 0 && computerAlive == 0) {
            System.out.println("ПОБЕДА ИГРОКА!");
        } else if (playerAlive == 0 && computerAlive > 0) {
            System.out.println("ПОБЕДА КОМПЬЮТЕРА!");
        } else {
            System.out.println("БОЙ ПРЕРВАН. Игрок: " + playerAlive + ", Компьютер: " + computerAlive);
        }
    }
}