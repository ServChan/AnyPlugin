package org.lts.anyplugin.tab;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.lts.anyplugin.core.Tasks;

public final class ServerStats {

    private ServerStats() {
    }

    public static double tps() {
        if (Tasks.FOLIA) {
            double[] region = region();
            return region == null ? -1 : region[0];
        }
        try {
            return Math.min(20.0, Bukkit.getTPS()[0]);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public static double mspt() {
        if (Tasks.FOLIA) {
            double[] region = region();
            return region == null ? -1 : region[1];
        }
        try {
            return Bukkit.getAverageTickTime();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public static String format(double value) {
        return value < 0 ? "-" : String.format(Locale.ROOT, "%.1f", value);
    }

    public static long uptimeMillis() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }

    private static double[] region() {
        try {
            Class<?> scheduler = Class.forName("io.papermc.paper.threadedregions.TickRegionScheduler");
            Object region = call(scheduler, null, "getCurrentRegion");
            if (region == null) {
                return null;
            }
            Object data = call(region.getClass(), region, "getData");
            Object handle = call(data.getClass(), data, "getRegionSchedulingHandle");
            Method reportMethod = handle.getClass().getMethod("getTickReport5s", long.class);
            reportMethod.setAccessible(true);
            Object report = reportMethod.invoke(handle, System.nanoTime());
            if (report == null) {
                return null;
            }
            double tps = average(call(report.getClass(), report, "tpsData"));
            double mspt = average(call(report.getClass(), report, "timePerTickData")) / 1.0e6;
            return new double[] {Math.min(20.0, tps), mspt};
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static double average(Object segmented) throws ReflectiveOperationException {
        Object all = call(segmented.getClass(), segmented, "segmentAll");
        Object value = call(all.getClass(), all, "average");
        return ((Number) value).doubleValue();
    }

    private static Object call(Class<?> type, Object target, String name) throws ReflectiveOperationException {
        Method method = type.getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
