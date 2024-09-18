package com.acme.cloud.restaurant;

import java.time.Instant;
import java.util.ArrayList;
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
    private final Credentials cred;

    public record Credentials(String url, String schema, String collection) {}

    public RestaurantService() {
        this.cred = Config.global().get("db").as(Credentials.class).orElseThrow();
        this.client = new ClientFactory().getClient(cred.url(), new Properties());
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

        try (Session session = client.getSession()) {
            Collection coll = session.getSchema(cred.schema()).getCollection(cred.collection());
            RemoveStatement statement = coll
                    .remove("name like :name")
                    .bind("name", name);

            Result result = statement.execute();

            res.send("Deleted " + result.getAffectedItemsCount() + " docs.");
        }
    }

    private void createRestaurant(ServerRequest req, ServerResponse res) {
        DbDoc payload = JsonParser.parseDoc(req.content().as(String.class));

        try (Session session = client.getSession()) {
            Collection coll = session.getSchema(cred.schema()).getCollection(cred.collection());
            AddStatement statement = coll.add(payload);

            AddResult result = statement.execute();

            res.send("Created restaurant with id" + result.getGeneratedIds() + " docs.");
        }
    }

    private void getRestaurantByName(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");

        try (Session session = client.getSession()) {
            Collection coll = session.getSchema(cred.schema()).getCollection(cred.collection());
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
        }
    }

    private void rateRestaurantByName(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        String grade = req.path().pathParameters().get("grade");
        String score = req.path().pathParameters().get("score");

        try (Session session = client.getSession()) {
            Collection coll = session.getSchema(cred.schema()).getCollection(cred.collection());
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
        }
    }

    private void updateRestaurantByName(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        DbDoc payload = JsonParser.parseDoc(req.content().as(String.class));

        try (Session session = client.getSession()) {
            Collection coll = session.getSchema(cred.schema()).getCollection(cred.collection());
            ModifyStatement statement = coll.modify("name like :name")
                    .bind("name", name);

            statement.patch(payload);

            long affectedItemsCount = statement.execute()
                    .getAffectedItemsCount();

            res.send("Updated " + affectedItemsCount + " docs.");
        }
    }

    private void getRestaurantList(ServerRequest req, ServerResponse res) {
        Optional<Integer> limit = req.headers()
                .value(LIMIT_HEADER)
                .map(Integer::parseInt);
        try (Session session = client.getSession()) {
            Collection coll = session.getSchema(cred.schema()).getCollection(cred.collection());
            JsonArray result = coll.find()
                    .limit(limit.orElse(10))
                    .execute()
                    .fetchAll()
                    .stream()
                    .map(dbDoc -> dbDoc.get("name"))
                    .collect(JsonArray::new, ArrayList::add, ArrayList::addAll);
            res.send(result.toFormattedString());
        }
    }

    private void getLeaderBoard(ServerRequest req, ServerResponse res) {
        Optional<Integer> limit = req.headers()
                .value(LIMIT_HEADER)
                .map(Integer::parseInt);

        try (Session session = client.getSession()) {
            SqlResult result = session.sql(
                            """
                                    select json_pretty(json_arrayagg(json_object("name", name, "cuisine", cuisine, "avg_score", avg_score))) leader_board
                                    from (select *
                                          from (with cte1 as (select doc ->> "$.name"                                                              as name,
                                                                     doc ->> "$.cuisine"                                                           as cuisine,
                                                                     (select avg(score)
                                                                      from json_table(doc, "$.grades[*]" columns (score int path "$.score")) as r) as avg_score
                                                              from restaurants)
                                                select *, row_number() over ( partition by cuisine order by avg_score desc) as `rank`
                                                from cte1
                                                order by `rank`, avg_score desc) b
                                          where `rank` = 1 limit ?) dt;
                                    """)
                    .bind(limit.orElse(5))
                    .execute();

            res.send(result.fetchOne().getString("leader_board"));
        }
    }
}
