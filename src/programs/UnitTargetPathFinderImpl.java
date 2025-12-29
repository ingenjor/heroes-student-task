package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.Edge;
import com.battle.heroes.army.programs.UnitTargetPathFinder;

import java.util.*;

/**
 * Поиск кратчайшего пути между юнитами на игровом поле.
 * Алгоритмическая сложность: O(V log V), где V = WIDTH × HEIGHT = 27 × 21 = 567
 * - V = 567 клеток
 * - Каждая клетка обрабатывается в худшем случае 1 раз
 * - Приоритетная очередь: O(log V) на операцию
 * - Итог: O(V log V)
 */
public class UnitTargetPathFinderImpl implements UnitTargetPathFinder {

    // Константы игрового поля
    private static final int WIDTH = 27;
    private static final int HEIGHT = 21;
    private static final int STRAIGHT_COST = 10;
    private static final int DIAGONAL_COST = 14;

    // 8 направлений движения (включая диагонали)
    private static final int[][] DIRECTIONS = {
            {0, 1},   // Вверх
            {1, 0},   // Вправо
            {0, -1},  // Вниз
            {-1, 0},  // Влево
            {1, 1},   // Вправо-вверх
            {1, -1},  // Вправо-вниз
            {-1, 1},  // Влево-вверх
            {-1, -1}  // Влево-вниз
    };

    // Внутренний класс для узла поиска
    private static class Node implements Comparable<Node> {
        final int x, y;
        int g; // Стоимость пути от старта
        int h; // Эвристическая оценка до цели
        Node parent;

        Node(int x, int y) {
            this.x = x;
            this.y = y;
            this.g = Integer.MAX_VALUE;
            this.h = 0;
            this.parent = null;
        }

        int f() {
            return g + h;
        }

        @Override
        public int compareTo(Node other) {
            return Integer.compare(this.f(), other.f());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Node node = (Node) obj;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    @Override
    public List<Edge> getTargetPath(Unit attackUnit, Unit targetUnit,
                                    List<Unit> existingUnitList) {
        // 1. Проверка входных данных
        if (attackUnit == null || targetUnit == null) {
            return Collections.emptyList();
        }

        if (!attackUnit.isAlive() || !targetUnit.isAlive()) {
            return Collections.emptyList();
        }

        // 2. Если existingUnitList null, создаем пустой список
        if (existingUnitList == null) {
            existingUnitList = Collections.emptyList();
        }

        // 3. Извлекаем координаты
        int startX = attackUnit.getxCoordinate();
        int startY = attackUnit.getyCoordinate();
        int targetX = targetUnit.getxCoordinate();
        int targetY = targetUnit.getyCoordinate();

        // 4. Проверка валидности координат
        if (!isValidCoordinate(startX, startY) || !isValidCoordinate(targetX, targetY)) {
            return Collections.emptyList();
        }

        // 5. Если начальная и конечная точки совпадают
        if (startX == targetX && startY == targetY) {
            List<Edge> path = new ArrayList<>();
            path.add(new Edge(startX, startY));
            return path;
        }

        // 6. Проверка прямой доступности (цель в соседней клетке)
        if (Math.abs(startX - targetX) <= 1 && Math.abs(startY - targetY) <= 1) {
            return checkDirectPath(startX, startY, targetX, targetY, existingUnitList,
                    attackUnit, targetUnit);
        }

        // 7. Создание карты препятствий
        boolean[][] obstacles = createObstacleMap(existingUnitList, attackUnit, targetUnit);

        // 8. Если цель находится на препятствии
        if (obstacles[targetX][targetY]) {
            return Collections.emptyList();
        }

        // 9. Поиск пути алгоритмом A*
        return findPathAStar(startX, startY, targetX, targetY, obstacles);
    }

    private boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    private List<Edge> checkDirectPath(int startX, int startY, int targetX, int targetY,
                                       List<Unit> units, Unit attacker, Unit target) {
        // Проверка диагональных углов
        if (Math.abs(startX - targetX) == 1 && Math.abs(startY - targetY) == 1) {
            for (Unit unit : units) {
                if (unit == null || !unit.isAlive()) continue;
                if (unit == attacker || unit == target) continue;

                int ux = unit.getxCoordinate();
                int uy = unit.getyCoordinate();

                // Проверяем клетки (startX, targetY) и (targetX, startY)
                if ((ux == startX && uy == targetY) || (ux == targetX && uy == startY)) {
                    return Collections.emptyList();
                }
            }
        }

        // Путь свободен
        List<Edge> path = new ArrayList<>();
        path.add(new Edge(startX, startY));
        path.add(new Edge(targetX, targetY));
        return path;
    }

    private boolean[][] createObstacleMap(List<Unit> units, Unit attacker, Unit target) {
        boolean[][] obstacles = new boolean[WIDTH][HEIGHT];

        for (Unit unit : units) {
            if (unit == null || !unit.isAlive()) continue;
            if (unit == attacker || unit == target) continue;

            int x = unit.getxCoordinate();
            int y = unit.getyCoordinate();

            if (isValidCoordinate(x, y)) {
                obstacles[x][y] = true;
            }
        }

        return obstacles;
    }

    private List<Edge> findPathAStar(int startX, int startY, int targetX, int targetY,
                                     boolean[][] obstacles) {
        // Инициализация узлов
        Node[][] nodes = new Node[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                nodes[x][y] = new Node(x, y);
            }
        }

        Node startNode = nodes[startX][startY];
        Node targetNode = nodes[targetX][targetY];

        startNode.g = 0;
        startNode.h = heuristic(startX, startY, targetX, targetY);

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        openSet.add(startNode);

        boolean[][] inOpenSet = new boolean[WIDTH][HEIGHT];
        inOpenSet[startX][startY] = true;

        boolean[][] closedSet = new boolean[WIDTH][HEIGHT];

        // Главный цикл A*
        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.x == targetX && current.y == targetY) {
                return reconstructPath(current);
            }

