package com.acme.cloud.restaurant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import io.helidon.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import com.mysql.cj.xdevapi.AddResult;
import com.mysql.cj.xdevapi.AddStatement;
import com.mysql.cj.xdevapi.Client;
import com.mysql.cj.xdevapi.ClientFactory;
import com.mysql.cj.xdevapi.Collection;
import com.mysql.cj.xdevapi.DbDoc;
import com.mysql.cj.xdevapi.JsonArray;
import com.mysql.cj.xdevapi.JsonNumber;
import com.mysql.cj.xdevapi.JsonParser;
import com.mysql.cj.xdevapi.JsonString;
import com.mysql.cj.xdevapi.ModifyStatement;
import com.mysql.cj.xdevapi.RemoveStatement;
import com.mysql.cj.xdevapi.Result;
import com.mysql.cj.xdevapi.Session;
import com.mysql.cj.xdevapi.SqlResult;

class RestaurantService implements HttpService {

    private static final HeaderName LIMIT_HEADER = HeaderNames.create("limit");
    private final Client client;
    private final String schema;
    private final String collection;

    public RestaurantService(Config config) {
        this.client = new ClientFactory().getClient(config.get("db.url").asString().get(), new Properties());
        this.schema = config.get("db.schema").asString().orElse("docstore");
        this.collection = config.get("db.collection").asString().orElse("restaurants");
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .put("/{name}", this::updateRestaurantByName)
                .put("/{name}/{grade}/{score}", this::rateRestaurantByName)
                .delete("/{name}", this::deleteRestaurantByName)
                .get("/leader-board", this::getLeaderBoard)
                .get("/{name}", this::getRestaurantByName)
                .post(this::createRestaurant)
                .get(this::getRestaurantList);
    }

    private void deleteRestaurantByName(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        Session session = null;
        try {
            session = client.getSession();
            Collection coll = session.getSchema(schema).getCollection(collection);
            RemoveStatement statement = coll
                    .remove("name like :name")
                    .bind("name", name);

            Result result = statement.execute();

            res.send("Deleted " + result.getAffectedItemsCount() + " docs.");
        } finally {
            Optional.ofNullable(session).ifPresent(Session::close);
        }
    }

    private void createRestaurant(ServerRequest req, ServerResponse res) {
        DbDoc payload = JsonParser.parseDoc(req.content().as(String.class));
        Session session = null;
        try {
            session = client.getSession();
            Collection coll = session.getSchema(schema).getCollection(collection);
            AddStatement statement = coll.add(payload);

            AddResult result = statement.execute();

            res.send("Created restaurant with id" + result.getGeneratedIds() + " docs.");
        } finally {
            Optional.ofNullable(session).ifPresent(Session::close);
        }
    }

    private void getRestaurantByName(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        Session session = null;
        try {
            session = client.getSession();
            Collection coll = session.getSchema(schema).getCollection(collection);
            DbDoc result = coll.find("name = :name")
                    .bind("name", name)
                    .execute()
                    .fetchOne();

            if (result != null) {
                res.send(result.toFormattedString());
            } else {
                res.status(Status.NOT_FOUND_404)
                        .send("No restaurant found");
            }
        } finally {
            Optional.ofNullable(session).ifPresent(Session::close);
        }
    }

    private void rateRestaurantByName(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        String grade = req.path().pathParameters().get("grade");
        String score = req.path().pathParameters().get("score");

        Session session = null;
        try {
            session = client.getSession();
            Collection coll = session.getSchema(schema).getCollection(collection);
            ModifyStatement statement = coll.modify("name like :name")
                    .bind("name", name);

            DbDoc gradeDoc = coll.newDoc()
                    .add("grade", new JsonString().setValue(grade))
                    .add("score", new JsonNumber().setValue(score))
                    .add("date", coll.newDoc()
                            .add("$date", new JsonString().setValue(Instant.now().toString()))
                    );

            statement.arrayAppend("$grades", gradeDoc);

            long affectedItemsCount = statement.execute()
                    .getAffectedItemsCount();

            res.send("Updated " + affectedItemsCount + " docs.");
        } finally {
            Optional.ofNullable(session).ifPresent(Session::close);
        }
    }

    private void updateRestaurantByName(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        DbDoc payload = JsonParser.parseDoc(req.content().as(String.class));

        Session session = null;
        try {
            session = client.getSession();
            Collection coll = session.getSchema(schema).getCollection(collection);
            ModifyStatement statement = coll.modify("name like :name")
                    .bind("name", name);

            statement.patch(payload);

            long affectedItemsCount = statement.execute()
                    .getAffectedItemsCount();

            res.send("Updated " + affectedItemsCount + " docs.");
        } finally {
            Optional.ofNullable(session).ifPresent(Session::close);
        }
    }

    private void getRestaurantList(ServerRequest req, ServerResponse res) {
        Optional<Integer> limit = req.headers()
                .value(LIMIT_HEADER)
                .map(Integer::parseInt);
        Session session = null;
        try {
            session = client.getSession();
            Collection coll = session.getSchema(schema).getCollection(collection);
            JsonArray result = coll.find()
                    .limit(limit.orElse(20))
                    .execute()
                    .fetchAll()
                    .stream()
                    .map(dbDoc -> dbDoc.get("name"))
                    .collect(JsonArray::new, ArrayList::add, ArrayList::addAll);
            res.send(result.toFormattedString());
        } finally {
            Optional.ofNullable(session).ifPresent(Session::close);
        }
    }

    private void getLeaderBoard(ServerRequest req, ServerResponse res) {
        Optional<Integer> limit = req.headers()
                .value(LIMIT_HEADER)
                .map(Integer::parseInt);

        Session session = null;
        try {
            session = client.getSession();
            SqlResult result = session.sql(
                    """
                     WITH cte1 AS (SELECT doc ->> "$.name"            AS name,
                                          doc ->> "$.cuisine"         AS cuisine,
                                          (SELECT AVG(score)
                                           FROM JSON_TABLE(doc, "$.grades[*]" COLUMNS (score INT
                                               PATH "$.score")) AS r) AS avg_score
                                   FROM docstore.restaurants)
                     SELECT *,
                            RANK()
                                    OVER ( PARTITION BY cuisine ORDER BY avg_score) AS `rank`
                     FROM cte1
                     ORDER BY `rank`, avg_score DESC, name
                     LIMIT ?;
                     """)
                    .bind(limit.orElse(5))
                    .execute();

            List<Leader> board = result.fetchAll().stream()
                    .map(row -> new Leader(row.getString("name"),
                                           row.getString("cuisine"),
                                           row.getDouble("avg_score")))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            res.send(board);
        } finally {
            Optional.ofNullable(session).ifPresent(Session::close);
        }
    }

    public record Leader(String name, String cuisine, Double score) {}
}
