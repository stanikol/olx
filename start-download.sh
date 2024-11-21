#!/usr/bin/env bash
curl -X POST http://localhost:8080/olx/run \
     -H "Content-Type: application/x-www-form-urlencoded" \
     --data-urlencode "name=Test-1"\
     --data-urlencode "count=5"\
     --data-urlencode "url=https://www.olx.ua/uk/nedvizhimost/odessa/q-4-%D1%81%D0%B5%D0%B7%D0%BE%D0%BD%D0%B0/?currency=UAH" \
