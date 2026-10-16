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
import uuid


ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "target/volumes-0.1.0-SNAPSHOT.jar"


def check_api(base_url):
    def request(method, path, expected_status, body=None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(base_url + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            response = urllib.request.urlopen(req, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            payload = response.read().decode()
            if response.status != expected_status:
                raise AssertionError(f"{method} {path}: {response.status}: {payload}")
            return json.loads(payload), response.headers

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
    return updated


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
        command("up", "--detach", "--wait", "--wait-timeout", "60", *flags)
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


def main():
    if not JAR.is_file():
        raise SystemExit("Run mvn verify first to build the executable JAR.")
    with temporary_database() as (environment, recreate_database):
        with running_api(environment) as base_url:
            expected = check_api(base_url)
        with running_api(environment) as base_url:
            assert_persisted(base_url, expected)
            print("PASS: book identity and updated fields survive an API restart")
        environment = recreate_database()
        with running_api(environment) as base_url:
            assert_persisted(base_url, expected)
            print("PASS: books survive database container recreation with the named volume retained")


if __name__ == "__main__":
    main()
