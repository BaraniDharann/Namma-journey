package com.travelplatform.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Puts a connection taken during an owner's request into that owner's row-level-security scope
 * (V28): {@code SET ROLE nj_owner_scope} plus the owner settings. Closing it resets both before
 * the pool gets it back; if the reset fails, the connection is evicted rather than handed to the
 * next request, which may be a customer's.
 */
public class OwnerScopedDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(OwnerScopedDataSource.class);

    public OwnerScopedDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return scoped(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return scoped(super.getConnection(username, password));
    }

    private Connection scoped(Connection connection) throws SQLException {
        OwnerScope.Bound bound = OwnerScope.current().orElse(null);
        if (bound == null) {
            return connection;
        }
        try {
            try (Statement s = connection.createStatement()) {
                s.execute("SET ROLE nj_owner_scope");
            }
            try (PreparedStatement p = connection.prepareStatement(
                    "SELECT set_config('nj.owner_id', ?, false), set_config('nj.platform', ?, false)")) {
                p.setString(1, Long.toString(bound.ownerId()));
                p.setString(2, bound.platform() ? "on" : "off");
                p.execute();
            }
            commitIfInTransaction(connection);
        } catch (SQLException e) {
            discard(connection);
            throw e;
        }
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                        closeUnscoped(connection);
                        return null;
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private void closeUnscoped(Connection connection) throws SQLException {
        if (connection.isClosed()) {
            return;
        }
        try {
            if (!connection.getAutoCommit()) {
                // Whatever the request left open must not carry the reset with it into a rollback.
                connection.rollback();
            }
            try (Statement s = connection.createStatement()) {
                s.execute("RESET ROLE; RESET nj.owner_id; RESET nj.platform");
            }
            commitIfInTransaction(connection);
        } catch (SQLException e) {
            log.warn("Could not reset an owner-scoped connection; evicting it: {}", e.getMessage());
            discard(connection);
            return;
        }
        connection.close();
    }

    private static void commitIfInTransaction(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    /** Never back to the pool: the next borrower could inherit this owner's scope. */
    private void discard(Connection connection) {
        try {
            if (getTargetDataSource() instanceof HikariDataSource hikari) {
                hikari.evictConnection(connection);
            } else {
                connection.abort(Runnable::run);
            }
        } catch (SQLException | RuntimeException e) {
            log.error("Could not discard an owner-scoped connection", e);
        }
    }
}
