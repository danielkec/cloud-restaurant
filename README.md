# Cloud Restaurant

Sample Helidon 4 SE project with MySQL Document Store.

## Build and run


With JDK21 and newer.
```bash
mvn package
java -jar target/cloud-restaurant-se.jar
```

## Prepare local MySQL cloud restaurant database

Prepare and start local MySQL database.
```sh
docker build -t mysql/docstore -f Dockerfile.mysql .
docker run --network host --name=mysql --rm -d mysql/docstore
docker run --network host --name=mysql -d mysql/docstore
```
Generate demo restaurant data with python script.
```sh
bash generate_restaurants.sh
```
Optionally check generated restaurant data presence.
```sql
SELECT doc->>'$.name' FROM restaurants LIMIT 10
```

## Exercise the application
List restaurant names
```sh
curl -s localhost:8080/restaurant | jq
```

Create restaurant
```sh
curl --json '{"name": "U Pinkasů", "borough": "Prague", "cuisine": "Czech", "grades": [], "address": {"building": "756","coord": [50.083379906386426, 14.423576184193138],"street": "Jungmannovo nám.", "zipcode": "11000"}}' -X POST localhost:8080/restaurant
```

Update cuisine by name
```sh
curl --json '{"cuisine": "Bohemian"}' -X PUT localhost:8080/restaurant/U%20Pinkasů
```

Rate restaurant with grade A and score 9
```sh
curl -X PUT localhost:8080/restaurant/U%20Pinkasů/A/9
```

Get restaurant by name
```sh
curl -s localhost:8080/restaurant/U%20Pinkasů | jq
```

Show restaurant leader board
```sh
curl -s localhost:8080/restaurant/leader-board | jq
```

Delete restaurant
```sh
curl -X DELETE localhost:8080/restaurant/U%20Pinkasů
```