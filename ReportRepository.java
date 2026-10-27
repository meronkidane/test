import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
public final class ReportRepository {
    public List<String> find(Connection connection, String tenant, String status) throws SQLException {
        String sql = "SELECT document FROM invoices WHERE tenant = '" + tenant + "' AND status = '" + status + "'";
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            var documents = new ArrayList<String>();
            while (rows.next()) documents.add(rows.getString(1));
            return List.copyOf(documents);
        }
    }
}
