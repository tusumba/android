#!/usr/bin/env python3
"""Локальная HTTP-заглушка Events, Identity и Ticketing для проверки #19059."""

import argparse
import json
import threading
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit
from uuid import uuid4
from zoneinfo import ZoneInfo

DEMO_USER = "5b0d3a4e-2c1f-4e8a-9d7b-000000000001"
ORGANIZER = "5b0d3a4e-2c1f-4e8a-9d7b-000000000002"
FRIENDS = (
    ("5b0d3a4e-2c1f-4e8a-9d7b-000000000003", "Маша", "going"),
    ("5b0d3a4e-2c1f-4e8a-9d7b-000000000004", "Руслан", "interested"),
)
APP_DEMO_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test_access_token"
VISIBILITIES = ("public", "friends_only", "anonymous")


def demo_events():
    today = datetime.now(ZoneInfo("Asia/Novosibirsk")).date()
    examples = (
        ("Вечер настольных игр", "Новосибирск, ул. Пирогова, 1", None),
        ("Музыка под открытым небом", "Новосибирск, Центральный парк", 50000),
        ("Мастерская керамики", "Новосибирск, ул. Ильича, 4", 90000),
        ("Научный лекторий", "Новосибирск, ул. Пирогова, 1", None),
        ("Турнир по шахматам", "Новосибирск, Морской проспект, 23", 20000),
        ("Экскурсия по городу", "Новосибирск, площадь Ленина", None),
    )
    events = {}
    for number, (title, address, price) in enumerate(examples, 1):
        event_id = "9f1e6a52-3c4b-4d7e-8a10-" + format(number, "012d")
        event = {
            "id": event_id,
            "title": title,
            "date": (today + timedelta(days=7 + number)).isoformat(),
            "place_address": address,
            "is_free": price is None,
            "status": "confirmed",
            "description": "Демонстрационное мероприятие для проверки страницы, карт и записи. "
                           "Выберите «Пойду» или «Интересно» и настройте видимость записи.",
            "organizer": {"type": "club", "club_id": "8a79a923-7788-4122-b010-000000000001", "club_name": "Клуб НГУ"},
            "current_version_id": "8a79a923-7788-4122-b011-" + format(number, "012d"),
            "version_no": 1,
        }
        if number == 6:
            event["organizer"] = {"type": "user", "user_id": ORGANIZER}
        if price is not None:
            event["price"] = {"amount": price, "currency": "RUB"}
        events[event_id] = event
    return events


class StubServer(ThreadingHTTPServer):
    def __init__(self, address, *, scenario="success", delay=0.35, registered=False):
        super().__init__(address, Handler)
        self.scenario = scenario
        self.delay = delay
        self.events = demo_events()
        self.registrations = {}
        self.lock = threading.Lock()
        if registered:
            event_id = "9f1e6a52-3c4b-4d7e-8a10-000000000004"
            self.registrations[(DEMO_USER, event_id)] = self.new_registration(event_id, "interested", "anonymous")

    @staticmethod
    def new_registration(event_id, status, visibility):
        return {
            "id": str(uuid4()), "event_id": event_id, "status": status, "visibility": visibility,
            "source": "free_signup", "created_at": datetime.now(timezone.utc).isoformat(),
        }


