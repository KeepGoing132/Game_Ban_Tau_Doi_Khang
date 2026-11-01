package server.bot;

import common.model.*;
import java.util.*;

/** Knowledge-only opponent: never receives the human's fleet or Board. */
public final class BotBrain {
    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    private static final int UNKNOWN = 0, MISS = 1, HIT = 2, SUNK = 3;
    private static final int[] FLEET_SIZES = {5, 4, 3, 3, 2};

    private final Difficulty difficulty;
    private final Random random;
    /** What the bot knows about the enemy board: 0 unknown, 1 miss, 2 hit, 3 sunk. */
    private final int[][] known = new int[10][10];
    private final boolean[][] fired = new boolean[10][10];
    private final List<Integer> remaining = new ArrayList<>(Arrays.asList(5, 4, 3, 3, 2));

    public BotBrain(Difficulty difficulty, Random random) {
        this.difficulty = difficulty;
        this.random = random;
    }

    /** Chooses the next shot, weighted by the difficulty level. */
    public Coordinate choose() {
        List<Coordinate> candidates = new ArrayList<>();
        int[][] weights = new int[10][10];
        boolean hasHit = false;
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                if (known[x][y] == HIT) {
                    hasHit = true;
                }
            }
        }
        if (difficulty == Difficulty.HARD) {
            weightByDensity(weights, hasHit);
        }
        int best = -1;
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                if (fired[x][y]) {
                    continue;
                }
                int weight = 0;
                if (difficulty == Difficulty.HARD) {
                    weight = weights[x][y];
                }
                if (difficulty == Difficulty.MEDIUM) {
                    // hunt on the checkerboard pattern, and chase any neighbour of a hit
                    weight = (x + y) % 2 == 0 ? 1 : 0;
                    int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] direction : directions) {
                        int nextX = x + direction[0], nextY = y + direction[1];
                        if (nextX >= 0 && nextX < 10 && nextY >= 0 && nextY < 10 && known[nextX][nextY] == HIT) {
                            weight += 100;
                        }
                    }
                }
                if (weight > best) {
                    best = weight;
                    candidates.clear();
                }
                if (weight == best) {
                    candidates.add(new Coordinate(x, y));
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No shots left");
        }
        Coordinate choice = candidates.get(random.nextInt(candidates.size()));
        fired[choice.getX()][choice.getY()] = true;
        return choice;
    }

    /**
     * HARD: counts, for every remaining ship, how many placements would explain the current hits,
     * and prefers the cells that most of those placements cover.
     */
    private void weightByDensity(int[][] weights, boolean hasHit) {
        for (int size : remaining) {
            for (int horizontal = 0; horizontal < 2; horizontal++) {
                for (int x = 0; x < 10; x++) {
                    for (int y = 0; y < 10; y++) {
                        int dx = horizontal, dy = 1 - horizontal;
                        if (x + dx * (size - 1) >= 10 || y + dy * (size - 1) >= 10) {
                            continue;
                        }
                        boolean possible = true;
                        int hits = 0;
                        for (int i = 0; i < size; i++) {
                            int state = known[x + dx * i][y + dy * i];
                            if (state == MISS || state == SUNK) {
                                possible = false;
                            }
                            if (state == HIT) {
                                hits++;
                            }
                        }
                        if (!possible || (hasHit && hits == 0)) {
                            continue;
                        }
                        for (int i = 0; i < size; i++) {
                            weights[x + dx * i][y + dy * i] += 1 + 100 * hits * hits;
                        }
                    }
                }
            }
        }
    }

    /** Records the public result of one shot; the bot never sees the board itself. */
    public void observe(Coordinate point, ShotResult result, List<Coordinate> sunk) {
        fired[point.getX()][point.getY()] = true;
        known[point.getX()][point.getY()] = result == ShotResult.MISS ? MISS : HIT;
        if (result == ShotResult.SUNK && sunk != null) {
            remaining.remove(Integer.valueOf(sunk.size()));
            for (Coordinate cell : sunk) {
                known[cell.getX()][cell.getY()] = SUNK;
                fired[cell.getX()][cell.getY()] = true;
            }
        }
    }

    /** A random but always legal fleet, used by the bot and by the tests. */
    public static List<Ship> fleet(Random random) {
        boolean[][] occupied = new boolean[10][10];
        List<Ship> fleet = new ArrayList<>();
        int index = 0;
        for (int size : FLEET_SIZES) {
            while (true) {
                int dx = random.nextBoolean() ? 1 : 0, dy = 1 - dx;
                int x = random.nextInt(10 - dx * (size - 1));
                int y = random.nextInt(10 - dy * (size - 1));
                boolean valid = true;
                for (int i = 0; i < size; i++) {
                    if (occupied[x + i * dx][y + i * dy]) {
                        valid = false;
                    }
                }
                if (!valid) {
                    continue;
                }
                List<Coordinate> cells = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    occupied[x + i * dx][y + i * dy] = true;
                    cells.add(new Coordinate(x + i * dx, y + i * dy));
                }
                fleet.add(new Ship("BOT_" + (index++), size, cells));
                break;
            }
        }
        return fleet;
    }
}
