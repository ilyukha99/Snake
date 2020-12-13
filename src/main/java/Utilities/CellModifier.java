package Utilities;

import MessageSerialize.SnakesProto;
import View.*;

public class CellModifier {

    private final int width;
    private final int height;
    private final int baseSize;
    private final Cell[] cells;
    private final int size;

    public CellModifier(int width, int height, int baseSize, Cell[] cells) {
        this.width = width;
        this.height = height;
        this.baseSize = baseSize;
        this.cells = cells;
        size = width * height;
        init();
    }

    public synchronized Cell get(int index) {
        if (index >= size || index < 0) {
            throw new IllegalStateException("Index out of bounds");
        }
        return cells[index];
    }

    public synchronized void setFood(int index) {
        cells[index] = new FoodCell();
    }

    public synchronized void setEmpty(int index) {
        cells[index] = new EmptyCell();
    }

    public synchronized void setSnakeBody(int index, int id) {
        cells[index] = new SnakeBodyCell(id);
    }

    public synchronized void setSnakeHead(int index, SnakesProto.Direction direction, int id) {
        cells[index] = new SnakeHeadCell(direction, id);
    }

    public synchronized void init() {
        for (int it = 0; it != size; ++it) {
            cells[it] = new EmptyCell();
        }
    }

    public synchronized int size() {
        return size;
    }

    public synchronized int getSnakeBase() {
        boolean flag;
        int size = width * height, it;
        for (it = 0; it % width <= (width - baseSize) && it / width <= (height - baseSize) && it < size; ++it) {
            flag = true;
            for (int i = 0; i < baseSize; ++i) {
                int index = it + i * width, stop = index + baseSize;
                for (int x = index; x < stop; ++x) {
                    CellType type = cells[x].getCellType();
                    if (type == CellType.SNAKE_BODY || type == CellType.SNAKE_HEAD) {
                        flag = false;
                        break;
                    }
                }
                if (!flag) {
                    break;
                }
            }
            if (flag) {
                int center = it + baseSize / 2 * (width + 1);
                if (!(cells[center - 1].getCellType() == CellType.FOOD
                        && cells[center + 1].getCellType() == CellType.FOOD
                        && cells[center - width].getCellType() == CellType.FOOD
                        && cells[center + width].getCellType() == CellType.FOOD
                        || cells[center].getCellType() == CellType.FOOD)) {
                    return it;
                }
            }
            if (it + 1 % width > (width - baseSize)) {
                it += baseSize - 1;
            }
        }
        throw new IllegalStateException("No base found");
    }

    //returns head and dir
    public synchronized Pair<Integer, SnakesProto.Direction> positionNewSnake(int leftCorner, int playerId) {
        int center = leftCorner + baseSize / 2 * (width + 1);

        int direction = (int)(Math.random() * 4);
        if (direction % 4 == 0) {
            if (cells[center + width].getCellType() != CellType.FOOD) {
                cells[center] = new SnakeHeadCell(SnakesProto.Direction.UP, playerId);
                cells[center + width] = new SnakeBodyCell(playerId);
                return new Pair<>(center, SnakesProto.Direction.UP);
            }
            ++direction;
        }
        if (direction % 4 == 1) {
            if (cells[center - 1].getCellType() != CellType.FOOD) {
                cells[center] = new SnakeHeadCell(SnakesProto.Direction.RIGHT, playerId);
                cells[center - 1] = new SnakeBodyCell(playerId);
                return new Pair<>(center, SnakesProto.Direction.RIGHT);
            }
            ++direction;
        }
        if (direction % 4 == 2) {
            if (cells[center - width].getCellType() != CellType.FOOD) {
                cells[center] = new SnakeHeadCell(SnakesProto.Direction.DOWN, playerId);
                cells[center - width] = new SnakeBodyCell(playerId);
                return new Pair<>(center, SnakesProto.Direction.DOWN);
            }
            ++direction;
        }
        if (direction % 4 == 3) {
            if (cells[center + 1].getCellType() != CellType.FOOD) {
                cells[center] = new SnakeHeadCell(SnakesProto.Direction.LEFT, playerId);
                cells[center + 1] = new SnakeBodyCell(playerId);
                return new Pair<>(center, SnakesProto.Direction.LEFT);
            }
            ++direction;
        }
        throw new IllegalStateException("How can dis be");
    }

    public synchronized void setEmpty() {
        for (int it = 0; it < size; ++it) {
            if (!cells[it].getCellType().equals(CellType.EMPTY)) {
                cells[it] = new EmptyCell();
            }
        }
    }
}