            inOpenSet[current.x][current.y] = false;
            closedSet[current.x][current.y] = true;

            // Проверяем всех соседей
            for (int[] dir : DIRECTIONS) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];

                // Проверка валидности клетки
                if (!isValidCoordinate(nx, ny) || obstacles[nx][ny] || closedSet[nx][ny]) {
                    continue;
                }

                // Для диагонального движения проверяем углы
                if (Math.abs(dir[0]) == 1 && Math.abs(dir[1]) == 1) {
                    if (obstacles[current.x][ny] || obstacles[nx][current.y]) {
                        continue;
                    }
                }

                // Стоимость движения
                int moveCost = (Math.abs(dir[0]) == 1 && Math.abs(dir[1]) == 1)
                        ? DIAGONAL_COST : STRAIGHT_COST;
                int tentativeG = current.g + moveCost;

                Node neighbor = nodes[nx][ny];

                if (tentativeG < neighbor.g) {
                    neighbor.parent = current;
                    neighbor.g = tentativeG;
                    neighbor.h = heuristic(nx, ny, targetX, targetY);

                    if (!inOpenSet[nx][ny]) {
                        openSet.add(neighbor);
                        inOpenSet[nx][ny] = true;
                    }
                }
            }
        }

        // Путь не найден
        return Collections.emptyList();
    }

    private int heuristic(int x1, int y1, int x2, int y2) {
        // Эвристика Чебышева (оптимальна для 8 направлений)
        int dx = Math.abs(x1 - x2);
        int dy = Math.abs(y1 - y2);
        return Math.max(dx, dy) * STRAIGHT_COST;
    }

    private List<Edge> reconstructPath(Node targetNode) {
        LinkedList<Edge> path = new LinkedList<>();
        Node current = targetNode;

        // Восстанавливаем путь от цели к старту
        while (current != null) {
            path.addFirst(new Edge(current.x, current.y));
            current = current.parent;
        }

        return new ArrayList<>(path);
    }
}