class Handler(BaseHTTPRequestHandler):
    server_version = "TusumbaEventStub/1.0"

    def respond(self, status, body=None):
        data = b"" if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def error(self, status, code, message):
        self.respond(status, {"error": {"code": code, "message": message}})

    def user(self):
        token = self.headers.get("Authorization", "")
        if self.server.scenario == "unauthorized":
            return None
        if token in ("Bearer " + APP_DEMO_TOKEN, "Bearer stub-user"):
            return DEMO_USER
        if token == "Bearer stub-organizer":
            return ORGANIZER
        return None

    def require_user(self):
        user = self.user()
        if user is None:
            self.error(401, "invalid_token", "Войдите, чтобы сохранить мероприятие в личном кабинете.")
        return user

    def page(self, rows, params):
        try:
            page = int(params.get("page", ["1"])[0])
            size = int(params.get("size", ["20"])[0])
            if page < 1 or not 1 <= size <= 100:
                raise ValueError()
        except ValueError:
            self.error(422, "validation_error", "Некорректная страница.")
            return
        start = (page - 1) * size
        self.respond(200, {"items": rows[start:start + size], "total": len(rows), "page": page, "size": size})

    def get_event(self, event_id):
        event = self.server.events.get(event_id)
        if event is None or self.server.scenario == "unavailable":
            self.error(404, "event_not_found", "мероприятие недоступно.")
            return None
        if self.server.scenario == "closed":
            event = dict(event)
            today = datetime.now(ZoneInfo("Asia/Novosibirsk")).date()
            event["date"] = (today - timedelta(days=1)).isoformat()
        return event

    def do_GET(self):
        url = urlsplit(self.path)
        parts = url.path.strip("/").split("/")
        params = parse_qs(url.query)
        if url.path == "/health":
            self.respond(200, {"status": "ok", "source": "event-detail-stub"})
            return
        time.sleep(self.server.delay)
        if self.server.scenario == "error":
            self.error(503, "service_unavailable", "Временная ошибка сервера.")
            return
        if url.path == "/v1/events":
            self.page(list(self.server.events.values()), params)
        elif url.path == "/v1/me/registrations":
            user = self.require_user()
            if user is not None:
                with self.server.lock:
                    rows = [dict(value) for (owner, _), value in self.server.registrations.items() if owner == user]
                self.page(sorted(rows, key=lambda row: row["created_at"], reverse=True), params)
        elif len(parts) == 3 and parts[:2] == ["v1", "users"]:
            profiles = {DEMO_USER: "demo", ORGANIZER: "Ольга", **{id_: name for id_, name, _ in FRIENDS}}
            name = profiles.get(parts[2])
            if name:
                self.respond(200, {"id": parts[2], "nickname": name})
            else:
                self.error(404, "user_not_found", "Пользователь не найден.")
        elif len(parts) in (3, 4) and parts[:2] == ["v1", "events"]:
            event = self.get_event(parts[2])
            if event is None:
                return
            if len(parts) == 3:
                self.respond(200, event)
                return
            if parts[3] not in ("friends-going", "participants"):
                self.error(404, "not_found", "Путь не поддерживается заглушкой.")
                return
            user = self.require_user()
            if user is None:
                return
            if parts[3] == "friends-going":
                rows = [{"user_id": id_, "status": status, "source": "free_signup"} for id_, _, status in FRIENDS]
                self.page(rows, params)
            elif user != ORGANIZER:
                self.error(403, "forbidden", "Список участников доступен организатору.")
            else:
                with self.server.lock:
                    rows = [
                        {"registration_id": row["id"], "user_id": None if row["visibility"] == "anonymous" else owner,
                         "status": row["status"], "visibility": row["visibility"], "source": row["source"]}
                        for (owner, event_id), row in self.server.registrations.items() if event_id == event["id"]
                    ]
                self.page(rows, params)
        else:
            self.error(404, "not_found", "Путь не поддерживается заглушкой.")

    def do_PUT(self):
        self.change_registration(cancel=False)

    def do_DELETE(self):
        self.change_registration(cancel=True)

    def change_registration(self, *, cancel):
        parts = urlsplit(self.path).path.strip("/").split("/")
        if len(parts) != 4 or parts[:2] != ["v1", "events"] or parts[3] != "registration":
            self.error(404, "not_found", "Путь не поддерживается заглушкой.")
            return
        user = self.require_user()
        if user is None:
            return
        time.sleep(self.server.delay)
        event = self.get_event(parts[2])
        if event is None:
            return
        today = datetime.now(ZoneInfo("Asia/Novosibirsk")).date().isoformat()
        if event["date"] < today:
            self.error(409, "event_finished", "Запись на мероприятие закрыта.")
            return
        if not event["is_free"]:
            self.error(409, "event_is_paid", "Мероприятие платное.")
            return
        if self.server.scenario in ("error", "registration-error"):
            self.error(503, "service_unavailable", "Не удалось сохранить запись. Попробуйте позже.")
            return
        key = (user, event["id"])
        if cancel:
            with self.server.lock:
                removed = self.server.registrations.pop(key, None)
            if removed is None:
                self.error(404, "registration_not_found", "Вы не записаны на это мероприятие.")
            else:
                self.respond(204)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length))
            status = body["status"]
            visibility = body.get("visibility")
            if status not in ("going", "interested") or visibility not in (*VISIBILITIES, None):
                raise ValueError()
        except (ValueError, KeyError, TypeError, UnicodeDecodeError):
            self.error(422, "validation_error", "Некорректные параметры записи.")
            return
        with self.server.lock:
            previous = self.server.registrations.get(key)
            result = dict(previous) if previous else self.server.new_registration(event["id"], status, visibility or "public")
            result["status"] = status
            if visibility is not None:
                result["visibility"] = visibility
            self.server.registrations[key] = result
        self.respond(200 if previous else 201, result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--delay", type=float, default=0.35)
    parser.add_argument("--scenario", choices=("success", "error", "unavailable", "closed", "registration-error", "unauthorized"), default="success")
    parser.add_argument("--registered", action="store_true", help="Предварительно записать demo на научный лекторий анонимно")
    args = parser.parse_args()
    if args.delay < 0:
        parser.error("Задержка не может быть отрицательной")
    with StubServer(("127.0.0.1", args.port), scenario=args.scenario, delay=args.delay, registered=args.registered) as server:
        print("Events/Ticketing stub: http://127.0.0.1:" + str(args.port), flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
