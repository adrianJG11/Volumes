# Verify the full Docker stack in a disposable project; never use development data.
import argparse
from html.parser import HTMLParser
import os
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

from smoke_api import ROOT, check_api, assert_persisted


class Assets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = []

    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key in ('src', 'href') and value and value.startswith('/assets/'):
                self.paths.append(value)


def fetch(url):
    with urllib.request.urlopen(url, timeout=10) as response:
        return response.read(), response.headers


def check_frontend(url):
    document, headers = fetch(url + '/')
    assert b'<div id="root">' in document
    assert 'no-store' in headers.get('Cache-Control', '')
    for path in ['/login', '/register', '/library']:
        assert fetch(url + path)[0] == document
    assets = Assets()
    assets.feed(document.decode())
    assert assets.paths
    for path in assets.paths:
        body, headers = fetch(url + path)
        assert body and 'immutable' in ', '.join(headers.get_all('Cache-Control', []))
        assert 'text/html' not in headers.get('Content-Type', '')
    try:
        fetch(url + '/assets/does-not-exist.js')
    except urllib.error.HTTPError as error:
        assert error.code == 404
        error.close()
    else:
        raise AssertionError('Missing assets must return 404, not the SPA HTML')
    assert fetch(url + '/healthz')[0] == b'ok'
    print('PASS: nginx serves built assets and direct React routes; missing assets return 404', flush=True)


def wait_for_api(url):
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        try:
            fetch(url + '/books')
            return
        except (urllib.error.URLError, TimeoutError):
            time.sleep(1)
    raise TimeoutError('nginx did not reconnect to the API')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--no-build', action='store_true')
    parser.add_argument('--browser', action='store_true', help='Also run the existing Chromium login journey')
    args = parser.parse_args()
    project = 'volumes-container-check-' + uuid.uuid4().hex
    environment = dict(os.environ, POSTGRES_PASSWORD=secrets.token_hex(24), POSTGRES_PORT='0', WEB_PORT='0')
    compose = ['docker', 'compose', '--project-name', project, '--file', str(ROOT / 'compose.yaml')]

    def command(*arguments, capture=False):
        result = subprocess.run(compose + list(arguments), cwd=ROOT, env=environment, check=True,
                                stdout=subprocess.PIPE if capture else None, text=True, timeout=900)
        return result.stdout.strip() if capture else None

    try:
        command('up', '--detach', '--wait', '--wait-timeout', '180',
                '--no-build' if args.no_build else '--build')
        address = command('port', 'web', '8080', capture=True)
        url = 'http://127.0.0.1:' + address.rsplit(':', 1)[1]
        for service in ['api', 'web']:
            assert command('exec', '-T', service, 'id', '-u', capture=True) != '0'
        check_frontend(url)
        expected, previous_session = check_api(url)
        if args.browser:
            sys.path.insert(0, str(ROOT / 'frontend/tests'))
            from auth_browser import check_auth
            check_auth(url)
        command('up', '--detach', '--wait', '--wait-timeout', '180', '--force-recreate', 'postgres', 'api')
        wait_for_api(url)
        assert_persisted(url, expected)
        try:
            previous_session.open(url + '/auth/me', timeout=5)
        except urllib.error.HTTPError as error:
            assert error.code == 401
            error.close()
        else:
            raise AssertionError('Old session survived API replacement')
        check_frontend(url)
        print('PASS: non-root runtimes, nginx session/CSRF proxy, private ratings, persistent data and API DNS recovery', flush=True)
    finally:
        # Explicit project + explicit file: only this run's disposable containers and volume.
        command('down', '--volumes')


if __name__ == '__main__':
    main()
