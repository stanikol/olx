#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Thu Sep 24 13:14:12 2020

@author: snc
"""
import pandas as pd
import re


    
df = pd.read_csv('/Users/snc/scala/olx3/export.csv', encoding="utf-8")
df.price = df.price.str.extract("([\s\d]*)")
df.price = df.price.str.replace("\s", "").astype(float)
assert(df.price.notnull().values.all())

df["size"] = df['Общая площадь'].str.extract("([\d\,\.\s]+)").values.astype(float)
assert (df['size'].notnull().values.all())

df["floor"] = pd.to_numeric(df['Этаж'], errors="coerce").fillna(-1).astype(int)
count_errors = len(df.floor.values[df.floor == -1])
if count_errors > 0:
    print(f"Found errors in floor: {count_errors}")

df.hist()
    
