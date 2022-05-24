import com.clickhouse.jdbc.*;
import java.sql.*;
import java.util.*;

public class clickhousetest {
    public static void main(String[] args) throws SQLException {
        var url = "jdbc:ch://localhost:8123";
        Properties properties = new Properties();
        properties.setProperty("sslmode","none");
        ClickHouseDataSource dataSource = new ClickHouseDataSource(url,properties);
        try (Connection connection = dataSource.getConnection("vedant.dokania", "Mind@123");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select * from system.tables limit 10")) {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int columns = resultSetMetaData.getColumnCount();
            while (resultSet.next()) {
                for (int c = 1; c <= columns; c++) {
                    System.out.print(resultSetMetaData.getColumnName(c) + ":" + resultSet.getString(c) + (c < columns ? ", " : "\n"));
                }
            }
        }

    }
}
