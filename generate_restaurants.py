#!/usr/bin/python3.8

"""Restaurant demo data generator.

Example data generator created Frédéric Descamps.

Usage:
        $ python3 generate_restaurants.py

@Author: Frédéric Descamps
@Links: https://github.com/lefred/restaurants-mysql-ds
"""

import mysqlx
import sys
from random import choice, randrange
from faker import Faker


def connect():
    session = mysqlx.get_session(
        {
            "host": "localhost",
            "port": 33060,
            "user": "user",
            "password": "password",
            "ssl-mode": "REQUIRED",
        }
    )
    return session


def gen_cuisine():
    cuisine_list = [
        "Afghan",
        "African",
        "American",
        "Armenian",
        "Asian",
        "Australian",
        "Bagels/Pretzels",
        "Bakery",
        "Bangladeshi",
        "Barbecue",
        "Belgian",
        "Bottled beverages, including water, sodas, juices, etc.",
        "Brazilian",
        "Café/Coffee/Tea",
        "Cajun",
        "Californian",
        "Caribbean",
        "Chicken",
        "Chilean",
        "Chinese",
        "Chinese/Cuban",
        "Chinese/Japanese",
        "Continental",
        "Creole",
        "Creole/Cajun",
        "Czech",
        "Delicatessen",
        "Donuts",
        "Eastern European",
        "Egyptian",
        "English",
        "Ethiopian",
        "Filipino",
        "French",
        "Fruits/Vegetables",
        "German",
        "Greek",
        "Hamburgers",
        "Hawaiian",
        "Hotdogs",
        "Hotdogs/Pretzels",
        "Ice Cream, Gelato, Yogurt, Ices",
        "Indian",
        "Indonesian",
        "Iranian",
        "Irish",
        "Italian",
        "Japanese",
        "Jewish/Kosher",
        "Juice, Smoothies, Fruit Salads",
        "Korean",
        "Latin (Cuban, Dominican, Puerto Rican, South & Central American)",
        "Mediterranean",
        "Mexican",
        "Middle Eastern",
        "Moroccan",
        "Not Listed/Not Applicable",
        "Nuts/Confectionary",
        "Other",
        "Pakistani",
        "Pancakes/Waffles",
        "Peruvian",
        "Pizza",
        "Pizza/Italian",
        "Polish",
        "Polynesian",
        "Portuguese",
        "Russian",
        "Salads",
        "Sandwiches",
        "Sandwiches/Salads/Mixed Buffet",
        "Scandinavian",
        "Seafood",
        "Soul Food",
        "Soups",
        "Soups & Sandwiches",
        "Southwestern",
        "Spanish",
        "Steak",
        "Tapas",
        "Tex-Mex",
        "Thai",
        "Turkish",
        "Vegetarian",
        "Vietnamese/Cambodian/Malaysia",
    ]
    return choice(cuisine_list)


session = connect()
db = session.get_schema("docstore")
col = db.create_collection("restaurants", True)
fake = Faker()

print("Generating new documents.", end="", flush=True)

total = 1000

if len(sys.argv) > 1:
    if sys.argv[1]:
        total = int(sys.argv[1])

for _ in range(total):
    doc = {}
    doc["name"] = fake.company()
    address = {}
    address["street"] = fake.street_name()
    address["building"] = fake.building_number()
    address["zipcode"] = fake.postcode()
    doc["borough"] = fake.city()
    doc["cuisine"] = gen_cuisine()
    coord = []
    coord.append(float(fake.latitude()))
    coord.append(float(fake.longitude()))
    address["coord"] = coord
    doc["address"] = address
    grades = []
    for _ in range(randrange(5)):
        grade = {}
        grade_date = {}
        date = fake.date_time_this_decade()
        grade_date["$date"] = date.strftime("%Y-%m-%dT00:00:00.000+0000")
        grade_note = choice(["A", "B", "C"])
        grade_score = randrange(20)
        grade["date"] = grade_date
        grade["grade"] = grade_note
        grade["score"] = grade_score
        grades.append(grade)

    doc["grades"] = grades

    col.add(doc).execute()
    if total > 100000 and not _ % 1000:
        print(".", end="", flush=True)
    else:
        print(".", end="", flush=True)

print("\nDone ! {} documents generated.".format(total))
