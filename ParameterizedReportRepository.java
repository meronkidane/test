import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
public final class ParameterizedReportRepository {
    public List<String> find(Connection connection, String authenticatedTenant, String status) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT document FROM invoices WHERE tenant = ? AND status = ?")) {
            statement.setString(1, authenticatedTenant);
            statement.setString(2, status);
            try (var rows = statement.executeQuery()) {
                var documents = new ArrayList<String>();
                while (rows.next()) documents.add(rows.getString(1));
                return List.copyOf(documents);
            }
        }
    }
}
