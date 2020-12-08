package GameLogic;

import Graphics.GameFrame;
import MessageSerialize.SnakesProto.*;
import MessageSerialize.SnakesProto.GameState.Snake;
import MessageSerialize.SnakesProto.GameState.Coord;
import Net.Analyzer;
import Utilities.CellModifier;
import Utilities.Pair;
import View.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GameCenter extends Thread {

    private final AtomicInteger nextPlayerId = new AtomicInteger(0);
    private int gameStateNumber = 0;
    private final int width;
    private final int height;
    private GameState actualGameState;
    private final GameConfig gameConfig;
    private final String playerName;
    private final ConcurrentHashMap<Integer, GamePlayer.Builder> players = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Snake.Builder> snakes = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Coord> food = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, Direction> nextDirection; //player's id and next direction
    private final GameFrame gameFrame;
    private DatagramSocket socket;
    private CompletableFuture<Void> completableFuture;
    private final CellModifier modifier;
    private final Analyzer analyzer;
    private final int gameId;

    public GameCenter(GameConfig config, String name, Cell[] cells,
                      ConcurrentHashMap<Integer, Direction> nextDirection) {
        gameConfig = config;
        playerName = name;
        width = gameConfig.getWidth();
        height = gameConfig.getHeight();
        this.nextDirection = nextDirection;
        gameFrame = new GameFrame(config, cells, nextDirection);
        modifier = new CellModifier(width, height, 5, cells);
        try {
            socket = new DatagramSocket();
        }
        catch (IOException exc) {
            System.err.println(exc.getMessage() + " in GameCenter");
            System.exit(1);
        }
        analyzer = new Analyzer(this, socket);
        analyzer.start();
        gameId = 0;
    }

    public void run() {
        initFirstState();
        ++gameStateNumber;
        gameFrame.repaint();
        
        completableFuture = CompletableFuture.runAsync(() -> {
            InetSocketAddress inetSocketAddress = new InetSocketAddress("239.192.0.4", 9192);
            DatagramPacket packet;
            boolean canJoin;

            while(!completableFuture.isCancelled()) {
                try {
                    modifier.getSnakeBase();
                    canJoin = true;
                }
                catch (IllegalStateException exc) {
                    canJoin = false;
                }
                GameMessage.AnnouncementMsg announcementMsg = GameMessage.AnnouncementMsg.newBuilder()
                        .setConfig(gameConfig)
                        .setCanJoin(canJoin)
                        .setPlayers(GamePlayers.newBuilder().addAllPlayers(players.values()
                                .stream().map(GamePlayer.Builder::build).collect(Collectors.toList())).build())
                        .build();

                byte[] gameMessageBytes = GameMessage.newBuilder()
                        .setAnnouncement(announcementMsg)
                        .setMsgSeq(UUID.randomUUID().getMostSignificantBits())
                        .build().toByteArray();

                packet = new DatagramPacket(gameMessageBytes, gameMessageBytes.length, inetSocketAddress);
                try {
                    socket.send(packet);
                } catch (IOException exc) {
                    System.err.println(exc.getMessage() + " in completableFuture(GameCenter)");
                    completableFuture.cancel(true);
                }
            }
        });

        long afterSleep = System.currentTimeMillis(), beforeSleep;
        try {
            while (!isInterrupted()) {
                beforeSleep = System.currentTimeMillis();
                Thread.sleep(Math.max(gameConfig.getStateDelayMs() - beforeSleep + afterSleep, 0));
                afterSleep = System.currentTimeMillis();
                updateFood();
                createNextState();
                gameFrame.repaint();
                ++gameStateNumber;
                gameFrame.updateTable(players.values());
            }
        }
        catch (InterruptedException ignored) {}
        socket.close();
        completableFuture.cancel(true);
        analyzer.interrupt();
    }

    private void initFirstState() {
//        addNewPlayer("Vasyok", "", 200, NodeRole.DEPUTY);
//        Pair<Integer, Direction> pl1 = modifier.positionNewSnake(modifier.getSnakeBase(), 1);
//        addNewSnake(1, pl1.getFirst(), pl1.getSecond());
        addNewSnake(gameId);
        addNewPlayer(playerName, "", 2020, NodeRole.MASTER, gameId);
        updateFood();
        actualGameState = createGameState(gameStateNumber);
    }

    public void addNewPlayer(String playerName, String address, int port, NodeRole role, int id) {
        players.put(players.size(), GamePlayer.newBuilder()
                .setName(playerName)
                .setId(id)
                .setIpAddress(address)
                .setPort(port)
                .setRole(role)
                .setType(PlayerType.HUMAN)
                .setScore(0));
    }

    public boolean addNewSnake(int id) throws IllegalStateException { //returns player id
        int baseLeftCorner, head;
        Pair<Integer, Direction> res;
        if (id == 0) {
            baseLeftCorner = (width - 5) / 2 + (height - 5) / 2 * width;
            res = modifier.positionNewSnake(baseLeftCorner, id);
        }
        else {
            synchronized (modifier) {
                try {
                    baseLeftCorner = modifier.getSnakeBase();
                }
                catch (IllegalStateException exception) {
                    return false;
                }
                res = modifier.positionNewSnake(baseLeftCorner, id);
            }
        }

        head = res.getFirst();
        Direction direction = res.getSecond();
        nextDirection.put(id, direction);
        Coord bodyCellOffset = switch (direction) {
            case UP -> Coord.newBuilder().setX(0).setY(1).build();
            case RIGHT -> Coord.newBuilder().setX(-1).setY(0).build();
            case DOWN -> Coord.newBuilder().setX(0).setY(-1).build();
            case LEFT -> Coord.newBuilder().setX(1).setY(0).build();
        };
        snakes.add((Snake.newBuilder()
                .setPlayerId(id)
                .addPoints(0, Coord.newBuilder()
                        .setX(head % width).setY(head / width).build()))
                .addPoints(1, bodyCellOffset)
                .setState(Snake.SnakeState.ALIVE)
                .setHeadDirection(direction));
        return true;
    }

    private void updateFood() {
        int online = 0;
        for (GamePlayer.Builder player : players.values()) {
            if (player.getRole() != NodeRole.VIEWER) {
                ++online;
            }
        }

        ArrayList<Integer> indexes = new ArrayList<>();
        int lack = gameConfig.getFoodStatic() + (int)(online * gameConfig.getFoodPerPlayer()) - food.size();
        if (lack == 0) {
            return;
        }

        int length = modifier.size();
        for (int it = 0; it < length; ++it) {
            if (modifier.get(it).getCellType() == CellType.EMPTY) {
                indexes.add(it);
            }
        }

        int emptyCellsAmount = indexes.size(), random, curIndex;
        for (int it = 0; it < lack && it < emptyCellsAmount; ++it) {
            random = (int)(Math.random() * indexes.size());
            curIndex = indexes.get(random);
            modifier.setFood(curIndex);
            indexes.remove(random);
            food.add(Coord.newBuilder().setX(curIndex % width).setY(curIndex / width).build());
        }
    }

    private GameState createGameState(int orderNum) {
        return GameState.newBuilder()
                .setStateOrder(orderNum)
                .addAllSnakes(snakes.stream().map(Snake.Builder::build).collect(Collectors.toCollection(ArrayList::new)))
                .addAllFoods(food)
                .setPlayers(GamePlayers.newBuilder().addAllPlayers(players.values().stream()
                        .map(GamePlayer.Builder::build).collect(Collectors.toCollection(ArrayList::new))).build())
                .setConfig(gameConfig).build();
    }

    private void createNextState() {
        //snake and index of next body position in cells with actual direction
        HashMap<Snake.Builder, Pair<Integer, Direction>> nextHeadIndexes = new HashMap<>();
        for (Snake.Builder snake : snakes) {
            Coord head = snake.getPoints(0);
            modifier.setSnakeBody(head.getY() * width + head.getX(), snake.getPlayerId());
            nextHeadIndexes.put(snake, getNextHeadIndex(snake));
        }

        Snake.Builder curSnakeBuilder;
        for (Map.Entry<Snake.Builder, Pair<Integer, Direction>> entry : nextHeadIndexes.entrySet()) {
            if (!modifier.get(entry.getValue().getFirst()).getCellType().equals(CellType.FOOD)) {
                curSnakeBuilder = entry.getKey();
                Coord tail = findTailCoords(curSnakeBuilder);
                modifier.setEmpty(tail.getY() * width + tail.getX());
                moveKeyPoint(curSnakeBuilder, curSnakeBuilder.getPointsCount() - 1, true);
            }
        }

        int id, nextCoordinate;
        HashSet<Snake.Builder> deadSnakes = new HashSet<>();
        for (Map.Entry<Snake.Builder, Pair<Integer, Direction>> entry : nextHeadIndexes.entrySet()) {
            curSnakeBuilder = entry.getKey();
            id = curSnakeBuilder.getPlayerId();
            nextCoordinate = entry.getValue().getFirst();
            switch (modifier.get(nextCoordinate).getCellType()) {
                case FOOD -> {
                    incrementPlayerScore(id);
                    moveSnakeHead(curSnakeBuilder, nextCoordinate, entry.getValue().getSecond());
                    food.remove(Coord.newBuilder().setX(nextCoordinate % width)
                            .setY(nextCoordinate / width).build());
                }
                case EMPTY -> moveSnakeHead(curSnakeBuilder, nextCoordinate, entry.getValue().getSecond());
                case SNAKE_BODY -> {
                    deadSnakes.add(curSnakeBuilder);
                    SnakeBodyCell bodyCell = (SnakeBodyCell) modifier.get(nextCoordinate);
                    incrementPlayerScore(bodyCell.getId());
                }
                case SNAKE_HEAD -> {
                    SnakeHeadCell anotherHead = (SnakeHeadCell) modifier.get(nextCoordinate);
                    deadSnakes.add(snakes.stream()
                            .filter(elem -> elem.getPlayerId() == anotherHead.getId())
                            .collect(Collectors.toList()).get(0));
                    deadSnakes.add(curSnakeBuilder);
                }
            }
        }
        for (Snake.Builder snake : deadSnakes) { //дополнить отправкой сообщений
            deleteSnake(snake);
        }
    }

    private void deleteSnake(Snake.Builder snake) {
        Coord head = snake.getPoints(0), keyPoint;
        int size = snake.getPointsCount(), x = head.getX(), y = head.getY();
        double deadFoodProb = gameConfig.getDeadFoodProb(), random = Math.random();
        if (random <= deadFoodProb) {
            modifier.setFood(y * width + x);
        }
        else modifier.setEmpty(y * width + x);

        for (int k = 1; k < size; ++k) {
            keyPoint = snake.getPoints(k);
            int keyX = keyPoint.getX(), keyY = keyPoint.getY(), module;
            module = (keyX == 0) ? Math.abs(keyY) : Math.abs(keyX);
            for (int it = 0; it < module; ++it) {
                if (keyX < 0) {
                    x = (x - 1 + width) % width;
                }
                if (keyX > 0) {
                    x = (x + 1) % width;
                }
                if (keyY < 0) {
                    y = (y - 1 + height) % height;
                }
                if (keyY > 0) {
                    y = (y + 1) % height;
                }
                random = Math.random();
                if (random <= deadFoodProb) {
                    modifier.setFood(y * width + x);
                }
                else modifier.setEmpty(y * width + x);
            }
        }
        snakes.remove(snake);
        players.get(snake.getPlayerId()).setRole(NodeRole.VIEWER);
    }

    private void moveSnakeHead(Snake.Builder snake, int nextHeadIndex, Direction nextDir) {
        modifier.setSnakeHead(nextHeadIndex, nextDir, snake.getPlayerId());
        snake.setPoints(0, Coord.newBuilder().setX(nextHeadIndex % width).setY(nextHeadIndex / width).build());
        if (nextDir == snake.getHeadDirection() && snake.getPointsCount() > 1) {
            moveKeyPoint(snake, 1, false);
        }
        else {
            Coord coord = switch (nextDir) {
                case UP -> Coord.newBuilder().setX(0).setY(1).build();
                case RIGHT -> Coord.newBuilder().setX(-1).setY(0).build();
                case DOWN -> Coord.newBuilder().setX(0).setY(-1).build();
                case LEFT -> Coord.newBuilder().setX(1).setY(0).build();
            };
            snake.addPoints(1, coord);
            snake.setHeadDirection(nextDir);
        }
    }

    private void incrementPlayerScore(int id) {
        GamePlayer.Builder player = players.get(id);
        player.setScore(player.getScore() + 1);
    }

    //returns index from cells and actual direction
    private Pair<Integer, Direction> getNextHeadIndex(Snake.Builder snake) {
        int playerId = snake.getPlayerId();
        if (areOpposite(snake.getHeadDirection(), nextDirection.get(playerId))) {
            nextDirection.put(playerId, snake.getHeadDirection()); //ignoring
        }
        int headX = snake.getPoints(0).getX(), headY = snake.getPoints(0).getY();
        int index = headY * width + headX;
        Direction actualDirection = nextDirection.get(playerId);
        int nextIndex = switch (nextDirection.get(playerId)) {
            case UP -> (headY == 0) ? (height - 1) * width + headX : index - width;
            case RIGHT -> (headX == width - 1) ? headY * width : index + 1;
            case DOWN -> (headY == height - 1) ? headX : index + width;
            case LEFT -> (headX == 0) ? headY * width + width - 1 : index - 1;
        };
        return new Pair<>(nextIndex, actualDirection);
    }

    private boolean areOpposite(Direction first, Direction second) {
        return switch (first) {
            case DOWN -> second == Direction.UP;
            case UP -> second == Direction.DOWN;
            case LEFT -> second == Direction.RIGHT;
            case RIGHT -> second == Direction.LEFT;
        };
    }

    private void moveKeyPoint(Snake.Builder snake, int index, boolean decrease) {
        Coord point = snake.getPoints(index);
        int x = point.getX(), y = point.getY();
        if (decrease) {
            if (x == 0) {
                y = (y < 0) ? y + 1 : y - 1;
            } else if (y == 0) {
                x = (x < 0) ? x + 1 : x - 1;
            }
        }
        else {
            if (x == 0) {
                y = (y < 0) ? y - 1 : y + 1;
            } else if (y == 0) {
                x = (x < 0) ? x - 1 : x + 1;
            }
        }

        if (x == 0 && y == 0) {
            snake.removePoints(index);
        }
        else {
            snake.setPoints(index, Coord.newBuilder().setX(x).setY(y).build());
        }
    }

    private Coord findTailCoords(Snake.Builder snake) {
        Coord head = snake.getPoints(0), key;
        int size = snake.getPointsCount(), x = head.getX(), y = head.getY();

        for (int k = 1; k < size; ++k) {
            key = snake.getPoints(k);
            int keyX = key.getX(), keyY = key.getY();
            if (keyX < 0) {
                x = (x + keyX + width) % width;
            }
            if (keyX > 0) {
                x = (x + keyX) % width;
            }
            if (keyY < 0) {
                y = (y + keyY + height) % height;
            }
            if (keyY > 0) {
                y = (y + keyY) % height;
            }
        }
        return Coord.newBuilder().setX(x).setY(y).build();
    }

    public void printSnake(Snake.Builder snake) {
        for (Coord coord : snake.getPointsList()) {
            System.out.print("(" + coord.getX() + ", " + coord.getY() + ") ");
        }
        System.out.println();
    }

    public int getNextPlayerId() {
        return nextPlayerId.incrementAndGet();
    }

    private Coord getCellCoordsByIndex(Snake.Builder snake, int index) {
        Coord head = snake.getPoints(0), key;
        if (index == 0) {
            return head;
        }

        int x = head.getX(), y = head.getY(), size = snake.getPointsCount(), it = 0;
        for (int k = 1; k < size; ++k) { //k бежит по ключевым точкам
            key = snake.getPoints(k);
            int keyX = key.getX(), keyY = key.getY(), module;
            if (it < index) {
                if (keyX != 0) {
                    module = Math.abs(keyX);
                    if (index <= it + module) {
                        return Coord.newBuilder().setX(x + (keyX > 0 ? 1 : -1) * (index - it)).setY(y).build();
                    }
                    x += keyX;
                }
                else {
                    module = Math.abs(keyY);
                    if (index <= it + module) {
                        return Coord.newBuilder().setY(y + (keyY > 0 ? 1 : -1) * (index - it)).setX(x).build();
                    }
                    y += keyY;
                }
                it += module;
            }
        }
        return Coord.newBuilder().setX(-1).setY(-1).build();
    }
}
