"""Real browser/session checks. Run via run_browser.py against its disposable database."""
import base64
import uuid
from playwright.sync_api import sync_playwright, expect


def check_auth(url):
    email = f"browser-{uuid.uuid4().hex}@example.com"
    password = "browser test passphrase"
    with sync_playwright() as p:
        browser = p.chromium.launch()
        try:
            context = browser.new_context(viewport={"width": 390, "height": 900})
            page = context.new_page()
            errors = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.route("https://covers.openlibrary.org/**", lambda route: route.fulfill(
                content_type="image/png", body=base64.b64decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")))
            # A network failure checking identity must be recoverable, not treated as signed in.
            page.route("**/auth/me", lambda route: route.abort())
            page.goto(url)
            expect(page.get_by_text("Could not check your session.")).to_be_visible()
            page.unroute("**/auth/me")
            page.get_by_role("button", name="Retry session").click()
            page.get_by_role("link", name="Sign in", exact=True).click()
            expect(page).to_have_url(url + "/login")
            expect(page.get_by_role("heading", name="Welcome back")).to_be_focused()
            page.reload()
            expect(page.get_by_label("Password")).to_have_attribute("autocomplete", "current-password")
            page.goto(url + "/register")
            expect(page.get_by_label("Password")).to_have_attribute("autocomplete", "new-password")
            page.go_back()
            expect(page).to_have_url(url + "/login")
            page.go_forward()
            expect(page).to_have_url(url + "/register")
            page.goto(url + "/login")
            form = page.locator("#account-form")
            form.get_by_role("link", name="Create an account").click()
            form.get_by_label("Email", exact=True).fill(email)
            form.get_by_label("Email", exact=True).fill(email)
            form.get_by_label("Password", exact=True).fill(password)
            form.get_by_role("button", name="Create account", exact=True).click()
            expect(page.get_by_text("Account created. Sign in to add books and change covers.")).to_be_visible()
            assert page.request.get(url + "/auth/me").status == 401
            form.get_by_role("link", name="Create an account").click()
            form.get_by_label("Email", exact=True).fill(email)
            form.get_by_label("Password", exact=True).fill(password)
            form.get_by_role("button", name="Create account", exact=True).click()
            expect(page.get_by_role("alert")).to_contain_text("already has an account")
            form.get_by_role("link", name="I already have an account").click()
            form.get_by_label("Email", exact=True).fill(email)
            form.get_by_label("Password", exact=True).fill("wrong test password")
            form.get_by_role("button", name="Sign in", exact=True).click()
            expect(page.get_by_role("alert")).to_have_text("Incorrect email or password.")
            form.get_by_label("Email", exact=True).fill(email)
            form.get_by_label("Password", exact=True).fill(password)
            pending = []
            page.route("**/auth/login", lambda route: pending.append(route))
            with page.expect_request("**/auth/login"):
                form.get_by_role("button", name="Sign in", exact=True).click()
            expect(form.get_by_role("button", name="Please wait…")).to_be_disabled()
            expect(form.get_by_label("Email", exact=True)).to_be_disabled()
            assert len(pending) == 1
            assert "application/x-www-form-urlencoded" in pending[0].request.headers["content-type"]
            assert pending[0].request.headers.get("x-csrf-token")
            pending[0].continue_()
            page.unroute("**/auth/login")
            expect(page.get_by_role("button", name="Sign out", exact=True)).to_be_visible()
            assert page.evaluate("localStorage.length === 0 && sessionStorage.length === 0")
            page.reload()
            expect(page.get_by_text(email, exact=True)).to_be_visible()
            print("PASS: session recovery, registration without auto-login, duplicate email, wrong password, pending state, persistent login")

            # Registration and login rotated CSRF/session data. A successful mutation proves the new token works.
            page.get_by_role("button", name="Add a book", exact=True).click()
            page.get_by_label("Title", exact=True).fill("The Brothers Karamazov")
            page.get_by_label("Author", exact=True).fill("Fyodor Dostoevsky")
            page.get_by_label("Genre", exact=True).fill("Fiction")
            page.get_by_label("Pages", exact=True).fill("1013")
            page.get_by_role("button", name="Add to catalog").click()
            page.get_by_role("button", name="Add ISBN for The Brothers Karamazov", exact=True).click()
            page.get_by_label("Edition ISBN", exact=True).fill("invalid-isbn")
            page.get_by_role("button", name="Save ISBN", exact=True).click()
            expect(page.get_by_role("alert")).to_contain_text("valid ISBN")
            page.get_by_label("Edition ISBN", exact=True).fill("0-14-044924-8")
            page.get_by_role("button", name="Save ISBN", exact=True).click()
            # Title search and ISBN lookup share a provider limit. The UI exposes a retry if needed.
            notice = page.get_by_text("Edition saved. The cover has been updated.", exact=True)
            try:
                expect(notice).to_be_visible(timeout=2000)
            except AssertionError:
                expect(page.get_by_role("alert")).to_contain_text("wait a second")
                page.wait_for_timeout(1100)
                page.get_by_role("button", name="Save ISBN", exact=True).click()
                expect(notice).to_be_visible()
            page.wait_for_function("document.querySelector('li img')?.naturalWidth > 0")
            page.reload()
            page.get_by_role("button", name="Edit ISBN for The Brothers Karamazov", exact=True).click()
            expect(page.get_by_label("Edition ISBN", exact=True)).to_have_value("9780140449242")
            page.get_by_role("button", name="Remove ISBN", exact=True).click()
            expect(page.get_by_text("ISBN and cover removed. The book is still in the catalog.")).to_be_visible()
            page.reload()
            expect(page.get_by_role("heading", name="The Brothers Karamazov", exact=True)).to_be_visible()
            expect(page.locator("li img")).to_have_count(0)
            assert page.evaluate("document.documentElement.scrollWidth <= innerWidth")
            print("PASS: real CSRF-protected create, invalid ISBN, cover save/reload/removal and narrow layout")

            page.get_by_role("button", name="Sign out", exact=True).click()
            expect(page.get_by_role("link", name="Sign in", exact=True)).to_be_visible()
            assert page.request.get(url + "/auth/me").status == 401
            page.reload()
            expect(page.get_by_role("link", name="Sign in", exact=True)).to_be_visible()
            expect(page.get_by_role("button", name="Add a book", exact=True)).to_have_count(0)
            # Session loss in another tab must hide editing controls when the next write fails.
            page.get_by_role("link", name="Sign in", exact=True).click()
            form.get_by_label("Email", exact=True).fill(email)
            form.get_by_label("Email", exact=True).fill(email)
            form.get_by_label("Password", exact=True).fill(password)
            form.get_by_role("button", name="Sign in", exact=True).click()
            page.get_by_role("button", name="Add ISBN for The Brothers Karamazov", exact=True).click()
            page.get_by_label("Edition ISBN", exact=True).fill("9780140449242")
            context.clear_cookies()
            page.get_by_role("button", name="Save ISBN", exact=True).click()
            expect(page.get_by_role("link", name="Sign in", exact=True)).to_be_visible()
            expect(page.get_by_role("button", name="Save ISBN", exact=True)).to_have_count(0)
            assert not errors, errors
            print("PASS: logout, anonymous reload and expired-session recovery")
        finally:
            browser.close()
