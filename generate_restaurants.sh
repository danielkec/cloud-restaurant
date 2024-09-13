## Create new dev env
# sudo apt install pipx
# python3 -m venv py-venv

## Install needed deps
py-venv/bin/pip3 install faker
py-venv/bin/pip3 install mysql-connector-python
py-venv/bin/pip3 install mysqlx-connector-python

## Generate
py-venv/bin/python3 generate_restaurants.py