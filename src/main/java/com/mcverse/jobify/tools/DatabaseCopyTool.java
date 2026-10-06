package com.mcverse.jobify.tools;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Copies every row of every table from one database to another through plain JDBC, so the same tool moves data
 * H2 to PostgreSQL, PostgreSQL to PostgreSQL (another server), or PostgreSQL to MySQL, as long as both JDBC drivers
 * are on the class path. See docs/DATABASE_MIGRATION.md.
 *
 * <p>What it does, in order:
 * <ol>
 *   <li>Reads the table list and foreign keys from the <em>target</em>, which must already have its schema (start the
 *       application once against the empty target so Flyway creates it) and sorts the tables parents first.</li>
 *   <li>Refuses to run if any target table already has rows, unless {@code --truncate} is given (which empties the
 *       target tables, children first).</li>
 *   <li>Copies each table in batches, matching tables and columns by name ignoring case (H2 upper-cases names,
 *       PostgreSQL and MySQL do not), reading each value with the getter that fits the <em>target</em> column type.</li>
 *   <li>Resets identity counters so the next insert does not collide with a copied id.</li>
 *   <li>Compares row counts per table. Exit code 0 only if every table matches.</li>
 * </ol>
 * It never writes to the source and skips {@code flyway_schema_history}: the target's own history is the truth about
 * its schema version.
 *
 * <p>Arguments: {@code --source-url --source-user --source-password --target-url --target-user --target-password
 * [--truncate] [--dry-run]}. Passwords may also come from the environment ({@code COPY_SOURCE_PASSWORD},
 * {@code COPY_TARGET_PASSWORD}) so they stay out of shell history.
 */
public final class DatabaseCopyTool {

    private static final int BATCH_SIZE = 500;
    private static final String FLYWAY_HISTORY = "flyway_schema_history";

    /** One table's outcome, for the final report. */
    public record TableResult(String table, long sourceRows, long targetRows) {
        public boolean matches() { return sourceRows == targetRows; }
    }

    private final Connection source;
    private final Connection target;
    private final Appendable out;

    public DatabaseCopyTool(Connection source, Connection target, Appendable out) {
        this.source = source;
        this.target = target;
        this.out = out;
    }

