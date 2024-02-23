#!/usr/bin/env python
import pymysql
from pymysql import ProgrammingError

try:
    import xml.etree.cElementTree as ET
except ImportError:
    import xml.etree.ElementTree as ET


def read_xml(file_path):
    tree = ET.parse(file_path)
    return tree.iter("device")


def insert_ua(e):
    ua = e.get('user_agent')
    if ua != '':
        try:
            sql = "insert into wurfl_ua values(\"" + e.get('id') + "\", \"" + e.get('fall_back') + "\",\"" + e.get(
                'user_agent') + "\");"
            print(sql)
            cur.execute(sql)
        except ProgrammingError:
            try:
                sql = "insert into wurfl_ua values('" + e.get('id') + "', '" + e.get('fall_back') + "','" + e.get(
                    'user_agent') + "');"
                print(sql)
                cur.execute(sql)
            except ProgrammingError:
                print('can not insert:', sql)


# CONNECT = ('10.14.41.61', 'root', '123456', 'venus_db')
db = pymysql.connect('10.14.41.61', 'root', '123456', 'venus_db')
cur = db.cursor()
# create table wurfl_ua(
#  id VARCHAR(100) not null UNIQUE,
#  fall_back VARCHAR(100) not null,
#  user_agent VARCHAR(500) not null
# );
SQL = 'insert into wurfl_ua values(%s, %s, %s);'

if __name__ == '__main__':
    file_path = 'wurfl.xml'
    elements = read_xml(file_path)
    for e in elements:
        insert_ua(e)
    db.commit()
    db.close()
