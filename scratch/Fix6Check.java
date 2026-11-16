import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Headless check of FIX 6: SnakeLevel.fromSegment maps a segment to a playable arena. */
public class Fix6Check {

    private static Object callStatic(String cls, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> c = Class.forName(cls);
        Method m = c.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static int getInt(Object o, String f) throws Exception {
        Field fl = o.getClass().getDeclaredField(f);
        fl.setAccessible(true);
        return fl.getInt(o);
    }

    private static String getStr(Object o, String f) throws Exception {
        Field fl = o.getClass().getDeclaredField(f);
        fl.setAccessible(true);
        return (String) fl.get(o);
    }

    private static int rows(Object level) throws Exception {
        Method m = level.getClass().getDeclaredMethod("rows");
        m.setAccessible(true);
        return (Integer) m.invoke(level);
    }

    public static void main(String[] args) throws Exception {
        int[][] cases = {
                // base,   limit, expectRows
                {1400, 400,  4},     // 400/100 = 4
                {6300, 900,  9},     // 900/100 = 9
                {4300, 100,  4},     // 1  -> clamped up to 4
                {1234, 3000, 10},    // 30 -> clamped down to 10
                {25099, 700, 7},     // 7
        };
        boolean ok = true;
        String[] names = {"Arcade_Sprites", "Audio_Engine", "High_Scores", "Level_Map_Data", "Seg_9"};

        for (int i = 0; i < cases.length; i++) {
            int base = cases[i][0], limit = cases[i][1], expect = cases[i][2];
            Object lv = callStatic("game.snake.SnakeLevel", "fromSegment",
                    new Class<?>[]{String.class, int.class, int.class},
                    names[i % names.length], base, limit);
            int heapLimit = getInt(lv, "heapLimit");
            int heapBase = getInt(lv, "heapBase");
            int srcBase = getInt(lv, "sourceBase");
            int srcLimit = getInt(lv, "sourceLimit");
            String srcName = getStr(lv, "sourceName");
            int r = rows(lv);
            boolean pass = heapLimit == expect * 100 && srcBase == base && srcLimit == limit
                    && srcName.equals(names[i % names.length]) && heapBase == 400 && r == 8 + expect;
            ok &= pass;
            System.out.printf("%s  base=%-6d limit=%-5d -> heapBase=%d heapLimit=%-4d rows=%-2d srcBase=%-6d srcLimit=%-5d name=%s  %s%n",
                    pass ? "PASS" : "FAIL", base, limit, heapBase, heapLimit, r, srcBase, srcLimit, srcName,
                    (srcLimit != heapLimit ? "(scaled from " + srcLimit + ")" : ""));
        }

        // The arena built from a segment must construct a SnakeModel without error.
        Class<?> modelCls = Class.forName("game.snake.SnakeModel");
        Constructor<?> ctor = modelCls.getDeclaredConstructor(Class.forName("game.snake.SnakeLevel"));
        ctor.setAccessible(true);
        Object lv = callStatic("game.snake.SnakeLevel", "fromSegment",
                new Class<?>[]{String.class, int.class, int.class}, "Arcade_Sprites", 1400, 400);
        Object model = ctor.newInstance(lv);
        int mRows = getInt(model, "rows");
        int mCols = getInt(model, "cols");
        int mHeapLimit = getInt(model, "heapLimit");
        System.out.printf("model: rows=%d cols=%d heapLimit=%d%n", mRows, mCols, mHeapLimit);
        ok &= mRows == rows(lv) && mHeapLimit == 400;

        System.out.println(ok ? "ALL FIX6 CHECKS PASS" : "FIX6 CHECKS FAILED");
    }
}