    // ---------------------------------------------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        for (String required : List.of("source-url", "target-url")) {
            if (!opts.containsKey(required)) {
                System.err.println("Missing --" + required + ". See docs/DATABASE_MIGRATION.md.");
                System.exit(2);
            }
        }
        String sourcePassword = opts.getOrDefault("source-password", env("COPY_SOURCE_PASSWORD"));
        String targetPassword = opts.getOrDefault("target-password", env("COPY_TARGET_PASSWORD"));
        try (Connection src = DriverManager.getConnection(opts.get("source-url"),
                     opts.getOrDefault("source-user", ""), sourcePassword);
             Connection tgt = DriverManager.getConnection(opts.get("target-url"),
                     opts.getOrDefault("target-user", ""), targetPassword)) {
            DatabaseCopyTool tool = new DatabaseCopyTool(src, tgt, System.out);
            List<TableResult> results = tool.copy(opts.containsKey("truncate"), opts.containsKey("dry-run"));
            System.exit(results.stream().allMatch(TableResult::matches) ? 0 : 1);
        } catch (IllegalStateException | SQLException e) {
            System.err.println("ERROR: " + e.getMessage());
            if (e instanceof SQLException) {
                System.err.println("(Is the application still running against the H2 file? Stop it first: H2 locks the file.)");
            }
            System.exit(1);
        }
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null ? "" : v;
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String a : args) {
            if (!a.startsWith("--")) continue;
            int eq = a.indexOf('=');
            if (eq < 0) opts.put(a.substring(2), "true");
            else opts.put(a.substring(2, eq), a.substring(eq + 1));
        }
        return opts;
    }

    // ----------------------------------------------------------------------------------------------- the copy

    /**
     * @param truncate empty the target tables first instead of refusing when they have rows
     * @param dryRun   only list the tables, in copy order, with their source row counts
     */
    public List<TableResult> copy(boolean truncate, boolean dryRun) throws SQLException {
        String quote = target.getMetaData().getIdentifierQuoteString().trim();
        Map<String, String> targetTables = tableNames(target);          // lower-case name -> real name
        Map<String, String> sourceTables = tableNames(source);
        List<String> order = dependencyOrder(targetTables);

        List<String> missing = new ArrayList<>();
        for (String t : order) if (!sourceTables.containsKey(t)) missing.add(t);
        List<String> extra = new ArrayList<>(sourceTables.keySet());
        extra.removeAll(order);
        if (!extra.isEmpty()) {
            throw new IllegalStateException("The source has tables the target does not: " + extra
                    + ". Create the target schema with the application's migrations first, or the data would be lost.");
        }
        say("Tables to copy (parents first): " + order);
        if (!missing.isEmpty()) say("Not in the source, left empty: " + missing);

        if (dryRun) {
            List<TableResult> plan = new ArrayList<>();
            for (String t : order) {
                long n = sourceTables.containsKey(t) ? count(source, sourceTables.get(t), quote) : 0;
                say(String.format("  %-26s %8d rows", t, n));
                plan.add(new TableResult(t, n, 0));
            }
            say("Dry run: nothing was written.");
            return plan;
        }

        target.setAutoCommit(false);
        guardTargetEmpty(order, targetTables, quote, truncate);

        List<TableResult> results = new ArrayList<>();
        for (String t : order) {
            if (!sourceTables.containsKey(t)) {
                results.add(new TableResult(t, 0, count(target, targetTables.get(t), quote)));
                continue;
            }
            try {
                copyTable(sourceTables.get(t), targetTables.get(t), quote);
                target.commit();
            } catch (SQLException | RuntimeException e) {
                target.rollback();
                throw new IllegalStateException("Copying table '" + t + "' failed and was rolled back (earlier tables "
                        + "stay copied; re-run with --truncate to start clean): " + e.getMessage(), e);
            }
        }
        resetIdentityCounters(order, targetTables, quote);
        target.commit();

        say("");
        say(String.format("%-26s %10s %10s  %s", "table", "source", "target", ""));
        for (String t : order) {
            long s = sourceTables.containsKey(t) ? count(source, sourceTables.get(t), quote) : 0;
            long g = count(target, targetTables.get(t), quote);
            TableResult r = new TableResult(t, s, g);
            results.removeIf(x -> x.table().equals(t));
            results.add(r);
            say(String.format("%-26s %10d %10d  %s", t, s, g, r.matches() ? "ok" : "MISMATCH"));
        }
        boolean ok = results.stream().allMatch(TableResult::matches);
        say(ok ? "All tables match." : "ROW COUNTS DIFFER: do not switch over; see the messages above.");
        return results;
    }

    private void copyTable(String sourceTable, String targetTable, String q) throws SQLException {
        Map<String, ColumnInfo> targetCols = columns(target, targetTable);
        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery("select * from " + q + sourceTable + q)) {
            ResultSetMetaData md = rs.getMetaData();
            List<String> names = new ArrayList<>();
            List<ColumnInfo> infos = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                ColumnInfo info = targetCols.get(md.getColumnName(i).toLowerCase(Locale.ROOT));
                if (info == null) {
                    throw new IllegalStateException("column " + md.getColumnName(i) + " of " + sourceTable
                            + " does not exist in the target table; the schemas differ");
                }
                names.add(info.name);
                infos.add(info);
            }
            StringBuilder sql = new StringBuilder("insert into ").append(q).append(targetTable).append(q).append(" (");
            for (int i = 0; i < names.size(); i++) sql.append(i > 0 ? ", " : "").append(q).append(names.get(i)).append(q);
            sql.append(") values (").append("?, ".repeat(names.size() - 1)).append("?)");

            try (PreparedStatement ps = target.prepareStatement(sql.toString())) {
                int pending = 0;
                while (rs.next()) {
                    for (int i = 0; i < infos.size(); i++) bind(ps, i + 1, rs, i + 1, infos.get(i).jdbcType);
                    ps.addBatch();
                    if (++pending % BATCH_SIZE == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
        }
    }

    /** Reads with the getter that fits the target column, so CLOB, TEXT, BOOLEAN, TIMESTAMP etc. convert correctly. */
    private static void bind(PreparedStatement ps, int pIdx, ResultSet rs, int rIdx, int type) throws SQLException {
        switch (type) {
            case Types.BOOLEAN, Types.BIT -> {
                boolean v = rs.getBoolean(rIdx);
                if (rs.wasNull()) ps.setNull(pIdx, Types.BOOLEAN); else ps.setBoolean(pIdx, v);
            }
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> {
                int v = rs.getInt(rIdx);
                if (rs.wasNull()) ps.setNull(pIdx, Types.INTEGER); else ps.setInt(pIdx, v);
            }
            case Types.BIGINT -> {
                long v = rs.getLong(rIdx);
                if (rs.wasNull()) ps.setNull(pIdx, Types.BIGINT); else ps.setLong(pIdx, v);
            }
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> {
                double v = rs.getDouble(rIdx);
                if (rs.wasNull()) ps.setNull(pIdx, Types.DOUBLE); else ps.setDouble(pIdx, v);
            }
            case Types.DECIMAL, Types.NUMERIC -> {
                BigDecimal v = rs.getBigDecimal(rIdx);
                if (v == null) ps.setNull(pIdx, Types.NUMERIC); else ps.setBigDecimal(pIdx, v);
            }
            case Types.DATE -> {
                java.sql.Date v = rs.getDate(rIdx);
                if (v == null) ps.setNull(pIdx, Types.DATE); else ps.setDate(pIdx, v);
            }
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                java.sql.Timestamp v = rs.getTimestamp(rIdx);
                if (v == null) ps.setNull(pIdx, Types.TIMESTAMP); else ps.setTimestamp(pIdx, v);
            }
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] v = rs.getBytes(rIdx);
                if (v == null) ps.setNull(pIdx, Types.VARBINARY); else ps.setBytes(pIdx, v);
            }
            default -> { // VARCHAR, CHAR, LONGVARCHAR, CLOB (text) and anything textual
                String v = rs.getString(rIdx);
                if (v == null) ps.setNull(pIdx, Types.VARCHAR); else ps.setString(pIdx, v);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- guards, counters

    private void guardTargetEmpty(List<String> order, Map<String, String> names, String q, boolean truncate)
            throws SQLException {
        List<String> filled = new ArrayList<>();
        for (String t : order) if (count(target, names.get(t), q) > 0) filled.add(t);
        if (filled.isEmpty()) return;
        if (!truncate) {
            throw new IllegalStateException("The target already has data in " + filled + ". Nothing was changed. "
                    + "Use an empty target database, or pass --truncate to empty these tables first.");
        }
        List<String> reverse = new ArrayList<>(order);
        Collections.reverse(reverse);
        for (String t : reverse) {
            try (Statement st = target.createStatement()) {
                st.executeUpdate("delete from " + q + names.get(t) + q);
            }
        }
        target.commit();
        say("Emptied the target tables (--truncate).");
    }

    /** After copying explicit ids, the next generated id must be above the largest copied one. */
    private void resetIdentityCounters(List<String> order, Map<String, String> names, String q) throws SQLException {
        String product = target.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        for (String t : order) {
            String table = names.get(t);
            for (ColumnInfo c : columns(target, table).values()) {
                if (!c.autoIncrement) continue;
                long max;
                try (Statement st = target.createStatement();
                     ResultSet rs = st.executeQuery("select max(" + q + c.name + q + ") from " + q + table + q)) {
                    rs.next();
                    max = rs.getLong(1);
                }
                long next = max + 1;
                if (product.contains("postgres")) {
                    try (PreparedStatement ps = target.prepareStatement(
                            "select setval(pg_get_serial_sequence(?, ?), ?, false)")) {
                        ps.setString(1, q + table + q);
                        ps.setString(2, c.name);
                        ps.setLong(3, next);
                        ps.execute();
                    }
                } else if (product.contains("h2")) {
                    try (Statement st = target.createStatement()) {
                        st.execute("alter table " + q + table + q + " alter column " + q + c.name + q
                                + " restart with " + next);
                    }
                } // MySQL / MariaDB advance AUTO_INCREMENT by themselves when explicit ids are inserted.
                say("Identity " + table + "." + c.name + " continues at " + next);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ metadata

    private record ColumnInfo(String name, int jdbcType, boolean autoIncrement) {}

    private static Map<String, ColumnInfo> columns(Connection c, String table) throws SQLException {
        Map<String, ColumnInfo> cols = new LinkedHashMap<>();
        try (ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), c.getSchema(), table, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                cols.put(name.toLowerCase(Locale.ROOT), new ColumnInfo(name, rs.getInt("DATA_TYPE"),
                        "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"))));
            }
        }
        return cols;
    }

    /** lower-case name to real name, for the user tables of the current schema (no views, no Flyway history). */
    private static Map<String, String> tableNames(Connection c) throws SQLException {
        Map<String, String> names = new LinkedHashMap<>();
        try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), c.getSchema(), "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!name.equalsIgnoreCase(FLYWAY_HISTORY)) names.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return names;
    }

    /** Topological order of the target tables by foreign keys: a table comes after every table it references. */
    private List<String> dependencyOrder(Map<String, String> targetTables) throws SQLException {
        DatabaseMetaData md = target.getMetaData();
        Map<String, Set<String>> dependsOn = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : targetTables.entrySet()) {
            Set<String> parents = new HashSet<>();
            try (ResultSet rs = md.getImportedKeys(target.getCatalog(), target.getSchema(), e.getValue())) {
                while (rs.next()) {
                    String parent = rs.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT);
                    if (!parent.equals(e.getKey())) parents.add(parent); // a self-reference needs no ordering
                }
            }
            dependsOn.put(e.getKey(), parents);
        }
        List<String> order = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (order.size() < dependsOn.size()) {
            boolean progressed = false;
            for (Map.Entry<String, Set<String>> e : dependsOn.entrySet()) {
                if (done.contains(e.getKey())) continue;
                if (done.containsAll(e.getValue())) {
                    order.add(e.getKey());
                    done.add(e.getKey());
                    progressed = true;
                }
            }
            if (!progressed) {
                throw new IllegalStateException("The tables reference each other in a cycle; copy them by hand: "
                        + dependsOn.keySet().stream().filter(k -> !done.contains(k)).toList());
            }
        }
        return order;
    }

    private static long count(Connection c, String table, String q) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select count(*) from " + q + table + q)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void say(String line) {
        try {
            out.append(line).append('\n');
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
