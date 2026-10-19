"""Check the packaged Volumes API using only Python's standard library."""
from contextlib import contextmanager
import os
import secrets
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import urllib.parse
import http.cookiejar
import uuid


ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "target/volumes-0.1.0-SNAPSHOT.jar"


class ApiSession:
    def __init__(self, base_url):
        self.base_url = base_url
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None

    def refresh_csrf(self):
        self.csrf = self.request("GET", "/auth/csrf", 200)[0]["token"]

    def login(self, account):
        self.refresh_csrf()
        self.request("POST", "/auth/login", 204, account)
        self.refresh_csrf()

    def request(self, method, path, expected_status, body=None):
        is_login = path == "/auth/login"
        data = None if body is None else (urllib.parse.urlencode(body).encode() if is_login else json.dumps(body).encode())
        headers = {"Content-Type": "application/x-www-form-urlencoded" if is_login else "application/json"}
        if self.csrf is not None and method != "GET":
            headers["X-CSRF-TOKEN"] = self.csrf
        req = urllib.request.Request(self.base_url + path, data=data, method=method, headers=headers)
        try:
            response = self.opener.open(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            payload = response.read().decode()
            if response.status != expected_status:
                raise AssertionError(f"{method} {path}: {response.status}: {payload}")
            return (json.loads(payload) if payload else None), response.headers


def check_api(base_url):
    client = ApiSession(base_url)
    request = client.request
    client.refresh_csrf()
    account = {"email": " Reader@Example.com ", "password": "a local smoke test passphrase"}
    registered, _ = request("POST", "/auth/register", 201, account)
    assert set(registered) == {"id", "email"}
    assert registered["email"] == "reader@example.com"
    request("POST", "/auth/register", 409, account)
    print("PASS: registration returns account identity and rejects duplicate email")
    client.login(account)
    assert request("GET", "/auth/me", 200)[0] == registered
    assert request("GET", "/books", 200)[0] == []
    original = {"title": "  Crime and Punishment  ", "author": "Fyodor Dostoevsky",
                "genre": "Fiction", "pages": 350}
    created, headers = request("POST", "/books", 201, original)
    path = headers["Location"]
    assert path == "/books/" + created["id"]
    assert created["title"] == "Crime and Punishment"
    assert request("GET", path, 200)[0] == created
    replacement = {"title": "The Idiot", "author": "Fyodor Dostoevsky",
                   "genre": "Classic fiction", "pages": 600}
    updated, _ = request("PUT", path, 200, replacement)
    assert updated == dict(replacement, id=created["id"])
    request("PUT", path, 400, dict(replacement, pages=0))
    request("POST", "/books", 400, dict(replacement, pages=3.5))
    assert request("GET", path, 200)[0] == updated
    assert request("GET", "/books", 200)[0] == [updated]
    request("GET", "/books/00000000-0000-0000-0000-000000000000", 404)
    request("GET", "/books/not-a-uuid", 400)
    print("PASS: packaged Volumes API creates, lists, retrieves, updates, and rejects invalid input")
    library_path = "/me/library/" + updated["id"]
    assert request("GET", "/me/library", 200)[0] == []
    assert request("POST", "/me/library", 201, {"bookId": updated["id"]})[0]["status"] == "PENDING"
    request("POST", "/me/library", 409, {"bookId": updated["id"]})
    assert request("PUT", library_path + "/rating", 200, {"rating": 5})[0]["rating"] == 5
    request("PUT", library_path + "/rating", 400, {"rating": 6})
    assert request("PATCH", library_path, 200, {"status": "READING"})[0]["rating"] == 5
    bob = ApiSession(base_url)
    bob.refresh_csrf()
    bob_account = {"email": "bob@example.com", "password": "another smoke test passphrase"}
    bob.request("POST", "/auth/register", 201, bob_account)
    bob.login(bob_account)
    assert bob.request("GET", "/me/library", 200)[0] == []
    bob.request("PATCH", library_path, 404, {"status": "FINISHED"})
    bob.request("DELETE", library_path, 404)
    bob.request("PUT", library_path + "/rating", 404, {"rating": 1})
    bob.request("POST", "/me/library", 201, {"bookId": updated["id"]})
    bob.request("PATCH", library_path, 200, {"status": "FINISHED"})
    bob.request("PUT", library_path + "/rating", 200, {"rating": 1})
    bob.request("DELETE", library_path + "/rating", 204)
    bob.request("DELETE", library_path + "/rating", 204)
    assert bob.request("GET", "/me/library", 200)[0][0]["rating"] is None
    assert request("GET", "/me/library", 200)[0][0]["rating"] == 5
    assert request("GET", "/me/library", 200)[0][0]["status"] == "READING"
    bob.request("DELETE", library_path, 204)
    assert request("GET", "/books", 200)[0] == [updated]
    assert len(request("GET", "/me/library", 200)[0]) == 1
    print("PASS: personal library statuses, ratings, clearing, duplicates, removal, and two-user isolation")
    request("POST", "/auth/logout", 204)
    request("GET", "/auth/me", 401)
    print("PASS: login, current user, authenticated mutations, and logout")
    client.login(account)
    return updated, client.opener


@contextmanager
def temporary_database():
    # Exercise the real Compose configuration under a unique project and volume.
    # Never stop, recreate, or remove the developer's "volumes" project.
    password = secrets.token_hex(24)
    environment = dict(os.environ, POSTGRES_PASSWORD=password, POSTGRES_PORT="0")
    project = "volumes-smoke-" + uuid.uuid4().hex
    compose = ["docker", "compose", "--project-name", project,
               "--file", str(ROOT / "compose.yaml")]

    def command(*args):
        return subprocess.check_output(compose + list(args), cwd=ROOT,
                                       env=environment, text=True).strip()

    def start(recreate=False):
        previous = command("ps", "--quiet", "postgres") if recreate else None
        flags = ["--force-recreate"] if recreate else []
        command("up", "--detach", "--wait", "--wait-timeout", "60", *flags, "postgres")
        if recreate:
            assert command("ps", "--quiet", "postgres") != previous
        address = command("port", "postgres", "5432")
        port = address.rsplit(":", 1)[1]
        return dict(os.environ,
                    SPRING_DATASOURCE_URL=f"jdbc:postgresql://127.0.0.1:{port}/volumes",
                    SPRING_DATASOURCE_USERNAME="volumes",
                    SPRING_DATASOURCE_PASSWORD=password,
                    SPRING_PROFILES_ACTIVE="")

    try:
        yield start(), lambda: start(recreate=True)
    finally:
        # Only remove the uniquely named test project's disposable resources.
        command("down", "--volumes")


@contextmanager
def running_api(environment):
    with tempfile.TemporaryFile(mode="w+") as log:
        process = subprocess.Popen(
            ["java", "-jar", str(JAR), "--server.address=127.0.0.1", "--server.port=0"],
            cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                log.seek(0)
                output = log.read()
                match = re.search(r"Tomcat started on port (\d+)", output)
                if match:
                    yield "http://127.0.0.1:" + match.group(1)
                    return
                if process.poll() is not None:
                    raise RuntimeError("API exited before startup")
                time.sleep(0.1)
            raise TimeoutError("API did not start within 30 seconds")
        except Exception:
            log.seek(0)
            print(log.read())
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


def assert_persisted(base_url, expected):
    with urllib.request.urlopen(base_url + "/books/" + expected["id"], timeout=5) as response:
        assert json.load(response) == expected
    with urllib.request.urlopen(base_url + "/books", timeout=5) as response:
        assert json.load(response) == [expected]
    client = ApiSession(base_url)
    client.login({"email": "reader@example.com", "password": "a local smoke test passphrase"})
    assert client.request("GET", "/auth/me", 200)[0]["email"] == "reader@example.com"
    library = client.request("GET", "/me/library", 200)[0]
    assert library == [dict(bookId=expected["id"], status="READING", rating=5,
                            **{key: value for key, value in expected.items() if key != "id"})]


def main():
    if not JAR.is_file():
        raise SystemExit("Run mvn verify first to build the executable JAR.")
    with temporary_database() as (environment, recreate_database):
        with running_api(environment) as base_url:
            expected, previous_session = check_api(base_url)
        with running_api(environment) as base_url:
            assert_persisted(base_url, expected)
            try:
                previous_session.open(base_url + "/auth/me", timeout=5)
            except urllib.error.HTTPError as error:
                assert error.code == 401
                error.close()
            else:
                raise AssertionError("An old session remained authenticated after API restart")
            print("PASS: accounts, books, and personal libraries survive API restart; old sessions do not")
        environment = recreate_database()
        with running_api(environment) as base_url:
            assert_persisted(base_url, expected)
            print("PASS: accounts, books, and personal libraries survive database container recreation")


if __name__ == "__main__":
    main()
