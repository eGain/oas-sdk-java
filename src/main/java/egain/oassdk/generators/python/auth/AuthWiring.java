package egain.oassdk.generators.python.auth;

/**
 * Resolved FastAPI auth dependency wiring for one operation.
 *
 * @param dependsExpr   Python expression passed to {@code Depends(...)}
 * @param strategyClass Python security strategy class name to import
 */
public record AuthWiring(String dependsExpr, String strategyClass) {
}
