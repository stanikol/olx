#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Thu Sep 24 13:14:12 2020

@author: snc
"""
import pandas as pd
import re
import pymongo

MONGO_DB_NAME = 'olx'
MONGO_COLECTION_NAME = "4seasons_20200929"

mongo_client = pymongo.MongoClient('mongodb://localhost:27017/')
db = mongo_client[MONGO_DB_NAME]
mongo_collection = db[MONGO_COLECTION_NAME]
json = mongo_collection.find()
df = pd.DataFrame.from_records(json)
df = df[df['section'] == 'Объявление Одесса/Недвижимость Одесса/Квартиры, комнаты Одесса/Продажа квартир, комнат Одесса']
convert_cols = ['price', 'Количество комнат', 'Этаж', 'Этажность', 
                'Площадь кухни', 'Общая площадь', 'Площадь участка',
                'viewed']

def convert_column(sr: pd.Series) -> pd.Series:
    return sr.str.replace(r'\s', '') \
             .str.extract(r'([\d\.\,]+).*', expand=False) \
             .astype(float)

for col in convert_cols:
    if col in df.columns:
        df[col] = convert_column(df[col])
        print(f'finished converting `{col}`')
    
df.hist(figsize=(20,20))
df.plot.scatter('Общая площадь', 'price')

