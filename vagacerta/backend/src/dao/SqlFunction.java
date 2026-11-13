package dao;

import java.sql.SQLException;

@FunctionalInterface
public interface SqlFunction<T, R> {
    R aplicar(T entrada) throws SQLException;
}
