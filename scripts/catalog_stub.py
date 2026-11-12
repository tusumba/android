#!/usr/bin/env python3
"""Локальная HTTP-заглушка GET /v1/events для проверки Android без общего стенда."""

import argparse
import json
import time
from datetime import datetime, timedelta, date
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit
from zoneinfo import ZoneInfo

STATUSES = ("confirmed", "not_confirmed", "held", "not_held")


def demo_events(today):
    examples = (
        ("Вечер настольных игр", "Молодёжный центр", None),
        ("Музыка под открытым небом", "Городской парк", 50000),
        ("Мастерская керамики", "Арт-пространство", 90000),
        ("Научный лекторий", "Библиотека", None),
        ("Турнир по шахматам", "Шахматный клуб", 20000),
        ("Экскурсия по городу", "Центральная площадь", None),
    )
    result = []
    for number in range(48):
        title, place, price = examples[number % len(examples)]
        status = STATUSES[number % len(STATUSES)]
        offset = -(number + 1) if status in ("held", "not_held") else number + 1
        event = {
            "id": "9f1e6a52-3c4b-4d7e-8a10-" + format(number + 1, "012d"),
            "title": title + " · демо " + str(number + 1),
            "date": (today + timedelta(days=offset)).isoformat(),
            "place_address": place,
            "is_free": price is None,
            "status": status,
            "club_id": None,
            "photo_url": None,
        }
        if price is not None:
            event["price"] = {"amount": price, "currency": "RUB"}
        result.append(event)
    return result


class Handler(BaseHTTPRequestHandler):
    server_version = "TusumbaCatalogStub/1.0"
    args = None
    events = []

    def respond(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def error(self, status, code, message):
        self.respond(status, {"error": {"code": code, "message": message}})

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path == "/health":
            self.respond(200, {"status": "ok", "source": "catalog-stub"})
            return
        if url.path != "/v1/events":
            self.error(404, "not_found", "В заглушке есть только каталог мероприятий.")
            return
        params = parse_qs(url.query, keep_blank_values=True)
        try:
            page = int(params.get("page", ["1"])[0])
            size = int(params.get("size", ["20"])[0])
            if page < 1 or not 1 <= size <= 100:
                raise ValueError("Некорректная страница")
            today = datetime.now(ZoneInfo("Asia/Novosibirsk")).date()
            from_date = date.fromisoformat(params["date_from"][0]) if "date_from" in params else today
            to_date = date.fromisoformat(params["date_to"][0]) if "date_to" in params else None
            if to_date is not None and from_date > to_date:
                raise ValueError("Начальная дата позже конечной")
            is_free = params.get("is_free", [None])[0]
            if is_free not in (None, "true", "false"):
                raise ValueError("Некорректный тип")
            statuses = params.get("status", [])
            if any(status not in STATUSES for status in statuses):
                raise ValueError("Некорректный статус")
            query = params.get("query", [""])[0].strip()
            place = params.get("place", [""])[0].strip()
            if ("query" in params and not 1 <= len(query) <= 100) or ("place" in params and not 1 <= len(place) <= 200):
                raise ValueError("Некорректная длина фильтра")
        except (ValueError, IndexError):
            self.error(422, "validation_error", "Некорректные параметры каталога.")
            return
        time.sleep(self.args.delay)
        if self.args.scenario == "error" or self.args.fail_page == page:
            self.error(503, "service_unavailable", "Демонстрационная ошибка каталога.")
            return
        rows = [] if self.args.scenario == "empty" else [
            event for event in self.events
            if event["date"] >= from_date.isoformat()
            and (to_date is None or event["date"] <= to_date.isoformat())
            and (is_free is None or event["is_free"] == (is_free == "true"))
            and (not statuses or event["status"] in statuses)
            and (not place or place.casefold() in event["place_address"].casefold())
            and (not query or query.casefold() in event["title"].casefold())
        ]
        rows.sort(key=lambda event: (event["date"], event["id"]))
        start = (page - 1) * size
        self.respond(200, {"items": rows[start:start + size], "total": len(rows), "page": page, "size": size})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--scenario", choices=("success", "empty", "error"), default="success")
    parser.add_argument("--fail-page", type=int)
    parser.add_argument("--delay", type=float, default=0.35)
    args = parser.parse_args()
    if args.delay < 0 or (args.fail_page is not None and args.fail_page < 1):
        parser.error("Задержка неотрицательная, номер страницы начинается с 1")
    Handler.args = args
    Handler.events = demo_events(datetime.now(ZoneInfo("Asia/Novosibirsk")).date())
    with ThreadingHTTPServer(("127.0.0.1", args.port), Handler) as server:
        print("Локальная заглушка Events: http://127.0.0.1:" + str(args.port) + "/v1/events", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
