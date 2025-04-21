/*

{
  "select": ["id", "name", "age"],
  "table": "users_data",
  "where": {
    "and": [
      {"column": "age", "operator": ">", "value": 30},
      {"column": "country", "operator": "=", "value": "India"}
    ]
  },
  "group_by": ["country"],
  "order_by": [
    {"column": "age", "order": "DESC"}
  ],
  "limit": 100
}

*/

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;

public class JsonToAthenaSqlConverter {

    public static String convertJsonToSql(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        StringBuilder sql = new StringBuilder("SELECT ");

        // Handle SELECT
        if (root.has("select") && root.get("select").isArray()) {
            Iterator<JsonNode> elements = root.get("select").elements();
            while (elements.hasNext()) {
                sql.append(elements.next().asText());
                if (elements.hasNext()) sql.append(", ");
            }
        } else {
            sql.append("*");
        }

        // FROM
        sql.append(" FROM ");
        sql.append(root.get("table").asText());

        // WHERE
        if (root.has("where")) {
            sql.append(" WHERE ");
            sql.append(buildWhereClause(root.get("where")));
        }

        // GROUP BY
        if (root.has("group_by") && root.get("group_by").isArray()) {
            sql.append(" GROUP BY ");
            Iterator<JsonNode> elements = root.get("group_by").elements();
            while (elements.hasNext()) {
                sql.append(elements.next().asText());
                if (elements.hasNext()) sql.append(", ");
            }
        }

        // ORDER BY
        if (root.has("order_by") && root.get("order_by").isArray()) {
            sql.append(" ORDER BY ");
            Iterator<JsonNode> elements = root.get("order_by").elements();
            while (elements.hasNext()) {
                JsonNode order = elements.next();
                sql.append(order.get("column").asText())
                   .append(" ")
                   .append(order.get("order").asText());
                if (elements.hasNext()) sql.append(", ");
            }
        }

        // LIMIT
        if (root.has("limit")) {
            sql.append(" LIMIT ").append(root.get("limit").asInt());
        }

        return sql.toString();
    }

    private static String buildWhereClause(JsonNode whereNode) {
        if (whereNode.has("and")) {
            StringBuilder clause = new StringBuilder("(");
            Iterator<JsonNode> conditions = whereNode.get("and").elements();
            while (conditions.hasNext()) {
                clause.append(buildCondition(conditions.next()));
                if (conditions.hasNext()) clause.append(" AND ");
            }
            clause.append(")");
            return clause.toString();
        } else if (whereNode.has("or")) {
            StringBuilder clause = new StringBuilder("(");
            Iterator<JsonNode> conditions = whereNode.get("or").elements();
            while (conditions.hasNext()) {
                clause.append(buildCondition(conditions.next()));
                if (conditions.hasNext()) clause.append(" OR ");
            }
            clause.append(")");
            return clause.toString();
        } else {
            return buildCondition(whereNode);
        }
    }

    private static String buildCondition(JsonNode condition) {
        String column = condition.get("column").asText();
        String operator = condition.get("operator").asText();
        JsonNode valueNode = condition.get("value");

        String value;
        if (valueNode.isTextual()) {
            value = "'" + valueNode.asText() + "'";
        } else {
            value = valueNode.toString();
        }

        return column + " " + operator + " " + value;
    }

    public static void main(String[] args) throws IOException {
        String inputJson = """
            {
              "select": ["id", "name", "age"],
              "table": "users_data",
              "where": {
                "and": [
                  {"column": "age", "operator": ">", "value": 30},
                  {"column": "country", "operator": "=", "value": "India"}
                ]
              },
              "group_by": ["country"],
              "order_by": [
                {"column": "age", "order": "DESC"}
              ],
              "limit": 100
            }
        """;

        String sqlQuery = convertJsonToSql(inputJson);
        System.out.println("Generated SQL:\n" + sqlQuery);
    }
}
