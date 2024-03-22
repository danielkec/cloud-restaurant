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
python3 generate_restaurants.py
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
curl --json '{"name": "U Pinkasů", "borough": "Prague", "cuisine": "Italian", "address": {"building": "756","coord": [50.083379906386426, 14.423576184193138],"street": "Jungmannovo nám.", "zipcode": "11000"}}' -X POST localhost:8080/restaurant
```

Update restaurant
```sh
curl --json '{"cuisine": "Bohemian"}' -X PUT localhost:8080/restaurant/U%20Pinkasů
```

Get restaurant by name
```sh
curl -s localhost:8080/restaurant/U%20Pinkasů | jq
```

Delete restaurant
```sh
curl -X DELETE localhost:8080/restaurant/U%20Pinkasů
```