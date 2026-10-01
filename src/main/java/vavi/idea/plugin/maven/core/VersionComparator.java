/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;


/**
 * VersionComparator, a simplified maven version ordering.
 * <p>
 * numeric tokens are compared as numbers, a qualifier like alpha &lt; beta &lt; milestone &lt; rc &lt; snapshot &lt; release.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class VersionComparator implements Comparator<String> {

    private static final Pattern SPLIT = Pattern.compile("[.\\-_]|(?<=\\d)(?=\\D)|(?<=\\D)(?=\\d)");
    private static final List<String> QUALIFIERS = List.of("alpha", "beta", "milestone", "rc", "snapshot", "", "sp");
    private static final Pattern UNSTABLE = Pattern.compile("(?i).*(alpha|beta|[.\\-]m\\d|milestone|rc|cr|snapshot|preview|ea|dev).*");

    /** whether the version is a release (not alpha/beta/rc/snapshot...) */
    public static boolean isStable(String version) {
        // jitpack also has commit hash versions (-8cacd12725-1) and branch snapshots
        return Character.isDigit(version.charAt(0)) && !UNSTABLE.matcher(version).matches();
    }

    private static List<String> tokens(String v) {
        List<String> r = new ArrayList<>();
        for (String t : SPLIT.split(v.toLowerCase())) {
            if (!t.isEmpty()) r.add(t);
        }
        // "final" / "ga" and zeros at the end or just before a qualifier are insignificant
        while (!r.isEmpty() && (r.getLast().equals("final") || r.getLast().equals("ga"))) {
            r.removeLast();
        }
        for (int i = r.size() - 1; i >= 0; i--) {
            if (r.get(i).equals("0") && (i == r.size() - 1 || !isNum(r.get(i + 1)))) {
                r.remove(i);
            }
        }
        return r;
    }

    private static int rank(String q) {
        switch (q) {
            case "a" -> q = "alpha";
            case "b" -> q = "beta";
            case "m" -> q = "milestone";
            case "cr" -> q = "rc";
            case "ga", "final", "release" -> q = "";
        }
        int i = QUALIFIERS.indexOf(q);
        return i < 0 ? QUALIFIERS.indexOf("sp") + 1 : i; // unknown qualifier sorts above all
    }

    private static boolean isNum(String s) {
        return Character.isDigit(s.charAt(0));
    }

    @Override
    public int compare(String o1, String o2) {
        List<String> a = tokens(o1), b = tokens(o2);
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            String x = i < a.size() ? a.get(i) : null;
            String y = i < b.size() ? b.get(i) : null;
            int c;
            if (x == null) {
                c = isNum(y) ? -1 : -Integer.compare(rank(y), rank(""));
            } else if (y == null) {
                c = isNum(x) ? 1 : Integer.compare(rank(x), rank(""));
            } else if (isNum(x) && isNum(y)) {
                c = new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y));
            } else if (isNum(x)) {
                c = 1;
            } else if (isNum(y)) {
                c = -1;
            } else {
                c = Integer.compare(rank(x), rank(y));
                if (c == 0) c = x.compareTo(y);
            }
            if (c != 0) return c;
        }
        return 0;
    }
}
